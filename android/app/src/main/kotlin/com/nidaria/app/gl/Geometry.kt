// Geometrías generadas por código, con los mismos parámetros y el mismo
// orden de vértices que las de Three.js, para que las formas coincidan.

package com.nidaria.app.gl

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// Identificadores de los búferes en la tarjeta gráfica.
class GpuGeometry(val vao: Int, val buffers: IntArray, val context: Int)

// Lo que hay que borrar de la tarjeta gráfica en el próximo fotograma.
object GpuTrash {
    val geometries = ArrayList<GpuGeometry>()
}

class Geometry(
    var positions: FloatArray,
    var normals: FloatArray,
    var uvs: FloatArray? = null,
    var indices: IntArray? = null,
) {
    // Compartida entre muchas mallas: no se libera al soltar una de ellas.
    var shared = false
    internal var gpu: GpuGeometry? = null
    private var boundsCache: FloatArray? = null

    val vertexCount: Int get() = positions.size / 3

    fun dispose() {
        gpu?.let { GpuTrash.geometries.add(it) }
        gpu = null
    }

    // minX, minY, minZ, maxX, maxY, maxZ
    fun bounds(): FloatArray {
        boundsCache?.let { return it }
        val b = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        var i = 0
        while (i < positions.size) {
            b[0] = min(b[0], positions[i]); b[1] = min(b[1], positions[i + 1]); b[2] = min(b[2], positions[i + 2])
            b[3] = max(b[3], positions[i]); b[4] = max(b[4], positions[i + 1]); b[5] = max(b[5], positions[i + 2])
            i += 3
        }
        boundsCache = b
        return b
    }

    private fun changed() {
        boundsCache = null
        dispose()
    }

    fun translate(x: Float, y: Float, z: Float): Geometry {
        var i = 0
        while (i < positions.size) {
            positions[i] += x; positions[i + 1] += y; positions[i + 2] += z
            i += 3
        }
        changed()
        return this
    }

    fun rotateX(angle: Float): Geometry {
        val c = cos(angle)
        val s = sin(angle)
        for (array in arrayOf(positions, normals)) {
            var i = 0
            while (i < array.size) {
                val y = array[i + 1]
                val z = array[i + 2]
                array[i + 1] = y * c - z * s
                array[i + 2] = y * s + z * c
                i += 3
            }
        }
        changed()
        return this
    }

    fun center(): Geometry {
        val b = bounds()
        return translate(-(b[0] + b[3]) / 2f, -(b[1] + b[4]) / 2f, -(b[2] + b[5]) / 2f)
    }

    // Normales por vértice: suma de las normales de sus caras, ponderada
    // por el área. Sin índices, cada cara queda plana.
    fun computeVertexNormals(): Geometry {
        normals = FloatArray(positions.size)
        val idx = indices
        val count = idx?.size ?: (positions.size / 3)
        var i = 0
        while (i < count) {
            val a = (idx?.get(i) ?: i) * 3
            val b = (idx?.get(i + 1) ?: (i + 1)) * 3
            val c = (idx?.get(i + 2) ?: (i + 2)) * 3
            val abx = positions[a] - positions[b]; val aby = positions[a + 1] - positions[b + 1]; val abz = positions[a + 2] - positions[b + 2]
            val cbx = positions[c] - positions[b]; val cby = positions[c + 1] - positions[b + 1]; val cbz = positions[c + 2] - positions[b + 2]
            val nx = cby * abz - cbz * aby
            val ny = cbz * abx - cbx * abz
            val nz = cbx * aby - cby * abx
            for (v in intArrayOf(a, b, c)) {
                normals[v] += nx; normals[v + 1] += ny; normals[v + 2] += nz
            }
            i += 3
        }
        i = 0
        while (i < normals.size) {
            val l = sqrt(normals[i] * normals[i] + normals[i + 1] * normals[i + 1] + normals[i + 2] * normals[i + 2])
            if (l > 0f) {
                normals[i] /= l; normals[i + 1] /= l; normals[i + 2] /= l
            }
            i += 3
        }
        changed()
        return this
    }
}

