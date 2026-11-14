// Ojos y bocas nuevos, como en creature-kit.js de la web: un globo con el
// iris pintado (pupila redonda, rasgada o con blanco), dos brillos fijos y
// un párpado del color de la piel con su línea de pestañas. Al mirar gira
// el globo; al parpadear baja el párpado. Y las bocas de perro y gato.

package com.nidaria.app.scene

import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import com.nidaria.app.gl.Color
import com.nidaria.app.gl.Geometry
import com.nidaria.app.gl.Group
import com.nidaria.app.gl.Material
import com.nidaria.app.gl.Mesh
import com.nidaria.app.gl.PI_F
import com.nidaria.app.gl.Texture
import com.nidaria.app.gl.sphereGeometry
import com.nidaria.app.gl.torusGeometry
import com.nidaria.app.sculpt.AXIS_Z
import com.nidaria.app.sculpt.Col
import com.nidaria.app.sculpt.Q
import com.nidaria.app.sculpt.V3
import kotlin.math.cos
import kotlin.math.sin

private const val INK = "#3a2118"

// Ángulo del párpado: abierto deja ver un filo arriba; cerrado tapa el globo.
const val LID_OPEN = -1.22f
const val LID_SHUT = 1.42f

// Esfera con las coordenadas de textura proyectadas desde delante.
private val EYEBALL: Geometry = sphereGeometry(1f, 40, 28).also { g ->
    val uv = g.uvs!!
    val pos = g.positions
    for (i in 0 until pos.size / 3) {
        uv[i * 2] = 0.5f + pos[i * 3] * 0.5f
        uv[i * 2 + 1] = 0.5f + pos[i * 3 + 1] * 0.5f
    }
}.keep()
private val LID: Geometry = sphereGeometry(1.1f, 36, 14, 0f, PI_F * 2f, 0f, PI_F / 2f).keep()
private val LASH: Geometry = torusGeometry(1.1f, 0.085f, 8, 36, PI_F).rotateX(PI_F / 2f).keep()
private val LASH_MAT = Material.standard(Color.hex("#1f1512"), 0.6f).also { it.shared = true }
private val GLINT_MAT = Material.basic(Color.hex("#ffffff")).also { it.shared = true }

private val textures = HashMap<String, Texture>()
private fun cached(key: String, make: () -> Texture): Texture = synchronized(textures) { textures.getOrPut(key, make) }

private fun hexOf(c: Col): String = "#" + c.hexString()

