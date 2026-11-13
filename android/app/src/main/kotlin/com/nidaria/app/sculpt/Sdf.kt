// El escultor de formas blandas, copia de public/js/scene/sdf-core.js.
// Un modelo es una suma de formas sencillas que se funden como
// plastilina; el campo de distancias se convierte en una malla lisa con
// su color, su oclusión y, si se pide, los pesos de los huesos.
//
// Las cuentas van en Double y el campo y la malla en Float, igual que en
// JavaScript (números de 64 bits y Float32Array), así que con el mismo
// encargo sale la misma malla: lo comprueba SculptParityTest.

package com.nidaria.app.sculpt

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sqrt

private fun hypot3(x: Double, y: Double, z: Double = 0.0): Double = sqrt(x * x + y * y + z * z)

fun smin(a: Double, b: Double, k: Double): Double {
    if (k <= 0) return if (a < b) a else b
    val h = max(k - abs(a - b), 0.0) / k
    return (if (a < b) a else b) - h * h * k * 0.25
}

private fun smax(a: Double, b: Double, k: Double): Double = -smin(-a, -b, k)

// Giro de una forma: ángulos (en el orden de Three.js) o un cuaternión.
sealed interface Rot
class Euler(val x: Double, val y: Double, val z: Double, val order: String = "XYZ") : Rot
class QuatRot(val q: Q) : Rot

fun rot(x: Double, y: Double, z: Double, order: String = "XYZ"): Rot = Euler(x, y, z, order)
fun rot(q: Q): Rot = QuatRot(q)

// Matriz que pasa del mundo al espacio propio de la forma (la traspuesta
// de su giro), como Matrix4.makeRotationFromQuaternion de Three.js.
private fun inverseRotation(r: Rot?): DoubleArray? {
    if (r == null) return null
    val q = when (r) {
        is Euler -> quaternionFromEuler(r.x, r.y, r.z, r.order)
        is QuatRot -> doubleArrayOf(r.q.x, r.q.y, r.q.z, r.q.w)
    }
    val x = q[0]
    val y = q[1]
    val z = q[2]
    val w = q[3]
    val x2 = x + x
    val y2 = y + y
    val z2 = z + z
    val xx = x * x2
    val xy = x * y2
    val xz = x * z2
    val yy = y * y2
    val yz = y * z2
    val zz = z * z2
    val wx = w * x2
    val wy = w * y2
    val wz = w * z2
    return doubleArrayOf(
        1 - (yy + zz), xy + wz, xz - wy,
        xy - wz, 1 - (xx + zz), yz + wx,
        xz + wy, yz - wx, 1 - (xx + yy),
    )
}

// ── Formas ────────────────────────────────────────────────────
// Cada forma sabe su distancia con signo (negativa dentro) y lleva una
// esfera que la envuelve, para no calcularla cuando está lejos.

abstract class Shape(center: DoubleArray, r: Rot?, radius: Double) {
    @JvmField var cx = center[0]
    @JvmField var cy = center[1]
    @JvmField var cz = center[2]
    @JvmField var m: DoubleArray? = inverseRotation(r)
    @JvmField var bx = cx
    @JvmField var by = cy
    @JvmField var bz = cz
    @JvmField var br = radius

    // De paso: el punto en coordenadas de la forma.
    @JvmField var lx = 0.0
    @JvmField var ly = 0.0
    @JvmField var lz = 0.0

    abstract val type: String
    abstract fun d(x: Double, y: Double, z: Double): Double

    // Copia con sus propias variables de paso (para esculpir en otro hilo).
    abstract fun copy(): Shape

    fun local(x: Double, y: Double, z: Double) {
        val dx = x - cx
        val dy = y - cy
        val dz = z - cz
        val mm = m
        if (mm != null) {
            lx = mm[0] * dx + mm[1] * dy + mm[2] * dz
            ly = mm[3] * dx + mm[4] * dy + mm[5] * dz
            lz = mm[6] * dx + mm[7] * dy + mm[8] * dz
        } else {
            lx = dx
            ly = dy
            lz = dz
        }
    }

    protected fun <T : Shape> T.base(from: Shape): T {
        cx = from.cx; cy = from.cy; cz = from.cz; m = from.m
        bx = from.bx; by = from.by; bz = from.bz; br = from.br
        return this
    }

    // Las medidas con los mismos nombres que en la web (para la prueba).
    open fun fields(): LinkedHashMap<String, Any?> = linkedMapOf(
        "cx" to cx, "cy" to cy, "cz" to cz, "m" to m, "bx" to bx, "by" to by, "bz" to bz, "br" to br,
    )