private class Builder {
    val p = ArrayList<Float>()
    val n = ArrayList<Float>()
    val uv = ArrayList<Float>()
    val idx = ArrayList<Int>()

    fun vertex(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, u: Float, v: Float) {
        p.add(x); p.add(y); p.add(z)
        n.add(nx); n.add(ny); n.add(nz)
        uv.add(u); uv.add(v)
    }

    fun tri(a: Int, b: Int, c: Int) {
        idx.add(a); idx.add(b); idx.add(c)
    }

    val count: Int get() = p.size / 3

    fun build(): Geometry = Geometry(p.toFloatArray(), n.toFloatArray(), uv.toFloatArray(), idx.toIntArray())
}

private const val TWO_PI = (PI * 2).toFloat()

fun sphereGeometry(
    radius: Float = 1f,
    widthSegments: Int = 32,
    heightSegments: Int = 16,
    phiStart: Float = 0f,
    phiLength: Float = TWO_PI,
    thetaStart: Float = 0f,
    thetaLength: Float = PI_F,
): Geometry {
    val ws = max(3, widthSegments)
    val hs = max(2, heightSegments)
    val thetaEnd = min(thetaStart + thetaLength, PI_F)
    val b = Builder()
    for (iy in 0..hs) {
        val v = iy / hs.toFloat()
        var uOffset = 0f
        if (iy == 0 && thetaStart == 0f) uOffset = 0.5f / ws
        else if (iy == hs && thetaEnd == PI_F) uOffset = -0.5f / ws
        for (ix in 0..ws) {
            val u = ix / ws.toFloat()
            val x = -radius * cos(phiStart + u * phiLength) * sin(thetaStart + v * thetaLength)
            val y = radius * cos(thetaStart + v * thetaLength)
            val z = radius * sin(phiStart + u * phiLength) * sin(thetaStart + v * thetaLength)
            val l = sqrt(x * x + y * y + z * z).let { if (it == 0f) 1f else it }
            b.vertex(x, y, z, x / l, y / l, z / l, u + uOffset, 1f - v)
        }
    }
    val row = ws + 1
    for (iy in 0 until hs) {
        for (ix in 0 until ws) {
            val a = iy * row + ix + 1
            val bb = iy * row + ix
            val c = (iy + 1) * row + ix
            val d = (iy + 1) * row + ix + 1
            if (iy != 0 || thetaStart > 0f) b.tri(a, bb, d)
            if (iy != hs - 1 || thetaEnd < PI_F) b.tri(bb, c, d)
        }
    }
    return b.build()
}

