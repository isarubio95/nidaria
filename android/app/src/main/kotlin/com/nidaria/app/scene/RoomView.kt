// La vista de Android que dibuja la habitación. Es dueña del hilo de
// dibujo: todo lo que toca la escena se encola hacia él, y lo que la
// escena avisa vuelve al hilo principal.

package com.nidaria.app.scene

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.MotionEvent
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.res.ResourcesCompat
import com.nidaria.app.R
import com.nidaria.app.gl.Renderer
import com.nidaria.app.data.Decor
import com.nidaria.app.rules.Creature
import com.nidaria.app.rules.EggLook
import com.nidaria.app.sculpt.Sculptor
import java.nio.ByteBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

@SuppressLint("ViewConstructor")
class RoomView(context: Context) : GLSurfaceView(context), GLSurfaceView.Renderer, Room.Listener {
    private val main = Handler(Looper.getMainLooper())
    private val density = resources.displayMetrics.density
    // Píxeles por unidad de la escena: los dp de la interfaz, que con el
    // zoom de la pantalla (ui/Zoom.kt) son más pequeños.
    @Volatile private var unit = density
    private val renderer = Renderer()
    private val studio = ThumbStudio()
    private val jobs = ArrayDeque<ThumbSpec>()
    private class PhotoJob(val width: Int, val height: Int, val done: (Bitmap?) -> Unit)
    private val photos = ArrayDeque<PhotoJob>()
    private val startNanos = SystemClock.elapsedRealtimeNanos()
    private var widthPx = 1
    private var heightPx = 1
    private var failed = false

    // Con las animaciones del sistema apagadas, la escena tampoco se mueve.
    val reduceMotion: Boolean = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

    private val room = Room(Painter(ResourcesCompat.getFont(context, R.font.zen_maru_gothic_black)), reduceMotion)

