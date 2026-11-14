// La habitación fija, como house.js en la web: pared con papel, friso de
// madera, suelo de tarima, la ventana (con hueco de verdad, cortinas y el
// sol que entra), una estantería, un puf y un cuadro. Lo que cambia con
// la decoración (papel, alfombra, muebles) lo pone Room.kt.

package com.nidaria.app.scene

import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.ColorUtils
import com.nidaria.app.gl.Color
import com.nidaria.app.gl.Geometry
import com.nidaria.app.gl.Group
import com.nidaria.app.gl.Material
import com.nidaria.app.gl.Mesh
import com.nidaria.app.gl.PI_F
import com.nidaria.app.gl.Shape
import com.nidaria.app.gl.Texture
import com.nidaria.app.gl.cylinderGeometry
import com.nidaria.app.gl.extrudeGeometry
import com.nidaria.app.gl.planeGeometry
import com.nidaria.app.gl.shapeGeometry
import com.nidaria.app.gl.sphereGeometry
import com.nidaria.app.gl.torusGeometry
import com.nidaria.app.rules.Rng
import com.nidaria.app.sculpt.Sculpt
import com.nidaria.app.sculpt.Sh
import com.nidaria.app.sculpt.cosD
import com.nidaria.app.sculpt.sinD
import com.nidaria.app.sculpt.v
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

const val WALL_Z = -2.4f
const val WAINSCOT = 0.62f

// Ventana: centro en x, altura del alféizar, ancho del hueco y tramo recto.
object WINDOW {
    const val x = -0.68f
    const val y = 0.98f
    const val width = 0.86f
    const val straight = 0.62f
    const val depth = 0.14f
}

private const val W = 60f
private const val H = 24f

private fun archShape(width: Float, straight: Float, cx: Float = 0f, cy: Float = 0f): Shape {
    val r = width / 2f
    return Shape().moveTo(cx - r, cy).lineTo(cx - r, cy + straight).absarc(cx, cy + straight, r, PI_F, 0f, true).lineTo(cx + r, cy).closePath()
}

// Un arco con el hueco de otro arco más estrecho que llega hasta abajo:
// el contorno es una sola U invertida.
private fun archFrame(outer: Float, inner: Float, straight: Float): Shape {
    val ro = outer / 2f
    val ri = inner / 2f
    return Shape().moveTo(-ro, 0f).lineTo(-ro, straight).absarc(0f, straight, ro, PI_F, 0f, true).lineTo(ro, 0f)
        .lineTo(ri, 0f).lineTo(ri, straight).absarc(0f, straight, ri, 0f, PI_F, false).lineTo(-ri, 0f).closePath()
}

// Rejilla plana como PlaneGeometry de Three.js con segmentos (mismo orden).
internal fun gridPlane(width: Float, height: Float, sx: Int, sy: Int): Geometry {
    val gx1 = sx + 1
    val gy1 = sy + 1
    val pos = FloatArray(gx1 * gy1 * 3)
    val nor = FloatArray(gx1 * gy1 * 3)
    val uv = FloatArray(gx1 * gy1 * 2)
    for (iy in 0..sy) {
        val y = iy * (height / sy) - height / 2f
        for (ix in 0..sx) {
            val i = iy * gx1 + ix
            pos[i * 3] = ix * (width / sx) - width / 2f
            pos[i * 3 + 1] = -y
            nor[i * 3 + 2] = 1f
            uv[i * 2] = ix / sx.toFloat()
            uv[i * 2 + 1] = 1f - iy / sy.toFloat()
        }
    }
    val idx = IntArray(sx * sy * 6)
    var k = 0
    for (iy in 0 until sy) {
        for (ix in 0 until sx) {
            val a = ix + gx1 * iy
            val b = ix + gx1 * (iy + 1)
            val c = (ix + 1) + gx1 * (iy + 1)
            val d = (ix + 1) + gx1 * iy
            idx[k++] = a; idx[k++] = b; idx[k++] = d
            idx[k++] = b; idx[k++] = c; idx[k++] = d
        }
    }
    return Geometry(pos, nor, uv, idx)
}