fun cylinderGeometry(
    radiusTop: Float = 1f,
    radiusBottom: Float = 1f,
    height: Float = 1f,
    radialSegments: Int = 32,
    heightSegments: Int = 1,
    openEnded: Boolean = false,
    thetaStart: Float = 0f,
    thetaLength: Float = TWO_PI,
): Geometry {
    val b = Builder()
    val half = height / 2f
    val slope = (radiusBottom - radiusTop) / height
    val row = radialSegments + 1
    for (y in 0..heightSegments) {
        val v = y / heightSegments.toFloat()
        val radius = v * (radiusBottom - radiusTop) + radiusTop
        for (x in 0..radialSegments) {
            val u = x / radialSegments.toFloat()
            val theta = u * thetaLength + thetaStart
            val st = sin(theta)
            val ct = cos(theta)
            val l = sqrt(st * st + slope * slope + ct * ct)
            b.vertex(radius * st, -v * height + half, radius * ct, st / l, slope / l, ct / l, u, 1f - v)
        }
    }
    for (x in 0 until radialSegments) {
        for (y in 0 until heightSegments) {
            val a = y * row + x
            val bb = (y + 1) * row + x
            val c = (y + 1) * row + x + 1
            val d = y * row + x + 1
            if (radiusTop > 0f || y != 0) b.tri(a, bb, d)
            if (radiusBottom > 0f || y != heightSegments - 1) b.tri(bb, c, d)
        }
    }
    fun cap(top: Boolean) {
        val radius = if (top) radiusTop else radiusBottom
        val sign = if (top) 1f else -1f
        val centerStart = b.count
        for (x in 1..radialSegments) b.vertex(0f, half * sign, 0f, 0f, sign, 0f, 0.5f, 0.5f)
        val centerEnd = b.count
        for (x in 0..radialSegments) {
            val theta = x / radialSegments.toFloat() * thetaLength + thetaStart
            val ct = cos(theta)
            val st = sin(theta)
            b.vertex(radius * st, half * sign, radius * ct, 0f, sign, 0f, ct * 0.5f + 0.5f, st * 0.5f * sign + 0.5f)
        }
        for (x in 0 until radialSegments) {
            val c = centerStart + x
            val i = centerEnd + x
            if (top) b.tri(i, i + 1, c) else b.tri(i + 1, i, c)
        }
    }
    if (!openEnded) {
        if (radiusTop > 0f) cap(true)
        if (radiusBottom > 0f) cap(false)
    }
    return b.build()
}

fun coneGeometry(radius: Float, height: Float, radialSegments: Int = 32, heightSegments: Int = 1): Geometry =
    cylinderGeometry(0f, radius, height, radialSegments, heightSegments)

fun torusGeometry(radius: Float = 1f, tube: Float = 0.4f, radialSegments: Int = 12, tubularSegments: Int = 48, arc: Float = TWO_PI): Geometry {
    val b = Builder()
    for (j in 0..radialSegments) {
        for (i in 0..tubularSegments) {
            val u = i / tubularSegments.toFloat() * arc
            val v = j / radialSegments.toFloat() * TWO_PI
            val x = (radius + tube * cos(v)) * cos(u)
            val y = (radius + tube * cos(v)) * sin(u)
            val z = tube * sin(v)
            val nx = x - radius * cos(u)
            val ny = y - radius * sin(u)
            val l = sqrt(nx * nx + ny * ny + z * z).let { if (it == 0f) 1f else it }
            b.vertex(x, y, z, nx / l, ny / l, z / l, i / tubularSegments.toFloat(), j / radialSegments.toFloat())
        }
    }
    val row = tubularSegments + 1
    for (j in 1..radialSegments) {
        for (i in 1..tubularSegments) {
            val a = row * j + i - 1
            val bb = row * (j - 1) + i - 1
            val c = row * (j - 1) + i
            val d = row * j + i
            b.tri(a, bb, d)
            b.tri(bb, c, d)
        }
    }
    return b.build()
}

// Sólido de revolución de un perfil (x = radio, y = altura). El perfil
// va de abajo arriba para que las caras miren hacia fuera.
fun latheGeometry(points: List<Vec2>, segments: Int = 12): Geometry {
    val b = Builder()
    val count = points.size
    val initX = FloatArray(count)
    val initY = FloatArray(count)
    var prevX = 0f
    var prevY = 0f
    for (j in 0 until count) {
        when (j) {
            0 -> {
                val dx = points[1].x - points[0].x
                val dy = points[1].y - points[0].y
                prevX = dy
                prevY = -dx
                val l = sqrt(dy * dy + dx * dx).let { if (it == 0f) 1f else it }
                initX[j] = dy / l
                initY[j] = -dx / l
            }
            count - 1 -> {
                initX[j] = prevX
                initY[j] = prevY
            }
            else -> {
                val dx = points[j + 1].x - points[j].x
                val dy = points[j + 1].y - points[j].y
                val sx = dy + prevX
                val sy = -dx + prevY
                val l = sqrt(sx * sx + sy * sy).let { if (it == 0f) 1f else it }
                initX[j] = sx / l
                initY[j] = sy / l
                prevX = dy
                prevY = -dx
            }
        }
    }
    for (i in 0..segments) {
        val phi = i / segments.toFloat() * TWO_PI
        val s = sin(phi)
        val c = cos(phi)
        for (j in 0 until count) {
            b.vertex(points[j].x * s, points[j].y, points[j].x * c, initX[j] * s, initY[j], initX[j] * c, i / segments.toFloat(), j / (count - 1f))
        }
    }
    for (i in 0 until segments) {
        for (j in 0 until count - 1) {
            val base = j + i * count
            val a = base
            val bb = base + count
            val c = base + count + 1
            val d = base + 1
            b.tri(a, bb, d)
            b.tri(c, d, bb)
        }
    }
    return b.build()
}