    internal open fun assign(name: String, value: Double) {
        when (name) {
            "cx" -> cx = value; "cy" -> cy = value; "cz" -> cz = value
            "bx" -> bx = value; "by" -> by = value; "bz" -> bz = value; "br" -> br = value
            else -> throw IllegalArgumentException("Medida desconocida: $type.$name")
        }
    }

    companion object {
        // Rehace una forma a partir de sus medidas (las de un encargo de la web).
        fun revive(type: String, values: Map<String, Any?>): Shape {
            val o = doubleArrayOf(0.0, 0.0, 0.0)
            val shape: Shape = when (type) {
                "ellipsoid" -> Ellipsoid(o, doubleArrayOf(1.0, 1.0, 1.0), null)
                "sphere" -> Sphere(o, 1.0)
                "cone" -> RoundCone(o, doubleArrayOf(0.0, 1.0, 0.0), 1.0, 1.0)
                "box" -> RoundBox(o, doubleArrayOf(1.0, 1.0, 1.0), 0.0, null)
                "cylinder" -> Cylinder(o, 1.0, 1.0, 0.0, null)
                "torus" -> Torus(o, 1.0, 0.1, null, 1.0)
                "leaf" -> Leaf(o, null, 1.0, 0.5, 0.1, 0.0)
                else -> throw IllegalArgumentException("Forma desconocida: $type")
            }
            for ((name, value) in values) {
                if (name == "type") continue
                if (name == "m") shape.m = (value as DoubleArray?) else shape.assign(name, value as Double)
            }
            return shape
        }
    }
}

class Ellipsoid(center: DoubleArray, radii: DoubleArray, r: Rot?) : Shape(center, r, max(max(radii[0], radii[1]), radii[2])) {
    @JvmField var rx = radii[0]
    @JvmField var ry = radii[1]
    @JvmField var rz = radii[2]
    override val type get() = "ellipsoid"

    override fun d(x: Double, y: Double, z: Double): Double {
        local(x, y, z)
        val ax = lx / rx
        val ay = ly / ry
        val az = lz / rz
        val k0 = sqrt(ax * ax + ay * ay + az * az)
        val bx = ax / rx
        val by = ay / ry
        val bz = az / rz
        val k1 = sqrt(bx * bx + by * by + bz * bz)
        if (k1 < 1e-9) return -min(min(rx, ry), rz)
        return (k0 * (k0 - 1)) / k1
    }

    override fun copy() = Ellipsoid(doubleArrayOf(cx, cy, cz), doubleArrayOf(rx, ry, rz), null).base(this)
    override fun fields() = super.fields().apply { put("rx", rx); put("ry", ry); put("rz", rz) }
    override fun assign(name: String, value: Double) = when (name) {
        "rx" -> rx = value; "ry" -> ry = value; "rz" -> rz = value
        else -> super.assign(name, value)
    }
}

class Sphere(center: DoubleArray, r: Double) : Shape(center, null, r) {
    @JvmField var r = r
    override val type get() = "sphere"
    override fun d(x: Double, y: Double, z: Double): Double = hypot3(x - cx, y - cy, z - cz) - r
    override fun copy() = Sphere(doubleArrayOf(cx, cy, cz), r).base(this)
    override fun fields() = super.fields().apply { put("r", r) }
    override fun assign(name: String, value: Double) = if (name == "r") r = value else super.assign(name, value)
}

