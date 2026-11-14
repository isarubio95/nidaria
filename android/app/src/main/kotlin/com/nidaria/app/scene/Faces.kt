// La cara pintada de las criaturas, como en creature.js: bocas, ojos
// cerrados, coloretes y cejas. Se dibujan una vez y las comparten todas.

package com.nidaria.app.scene

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.nidaria.app.gl.Texture

private const val INK = "#3a2118"
private val faces = HashMap<String, Texture>()

private fun face(key: String, width: Int, height: Int, draw: (Canvas, Paint, Float, Float) -> Unit): Texture = synchronized(faces) {
    faces.getOrPut(key) {
        painted(width, height, mipmaps = true) { canvas, paint ->
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            draw(canvas, paint, width.toFloat(), height.toFloat())
        }
    }
}

private fun Paint.stroke(hex: String, width: Float): Paint {
    style = Paint.Style.STROKE
    color = css(hex)
    strokeWidth = width
    return this
}

private fun Paint.fill(hex: String): Paint {
    style = Paint.Style.FILL
    color = css(hex)
    return this
}

// La boca ocupa el centro de un lienzo de 256 × 160.
fun mouthTexture(kind: String, fang: Boolean): Texture = face("mouth:$kind:$fang", 256, 160) { canvas, paint, w, _ ->
    val cx = w / 2f
    paint.stroke(INK, 11f)
    val path = Path()
    when (kind) {
        "smile" -> {
            path.moveTo(cx - 34f, 66f)
            path.quadTo(cx, 108f, cx + 34f, 66f)
            canvas.drawPath(path, paint)
        }
        "cat" -> {
            path.moveTo(cx - 40f, 64f)
            path.quadTo(cx - 20f, 96f, cx, 68f)
            path.quadTo(cx + 20f, 96f, cx + 40f, 64f)
            canvas.drawPath(path, paint)
        }
        "sad" -> {
            path.moveTo(cx - 30f, 96f)
            path.quadTo(cx, 62f, cx + 30f, 96f)
            canvas.drawPath(path, paint)
        }
        "flat" -> {
            path.moveTo(cx - 24f, 80f)
            path.lineTo(cx + 24f, 80f)
            canvas.drawPath(path, paint)
        }
        "open" -> {
            path.moveTo(cx - 34f, 58f)
            path.quadTo(cx, 50f, cx + 34f, 58f)
            path.cubicTo(cx + 36f, 104f, cx - 36f, 104f, cx - 34f, 58f)
            path.close()
            canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).fill("#7a2e36"))
            canvas.save()
            canvas.clipPath(path)
            canvas.drawOval(RectF(cx - 26f, 84f, cx + 26f, 124f), Paint(Paint.ANTI_ALIAS_FLAG).fill("#ff8fa3"))
            canvas.restore()
            paint.strokeWidth = 8f
            canvas.drawPath(path, paint)
        }
    }
    if (fang && kind != "sad") {
        val y = when (kind) {
            "open" -> 58f
            "flat" -> 80f
            else -> 74f
        }
        val tooth = Path()
        tooth.moveTo(cx + 12f, y)
        tooth.lineTo(cx + 26f, y)
        tooth.lineTo(cx + 19f, y + 18f)
        tooth.close()
        canvas.drawPath(tooth, Paint(Paint.ANTI_ALIAS_FLAG).fill("#ffffff"))
        canvas.drawPath(tooth, paint.stroke(INK, 5f))
    }
}

// Ojo cerrado: 'sleep' es un arco hacia abajo (◡), 'happy' hacia arriba (^).
fun lidTexture(kind: String): Texture = face("lid:$kind", 128, 96) { canvas, paint, w, _ ->
    paint.stroke(INK, 12f)
    val path = Path()
    if (kind == "sleep") {
        path.moveTo(22f, 40f)
        path.quadTo(w / 2f, 84f, w - 22f, 40f)
    } else {
        path.moveTo(24f, 66f)
        path.quadTo(w / 2f, 14f, w - 24f, 66f)
    }
    canvas.drawPath(path, paint)
}

fun blushTexture(): Texture = face("blush", 128, 128) { canvas, paint, w, h ->
    paint.shader = RadialGradient(
        w / 2f, h / 2f, w / 2f,
        intArrayOf(rgba(255, 255, 255, 0.95f), rgba(255, 255, 255, 0.7f), rgba(255, 255, 255, 0f)),
        floatArrayOf(0f, 0.45f, 1f),
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, w, h, paint)
}

// Ceja enfadada: baja hacia el centro de la cara.
fun browTexture(side: Int): Texture = face("brow:$side", 128, 64) { canvas, paint, _, _ ->
    paint.stroke(INK, 16f)
    canvas.drawLine(22f, if (side < 0) 22f else 44f, 106f, if (side < 0) 44f else 22f, paint)
}