// Cápsula a lo largo del eje Y: un cilindro de largo `length` con dos
// semiesferas de radio `radius`.
fun capsuleGeometry(radius: Float = 1f, length: Float = 1f, capSegments: Int = 4, radialSegments: Int = 8): Geometry {
    val points = ArrayList<Vec2>()
    // Como en Three.js, cada arco lleva el doble de tramos que capSegments.
    val steps = capSegments * 2
    for (i in 0..steps) {
        val a = PI_F * 1.5f + (i / steps.toFloat()) * (PI_F * 0.5f)
        points.add(Vec2(radius * cos(a), -length / 2f + radius * sin(a)))
    }
    for (i in 0..steps) {
        val a = (i / steps.toFloat()) * (PI_F * 0.5f)
        points.add(Vec2(radius * cos(a), length / 2f + radius * sin(a)))
    }
    points[0].x = 0f
    points[points.size - 1].x = 0f
    return latheGeometry(points, radialSegments)
}

fun boxGeometry(width: Float, height: Float, depth: Float): Geometry {
    val b = Builder()
    val w = width / 2f
    val h = height / 2f
    val d = depth / 2f
    // Cada cara: normal y sus cuatro esquinas en sentido antihorario.
    val faces = arrayOf(
        floatArrayOf(1f, 0f, 0f, w, -h, d, w, -h, -d, w, h, -d, w, h, d),
        floatArrayOf(-1f, 0f, 0f, -w, -h, -d, -w, -h, d, -w, h, d, -w, h, -d),
        floatArrayOf(0f, 1f, 0f, -w, h, d, w, h, d, w, h, -d, -w, h, -d),
        floatArrayOf(0f, -1f, 0f, -w, -h, -d, w, -h, -d, w, -h, d, -w, -h, d),
        floatArrayOf(0f, 0f, 1f, -w, -h, d, w, -h, d, w, h, d, -w, h, d),
        floatArrayOf(0f, 0f, -1f, w, -h, -d, -w, -h, -d, -w, h, -d, w, h, -d),
    )
    val corners = arrayOf(floatArrayOf(0f, 0f), floatArrayOf(1f, 0f), floatArrayOf(1f, 1f), floatArrayOf(0f, 1f))
    for (f in faces) {
        val start = b.count
        for (k in 0 until 4) b.vertex(f[3 + k * 3], f[4 + k * 3], f[5 + k * 3], f[0], f[1], f[2], corners[k][0], corners[k][1])
        b.tri(start, start + 1, start + 2)
        b.tri(start, start + 2, start + 3)
    }
    return b.build()
}

// Plano en XY mirando a +Z, con v = 1 arriba.
fun planeGeometry(width: Float, height: Float): Geometry {
    val b = Builder()
    val w = width / 2f
    val h = height / 2f
    b.vertex(-w, h, 0f, 0f, 0f, 1f, 0f, 1f)
    b.vertex(w, h, 0f, 0f, 0f, 1f, 1f, 1f)
    b.vertex(-w, -h, 0f, 0f, 0f, 1f, 0f, 0f)
    b.vertex(w, -h, 0f, 0f, 0f, 1f, 1f, 0f)
    b.tri(0, 2, 1)
    b.tri(2, 3, 1)
    return b.build()
}