// Caja con los cantos redondeados (RoundedBoxGeometry de Three.js): un
// cubo subdividido cuyos vértices se empujan hacia una esfera en cada
// esquina.
internal fun roundedBox(width: Float, height: Float, depth: Float, segmentsIn: Int, radiusIn: Float): Geometry {
    val segments = segmentsIn * 2 + 1
    val radius = minOf(width / 2f, height / 2f, depth / 2f, radiusIn)
    val half = 0.5f / segments
    val bx = width / 2f - radius
    val by = height / 2f - radius
    val bz = depth / 2f - radius
    val pos = ArrayList<Float>()
    val nor = ArrayList<Float>()
    val idx = ArrayList<Int>()
    // Cada cara: su eje normal y los dos ejes que la recorren.
    val faces = arrayOf(
        intArrayOf(0, 1, 2, 2, 1), intArrayOf(0, -1, 2, 2, -1), intArrayOf(1, 1, 0, 2, 1),
        intArrayOf(1, -1, 0, 2, -1), intArrayOf(2, 1, 0, 1, 1), intArrayOf(2, -1, 0, 1, -1),
    )
    for (f in faces) {
        val axis = f[0]
        val dir = f[1].toFloat()
        val u = f[2]
        val w = f[3]
        val start = pos.size / 3
        for (j in 0..segments) {
            for (i in 0..segments) {
                val p = FloatArray(3)
                p[axis] = dir * 0.5f
                p[u] = i / segments.toFloat() - 0.5f
                p[w] = j / segments.toFloat() - 0.5f
                val n = FloatArray(3) { c -> p[c] - sign(p[c]) * half }
                val l = sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2])
                for (c in 0 until 3) n[c] /= l
                pos.add(bx * sign(p[0]) + n[0] * radius)
                pos.add(by * sign(p[1]) + n[1] * radius)
                pos.add(bz * sign(p[2]) + n[2] * radius)
                nor.add(n[0]); nor.add(n[1]); nor.add(n[2])
            }
        }
        val row = segments + 1
        for (j in 0 until segments) {
            for (i in 0 until segments) {
                val a = start + j * row + i
                val b = a + 1
                val c = a + row
                val d = c + 1
                // Que el triángulo mire hacia fuera según la cara.
                val outward = (u == (axis + 1) % 3) == (dir > 0)
                if (outward) {
                    idx.add(a); idx.add(b); idx.add(d); idx.add(a); idx.add(d); idx.add(c)
                } else {
                    idx.add(a); idx.add(d); idx.add(b); idx.add(a); idx.add(c); idx.add(d)
                }
            }
        }
    }
    return Geometry(pos.toFloatArray(), nor.toFloatArray(), null, idx.toIntArray())
}

private fun rounded(w: Float, h: Float, d: Float, r: Float, color: String, finish: String = "matte"): Mesh {
    val mesh = Mesh(roundedBox(w, h, d, 3, r), plain(color, finish))
    mesh.castShadow = true
    mesh.receiveShadow = true
    return mesh
}

private fun hslColor(h: Double, s: Double, l: Double, a: Float = 1f): Int {
    val c = ColorUtils.HSLToColor(floatArrayOf(((h % 360 + 360) % 360).toFloat(), (s / 100).toFloat().coerceIn(0f, 1f), (l / 100).toFloat().coerceIn(0f, 1f)))
    return ColorUtils.setAlphaComponent(c, (a * 255f + 0.5f).toInt().coerceIn(0, 255))
}

// ── Texturas ──

