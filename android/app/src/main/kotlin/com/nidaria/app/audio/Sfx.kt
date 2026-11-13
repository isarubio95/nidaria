// Reproduce los efectos de sonido (ver Sounds.kt). Todos se generan al
// arrancar, en segundo plano, y se guardan en memoria.
//
// Suena por una sola pista de audio con un mezclador propio: así se
// pueden solapar sonidos rápidos (pompas, mordiscos) sin crear una pista
// por cada uno. Cuando no suena nada, la pista se para.
//
// Se puede llamar desde cualquier hilo: la escena 3D pide sonidos desde
// el suyo. El ajuste es de este teléfono y viene activado, como en la web.

package com.nidaria.app.audio

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import androidx.core.content.edit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock
import kotlin.math.max
import kotlin.math.roundToInt

object Sfx {
    private const val RATE = 44100
    private const val CHUNK = 256
    private const val KEY = "sonido"

    private class Voice(val samples: FloatArray, val volume: Float, var wait: Int) {
        var position = 0
    }

    private val cache = ConcurrentHashMap<String, FloatArray>()
    private val voices = ArrayList<Voice>()
    private val lock = ReentrantLock()
    private val ready = lock.newCondition()
    private var prefs: SharedPreferences? = null
    private var track: AudioTrack? = null
    private var mixer: Thread? = null
    private var broken = false

    @Volatile var enabled = true
        private set

    // Con la app en segundo plano no suena nada.
    @Volatile var foreground = true

    fun init(context: Context) {
        if (prefs != null) return
        val store = context.applicationContext.getSharedPreferences("nidaria", Context.MODE_PRIVATE)
        prefs = store
        enabled = store.getBoolean(KEY, true)
        thread(name = "nidaria-sonidos", isDaemon = true, priority = Thread.MIN_PRIORITY) {
            for (name in SOUND_NAMES) samplesOf(name)
        }
    }

    fun setEnabled(on: Boolean) {
        enabled = on
        prefs?.edit { putBoolean(KEY, on) }
        if (!on) lock.withLock { voices.clear() }
    }

    private fun samplesOf(name: String): FloatArray? =
        cache[name] ?: renderSound(name, RATE)?.also { cache[name] = it }

    // play("monedas"), play("punto", volume = 0.6f), play("crac", delay = 0.3f)
    fun play(name: String, volume: Float = 1f, delay: Float = 0f) {
        if (!enabled || !foreground || broken) return
        val samples = cache[name]
        if (samples == null) {
            // Aún no está hecho: se hace aparte y suena en cuanto esté.
            thread(isDaemon = true) { samplesOf(name)?.let { start(it, volume, delay) } }
            return
        }
        start(samples, volume, delay)
    }

    private fun start(samples: FloatArray, volume: Float, delay: Float) {
        lock.withLock {
            if (!ensureMixer()) return
            voices.add(Voice(samples, volume, max(0, (delay * RATE).roundToInt())))
            ready.signal()
        }
    }

    private fun ensureMixer(): Boolean {
        if (mixer != null) return true
        val created = try {
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setSampleRate(RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val minimum = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(format)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .setBufferSizeInBytes(max(minimum, CHUNK * 4 * 2))
                .build()
        } catch (error: RuntimeException) {
            Log.w("Nidaria", "Sin sonido", error)
            null
        }
        if (created == null || created.state != AudioTrack.STATE_INITIALIZED) {
            created?.release()
            broken = true
            return false
        }
        track = created
        mixer = thread(name = "nidaria-mezclador", isDaemon = true, priority = Thread.MAX_PRIORITY) { mix(created) }
        return true
    }

    private fun mix(track: AudioTrack) {
        val buffer = FloatArray(CHUNK)
        var playing = false
        while (true) {
            lock.withLock {
                while (voices.isEmpty()) {
                    if (playing) {
                        // Lo ya escrito termina de sonar y la pista se para.
                        track.stop()
                        playing = false
                    }
                    ready.await()
                }
                buffer.fill(0f)
                val iterator = voices.iterator()
                while (iterator.hasNext()) {
                    val voice = iterator.next()
                    var i = 0
                    if (voice.wait > 0) {
                        val skip = minOf(voice.wait, CHUNK)
                        voice.wait -= skip
                        i = skip
                    }
                    while (i < CHUNK && voice.position < voice.samples.size) {
                        buffer[i] += voice.samples[voice.position++] * voice.volume
                        i++
                    }
                    if (voice.wait == 0 && voice.position >= voice.samples.size) iterator.remove()
                }
            }
            for (i in 0 until CHUNK) buffer[i] = buffer[i].coerceIn(-1f, 1f)
            if (!playing) {
                track.play()
                playing = true
            }
            track.write(buffer, 0, CHUNK, AudioTrack.WRITE_BLOCKING)
        }
    }
}
