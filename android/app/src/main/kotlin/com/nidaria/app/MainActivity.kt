package com.nidaria.app

import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.nidaria.app.notify.Notifier
import com.nidaria.app.scene.RoomView
import com.nidaria.app.ui.App
import com.nidaria.app.ui.Zoom
import kotlinx.coroutines.CompletableDeferred

// Una sola pantalla: la habitación 3D al fondo y la interfaz encima.
class MainActivity : ComponentActivity() {
    private lateinit var room: RoomView
    private lateinit var game: Game
    private lateinit var zoom: Zoom

    // El pellizco del zoom, en cualquier sitio menos en un minijuego, donde
    // los dos dedos son para jugar. Mientras dura, la interfaz y la escena
    // no reciben los dedos: lo que tenían empezado se cancela.
    private lateinit var pinch: ScaleGestureDetector
    private var pinching = false

    // Una petición de permiso cada vez; `Game` espera su respuesta.
    private var permissionWait: CompletableDeferred<Boolean>? = null
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionWait?.complete(granted)
        permissionWait = null
    }

    private suspend fun askPermission(name: String): Boolean {
        if (ContextCompat.checkSelfPermission(this, name) == PackageManager.PERMISSION_GRANTED) return true
        permissionWait?.complete(false)
        val wait = CompletableDeferred<Boolean>()
        permissionWait = wait
        permissionLauncher.launch(name)
        return wait.await()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val clear = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = clear, navigationBarStyle = clear)
        super.onCreate(savedInstanceState)
        room = RoomView(this)
        zoom = Zoom(this)
        room.setZoom(zoom.level)
        zoom.onChange = room::setZoom
        pinch = ScaleGestureDetector(
            this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector) = game.play == null
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    pinching = true
                    zoom.set(zoom.level * detector.scaleFactor)
                    return true
                }
            },
        ).apply { isQuickScaleEnabled = false }
        game = Game(applicationContext, room, lifecycleScope)
        game.askPermission = ::askPermission
        Notifier.ensureChannel(this)
        // Las alarmas se pierden si se fuerza el cierre de la app: se vuelven a poner.
        if (Notifier.enabled(this)) Notifier.schedule(this)
        val ui = ComposeView(this).apply { setContent { App(game, zoom) } }
        setContentView(
            FrameLayout(this).apply {
                addView(room, MATCH_PARENT, MATCH_PARENT)
                addView(ui, MATCH_PARENT, MATCH_PARENT)
            },
        )
        game.boot()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) pinching = false
        val was = pinching
        pinch.onTouchEvent(event)
        if (pinching) {
            if (!was) {
                val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                super.dispatchTouchEvent(cancel)
                cancel.recycle()
            }
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                pinching = false
                zoom.save()
            }
            return true
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onResume() {
        super.onResume()
        // Con el juego a la vista no se avisa, y lo que quedara en la bandeja sobra.
        Notifier.visible = true
        Notifier.clear(this)
        room.onResume()
        game.onResume()
    }

    override fun onPause() {
        Notifier.visible = false
        game.onPause()
        room.onPause()
        super.onPause()
    }
}