// ── Contornos 2D ──────────────────────────────────────────────

class Shape {
    private sealed interface Curve {
        fun point(t: Float): Vec2
        val divisions: Int
    }

    private class Line(val a: Vec2, val b: Vec2) : Curve {
        override fun point(t: Float) = Vec2(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
        override val divisions = 0
    }

    private class Cubic(val p0: Vec2, val p1: Vec2, val p2: Vec2, val p3: Vec2) : Curve {
        override fun point(t: Float): Vec2 {
            val k = 1f - t
            val a = k * k * k
            val b = 3f * k * k * t
            val c = 3f * k * t * t
            val d = t * t * t
            return Vec2(a * p0.x + b * p1.x + c * p2.x + d * p3.x, a * p0.y + b * p1.y + c * p2.y + d * p3.y)
        }
        override val divisions = 1
    }

    private class Quadratic(val p0: Vec2, val p1: Vec2, val p2: Vec2) : Curve {
        override fun point(t: Float): Vec2 {
            val k = 1f - t
            return Vec2(k * k * p0.x + 2f * k * t * p1.x + t * t * p2.x, k * k * p0.y + 2f * k * t * p1.y + t * t * p2.y)
        }
        override val divisions = 1
    }

    private class Arc(val cx: Float, val cy: Float, val r: Float, val start: Float, val end: Float, val clockwise: Boolean) : Curve {
        override fun point(t: Float): Vec2 {
            var delta = end - start
            val same = abs(delta) < 1e-7f
            while (delta < 0f) delta += TWO_PI
            while (delta > TWO_PI) delta -= TWO_PI
            if (delta < 1e-7f) delta = if (same) 0f else TWO_PI
            if (clockwise && !same) delta = if (delta == TWO_PI) -TWO_PI else delta - TWO_PI
            val angle = start + t * delta
            return Vec2(cx + r * cos(angle), cy + r * sin(angle))
        }
        override val divisions = 2
    }

    private val curves = ArrayList<Curve>()
    private val current = Vec2()

    fun moveTo(x: Float, y: Float): Shape {
        current.set(x, y)
        return this
    }

    fun lineTo(x: Float, y: Float): Shape {
        curves.add(Line(Vec2(current.x, current.y), Vec2(x, y)))
        current.set(x, y)
        return this
    }

    fun bezierCurveTo(c1x: Float, c1y: Float, c2x: Float, c2y: Float, x: Float, y: Float): Shape {
        curves.add(Cubic(Vec2(current.x, current.y), Vec2(c1x, c1y), Vec2(c2x, c2y), Vec2(x, y)))
        current.set(x, y)
        return this
    }

    fun quadraticCurveTo(cx: Float, cy: Float, x: Float, y: Float): Shape {
        curves.add(Quadratic(Vec2(current.x, current.y), Vec2(cx, cy), Vec2(x, y)))
        current.set(x, y)
        return this
    }

    fun absarc(x: Float, y: Float, radius: Float, start: Float, end: Float, clockwise: Boolean): Shape {
        val arc = Arc(x, y, radius, start, end, clockwise)
        if (curves.isNotEmpty()) {
            val first = arc.point(0f)
            if (abs(first.x - current.x) > 1e-6f || abs(first.y - current.y) > 1e-6f) lineTo(first.x, first.y)
        }
        curves.add(arc)
        val last = arc.point(1f)
        current.set(last.x, last.y)
        return this
    }

    fun closePath(): Shape {
        if (curves.isEmpty()) return this
        val start = curves.first().point(0f)
        val end = curves.last().point(1f)
        if (abs(start.x - end.x) > 1e-6f || abs(start.y - end.y) > 1e-6f) curves.add(Line(end, start))
        return this
    }