// Cono de puntas redondas entre a (radio ra) y b (radio rb). Con los dos
// radios iguales es una cápsula.
class RoundCone(a: DoubleArray, b: DoubleArray, ra: Double, rb: Double) : Shape(
    doubleArrayOf((a[0] + b[0]) / 2, (a[1] + b[1]) / 2, (a[2] + b[2]) / 2),
    null,
    hypot3(b[0] - a[0], b[1] - a[1], b[2] - a[2]) / 2 + max(ra, rb),
) {
    @JvmField var ax = a[0]
    @JvmField var ay = a[1]
    @JvmField var az = a[2]
    @JvmField var bax = b[0] - a[0]
    @JvmField var bay = b[1] - a[1]
    @JvmField var baz = b[2] - a[2]
    @JvmField var r1 = ra
    @JvmField var r2 = rb
    @JvmField var l2 = (bax * bax + bay * bay + baz * baz).let { if (it == 0.0) 1e-9 else it }
    @JvmField var rr = ra - rb
    @JvmField var a2 = l2 - rr * rr
    @JvmField var il2 = 1 / l2
    override val type get() = "cone"

    override fun d(x: Double, y: Double, z: Double): Double {
        val pax = x - ax
        val pay = y - ay
        val paz = z - az
        val yy = pax * bax + pay * bay + paz * baz
        val zz = yy - l2
        val qx = pax * l2 - bax * yy
        val qy = pay * l2 - bay * yy
        val qz = paz * l2 - baz * yy
        val x2 = qx * qx + qy * qy + qz * qz
        val y2 = yy * yy * l2
        val z2 = zz * zz * l2
        val k = sign(rr) * rr * rr * x2
        if (sign(zz) * a2 * z2 > k) return sqrt(x2 + z2) * il2 - r2
        if (sign(yy) * a2 * y2 < k) return sqrt(x2 + y2) * il2 - r1
        return (sqrt(x2 * a2 * il2) + yy * rr) * il2 - r1
    }

    override fun copy() = RoundCone(doubleArrayOf(ax, ay, az), doubleArrayOf(ax + bax, ay + bay, az + baz), r1, r2).base(this).also {
        it.bax = bax; it.bay = bay; it.baz = baz; it.l2 = l2; it.rr = rr; it.a2 = a2; it.il2 = il2
    }

    override fun fields() = super.fields().apply {
        put("ax", ax); put("ay", ay); put("az", az); put("bax", bax); put("bay", bay); put("baz", baz)
        put("r1", r1); put("r2", r2); put("l2", l2); put("rr", rr); put("a2", a2); put("il2", il2)
    }

    override fun assign(name: String, value: Double) = when (name) {
        "ax" -> ax = value; "ay" -> ay = value; "az" -> az = value
        "bax" -> bax = value; "bay" -> bay = value; "baz" -> baz = value
        "r1" -> r1 = value; "r2" -> r2 = value; "l2" -> l2 = value; "rr" -> rr = value; "a2" -> a2 = value; "il2" -> il2 = value
        else -> super.assign(name, value)
    }
}

class RoundBox(center: DoubleArray, half: DoubleArray, round: Double, r: Rot?) : Shape(center, r, hypot3(half[0], half[1], half[2])) {
    @JvmField var hx = half[0]
    @JvmField var hy = half[1]
    @JvmField var hz = half[2]
    @JvmField var r = min(min(min(round, half[0]), half[1]), half[2])
    override val type get() = "box"

    override fun d(x: Double, y: Double, z: Double): Double {
        local(x, y, z)
        val qx = abs(lx) - hx + r
        val qy = abs(ly) - hy + r
        val qz = abs(lz) - hz + r
        return hypot3(max(qx, 0.0), max(qy, 0.0), max(qz, 0.0)) + min(max(max(qx, qy), qz), 0.0) - r
    }

    override fun copy() = RoundBox(doubleArrayOf(cx, cy, cz), doubleArrayOf(hx, hy, hz), r, null).base(this).also { it.r = r }
    override fun fields() = super.fields().apply { put("hx", hx); put("hy", hy); put("hz", hz); put("r", r) }
    override fun assign(name: String, value: Double) = when (name) {
        "hx" -> hx = value; "hy" -> hy = value; "hz" -> hz = value; "r" -> r = value
        else -> super.assign(name, value)
    }
}

// Cilindro de eje y, con los cantos redondeados.
class Cylinder(center: DoubleArray, radius: Double, half: Double, round: Double, r: Rot?) : Shape(center, r, hypot3(radius, half)) {
    @JvmField var r = radius
    @JvmField var h = half
    @JvmField var round = min(min(round, radius), half)
    override val type get() = "cylinder"

    override fun d(x: Double, y: Double, z: Double): Double {
        local(x, y, z)
        val rr = round
        val dx = hypot3(lx, lz) - r + rr
        val dy = abs(ly) - h + rr
        return min(max(dx, dy), 0.0) + hypot3(max(dx, 0.0), max(dy, 0.0)) - rr
    }

    override fun copy() = Cylinder(doubleArrayOf(cx, cy, cz), r, h, round, null).base(this).also { it.round = round }
    override fun fields() = super.fields().apply { put("r", r); put("h", h); put("round", round) }
    override fun assign(name: String, value: Double) = when (name) {
        "r" -> r = value; "h" -> h = value; "round" -> round = value
        else -> super.assign(name, value)
    }
}

// Rosco de eje y: radio mayor R, grosor r.
class Torus(center: DoubleArray, bigR: Double, r: Double, rotation: Rot?, scaleY: Double = 1.0) : Shape(center, rotation, bigR + r) {
    @JvmField var bigR = bigR
    @JvmField var r = r
    @JvmField var sy = scaleY
    override val type get() = "torus"

    override fun d(x: Double, y: Double, z: Double): Double {
        local(x, y, z)
        val q = hypot3(lx, lz) - bigR
        return (hypot3(q, ly / sy) - r) * min(1.0, sy)
    }