private fun woodFloor(): Texture {
    val size = 1024
    val boards = 8
    val bw = size / boards.toFloat()
    return painted(size, size, repeat = true, mipmaps = true) { canvas, paint ->
        val rng = Rng(90210)
        for (b in 0 until boards) {
            val x = b * bw
            var y = -rng.range(0.0, 700.0)
            while (y < size) {
                val len = rng.range(420.0, 900.0)
                val l = rng.range(83.0, 88.0)
                val hue = rng.range(30.0, 36.0)
                val sat = rng.range(52.0, 64.0)
                fun draw(oy: Float) {
                    val top = (y + oy).toFloat()
                    paint.reset()
                    paint.isAntiAlias = true
                    paint.color = hslColor(hue, sat, l)
                    canvas.drawRect(x, top, x + bw, top + len.toFloat(), paint)
                    // Vetas: líneas finas que ondulan a lo largo de la tabla.
                    canvas.save()
                    canvas.clipRect(x, top, x + bw, top + len.toFloat())
                    for (g in 0 until 9) {
                        val gx = x + rng.range(6.0, bw - 6.0).toFloat()
                        paint.style = android.graphics.Paint.Style.STROKE
                        paint.color = hslColor(hue - 4, sat + 6, l - rng.range(8.0, 16.0), rng.range(0.12, 0.28).toFloat())
                        paint.strokeWidth = rng.range(0.8, 2.2).toFloat()
                        val amp = rng.range(1.0, 5.0)
                        val freq = rng.range(0.004, 0.012)
                        val phase = rng.range(0.0, 6.0)
                        val path = Path()
                        var t = 0.0
                        while (t <= len) {
                            val px = (gx + sin(t * freq + phase) * amp).toFloat()
                            if (t == 0.0) path.moveTo(px, top + t.toFloat()) else path.lineTo(px, top + t.toFloat())
                            t += 12
                        }
                        canvas.drawPath(path, paint)
                    }
                    // Un brillo suave hacia el centro de la tabla.
                    paint.style = android.graphics.Paint.Style.FILL
                    paint.shader = LinearGradient(x, 0f, x + bw, 0f, intArrayOf(rgba(120, 70, 40, 0.07f), rgba(255, 250, 240, 0.06f), rgba(120, 70, 40, 0.08f)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
                    canvas.drawRect(x, top, x + bw, top + len.toFloat(), paint)
                    paint.shader = null
                    canvas.restore()
                    // Junta del final de la tabla.
                    paint.color = rgba(110, 70, 45, 0.45f)
                    canvas.drawRect(x, top + len.toFloat() - 2f, x + bw, top + len.toFloat(), paint)
                }
                draw(0f)
                if (y + len > size) draw(-size.toFloat())
                if (y < 0) draw(size.toFloat())
                y += len
            }
            // Junta entre tablas, con un filo de luz al lado.
            paint.reset()
            paint.color = rgba(105, 65, 40, 0.5f)
            canvas.drawRect(x, 0f, x + 2.5f, size.toFloat(), paint)
            paint.color = rgba(255, 248, 236, 0.35f)
            canvas.drawRect(x + 2.5f, 0f, x + 4f, size.toFloat(), paint)
        }
    }
}

private fun beadboard(): Texture = painted(128, 256, repeat = true, mipmaps = true) { canvas, paint ->
    val w = 128f
    val h = 256f
    canvas.drawColor(css("#fbf2e4"))
    paint.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(rgba(110, 70, 40, 0.16f), rgba(110, 70, 40, 0f), rgba(110, 70, 40, 0f), rgba(110, 70, 40, 0.18f)), floatArrayOf(0f, 0.08f, 0.85f, 1f), Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, w, h, paint)
    paint.shader = null
    // Ranura de la tabla: sombra a un lado y luz al otro.
    paint.color = rgba(150, 110, 80, 0.35f)
    canvas.drawRect(0f, 0f, 4f, h, paint)
    paint.color = rgba(255, 255, 255, 0.7f)
    canvas.drawRect(4f, 0f, 7f, h, paint)
    paint.color = rgba(150, 110, 80, 0.12f)
    canvas.drawRect(w - 6f, 0f, w, h, paint)
}

// Mancha de luz de la ventana: el arco con la cruz de los palillos y los
// bordes difuminados.
private fun sunPatchTexture(): Texture = painted(256, 320) { canvas, paint ->
    val w = 256f
    val h = 320f
    paint.maskFilter = BlurMaskFilter(14f, BlurMaskFilter.Blur.NORMAL)
    paint.color = css("#ffffff")
    val r = w * 0.36f
    val cx = w / 2f
    val base = h * 0.92f
    val straight = h * 0.42f
    val path = Path()
    path.moveTo(cx - r, base)
    path.lineTo(cx - r, base - straight)
    path.arcTo(RectF(cx - r, base - straight - r, cx + r, base - straight + r), 180f, 180f)
    path.lineTo(cx + r, base)
    path.close()
    canvas.drawPath(path, paint)
    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    paint.color = css("#000000")
    canvas.drawRect(cx - 5f, base - straight - r, cx + 5f, base, paint)
    canvas.drawRect(cx - r, base - straight - 5f, cx + r, base - straight + 5f, paint)
}

// Haz de luz: más denso arriba, en la ventana, y se apaga hacia el suelo.
private fun beamTexture(): Texture = painted(64, 256) { canvas, paint ->
    val w = 64f
    val h = 256f
    paint.shader = LinearGradient(0f, 0f, 0f, h, rgba(255, 255, 255, 0.9f), rgba(255, 255, 255, 0f), Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, w, h, paint)
    paint.shader = LinearGradient(0f, 0f, w, 0f, intArrayOf(rgba(0, 0, 0, 1f), rgba(0, 0, 0, 0f), rgba(0, 0, 0, 0f), rgba(0, 0, 0, 1f)), floatArrayOf(0f, 0.25f, 0.75f, 1f), Shader.TileMode.CLAMP)
    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    canvas.drawRect(0f, 0f, w, h, paint)
}

private fun glassGlint(): Texture = painted(128, 160) { canvas, paint ->
    val w = 128f
    val h = 160f
    paint.maskFilter = BlurMaskFilter(3f, BlurMaskFilter.Blur.NORMAL)
    paint.color = rgba(255, 255, 255, 0.9f)
    canvas.drawPath(Path().apply { moveTo(w * 0.15f, h * 0.75f); lineTo(w * 0.35f, h * 0.75f); lineTo(w * 0.75f, h * 0.2f); lineTo(w * 0.62f, h * 0.2f); close() }, paint)
    paint.color = rgba(255, 255, 255, 0.5f)
    canvas.drawPath(Path().apply { moveTo(w * 0.42f, h * 0.75f); lineTo(w * 0.48f, h * 0.75f); lineTo(w * 0.86f, h * 0.24f); lineTo(w * 0.8f, h * 0.24f); close() }, paint)
}

private fun cornerShade(): Texture = painted(8, 128) { canvas, paint ->
    paint.shader = LinearGradient(0f, 0f, 0f, 128f, intArrayOf(rgba(80, 45, 25, 0.42f), rgba(80, 45, 25, 0.14f), rgba(80, 45, 25, 0f)), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, 8f, 128f, paint)
}

private fun curtainTexture(): Texture = painted(128, 512, mipmaps = true) { canvas, paint ->
    val w = 128f
    val h = 512f
    canvas.drawColor(css("#fff6ee"))
    paint.color = rgba(255, 170, 190, 0.22f)
    var y = 18
    while (y < h - 60) {
        var x = if ((y / 36) % 2 == 1) 16 else 48
        while (x < w) {
            canvas.drawCircle(x.toFloat(), y.toFloat(), 5f, paint)
            x += 64
        }
        y += 36
    }
    // Ribete rosa abajo.
    paint.color = css("#c65a37")
    canvas.drawRect(0f, h - 40f, w, h - 14f, paint)
    paint.color = css("#e3a184")
    canvas.drawRect(0f, h - 14f, w, h, paint)
    // Trama de la tela.
    paint.color = rgba(120, 80, 60, 0.035f)
    var x = 0f
    while (x < w) {
        canvas.drawRect(x, 0f, x + 1f, h, paint)
        x += 3f
    }
    var yy = 0f
    while (yy < h) {
        canvas.drawRect(0f, yy, w, yy + 1f, paint)
        yy += 3f
    }
}

private fun paintingTexture(): Texture = painted(256, 200, mipmaps = true) { canvas, paint ->
    val w = 256f
    val h = 200f
    paint.shader = LinearGradient(0f, 0f, 0f, h, css("#bfe3ff"), css("#fff1d6"), Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, w, h, paint)
    paint.shader = null
    paint.color = css("#ffd166")
    canvas.drawCircle(w * 0.72f, h * 0.34f, 26f, paint)
    fun hill(y: Float, hex: String, amp: Float, phase: Float) {
        paint.color = css(hex)
        val path = Path()
        path.moveTo(0f, h)
        var x = 0f
        while (x <= w) {
            path.lineTo(x, y + sin(x * 0.02f + phase) * amp)
            x += 8f
        }
        path.lineTo(w, h)
        path.close()
        canvas.drawPath(path, paint)
    }
    hill(h * 0.62f, "#9ad9b3", 14f, 1f)
    hill(h * 0.75f, "#7ec99c", 10f, 3f)
    paint.color = css("#ffffff")
    for (cloud in arrayOf(floatArrayOf(60f, 50f, 1f), floatArrayOf(150f, 70f, 0.8f))) {
        val (cx, cy, s) = cloud.toList()
        canvas.drawCircle(cx, cy, 12f * s, paint)
        canvas.drawCircle(cx + 14f * s, cy - 6f * s, 14f * s, paint)
        canvas.drawCircle(cx + 30f * s, cy, 11f * s, paint)
    }
    // Pincelada: un poco de grano.
    val rng = Rng(7)
    for (i in 0 until 1600) {
        paint.color = if (rng.chance(0.5)) rgba(255, 255, 255, 0.08f) else rgba(0, 0, 0, 0.035f)
        val x = rng.range(0.0, w.toDouble()).toFloat()
        val y = rng.range(0.0, h.toDouble()).toFloat()
        canvas.drawRect(x, y, x + 2f, y + 1f, paint)
    }
}

// ── Piezas ──

// Una cortina: un paño con pliegues que se recoge con un alzapaño.
private fun curtain(width: Float, height: Float, side: Int, material: Material): Group {
    val geometry = gridPlane(width, height, 40, 48)
    val pos = geometry.positions
    val outer = side * width / 2f
    val tieY = -height / 2f + height * 0.36f
    for (i in 0 until pos.size / 3) {
        val x = pos[i * 3]
        val y = pos[i * 3 + 1]
        val u = (x + width / 2f) / width
        // Fruncido: el alzapaño junta la tela hacia el borde de fuera.
        val q = (y - tieY) / (height * 0.2f)
        val pinch = 1f - 0.62f * exp(-(q * q))
        val below = if (y < tieY) 1f + 0.25f * min(1f, (tieY - y) / (height * 0.3f)) else 1f
        val nx = outer + (x - outer) * pinch * below
        val folds = sin(u * PI_F * 9f) * (0.022f + 0.012f * (1f - pinch)) + sin(u * PI_F * 4f + 1f) * 0.008f
        pos[i * 3] = nx
        pos[i * 3 + 1] = y
        pos[i * 3 + 2] = folds + 0.03f
    }
    geometry.computeVertexNormals()
    val mesh = Mesh(geometry, material)
    mesh.castShadow = true
    mesh.receiveShadow = true
    val tie = Mesh(torusGeometry(width * 0.2f, 0.018f, 10, 28), plain("#c65a37", "fabric"))
    tie.scale.set(1f, 0.42f, 1f)
    tie.rotation.x = PI_F / 2f
    tie.position.set(outer - side * width * 0.2f, tieY, 0.04f)
    tie.castShadow = true
    val group = Group()
    group.add(mesh, tie)
    return group
}

private fun buildWindow(sky: Texture?): Group {
    val x = WINDOW.x
    val y = WINDOW.y
    val width = WINDOW.width
    val straight = WINDOW.straight
    val depth = WINDOW.depth
    val win = Group()
    win.position.set(x, y, WALL_Z)
    val frameColor = "#fffaf2"

    // Cielo, al fondo del hueco.
    val paneGeo = shapeGeometry(archShape(width, straight), 24)
    val pp = paneGeo.positions
    paneGeo.uvs = FloatArray(pp.size / 3 * 2) { i -> if (i % 2 == 0) (pp[i / 2 * 3] + width / 2f) / width else pp[i / 2 * 3 + 1] / (straight + width / 2f) }
    val pane = Mesh(paneGeo, Material.basic(Color(1f, 1f, 1f)) {
        map = sky
        toneMapped = false
    })
    pane.position.z = -depth

    // Mocheta: el grosor del muro alrededor del hueco.
    val reveal = Mesh(extrudeGeometry(archFrame(width + 0.04f, width, straight), depth, curveSegments = 24), plain("#f7eedf", "matte"))
    reveal.position.z = -depth
    reveal.receiveShadow = true

    // Marco de la hoja y los palillos, metidos en el hueco.
    val sash = Mesh(extrudeGeometry(archFrame(width, width - 0.07f, straight), 0.03f, bevelEnabled = true, bevelThickness = 0.008f, bevelSize = 0.008f, bevelSegments = 3, curveSegments = 24), plain(frameColor, "gloss"))
    sash.position.z = -depth * 0.55f
    sash.castShadow = true
    val sashFoot = rounded(width, 0.035f, 0.03f, 0.008f, frameColor, "gloss")
    sashFoot.position.set(0f, 0.0175f, -depth * 0.55f + 0.015f)
    val barV = rounded(0.03f, straight + width / 2f, 0.03f, 0.012f, frameColor, "gloss")
    barV.position.set(0f, (straight + width / 2f) / 2f, -depth * 0.5f)
    val barH = rounded(width - 0.04f, 0.03f, 0.03f, 0.012f, frameColor, "gloss")
    barH.position.set(0f, straight, -depth * 0.5f)

    // Brillo del cristal.
    val glint = Mesh(planeGeometry(width * 0.9f, straight + width * 0.4f), Material.basic(Color(1f, 1f, 1f)) {
        map = glassGlint()
        transparent = true
        opacity = 0.14f
        depthWrite = false
        toneMapped = false
        additive = true
    })
    glint.position.set(0f, (straight + width / 2f) / 2f, -depth * 0.6f)

    // Moldura sobre la pared y alféizar.
    val casing = Mesh(extrudeGeometry(archFrame(width + 0.2f, width, straight), 0.025f, bevelEnabled = true, bevelThickness = 0.014f, bevelSize = 0.012f, bevelSegments = 4, curveSegments = 28), plain(frameColor, "gloss"))
    casing.castShadow = true
    casing.receiveShadow = true
    val sill = rounded(width + 0.36f, 0.055f, 0.2f, 0.02f, frameColor, "gloss")
    sill.position.set(0f, -0.02f, 0.04f)
    val apron = rounded(width + 0.2f, 0.08f, 0.03f, 0.012f, frameColor, "gloss")
    apron.position.set(0f, -0.085f, 0.015f)

    // Barra y cortinas.
    val top = y + straight + width / 2f
    val rodY = top - y + 0.17f
    val rod = Mesh(cylinderGeometry(0.014f, 0.014f, width + 0.62f, 16), plain("#e2b279", "gloss"))
    rod.rotation.z = PI_F / 2f
    rod.position.set(0f, rodY, 0.09f)
    rod.castShadow = true
    val finials = intArrayOf(-1, 1).map { side ->
        val ball = Mesh(sphereGeometry(0.032f, 20, 14), plain("#e2b279", "gloss"))
        ball.position.set(side * (width / 2f + 0.33f), rodY, 0.09f)
        ball.castShadow = true
        ball
    }
    val fabric = Material.standard(Color(1f, 1f, 1f), 0.9f) {
        map = curtainTexture()
        doubleSide = true
        sheen = 0.8f
        sheenRoughness = 0.5f
        sheenColor.set(0.35f, 0.35f, 0.35f)
    }
    val curtainH = rodY + 0.14f
    val curtains = intArrayOf(-1, 1).map { side ->
        val c = curtain(0.4f, curtainH, side, fabric)
        c.position.set(side * (width / 2f + 0.13f), rodY - curtainH / 2f + 0.01f, 0.07f)
        c
    }

    win.add(pane, reveal, sash, sashFoot, barV, barH, glint, casing, sill, apron, rod, *finials.toTypedArray(), *curtains.toTypedArray())
    return win
}

private fun buildShelf(): Group {
    val shelf = Group()
    shelf.add(rounded(1.0f, 0.045f, 0.24f, 0.014f, "#e8bf8c", "wood"))
    for (side in intArrayOf(-1, 1)) {
        val bracket = rounded(0.03f, 0.16f, 0.2f, 0.01f, "#d9a874", "wood")
        bracket.position.set(side * 0.36f, -0.1f, -0.01f)
        shelf.add(bracket)
    }
    val books = arrayOf(Triple("#c65a37", 0.26f, 0.07f), Triple("#3f6fb0", 0.3f, 0.065f), Triple("#e0a93b", 0.22f, 0.06f), Triple("#3f9a7a", 0.27f, 0.07f))
    var bx = -0.42f
    books.forEachIndexed { i, (color, tall, thick) ->
        val book = rounded(thick, tall, 0.17f, 0.008f, color, "matte")
        val tilt = if (i == 3) 0.3f else 0f
        book.position.set(bx + thick / 2f + (if (i == 3) 0.05f else 0f), 0.0225f + tall / 2f - (if (i == 3) 0.02f else 0f), 0f)
        book.rotation.z = -tilt
        val band = rounded(thick + 0.004f, 0.022f, 0.172f, 0.004f, "#fffaf2", "matte")
        band.position.y = tall * 0.28f
        book.add(band)
        shelf.add(book)
        bx += thick + 0.006f
    }
    // Maceta con una suculenta esculpida.
    val pot = Sculpt()
    pot.add(Sh.cylinder(doubleArrayOf(0.0, 0.07, 0.0), 0.075, 0.07, 0.02), "#ffffff", k = 0.0)
    pot.add(Sh.torus(doubleArrayOf(0.0, 0.135, 0.0), 0.075, 0.016), "#ffd166", k = 0.01)
    pot.sub(Sh.cylinder(doubleArrayOf(0.0, 0.16, 0.0), 0.062, 0.04, 0.01), k = 0.01)
    pot.add(Sh.cylinder(doubleArrayOf(0.0, 0.125, 0.0), 0.062, 0.006, 0.004), "#8a5a3c", k = 0.0)
    for (i in 0 until 7) {
        val a = (i / 7.0) * Math.PI * 2
        val tilt = if (i == 0) 0.0 else 0.75
        val dir = v(sinD(a) * sinD(tilt), cosD(tilt), cosD(a) * sinD(tilt))
        val base = v(0.0, 0.13, 0.0)
        pot.add(Sh.cone(base.toArray(), base.clone().addScaledVector(dir, if (i == 0) 0.09 else 0.1).toArray(), 0.03, 0.012), if (i % 2 == 1) "#7fcf9a" else "#5fb985", k = 0.015)
    }
    val plant = Mesh(pot.build(cell = 0.006, aoReach = 0.05), clay("matte"))
    plant.castShadow = true
    plant.position.set(0.28f, 0.0225f, 0f)
    shelf.add(plant)
    val ball = Mesh(sphereGeometry(0.06f, 24, 16), plain("#ff7a7a", "glaze"))
    ball.position.set(0.04f, 0.0825f, 0.02f)
    ball.castShadow = true
    shelf.add(ball)
    shelf.traverse { node ->
        if (node is Mesh) {
            node.castShadow = true
            node.receiveShadow = true
        }
    }
    return shelf
}

private fun buildPouf(): Group {
    val s = Sculpt(ground = 0.0, groundK = 0.03)
    s.add(Sh.cylinder(doubleArrayOf(0.0, 0.2, 0.0), 0.4, 0.2, 0.16), "#86dbaa", k = 0.0)
    s.add(Sh.torus(doubleArrayOf(0.0, 0.205, 0.0), 0.39, 0.03, null, 1.0), "#6cc794", k = 0.02)
    for (i in 0 until 6) {
        val a = (i / 6.0) * Math.PI * 2
        s.sub(Sh.sphere(doubleArrayOf(cosD(a) * 0.2, 0.43, sinD(a) * 0.2), 0.035), k = 0.06)
    }
    s.sub(Sh.sphere(doubleArrayOf(0.0, 0.44, 0.0), 0.04), k = 0.06)
    for (i in 0 until 6) {
        val a = (i / 6.0) * Math.PI * 2
        s.add(Sh.sphere(doubleArrayOf(cosD(a) * 0.2, 0.385, sinD(a) * 0.2), 0.022), "#4fae7c", k = 0.01)
    }
    s.add(Sh.sphere(doubleArrayOf(0.0, 0.39, 0.0), 0.024), "#4fae7c", k = 0.01)
    val mesh = Mesh(s.build(cell = 0.014, aoReach = 0.12), clay("plush"))
    mesh.castShadow = true
    mesh.receiveShadow = true
    val group = Group()
    group.add(contactShadow(1.05f, 1.05f, 0.9f), mesh)
    return group
}

private fun buildPainting(): Group {
    val group = Group()
    // El marco: cuatro listones (la web lo extruye con el hueco del lienzo).
    val wood = "#e8bf8c"
    val top = rounded(0.5f, 0.05f, 0.045f, 0.012f, wood, "wood").also { it.position.set(0f, 0.165f, 0.01f) }
    val bottom = rounded(0.5f, 0.05f, 0.045f, 0.012f, wood, "wood").also { it.position.set(0f, -0.165f, 0.01f) }
    val left = rounded(0.05f, 0.38f, 0.045f, 0.012f, wood, "wood").also { it.position.set(-0.215f, 0f, 0.01f) }
    val right = rounded(0.05f, 0.38f, 0.045f, 0.012f, wood, "wood").also { it.position.set(0.215f, 0f, 0.01f) }
    for (bar in arrayOf(top, bottom, left, right)) bar.castShadow = true
    val art = Mesh(planeGeometry(0.4f, 0.3f), Material.standard(Color(1f, 1f, 1f), 0.8f) { map = paintingTexture() })
    art.position.z = 0.012f
    group.add(top, bottom, left, right, art)
    return group
}

// ── La habitación ──

class House(
    val group: Group,
    val wallMat: Material,
    val floorMat: Material,
    val trimMat: Material,
    val patchMat: Material,
    val beamMat: Material,
)

fun buildHouse(sky: Texture?): House {
    val group = Group()

    // Pared con papel (con el hueco de la ventana) y friso de madera. Sin
    // agujeros en los contornos, la pared va en cuatro piezas alrededor
    // de la ventana; la textura usa metros, así que el papel casa.
    val wallMat = Material.standard(Color(1f, 1f, 1f), 0.9f)
    val r = WINDOW.width / 2f
    val x0 = WINDOW.x - r
    val x1 = WINDOW.x + r
    val sillY = WINDOW.y
    val springY = WINDOW.y + WINDOW.straight
    val pieces = listOf(
        Shape().moveTo(-W / 2f, WAINSCOT).lineTo(x0, WAINSCOT).lineTo(x0, H).lineTo(-W / 2f, H).closePath(),
        Shape().moveTo(x1, WAINSCOT).lineTo(W / 2f, WAINSCOT).lineTo(W / 2f, H).lineTo(x1, H).closePath(),
        Shape().moveTo(x0, WAINSCOT).lineTo(x1, WAINSCOT).lineTo(x1, sillY).lineTo(x0, sillY).closePath(),
        Shape().moveTo(x0, springY).lineTo(x0, H).lineTo(x1, H).lineTo(x1, springY).absarc(WINDOW.x, springY, r, 0f, PI_F, false).closePath(),
    )
    for (piece in pieces) {
        val wall = Mesh(shapeGeometry(piece, 24), wallMat)
        wall.position.z = WALL_Z
        wall.receiveShadow = true
        group.add(wall)
    }

    val trimMat = Material.standard(Color(1f, 1f, 1f), 0.62f) {
        map = beadboard()
        uvScaleX = W / 0.12f
        uvScaleY = 1f
    }
    val wainscot = Mesh(planeGeometry(W, WAINSCOT), trimMat)
    wainscot.position.set(0f, WAINSCOT / 2f, WALL_Z + 0.012f)
    wainscot.receiveShadow = true
    val rail = rounded(W, 0.05f, 0.05f, 0.018f, "#fffaf2", "gloss")
    rail.position.set(0f, WAINSCOT, WALL_Z + 0.025f)
    val baseboard = rounded(W, 0.12f, 0.04f, 0.012f, "#fffaf2", "gloss")
    baseboard.position.set(0f, 0.06f, WALL_Z + 0.03f)

    // Suelo de tarima.
    val floorMap = woodFloor()
    val floorMat = Material.standard(Color(1f, 1f, 1f), 0.5f) {
        map = floorMap
        uvScaleX = W / 1.6f
        uvScaleY = 40f / 1.6f
        bumpMap = floorMap
        bumpScale = 0.35f
        bumpUvScaleX = W / 1.6f
        bumpUvScaleY = 40f / 1.6f
    }
    val floor = Mesh(planeGeometry(W, 40f).rotateX(-PI_F / 2f), floorMat)
    floor.position.z = WALL_Z + 20f
    floor.receiveShadow = true

    // Sombra del rincón entre suelo y pared.
    val corner = Mesh(planeGeometry(W, 0.9f).rotateX(-PI_F / 2f), Material.basic(Color(1f, 1f, 1f)) {
        map = cornerShade()
        transparent = true
        depthWrite = false
        toneMapped = false
    })
    corner.position.set(0f, 0.003f, WALL_Z + 0.45f)

    val win = buildWindow(sky)

    // Luz que entra por la ventana: una mancha en el suelo y un haz.
    val patchGeo = gridPlane(1.25f, 1.5f, 1, 8)
    run {
        val pos = patchGeo.positions
        for (i in 0 until pos.size / 3) {
            val py = pos[i * 3 + 1]
            // Se estira hacia la cámara y se tuerce a la derecha, como el sol de la tarde.
            pos[i * 3] = pos[i * 3] + (0.55f - py) * 0.3f
            pos[i * 3 + 1] = 0f
            pos[i * 3 + 2] = -py
        }
        patchGeo.computeVertexNormals()
    }
    val patchMat = Material.basic(Color.hex("#fff2d2")) {
        map = sunPatchTexture()
        transparent = true
        opacity = 0.2f
        depthWrite = false
        toneMapped = false
        additive = true
    }
    val patch = Mesh(patchGeo, patchMat)
    patch.position.set(WINDOW.x - 0.1f, 0.006f, WALL_Z + 0.85f)

    val topY = WINDOW.y + WINDOW.straight * 0.9f
    val beamGeo = Geometry(
        floatArrayOf(
            WINDOW.x - 0.36f, topY, WALL_Z + 0.02f, WINDOW.x + 0.36f, topY, WALL_Z + 0.02f,
            WINDOW.x + 0.62f, 0.02f, WALL_Z + 1.35f, WINDOW.x - 0.08f, 0.02f, WALL_Z + 1.35f,
        ),
        FloatArray(12),
        floatArrayOf(0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f),
        intArrayOf(0, 3, 1, 1, 3, 2),
    ).computeVertexNormals()
    val beamMat = Material.basic(Color.hex("#fff4dc")) {
        map = beamTexture()
        transparent = true
        opacity = 0.12f
        depthWrite = false
        doubleSide = true
        toneMapped = false
        additive = true
    }
    val beam = Mesh(beamGeo, beamMat)

    val shelf = buildShelf()
    shelf.position.set(1.9f, 1.5f, WALL_Z + 0.13f)
    val pouf = buildPouf()
    pouf.position.set(-1.75f, 0f, -1.25f)
    val painting = buildPainting()
    painting.position.set(1.02f, 1.36f, WALL_Z + 0.02f)

    group.add(wainscot, rail, baseboard, floor, corner, win, patch, beam, shelf, pouf, painting)
    return House(group, wallMat, floorMat, trimMat, patchMat, beamMat)
}