    // Puntos del contorno, sin repetir el de cierre.
    fun getPoints(curveSegments: Int = 12): List<Vec2> {
        val points = ArrayList<Vec2>()
        for (curve in curves) {
            val resolution = when (curve.divisions) {
                0 -> 1
                2 -> curveSegments * 2
                else -> curveSegments
            }
            for (d in 0..resolution) {
                val pt = curve.point(d / resolution.toFloat())
                val last = points.lastOrNull()
                if (last != null && abs(last.x - pt.x) < 1e-7f && abs(last.y - pt.y) < 1e-7f) continue
                points.add(pt)
            }
        }
        if (points.size > 1) {
            val first = points.first()
            val last = points.last()
            if (abs(first.x - last.x) < 1e-6f && abs(first.y - last.y) < 1e-6f) points.removeAt(points.size - 1)
        }
        return points
    }
}

private fun signedArea(points: List<Vec2>): Float {
    var a = 0f
    var p = points.size - 1
    for (q in points.indices) {
        a += points[p].x * points[q].y - points[q].x * points[p].y
        p = q
    }
    return a * 0.5f
}

private fun counterClockwise(points: List<Vec2>): List<Vec2> = if (signedArea(points) < 0f) points.reversed() else points

// Triangula un polígono simple antihorario cortando orejas.
internal fun triangulate(points: List<Vec2>): IntArray {
    val n = points.size
    if (n < 3) return IntArray(0)
    val out = ArrayList<Int>()
    val v = ArrayList<Int>(n)
    for (i in 0 until n) v.add(i)
    var nv = n
    var guard = 2 * nv
    var cur = nv - 1
    while (nv > 2) {
        if (guard-- <= 0) break
        var u = cur
        if (nv <= u) u = 0
        cur = u + 1
        if (nv <= cur) cur = 0
        var w = cur + 1
        if (nv <= w) w = 0
        if (isEar(points, v, u, cur, w, nv)) {
            out.add(v[u]); out.add(v[cur]); out.add(v[w])
            v.removeAt(cur)
            nv--
            guard = 2 * nv
        }
    }
    return out.toIntArray()
}

private fun isEar(points: List<Vec2>, v: List<Int>, u: Int, cur: Int, w: Int, nv: Int): Boolean {
    val a = points[v[u]]
    val b = points[v[cur]]
    val c = points[v[w]]
    if ((b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x) < 1e-10f) return false
    for (p in 0 until nv) {
        if (p == u || p == cur || p == w) continue
        val pt = points[v[p]]
        val c1 = (c.x - b.x) * (pt.y - b.y) - (c.y - b.y) * (pt.x - b.x)
        val c2 = (b.x - a.x) * (pt.y - a.y) - (b.y - a.y) * (pt.x - a.x)
        val c3 = (a.x - c.x) * (pt.y - c.y) - (a.y - c.y) * (pt.x - c.x)
        if (c1 >= 0f && c2 >= 0f && c3 >= 0f) return false
    }
    return true
}

// Contorno plano relleno, mirando a +Z, con uv = (x, y).
fun shapeGeometry(shape: Shape, curveSegments: Int = 12): Geometry {
    val pts = counterClockwise(shape.getPoints(curveSegments))
    val b = Builder()
    for (pt in pts) b.vertex(pt.x, pt.y, 0f, 0f, 0f, 1f, pt.x, pt.y)
    val tris = triangulate(pts)
    var i = 0
    while (i < tris.size) {
        b.tri(tris[i], tris[i + 1], tris[i + 2])
        i += 3
    }
    return b.build()
}

// Contorno con grosor, de z = 0 a z = depth, con biseles opcionales que
// lo ensanchan y redondean el canto. Caras planas, como en Three.js.
fun extrudeGeometry(
    shape: Shape,
    depth: Float,
    bevelEnabled: Boolean = false,
    bevelThickness: Float = 0.2f,
    bevelSize: Float = bevelThickness - 0.1f,
    bevelSegments: Int = 3,
    curveSegments: Int = 12,
): Geometry {
    val contour = counterClockwise(shape.getPoints(curveSegments))
    val n = contour.size
    val tris = triangulate(contour)

    // Dirección en la que se ensancha cada punto con el bisel.
    val moveX = FloatArray(n)
    val moveY = FloatArray(n)
    if (bevelEnabled) {
        for (i in 0 until n) {
            val prev = contour[(i + n - 1) % n]
            val pt = contour[i]
            val next = contour[(i + 1) % n]
            var d1x = pt.x - prev.x
            var d1y = pt.y - prev.y
            var d2x = next.x - pt.x
            var d2y = next.y - pt.y
            val l1 = sqrt(d1x * d1x + d1y * d1y).let { if (it == 0f) 1f else it }
            val l2 = sqrt(d2x * d2x + d2y * d2y).let { if (it == 0f) 1f else it }
            d1x /= l1; d1y /= l1; d2x /= l2; d2y /= l2
            // Normales hacia fuera de un contorno antihorario.
            val n1x = d1y
            val n1y = -d1x
            val n2x = d2y
            val n2y = -d2x
            val k = 1f + n1x * n2x + n1y * n2y
            var mx: Float
            var my: Float
            if (k < 1e-4f) {
                mx = n1x
                my = n1y
            } else {
                mx = (n1x + n2x) / k
                my = (n1y + n2y) / k
            }
            val lenSq = mx * mx + my * my
            if (lenSq > 2f) {
                val shrink = sqrt(lenSq / 2f)
                mx /= shrink
                my /= shrink
            }
            moveX[i] = mx
            moveY[i] = my
        }
    }

    // Capas: cuánto se ensancha el contorno y a qué z.
    val layerSize = ArrayList<Float>()
    val layerZ = ArrayList<Float>()
    if (bevelEnabled) {
        for (s in 0 until bevelSegments) {
            val t = s / bevelSegments.toFloat()
            layerSize.add(bevelSize * sin(t * PI_F / 2f))
            layerZ.add(-bevelThickness * cos(t * PI_F / 2f))
        }
        layerSize.add(bevelSize); layerZ.add(0f)
        layerSize.add(bevelSize); layerZ.add(depth)
        for (s in bevelSegments - 1 downTo 0) {
            val t = s / bevelSegments.toFloat()
            layerSize.add(bevelSize * sin(t * PI_F / 2f))
            layerZ.add(depth + bevelThickness * cos(t * PI_F / 2f))
        }
    } else {
        layerSize.add(0f); layerZ.add(0f)
        layerSize.add(0f); layerZ.add(depth)
    }

    val out = ArrayList<Float>()
    fun push(layer: Int, i: Int) {
        out.add(contour[i].x + moveX[i] * layerSize[layer])
        out.add(contour[i].y + moveY[i] * layerSize[layer])
        out.add(layerZ[layer])
    }
    var t = 0
    while (t < tris.size) {
        // Tapa de atrás (mira a -Z) y de delante (mira a +Z).
        push(0, tris[t + 2]); push(0, tris[t + 1]); push(0, tris[t])
        val top = layerZ.size - 1
        push(top, tris[t]); push(top, tris[t + 1]); push(top, tris[t + 2])
        t += 3
    }
    for (layer in 0 until layerZ.size - 1) {
        for (i in 0 until n) {
            val j = (i + 1) % n
            push(layer, i); push(layer, j); push(layer + 1, j)
            push(layer, i); push(layer + 1, j); push(layer + 1, i)
        }
    }
    val positions = out.toFloatArray()
    return Geometry(positions, FloatArray(positions.size)).computeVertexNormals()
}