    override fun copy() = Torus(doubleArrayOf(cx, cy, cz), bigR, r, null, sy).base(this)
    override fun fields() = super.fields().apply { put("R", bigR); put("r", r); put("sy", sy) }
    override fun assign(name: String, value: Double) = when (name) {
        "R" -> bigR = value; "r" -> r = value; "sy" -> sy = value
        else -> super.assign(name, value)
    }
}

// Hoja: una vesica (dos arcos que se juntan en punta) con grosor y los
// bordes redondos. Nace en `base`, crece por su eje y y es plana en z.
// `bend` la curva hacia +z en la punta.
class Leaf(base: DoubleArray, r: Rot?, length: Double, width: Double, thick: Double, bend: Double = 0.0) : Shape(base, r, length) {
    @JvmField var len = length
    @JvmField var hw = width / 2
    @JvmField var t = thick / 2
    @JvmField var bend = bend
    @JvmField var vr = 0.0
    @JvmField var vd = 0.0
    @JvmField var vb = 0.0
    override val type get() = "leaf"

    init {
        // La esfera envolvente se centra a media hoja.
        val mm = m
        val half = length / 2
        // Eje y de la forma en el mundo: la segunda fila de la traspuesta.
        bx = base[0] + (mm?.get(3) ?: 0.0) * half
        by = base[1] + (mm?.get(4) ?: 1.0) * half
        bz = base[2] + (mm?.get(5) ?: 0.0) * half
        br = half + thick + abs(bend) * length
        val b = half
        val w = hw
        vr = (b * b + w * w) / (2 * w)
        vd = vr - w
        vb = b
    }

    override fun d(x: Double, y: Double, z: Double): Double {
        local(x, y, z)
        val u = ly / len
        val lzz = lz - bend * len * u * u
        val px = abs(lx)
        val py = abs(ly - len / 2)
        val d2 = if ((py - vb) * vd > px * vb) hypot3(px, py - vb) else hypot3(px + vd, py) - vr
        val rr = t * 0.92
        val wx = d2 + rr
        val wy = abs(lzz) - t + rr
        return min(max(wx, wy), 0.0) + hypot3(max(wx, 0.0), max(wy, 0.0)) - rr
    }

    override fun copy() = Leaf(doubleArrayOf(cx, cy, cz), null, len, hw * 2, t * 2, bend).base(this).also {
        it.hw = hw; it.t = t; it.vr = vr; it.vd = vd; it.vb = vb
    }

    override fun fields() = super.fields().apply {
        put("len", len); put("hw", hw); put("t", t); put("bend", bend); put("vr", vr); put("vd", vd); put("vb", vb)
    }

    override fun assign(name: String, value: Double) = when (name) {
        "len" -> len = value; "hw" -> hw = value; "t" -> t = value; "bend" -> bend = value
        "vr" -> vr = value; "vd" -> vd = value; "vb" -> vb = value
        else -> super.assign(name, value)
    }
}

// Las mismas fábricas que `shape` en la web.
object Sh {
    fun sphere(c: DoubleArray, r: Double): Shape = Sphere(c, r)
    fun ellipsoid(c: DoubleArray, radii: DoubleArray, r: Rot? = null): Shape = Ellipsoid(c, radii, r)
    fun cone(a: DoubleArray, b: DoubleArray, ra: Double, rb: Double): Shape = RoundCone(a, b, ra, rb)
    fun capsule(a: DoubleArray, b: DoubleArray, r: Double): Shape = RoundCone(a, b, r, r)
    fun box(c: DoubleArray, half: DoubleArray, round: Double = 0.0, r: Rot? = null): Shape = RoundBox(c, half, round, r)
    fun cylinder(c: DoubleArray, radius: Double, half: Double, round: Double = 0.0, r: Rot? = null): Shape = Cylinder(c, radius, half, round, r)
    fun torus(c: DoubleArray, bigR: Double, r: Double, rotation: Rot? = null, scaleY: Double = 1.0): Shape = Torus(c, bigR, r, rotation, scaleY)
    fun leaf(base: DoubleArray, r: Rot?, length: Double, width: Double, thick: Double, bend: Double = 0.0): Shape = Leaf(base, r, length, width, thick, bend)
}

// ── Campo ─────────────────────────────────────────────────────

const val ADD = 0
const val SUB = 1

class Split(val bone: Int, val from: Double, val to: Double)

class Op(
    val op: Int,
    val form: Shape,
    val color: DoubleArray,
    val k: Double,
    val bone: Int,
    val colorK: Double,
    val split: Split?,
) {
    fun copy() = Op(op, form.copy(), color, k, bone, colorK, split)
}