// `kind`: 'round' (pupila redonda), 'slit' (rasgada, de gato) o 'sclera'
// (con blanco alrededor, como los monstruos de pie).
fun irisTexture(color: String, kind: String = "round"): Texture = cached("iris:$color:$kind") {
    painted(256, 256, mipmaps = true) { canvas, paint ->
        val w = 256f
        val c = w / 2f
        val iris = Col.hex(color)
        val dark = hexOf(iris.clone().lerp(Col.hex("#0d0806"), 0.82))
        val deep = hexOf(iris.clone().lerp(Col.hex("#0d0806"), 0.45))
        val bright = hexOf(iris.clone().lerp(Col.hex("#ffffff"), 0.28))
        val r = if (kind == "sclera") w * 0.3f else w * 0.4f
        paint.style = Paint.Style.FILL
        if (kind == "sclera") {
            paint.shader = LinearGradient(0f, 0f, 0f, w, intArrayOf(css("#d9d3cc"), css("#fbf8f2"), css("#ffffff")), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
        } else {
            paint.color = css(dark)
        }
        canvas.drawRect(0f, 0f, w, w, paint)
        // Iris: más oscuro arriba (la sombra del párpado) y claro abajo.
        paint.shader = LinearGradient(0f, c - r, 0f, c + r, intArrayOf(css(deep), css(color), css(bright)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(c, c, r, paint)
        paint.shader = null
        // Fibras del iris.
        paint.style = Paint.Style.STROKE
        paint.color = rgba(255, 255, 255, 0.12f)
        paint.strokeWidth = 2f
        for (i in 0 until 40) {
            val a = i / 40f * PI_F * 2f
            canvas.drawLine(c + cos(a) * r * 0.42f, c + sin(a) * r * 0.42f, c + cos(a) * r * 0.9f, c + sin(a) * r * 0.9f, paint)
        }
        // Anillo exterior.
        paint.color = css(dark)
        paint.strokeWidth = r * 0.14f
        canvas.drawCircle(c, c, r - r * 0.07f, paint)
        // Pupila.
        paint.style = Paint.Style.FILL
        paint.color = css("#0a0605")
        if (kind == "slit") canvas.drawOval(RectF(c - r * 0.24f, c - r * 0.78f, c + r * 0.24f, c + r * 0.78f), paint)
        else canvas.drawCircle(c, c, r * (if (kind == "sclera") 0.5f else 0.44f), paint)
    }
}

private val eyeMats = HashMap<String, Material>()
private fun eyeMaterial(color: String, kind: String): Material = synchronized(eyeMats) {
    eyeMats.getOrPut("$color:$kind") {
        physical("#ffffff", 0.2f) {
            map = irisTexture(color, kind)
            clearcoat = 1f
            clearcoatRoughness = 0.03f
            shared = true
        }
    }
}

private val lidMats = HashMap<String, Material>()
private fun lidMaterial(color: Col): Material = synchronized(lidMats) {
    lidMats.getOrPut(color.hexString()) {
        Material.standard(color.toGl(), 0.78f).also { it.shared = true }
    }
}

class EyeParts(val group: Group, val open: Group, val ball: Group, val bead: Mesh, val lid: Group, val r: Float, val tall: Float)

// Un ojo puesto en la cara. `point` y `normal` son de la superficie, en
// coordenadas del cuerpo; `tilt` gira el ojo (rasgado por fuera).
fun makeEye(point: V3, normal: V3, r: Double, tall: Double, depth: Double = 0.62, iris: String, kind: String, skin: Col, tilt: Double = 0.0, sparkle: Boolean = false): EyeParts {
    val group = Group()
    group.place(point.clone().addScaledVector(normal, -r * 0.18))
    group.turn(faceFrame(normal).multiply(Q().setFromAxisAngle(AXIS_Z, tilt)))
    val open = Group()
    val ball = Group()
    val rf = r.toFloat()
    val tl = tall.toFloat()
    val dp = depth.toFloat()
    ball.scale.set(rf, rf * tl, rf * dp)
    val bead = Mesh(EYEBALL, eyeMaterial(iris, kind))
    ball.add(bead)
    val lid = Group()
    lid.add(Mesh(LID, lidMaterial(skin)), Mesh(LASH, LASH_MAT))
    lid.rotation.x = LID_OPEN
    ball.add(lid)
    open.add(ball)
    val glint = Mesh(BALL, GLINT_MAT)
    glint.scale.set(rf * 0.26f, rf * 0.3f, rf * 0.1f)
    glint.position.set(-rf * 0.3f, rf * 0.34f * tl, rf * dp * 0.95f)
    val glint2 = Mesh(BALL, GLINT_MAT)
    glint2.scale.set(rf * 0.11f, rf * 0.11f, rf * 0.06f)
    glint2.position.set(rf * 0.32f, -rf * 0.3f * tl, rf * dp * 0.93f)
    open.add(glint, glint2)
    if (sparkle) {
        val star = Mesh(BALL, GLINT_MAT)
        star.scale.set(rf * 0.08f, rf * 0.08f, rf * 0.05f)
        star.position.set(-rf * 0.05f, rf * 0.1f, rf * dp)
        open.add(star)
    }
    group.add(open)
    return EyeParts(group, open, ball, bead, lid, rf, tl)
}

// Boca de perro o gato: el borde de arriba queda justo bajo la nariz;
// de ahí baja el surco y salen los labios. Lienzo de 256 × 160.
fun petMouthTexture(species: String, kind: String): Texture = cached("petmouth:$species:$kind") {
    painted(256, 160, mipmaps = true) { canvas, paint ->
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        val cx = 128f
        val cat = species == "gato"
        val drop = if (cat) 30f else 46f
        val spread = if (cat) 30f else 44f
        paint.style = Paint.Style.STROKE
        paint.color = css(INK)
        paint.strokeWidth = if (cat) 8f else 10f
        canvas.drawLine(cx, 4f, cx, drop, paint)
        if (kind == "open") {
            val wide = if (cat) 34f else 48f
            val deep = if (cat) 64f else 104f
            val path = Path()
            path.moveTo(cx - wide, drop - 6f)
            path.quadTo(cx - wide * 0.4f, drop + 10f, cx, drop)
            path.quadTo(cx + wide * 0.4f, drop + 10f, cx + wide, drop - 6f)
            path.cubicTo(cx + wide * 0.9f, deep, cx - wide * 0.9f, deep, cx - wide, drop - 6f)
            path.close()
            val fill = Paint(Paint.ANTI_ALIAS_FLAG)
            fill.color = css("#6e2630")
            canvas.drawPath(path, fill)
            canvas.save()
            canvas.clipPath(path)
            fill.color = css("#ef7f8e")
            val ty = deep - (if (cat) 4f else 10f)
            val tw = wide * 0.62f
            val th = if (cat) 16f else 30f
            canvas.drawOval(RectF(cx - tw, ty - th, cx + tw, ty + th), fill)
            val line = Paint(Paint.ANTI_ALIAS_FLAG)
            line.style = Paint.Style.STROKE
            line.color = css("#d0606f")
            line.strokeWidth = 4f
            canvas.drawLine(cx, deep - (if (cat) 16f else 34f), cx, deep - 2f, line)
            canvas.restore()
            paint.strokeWidth = if (cat) 7f else 8f
            canvas.drawPath(path, paint)
            if (cat) {
                fill.color = css("#ffffff")
                for (side in intArrayOf(-1, 1)) {
                    val t = Path()
                    t.moveTo(cx + side * 22f, drop + 2f)
                    t.lineTo(cx + side * 13f, drop + 2f)
                    t.lineTo(cx + side * 18f, drop + 16f)
                    t.close()
                    canvas.drawPath(t, fill)
                }
            }
            return@painted
        }
        val lift = when (kind) { "smile" -> -14f; "sad" -> 16f; else -> -2f }
        val curve = when (kind) { "smile" -> 26f; "sad" -> -8f; else -> 10f }
        for (side in intArrayOf(-1, 1)) {
            val path = Path()
            path.moveTo(cx, drop)
            path.quadTo(cx + side * spread * 0.5f, drop + curve, cx + side * spread, drop + lift)
            canvas.drawPath(path, paint)
        }
    }
}

// Cicatriz: un tajo con dos puntadas, sobre un ojo.
fun scarTexture(): Texture = cached("scar") {
    painted(64, 160, mipmaps = true) { canvas, paint ->
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = rgba(70, 30, 30, 0.85f)
        paint.strokeWidth = 9f
        canvas.drawLine(26f, 12f, 38f, 148f, paint)
        paint.strokeWidth = 6f
        for (y in floatArrayOf(55f, 100f)) canvas.drawLine(18f, y, 48f, y + 4f, paint)
    }
}