    // Avisos de la escena, ya en el hilo principal.
    var onPet: (() -> Unit)? = null
    var onTap: (() -> Unit)? = null
    var onEggTap: (() -> Unit)? = null
    var onEggPick: ((Int) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    // Lo que la interfaz necesita leer sin esperar al hilo de dibujo.
    @Volatile var headX = 0f
        private set
    @Volatile var headY = 0f
        private set
    @Volatile var hasActor = false
        private set

    init {
        // Las miniaturas que se quedaron a medias en una vista anterior
        // (la actividad se puede recrear) hay que poder pedirlas otra vez.
        Thumbs.pending.clear()
        renderer.setEnvironment(context.assets.open("env.bin").use { it.readBytes() })
        // Los modelos de criatura (.glb) van en los recursos de la app.
        val assets = context.applicationContext.assets
        Glb.open = { name -> assets.open(name).use { it.readBytes() } }
        // Esculpir va en hilos aparte, con las mallas guardadas en la caché.
        Sculptor.start(context.cacheDir)
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        holder.setFormat(PixelFormat.RGBA_8888)
        preserveEGLContextOnPause = true
        setRenderer(this)
        renderMode = RENDERMODE_CONTINUOUSLY
        room.listener = this
    }

    // ── Hilo de dibujo ──

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        try {
            renderer.onContextCreated()
        } catch (error: RuntimeException) {
            fail(error)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        widthPx = width
        heightPx = height
        // El búfer puede ser más pequeño que la vista (ver onSizeChanged);
        // la escena se encuadra con el tamaño de la vista, en dp.
        room.resize(this.width / unit, this.height / unit)
    }

    // Como la web, que no pasa de dos píxeles por punto: en pantallas más
    // densas el 3D se dibuja a 2× y el sistema lo amplía. Se nota poco y
    // ahorra mucho trabajo a la tarjeta gráfica.
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        if (density > MAX_PIXEL_RATIO) {
            val scale = MAX_PIXEL_RATIO / density
            holder.setFixedSize((w * scale).toInt(), (h * scale).toInt())
        } else {
            holder.setSizeFromLayout()
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        if (failed) return
        try {
            val t = (SystemClock.elapsedRealtimeNanos() - startNanos) / 1e9f
            room.tick(t)
            // Miniaturas: se encargan unas pocas a la vez y se dibujan
            // cuando su malla está esculpida.
            while (studio.busy < THUMBS_IN_FLIGHT) {
                val spec = synchronized(jobs) { jobs.removeFirstOrNull() } ?: break
                if (!studio.start(spec)) main.post { Thumbs.pending.remove(spec.key) }
            }
            for ((spec, bitmap) in studio.finish(renderer, THUMBS_PER_FRAME)) {
                main.post {
                    Thumbs.pending.remove(spec.key)
                    if (bitmap != null) Thumbs.images[spec.key] = bitmap.asImageBitmap()
                }
            }
            if (studio.busy > 0 || synchronized(jobs) { jobs.isNotEmpty() }) requestRender()
            while (true) {
                val job = synchronized(photos) { photos.removeFirstOrNull() } ?: break
                val bitmap = photo(job.width, job.height)
                main.post { job.done(bitmap) }
            }
            renderer.render(room.scene, room.camera, widthPx, heightPx)
            val head = room.headPoint(0.16f)
            headX = head.first
            headY = head.second
            hasActor = room.hasActor()
        } catch (error: RuntimeException) {
            fail(error)
        }
    }

    private fun photo(width: Int, height: Int): Bitmap? = try {
        val pixels = renderer.renderPhoto(room.scene, room.photoCamera(width, height), width, height)
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { copyPixelsFromBuffer(ByteBuffer.wrap(pixels)) }
    } catch (error: RuntimeException) {
        Log.w("Nidaria", "No se ha podido hacer la foto", error)
        null
    }

    private fun fail(error: RuntimeException) {
        failed = true
        Log.e("Nidaria", "No se puede dibujar la escena", error)
        main.post { onError?.invoke(error.message ?: "Error de gráficos") }
    }

    override fun onPet() { main.post { onPet?.invoke() } }
    override fun onTap() { main.post { onTap?.invoke() } }
    override fun onEggTap() { main.post { onEggTap?.invoke() } }
    override fun onEggPick(index: Int) { main.post { onEggPick?.invoke(index) } }

    // ── Hilo principal ──

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val id = event.getPointerId(event.actionIndex)
        val x = event.getX(event.actionIndex) / unit
        val y = event.getY(event.actionIndex) / unit
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> queueEvent { room.pointerDown(id, x, y) }
            MotionEvent.ACTION_MOVE -> {
                val firstId = event.getPointerId(0)
                val fx = event.getX(0) / unit
                val fy = event.getY(0) / unit
                queueEvent { room.pointerMove(firstId, fx, fy) }
            }
            MotionEvent.ACTION_UP -> queueEvent { room.pointerUp(id, x, y) }
            // Con un segundo dedo es un pellizco (el zoom).
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> queueEvent { room.pointerCancel() }
        }
        return true
    }

    // Ejecuta algo sobre la escena en su hilo.
    fun post(block: Room.() -> Unit) = queueEvent { room.block() }

    // Lanza una secuencia y espera a que termine.
    private suspend fun await(block: Room.(done: () -> Unit) -> Unit) = suspendCancellableCoroutine { continuation ->
        queueEvent {
            room.block { main.post { if (continuation.isActive) continuation.resume(Unit) } }
        }
    }

    fun forget() = post { forget() }
    fun showCreature(view: Creature) = post { showCreature(view) }
    fun showDemo() = post { showDemo() }
    fun showEmpty() = post { showEmpty() }
    fun setWarmable(on: Boolean) = post { setWarmable(on) }
    fun showEggs(offer: List<EggLook>) = post { showEggs(offer) }
    fun selectEgg(index: Int) = post { selectEgg(index) }
    fun setEggProgress(value: Float) = post { setEggProgress(value) }
    fun setMood(mood: String) = post { setMood(mood) }
    fun say(seconds: Float) = post { say(seconds) }
    fun setNight(on: Boolean) = post { setNight(on) }
    fun setDecor(decor: Decor) = post { setDecor(decor) }
    fun setWeather(kind: String?) = post { setWeather(kind) }
    fun setFocus(top: Float, bottom: Float, animate: Boolean = true) = post { setFocus(top, bottom, animate) }

    fun setZoom(level: Float) {
        unit = density * level
        val w = width / unit
        val h = height / unit
        post { setZoom(level, w, h) }
    }

    fun bath() = post { bath() }
    fun heal() = post { heal() }
    fun cheer(won: Boolean) = post { cheer(won) }
    fun trick(kind: String) = post { trick(kind) }

    // En pausa (durante un minijuego) la escena deja de dibujarse.
    fun setPaused(value: Boolean) {
        post { paused = value }
        renderMode = if (value) RENDERMODE_WHEN_DIRTY else RENDERMODE_CONTINUOUSLY
    }

    suspend fun feed(itemId: String, reaction: String?) = await { done -> feed(itemId, reaction, done) }
    suspend fun playToy(itemId: String) = await { done -> playToy(itemId, done) }
    suspend fun hatch(view: Creature) = await { done -> hatch(view, done) }
    suspend fun evolve(view: Creature) = await { done -> evolve(view, done) }
    suspend fun farewell() = await { done -> farewell(done) }

    // Foto de la habitación tal cual, de `width`×`height` píxeles, para la
    // tarjeta de compartir. Se hace en el siguiente dibujo; null si falla.
    suspend fun snapshot(width: Int, height: Int): Bitmap? = suspendCancellableCoroutine { continuation ->
        synchronized(photos) {
            photos.addLast(PhotoJob(width, height) { bitmap -> if (continuation.isActive) continuation.resume(bitmap) })
        }
        requestRender()
    }

    // Pide una miniatura. Aparecerá en `Thumbs.images` cuando esté.
    fun requestThumb(spec: ThumbSpec) {
        val key = spec.key
        if (key in Thumbs.images || !Thumbs.pending.add(key)) return
        synchronized(jobs) { jobs.addLast(spec) }
        requestRender()
    }

    companion object {
        private const val THUMBS_PER_FRAME = 2
        private const val THUMBS_IN_FLIGHT = 4
        private const val MAX_PIXEL_RATIO = 2f
    }
}
