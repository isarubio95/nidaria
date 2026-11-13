// Texturas pintadas por código, como los lienzos de la versión web: el
// papel de la pared, las tablas del suelo, el cielo de la ventana, la
// sombra redonda y la «z» de los ronquidos.

package com.nidaria.app.scene

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import com.nidaria.app.gl.Texture
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class Painter(private val black: Typeface?) {
    private fun color(hex: String): Int = android.graphics.Color.parseColor(hex)

    private fun bytesOf(bitmap: Bitmap): ByteArray {
        val w = bitmap.width
        val h = bitmap.height
        val argb = IntArray(w * h)
        bitmap.getPixels(argb, 0, w, 0, 0, w, h)
        val out = ByteArray(w * h * 4)
        for (i in argb.indices) {
            val c = argb[i]
            out[i * 4] = (c shr 16).toByte()
            out[i * 4 + 1] = (c shr 8).toByte()
            out[i * 4 + 2] = c.toByte()
            out[i * 4 + 3] = (c ushr 24).toByte()
        }
        return out
    }

    private fun paint(width: Int, height: Int, draw: (Canvas, Paint) -> Unit): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        draw(Canvas(bitmap), Paint(Paint.ANTI_ALIAS_FLAG))
        val bytes = bytesOf(bitmap)
        bitmap.recycle()
        return bytes
    }

    fun wall(): Texture {
        val texture = Texture(96, 96, repeat = true, mipmaps = true)
        texture.update(
            paint(96, 96) { canvas, p ->
                canvas.drawColor(color("#bfe3ff"))
                p.color = color("#cdeaff")
                for ((x, y) in listOf(48f to 48f, 0f to 0f, 96f to 0f, 0f to 96f, 96f to 96f)) canvas.drawCircle(x, y, 7f, p)
            },
        )
        return texture
    }

    fun floor(): Texture {
        val texture = Texture(256, 256, repeat = true, mipmaps = true)
        texture.update(
            paint(256, 256) { canvas, p ->
                canvas.drawColor(color("#fff8ec"))
                p.color = color("#f5ead6")
                p.isAntiAlias = false
                var x = 0f
                while (x < 256f) {
                    canvas.drawRect(x, 0f, x + 3f, 256f, p)
                    x += 64f
                }
            },
        )
        return texture
    }

    // Sombra redonda bajo la criatura: marrón, más densa en el centro.
    fun blob(): Texture {
        val size = 64
        val out = ByteArray(size * size * 4)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val dx = x + 0.5f - 32f
                val dy = y + 0.5f - 32f
                val t = ((sqrt(dx * dx + dy * dy) - 2f) / 30f).coerceIn(0f, 1f)
                val o = (y * size + x) * 4
                out[o] = 91
                out[o + 1] = 58
                out[o + 2] = 41
                out[o + 3] = (0.42f * (1f - t) * 255f + 0.5f).toInt().toByte()
            }
        }
        val texture = Texture(size, size)
        texture.update(out)
        return texture
    }

    fun z(): Texture {
        val mask = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = android.graphics.Color.WHITE
        p.textSize = 54f
        p.textAlign = Paint.Align.CENTER
        p.typeface = black ?: Typeface.DEFAULT_BOLD
        val metrics = p.fontMetrics
        Canvas(mask).drawText("z", 32f, 30f - (metrics.ascent + metrics.descent) / 2f, p)
        val argb = IntArray(64 * 64)
        mask.getPixels(argb, 0, 64, 0, 0, 64, 64)
        mask.recycle()
        val out = ByteArray(64 * 64 * 4)
        for (i in argb.indices) {
            out[i * 4] = 0xff.toByte()
            out[i * 4 + 1] = 0xfd.toByte()
            out[i * 4 + 2] = 0xf8.toByte()
            out[i * 4 + 3] = (argb[i] ushr 24).toByte()
        }
        val texture = Texture(64, 64)
        texture.update(out)
        return texture
    }

    fun newSky(): Texture = Texture(128, 160)

    // El cielo se repinta a menudo cuando llueve o nieva: su lienzo y sus
    // bytes se reutilizan en vez de crearse en cada dibujo.
    private val skyBitmap by lazy { Bitmap.createBitmap(128, 160, Bitmap.Config.ARGB_8888) }
    private val skyArgb = IntArray(128 * 160)
    private val skyBytes = Array(2) { ByteArray(128 * 160 * 4) }
    private var skyFlip = 0

    private fun paintSky(draw: (Canvas, Paint) -> Unit): ByteArray {
        val bitmap = skyBitmap
        bitmap.eraseColor(0)
        draw(Canvas(bitmap), Paint(Paint.ANTI_ALIAS_FLAG))
        bitmap.getPixels(skyArgb, 0, 128, 0, 0, 128, 160)
        // Dos búferes alternos: el que se sube a la GPU no se pisa mientras.
        skyFlip = 1 - skyFlip
        val out = skyBytes[skyFlip]
        for (i in skyArgb.indices) {
            val c = skyArgb[i]
            out[i * 4] = (c shr 16).toByte()
            out[i * 4 + 1] = (c shr 8).toByte()
            out[i * 4 + 2] = c.toByte()
            out[i * 4 + 3] = (c ushr 24).toByte()
        }
        return out
    }

    // Cielo de la ventana según la hora del jugador y, si lo ha activado,
    // el tiempo que hace donde está. `t` (segundos) mueve lluvia y nieve.
    // Devuelve la fase del día.
    fun sky(texture: Texture, now: Calendar = Calendar.getInstance(), weather: String? = null, t: Float = 0f, still: Boolean = false): String {
        val w = 128f
        val h = 160f
        val hour = now.get(Calendar.HOUR_OF_DAY) + now.get(Calendar.MINUTE) / 60f
        val phase = if (hour < 6f || hour >= 21f) "night" else if (hour < 8.5f) "dawn" else if (hour < 18.5f) "day" else "dusk"
        val grey = weather in GREY_SKIES
        val palette = if (weather == "tormenta") STORM_SKY else if (grey) GREY_SKY else CLEAR_SKY
        val colors = palette.getValue(phase)
        texture.update(
            paintSky { canvas, p ->
                p.shader = LinearGradient(0f, 0f, 0f, h, color(colors.first), color(colors.second), Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w, h, p)
                p.shader = null
                fun cloud(x: Float, y: Float, s: Float, hex: String) {
                    p.color = color(hex)
                    val path = Path()
                    path.addCircle(x, y, 10f * s, Path.Direction.CW)
                    path.addCircle(x + 12f * s, y - 5f * s, 12f * s, Path.Direction.CW)
                    path.addCircle(x + 26f * s, y, 10f * s, Path.Direction.CW)
                    path.addRect(x, y, x + 26f * s, y + 10f * s, Path.Direction.CW)
                    canvas.drawPath(path, p)
                }
                if (grey) {
                    drawWeather(canvas, p, w, h, phase, weather!!, t, still, ::cloud)
                    return@paintSky
                }
                when (phase) {
                    "night" -> {
                        p.color = color("#fff8d6")
                        for (i in 0 until 16) {
                            val x = (sin(i * 12.9898) * 43758.5453) % 1.0
                            val y = (sin(i * 78.233) * 12543.123) % 1.0
                            canvas.drawCircle((abs(x) * w).toFloat(), (abs(y) * h * 0.8).toFloat(), 1f + (i % 3) * 0.5f, p)
                        }
                        canvas.drawCircle(w * 0.66f, h * 0.3f, 15f, p)
                        p.color = color("#263468")
                        canvas.drawCircle(w * 0.66f + 7f, h * 0.3f - 4f, 13f, p)
                    }
                    "day" -> {
                        val progress = (hour - 8.5f) / 10f
                        p.color = color("#fff3b0")
                        canvas.drawCircle(w * (0.2f + progress * 0.6f), h * (0.3f - sin(progress * PI.toFloat()) * 0.12f), 15f, p)
                        cloud(w * 0.12f, h * 0.56f, 1f, "#ffffff")
                        cloud(w * 0.56f, h * 0.74f, 0.8f, "#ffffff")
                        if (weather == "nubes") {
                            cloud(w * 0.42f, h * 0.3f, 1.1f, "#ffffff")
                            cloud(w * 0.02f, h * 0.18f, 0.8f, "#f4f8fc")
                        }
                    }
                    else -> {
                        p.color = color(if (phase == "dawn") "#fff0c4" else "#ffe0a6")
                        canvas.drawCircle(w * (if (phase == "dawn") 0.3f else 0.7f), h * 0.82f, 17f, p)
                        cloud(w * 0.2f, h * 0.5f, 0.9f, if (phase == "dawn") "#fff1e6" else "#ffd0c0")
                        if (weather == "nubes") cloud(w * 0.5f, h * 0.28f, 1f, if (phase == "dawn") "#fff1e6" else "#ffd0c0")
                    }
                }
                if (weather == "nubes" && phase == "night") {
                    cloud(w * 0.1f, h * 0.5f, 1.1f, "#3a4677")
                    cloud(w * 0.5f, h * 0.42f, 0.9f, "#3a4677")
                }
            },
        )
        return phase
    }

    // Cielo tapado: nubes grises, y encima lluvia, nieve, niebla o tormenta.
    private fun drawWeather(
        canvas: Canvas, p: Paint, w: Float, h: Float, phase: String, weather: String, t: Float, still: Boolean,
        cloud: (Float, Float, Float, String) -> Unit,
    ) {
        val dark = phase == "night"
        val storm = weather == "tormenta"
        val tone = if (storm) (if (dark) "#2b3049" else "#8a93a3") else if (dark) "#3c4566" else "#eef2f6"
        val shade = if (storm) (if (dark) "#22263b" else "#7a8394") else if (dark) "#323a58" else "#dde3ea"
        cloud(w * -0.08f, h * 0.12f, 1.3f, shade)
        cloud(w * 0.42f, h * 0.06f, 1.4f, shade)
        cloud(w * 0.08f, h * 0.3f, 1.2f, tone)
        cloud(w * 0.55f, h * 0.26f, 1.1f, tone)

        when (weather) {
            "lluvia", "tormenta" -> {
                p.color = if (dark) android.graphics.Color.argb(140, 200, 214, 255) else android.graphics.Color.argb(191, 255, 255, 255)
                p.strokeWidth = 1.6f
                p.strokeCap = Paint.Cap.ROUND
                for ((a, b) in DROPS) {
                    val x = ((a * w + t * 18f) % w)
                    val y = ((b * h + t * h * 1.4f) % h)
                    canvas.drawLine(x, y, x - 3f, y + 10f, p)
                }
            }
            "nieve" -> {
                p.color = android.graphics.Color.WHITE
                DROPS.forEachIndexed { i, (a, b) ->
                    val y = (b * h + t * 16f * (0.7f + a * 0.6f)) % h
                    val x = a * w + sin(t * 0.9f + i) * 4f
                    canvas.drawCircle(x, y, 1.6f + (i % 3) * 0.6f, p)
                }
            }
            "niebla" -> {
                p.color = if (dark) android.graphics.Color.argb(89, 150, 160, 190) else android.graphics.Color.argb(128, 255, 255, 255)
                for (i in 0 until 4) canvas.drawRect(0f, h * (0.3f + i * 0.18f), w, h * (0.4f + i * 0.18f), p)
                p.color = if (dark) android.graphics.Color.argb(51, 150, 160, 190) else android.graphics.Color.argb(77, 255, 255, 255)
                canvas.drawRect(0f, 0f, w, h, p)
            }
        }
        // Un relámpago suave cada pocos segundos. Sin él si se pide menos movimiento.
        if (storm && !still && t % 9f < 0.14f) {
            p.color = android.graphics.Color.argb(115, 255, 255, 255)
            canvas.drawRect(0f, 0f, w, h, p)
        }
    }

    companion object {
        val GREY_SKIES = setOf("cubierto", "niebla", "lluvia", "nieve", "tormenta")
        val MOVING_SKIES = setOf("lluvia", "nieve", "tormenta")
        private val CLEAR_SKY = mapOf(
            "night" to ("#1c2756" to "#44539a"), "dawn" to ("#86b8ee" to "#ffd9b8"),
            "day" to ("#62b8f2" to "#d4f0ff"), "dusk" to ("#5f6fc4" to "#ffb48c"),
        )
        private val GREY_SKY = mapOf(
            "night" to ("#232a45" to "#3e4668"), "dawn" to ("#94a3bd" to "#d6cbc4"),
            "day" to ("#a3b6ca" to "#e1e8ef"), "dusk" to ("#6f7298" to "#c8a99f"),
        )
        private val STORM_SKY = mapOf(
            "night" to ("#191c2e" to "#2d3250"), "dawn" to ("#6c7789" to "#a7acb6"),
            "day" to ("#75839a" to "#b2bcc8"), "dusk" to ("#55577a" to "#8e8193"),
        )
        // Posiciones fijas de gotas y copos: así no tiemblan entre dibujos.
        private val DROPS = List(28) { i ->
            (abs(sin(i * 12.9898) * 43758.5453) % 1.0).toFloat() to (abs(sin(i * 78.233) * 12543.123) % 1.0).toFloat()
        }
    }
}