class Paint(val form: Shape, val color: DoubleArray, val soft: Double, val amount: Double) {
    fun copy() = Paint(form.copy(), color, soft, amount)
}

class MeshOptions(val cell: Double = 0.02, val bones: Int = 0, val ao: Double = 1.0, val aoReach: Double = 0.18)

// Lo que sale del escultor: las mismas listas que en la web.
class SculptMesh(
    val position: FloatArray,
    val normal: FloatArray,
    val color: FloatArray,
    val ao: FloatArray,
    val index: IntArray,
    val skinIndex: IntArray?,
    val skinWeight: FloatArray?,
) {
    val bytes: Int get() = (position.size + normal.size + color.size + ao.size + index.size + (skinIndex?.size ?: 0) + (skinWeight?.size ?: 0)) * 4

    // Huella de la malla: SHA-256 de las listas (Float32 e Int32 en
    // little-endian), en el mismo orden que la calcula la web.
    fun fingerprint(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun floats(a: FloatArray) {
            val b = ByteBuffer.allocate(a.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            for (f in a) b.putFloat(f)
            digest.update(b.array())
        }
        fun ints(a: IntArray) {
            val b = ByteBuffer.allocate(a.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            for (i in a) b.putInt(i)
            digest.update(b.array())
        }
        floats(position); floats(normal); floats(color); floats(ao); ints(index)
        skinIndex?.let { ints(it) }
        skinWeight?.let { floats(it) }
        return digest.digest().joinToString("") { "%02x".format(it) }.take(24)
    }
}

// Las 12 aristas de una celda como pares de esquinas (bit 1 = x, 2 = y, 4 = z).
private val EDGES: Array<IntArray> = buildList {
    for (a in 0 until 8) for (bit in intArrayOf(1, 2, 4)) if (a and bit == 0) add(intArrayOf(a, a or bit))
}.toTypedArray()

private val TETRA = arrayOf(intArrayOf(1, -1, -1), intArrayOf(-1, -1, 1), intArrayOf(-1, 1, -1), intArrayOf(1, 1, 1))

open class Field(@JvmField val ground: Double? = null, @JvmField val groundK: Double = 0.03) {
    @JvmField val ops = ArrayList<Op>()
    @JvmField val paints = ArrayList<Paint>()

    // `color` va ya en lineal: [r, g, b].
    fun addShape(form: Shape, color: DoubleArray = doubleArrayOf(1.0, 1.0, 1.0), k: Double = 0.04, bone: Int = 0, colorK: Double? = null, split: Split? = null): Field {
        ops.add(Op(ADD, form, color, k, bone, colorK ?: (min(k * 0.5, 0.02) + 0.002), split))
        return this
    }

    fun subShape(form: Shape, k: Double = 0.02): Field {
        ops.add(Op(SUB, form, doubleArrayOf(), k, 0, 0.0, null))
        return this
    }

    fun paintShape(form: Shape, color: DoubleArray, soft: Double = 0.008, amount: Double = 1.0): Field {
        paints.add(Paint(form, color, soft, amount))
        return this
    }

    fun sdf(x: Double, y: Double, z: Double): Double {
        var d = 1e9
        for (i in ops.indices) {
            val op = ops[i]
            val f = op.form
            val bd = hypot3(x - f.bx, y - f.by, z - f.bz) - f.br
            if (op.op == ADD) {
                if (bd > d + op.k) continue
                d = smin(d, f.d(x, y, z), op.k)
            } else {
                if (bd > op.k - d) continue
                d = smax(d, -f.d(x, y, z), op.k)
            }
        }
        val g = ground
        if (g != null) d = smax(d, g - y, groundK)
        return d
    }

    // El encargo: una copia independiente que se puede esculpir en otro hilo.
    fun toJob(options: MeshOptions = MeshOptions()): Job = Job(ground, groundK, ops.map { it.copy() }, paints.map { it.copy() }, options)

    // Convierte el campo en malla, o null si no hay nada que esculpir.
    fun mesh(options: MeshOptions = MeshOptions()): SculptMesh? {
        val cell = options.cell
        val bones = options.bones
        val ao = options.ao
        val aoReach = options.aoReach
        val adds = ops.filter { it.op == ADD }
        if (adds.isEmpty()) return null

        // Caja de trabajo
        val lo = doubleArrayOf(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY)
        val hi = doubleArrayOf(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY)
        for (op in adds) {
            val f = op.form
            val r = f.br + op.k
            lo[0] = min(lo[0], f.bx - r); hi[0] = max(hi[0], f.bx + r)
            lo[1] = min(lo[1], f.by - r); hi[1] = max(hi[1], f.by + r)
            lo[2] = min(lo[2], f.bz - r); hi[2] = max(hi[2], f.bz + r)
        }
        val g = ground
        if (g != null) lo[1] = max(lo[1], g - cell * 2)
        val pad = cell * 2
        val ox = lo[0] - pad
        val oy = lo[1] - pad
        val oz = lo[2] - pad
        val nx = ceil((hi[0] - lo[0] + pad * 2) / cell).toInt() + 1
        val ny = ceil((hi[1] - lo[1] + pad * 2) / cell).toInt() + 1
        val nz = ceil((hi[2] - lo[2] + pad * 2) / cell).toInt() + 1
        val field = FloatArray(nx * ny * nz)

        // Campo de distancias. Se mira primero el centro de cada bloque de
        // 4×4×4: si está lejos de la superficie, todo el bloque tiene su signo.
        val bb = 4
        val reach = ((bb - 1) / 2.0) * cell * sqrt(3.0) * 1.35 + cell
        var bz = 0
        while (bz < nz) {
            var by = 0
            while (by < ny) {
                var bx = 0
                while (bx < nx) {
                    val dc = sdf(ox + (bx + (bb - 1) / 2.0) * cell, oy + (by + (bb - 1) / 2.0) * cell, oz + (bz + (bb - 1) / 2.0) * cell)
                    val far = abs(dc) > reach
                    val dcf = dc.toFloat()
                    val ez = min(bz + bb, nz)
                    val ey = min(by + bb, ny)
                    val ex = min(bx + bb, nx)
                    for (k in bz until ez) {
                        for (j in by until ey) {
                            var idx = bx + nx * (j + ny * k)
                            for (i in bx until ex) {
                                field[idx] = if (far) dcf else sdf(ox + i * cell, oy + j * cell, oz + k * cell).toFloat()
                                idx++
                            }
                        }
                    }
                    bx += bb
                }
                by += bb
            }
            bz += bb
        }

        // Un vértice por celda que cruza la superficie.
        val cx = nx - 1
        val cy = ny - 1
        val cz = nz - 1
        val cellVertex = IntArray(cx * cy * cz) { -1 }
        val positions = DoubleList()
        val corner = FloatArray(8)
        val offsets = intArrayOf(0, 1, nx, nx + 1, nx * ny, nx * ny + 1, nx * ny + nx, nx * ny + nx + 1)
        for (k in 0 until cz) {
            for (j in 0 until cy) {
                for (i in 0 until cx) {
                    val base = i + nx * (j + ny * k)
                    var mask = 0
                    for (c in 0 until 8) {
                        corner[c] = field[base + offsets[c]]
                        if (corner[c] < 0) mask = mask or (1 shl c)
                    }
                    if (mask == 0 || mask == 255) continue
                    var sx = 0.0
                    var sy = 0.0
                    var sz = 0.0
                    var count = 0
                    for (e in EDGES) {
                        val a = e[0]
                        val b = e[1]
                        val da = corner[a].toDouble()
                        val db = corner[b].toDouble()
                        if ((da < 0) == (db < 0)) continue
                        val t = da / (da - db)
                        sx += (a and 1) + (((b and 1) - (a and 1)) * t)
                        sy += ((a shr 1) and 1) + ((((b shr 1) and 1) - ((a shr 1) and 1)) * t)
                        sz += ((a shr 2) and 1) + ((((b shr 2) and 1) - ((a shr 2) and 1)) * t)
                        count++
                    }
                    cellVertex[i + cx * (j + cy * k)] = positions.size / 3
                    positions.add(ox + (i + sx / count) * cell)
                    positions.add(oy + (j + sy / count) * cell)
                    positions.add(oz + (k + sz / count) * cell)
                }
            }
        }

        // Caras: cada arista de la rejilla que cruza la superficie une las
        // cuatro celdas que la rodean.
        val indices = IntList()
        fun cellAt(i: Int, j: Int, k: Int) = cellVertex[i + cx * (j + cy * k)]
        fun quad(a: Int, b: Int, c: Int, d: Int, flip: Boolean) {
            if (a < 0 || b < 0 || c < 0 || d < 0) return
            if (flip) {
                indices.add(a); indices.add(c); indices.add(b); indices.add(a); indices.add(d); indices.add(c)
            } else {
                indices.add(a); indices.add(b); indices.add(c); indices.add(a); indices.add(c); indices.add(d)
            }
        }
        for (k in 0 until nz) {
            for (j in 0 until ny) {
                for (i in 0 until nx) {
                    val idx = i + nx * (j + ny * k)
                    val inside = field[idx] < 0
                    if (i < cx && j > 0 && k > 0 && j < cy && k < cz && inside != (field[idx + 1] < 0)) {
                        quad(cellAt(i, j - 1, k - 1), cellAt(i, j, k - 1), cellAt(i, j, k), cellAt(i, j - 1, k), !inside)
                    }
                    if (j < cy && i > 0 && k > 0 && i < cx && k < cz && inside != (field[idx + nx] < 0)) {
                        quad(cellAt(i - 1, j, k - 1), cellAt(i - 1, j, k), cellAt(i, j, k), cellAt(i, j, k - 1), !inside)
                    }
                    if (k < cz && i > 0 && j > 0 && i < cx && j < cy && inside != (field[idx + nx * ny] < 0)) {
                        quad(cellAt(i - 1, j - 1, k), cellAt(i, j - 1, k), cellAt(i, j, k), cellAt(i - 1, j, k), !inside)
                    }
                }
            }
        }

        // Pegar cada vértice a la superficie y sacar su normal.
        val count = positions.size / 3
        val pos = FloatArray(positions.size) { positions[it].toFloat() }
        val nor = FloatArray(count * 3)
        val h = cell * 0.3
        for (vtx in 0 until count) {
            var x = pos[vtx * 3].toDouble()
            var y = pos[vtx * 3 + 1].toDouble()
            var z = pos[vtx * 3 + 2].toDouble()
            var gx = 0.0
            var gy = 0.0
            var gz = 0.0
            for (it in 0 until 2) {
                var dsum = 0.0
                gx = 0.0
                gy = 0.0
                gz = 0.0
                for (tt in TETRA) {
                    val a = tt[0]
                    val b = tt[1]
                    val c = tt[2]
                    val f = sdf(x + a * h, y + b * h, z + c * h)
                    dsum += f
                    gx += a * f
                    gy += b * f
                    gz += c * f
                }
                val dd = dsum / 4
                val gl = hypot3(gx, gy, gz)
                if (gl < 1e-12) break
                gx /= gl
                gy /= gl
                gz /= gl
                if (it == 1) break
                val step = max(-cell, min(cell, dd))
                x -= gx * step
                y -= gy * step
                z -= gz * step
            }
            pos[vtx * 3] = x.toFloat()
            pos[vtx * 3 + 1] = y.toFloat()
            pos[vtx * 3 + 2] = z.toFloat()
            nor[vtx * 3] = gx.toFloat()
            nor[vtx * 3 + 1] = gy.toFloat()
            nor[vtx * 3 + 2] = gz.toFloat()
        }

        // Color, huesos y oclusión.
        val col = FloatArray(count * 3)
        val aoAttr = FloatArray(count)
        val skinIndex = if (bones > 0) IntArray(count * 4) else null
        val skinWeight = if (bones > 0) FloatArray(count * 4) else null
        val dist = FloatArray(adds.size)
        val boneW = FloatArray(max(1, bones))
        for (vtx in 0 until count) {
            val x = pos[vtx * 3].toDouble()
            val y = pos[vtx * 3 + 1].toDouble()
            val z = pos[vtx * 3 + 2].toDouble()
            var dmin = Double.POSITIVE_INFINITY
            for (i in adds.indices) {
                dist[i] = max(adds[i].form.d(x, y, z), 0.0).toFloat()
                if (dist[i] < dmin) dmin = dist[i].toDouble()
            }
            var r = 0.0
            var gg = 0.0
            var b = 0.0
            var wsum = 0.0
            if (bones > 0) boneW.fill(0f)
            for (i in adds.indices) {
                val op = adds[i]
                val gap = dist[i] - dmin
                val wc = max(0.0, 1 - gap / op.colorK)
                if (wc > 0) {
                    val w = wc * wc
                    r += op.color[0] * w
                    gg += op.color[1] * w
                    b += op.color[2] * w
                    wsum += w
                }
                if (bones > 0) {
                    val wb = max(0.0, 1 - gap / (op.k + 0.015))
                    if (wb > 0) {
                        val w = wb * wb
                        val split = op.split
                        if (split != null) {
                            val t = min(1.0, max(0.0, (y - split.from) / (split.to - split.from)))
                            val s = t * t * (3 - 2 * t)
                            boneW[op.bone] = (boneW[op.bone] + w * (1 - s)).toFloat()
                            boneW[split.bone] = (boneW[split.bone] + w * s).toFloat()
                        } else {
                            boneW[op.bone] = (boneW[op.bone] + w).toFloat()
                        }
                    }
                }
            }
            r /= wsum
            gg /= wsum
            b /= wsum
            for (paint in paints) {
                val pd = paint.form.d(x, y, z)
                if (pd > paint.soft) continue
                val t0 = min(1.0, max(0.0, 0.5 - pd / (2 * paint.soft)))
                val t = t0 * t0 * (3 - 2 * t0) * paint.amount
                r += (paint.color[0] - r) * t
                gg += (paint.color[1] - gg) * t
                b += (paint.color[2] - b) * t
            }
            col[vtx * 3] = r.toFloat()
            col[vtx * 3 + 1] = gg.toFloat()
            col[vtx * 3 + 2] = b.toFloat()

            if (skinIndex != null && skinWeight != null) {
                // Los cuatro huesos que más pesan.
                for (slot in 0 until 4) {
                    var best = -1
                    var bestW = 0.0
                    for (bi in 0 until bones) {
                        if (boneW[bi] > bestW) {
                            best = bi
                            bestW = boneW[bi].toDouble()
                        }
                    }
                    if (best < 0) break
                    skinIndex[vtx * 4 + slot] = best
                    skinWeight[vtx * 4 + slot] = bestW.toFloat()
                    boneW[best] = 0f
                }
                val total = skinWeight[vtx * 4].toDouble() + skinWeight[vtx * 4 + 1] + skinWeight[vtx * 4 + 2] + skinWeight[vtx * 4 + 3]
                if (total > 0) {
                    for (s in 0 until 4) skinWeight[vtx * 4 + s] = (skinWeight[vtx * 4 + s] / total).toFloat()
                } else {
                    skinWeight[vtx * 4] = 1f
                }
            }

            // Oclusión: cuánto se mete la forma (y el suelo) en el camino de la normal.
            if (ao > 0) {
                val nx0 = nor[vtx * 3].toDouble()
                val ny0 = nor[vtx * 3 + 1].toDouble()
                val nz0 = nor[vtx * 3 + 2].toDouble()
                var occ = 0.0
                var sca = 1.0
                for (i in 1..4) {
                    val hr = aoReach * (0.08 + 0.92 * powD(i / 4.0, 1.6))
                    val px = x + nx0 * hr
                    val py = y + ny0 * hr
                    val pz = z + nz0 * hr
                    var dd = sdf(px, py, pz)
                    if (g != null) dd = min(dd, py - g)
                    occ += max(0.0, hr - dd) * sca
                    sca *= 0.68
                }
                aoAttr[vtx] = max(0.0, 1 - (occ / aoReach) * 1.05 * ao).toFloat()
            } else {
                aoAttr[vtx] = 1f
            }
        }

        return SculptMesh(pos, nor, col, aoAttr, indices.toArray(), skinIndex, skinWeight)
    }
}

// Un encargo: datos propios (no compartidos con quien lo hizo) para
// esculpir en otro hilo. `key` es su huella, para las cachés.
class Job(val ground: Double?, val groundK: Double, val ops: List<Op>, val paints: List<Paint>, val options: MeshOptions) {
    fun field(): Field {
        val field = Field(ground, groundK)
        field.ops.addAll(ops)
        field.paints.addAll(paints)
        return field
    }

    fun mesh(): SculptMesh? = field().mesh(options)

    val key: String by lazy {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)
        fun num(value: Double) {
            buffer.clear()
            buffer.putDouble(value)
            digest.update(buffer.array(), 0, 8)
        }
        fun text(value: String) = digest.update(value.toByteArray())
        fun shape(s: Shape) {
            text(s.type)
            for ((_, value) in s.fields()) {
                when (value) {
                    is Double -> num(value)
                    is DoubleArray -> value.forEach { num(it) }
                    null -> num(Double.NaN)
                }
            }
        }
        num(ground ?: Double.NaN); num(groundK)
        for (op in ops) {
            num(op.op.toDouble()); shape(op.form); op.color.forEach { num(it) }
            num(op.k); num(op.bone.toDouble()); num(op.colorK)
            op.split?.let { num(it.bone.toDouble()); num(it.from); num(it.to) }
        }
        text("|paints|")
        for (p in paints) {
            shape(p.form); p.color.forEach { num(it) }; num(p.soft); num(p.amount)
        }
        num(options.cell); num(options.bones.toDouble()); num(options.ao); num(options.aoReach)
        digest.digest().joinToString("") { "%02x".format(it) }.take(32)
    }
}

// Listas que crecen sin cajas: el escultor mete cientos de miles de números.
internal class DoubleList {
    private var data = DoubleArray(4096)
    var size = 0
        private set

    fun add(value: Double) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = value
    }

    operator fun get(i: Int): Double = data[i]
}

internal class IntList {
    private var data = IntArray(8192)
    var size = 0
        private set

    fun add(value: Int) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = value
    }

    fun toArray(): IntArray = data.copyOf(size)
}
