// Las cuentas de Three.js que usan los modelos de la web (vectores,
// cuaterniones y colores), copiadas tal cual y en Double. Así lo que se
// esculpe aquí sale con los mismos números que en el navegador: la
// prueba de paridad compara los encargos con los de la web.
//
// Los senos, cosenos y potencias van por StrictMath, que es la misma
// biblioteca (fdlibm) que usa el motor de JavaScript.

package com.nidaria.app.sculpt

import kotlin.math.abs
import kotlin.math.sqrt

internal fun sinD(x: Double): Double = StrictMath.sin(x)
internal fun cosD(x: Double): Double = StrictMath.cos(x)
internal fun powD(x: Double, y: Double): Double = StrictMath.pow(x, y)
internal fun clampD(v: Double, lo: Double, hi: Double): Double = Math.max(lo, Math.min(hi, v))

class V3(@JvmField var x: Double = 0.0, @JvmField var y: Double = 0.0, @JvmField var z: Double = 0.0) {
    fun set(x: Double, y: Double, z: Double): V3 {
        this.x = x
        this.y = y
        this.z = z
        return this
    }

    fun copy(v: V3): V3 = set(v.x, v.y, v.z)
    fun clone(): V3 = V3(x, y, z)
    fun add(v: V3): V3 = set(x + v.x, y + v.y, z + v.z)
    fun sub(v: V3): V3 = set(x - v.x, y - v.y, z - v.z)
    fun addScaledVector(v: V3, s: Double): V3 = set(x + v.x * s, y + v.y * s, z + v.z * s)
    fun multiplyScalar(s: Double): V3 = set(x * s, y * s, z * s)
    fun divideScalar(s: Double): V3 = multiplyScalar(1 / s)
    fun dot(v: V3): Double = x * v.x + y * v.y + z * v.z
    fun length(): Double = sqrt(x * x + y * y + z * z)

    fun normalize(): V3 {
        val l = length()
        return divideScalar(if (l == 0.0 || l.isNaN()) 1.0 else l)
    }

    fun lerp(v: V3, alpha: Double): V3 {
        x += (v.x - x) * alpha
        y += (v.y - y) * alpha
        z += (v.z - z) * alpha
        return this
    }

    fun crossVectors(a: V3, b: V3): V3 {
        val ax = a.x
        val ay = a.y
        val az = a.z
        val bx = b.x
        val by = b.y
        val bz = b.z
        return set(ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx)
    }

    fun applyQuaternion(q: Q): V3 {
        val vx = x
        val vy = y
        val vz = z
        val tx = 2 * (q.y * vz - q.z * vy)
        val ty = 2 * (q.z * vx - q.x * vz)
        val tz = 2 * (q.x * vy - q.y * vx)
        x = vx + q.w * tx + q.y * tz - q.z * ty
        y = vy + q.w * ty + q.z * tx - q.x * tz
        z = vz + q.w * tz + q.x * ty - q.y * tx
        return this
    }

    fun applyAxisAngle(axis: V3, angle: Double): V3 = applyQuaternion(Q().setFromAxisAngle(axis, angle))

    fun toArray(): DoubleArray = doubleArrayOf(x, y, z)

    fun setY(value: Double): V3 {
        y = value
        return this
    }

    fun negate(): V3 = set(-x, -y, -z)

    fun distanceToSquared(v: V3): Double {
        val dx = x - v.x
        val dy = y - v.y
        val dz = z - v.z
        return dx * dx + dy * dy + dz * dz
    }

    override fun toString(): String = "($x, $y, $z)"
}

class Q(@JvmField var x: Double = 0.0, @JvmField var y: Double = 0.0, @JvmField var z: Double = 0.0, @JvmField var w: Double = 1.0) {
    fun clone(): Q = Q(x, y, z, w)

    fun setFromAxisAngle(axis: V3, angle: Double): Q {
        val halfAngle = angle / 2
        val s = sinD(halfAngle)
        x = axis.x * s
        y = axis.y * s
        z = axis.z * s
        w = cosD(halfAngle)
        return this
    }

    fun setFromUnitVectors(from: V3, to: V3): Q {
        var r = from.dot(to) + 1
        if (r < 1e-8) {
            r = 0.0
            if (abs(from.x) > abs(from.z)) {
                x = -from.y; y = from.x; z = 0.0; w = r
            } else {
                x = 0.0; y = -from.z; z = from.y; w = r
            }
        } else {
            x = from.y * to.z - from.z * to.y
            y = from.z * to.x - from.x * to.z
            z = from.x * to.y - from.y * to.x
            w = r
        }
        return normalize()
    }

    fun length(): Double = sqrt(x * x + y * y + z * z + w * w)

    fun normalize(): Q {
        var l = length()
        if (l == 0.0) {
            x = 0.0; y = 0.0; z = 0.0; w = 1.0
        } else {
            l = 1 / l
            x *= l; y *= l; z *= l; w *= l
        }
        return this
    }

    // this = this × q
    fun multiply(q: Q): Q = multiplyQuaternions(this, q)

    fun multiplyQuaternions(a: Q, b: Q): Q {
        val qax = a.x
        val qay = a.y
        val qaz = a.z
        val qaw = a.w
        val qbx = b.x
        val qby = b.y
        val qbz = b.z
        val qbw = b.w
        x = qax * qbw + qaw * qbx + qay * qbz - qaz * qby
        y = qay * qbw + qaw * qby + qaz * qbx - qax * qbz
        z = qaz * qbw + qaw * qbz + qax * qby - qay * qbx
        w = qaw * qbw - qax * qbx - qay * qby - qaz * qbz
        return this
    }

    fun setFromEuler(ex: Double, ey: Double, ez: Double, order: String = "XYZ"): Q {
        val q = quaternionFromEuler(ex, ey, ez, order)
        x = q[0]; y = q[1]; z = q[2]; w = q[3]
        return this
    }

    // Base ortonormal (como Matrix4.makeBasis + setFromRotationMatrix).
    fun setFromBasis(xAxis: V3, yAxis: V3, zAxis: V3): Q {
        val m11 = xAxis.x
        val m12 = yAxis.x
        val m13 = zAxis.x
        val m21 = xAxis.y
        val m22 = yAxis.y
        val m23 = zAxis.y
        val m31 = xAxis.z
        val m32 = yAxis.z
        val m33 = zAxis.z
        val trace = m11 + m22 + m33
        if (trace > 0) {
            val s = 0.5 / sqrt(trace + 1.0)
            w = 0.25 / s
            x = (m32 - m23) * s
            y = (m13 - m31) * s
            z = (m21 - m12) * s
        } else if (m11 > m22 && m11 > m33) {
            val s = 2.0 * sqrt(1.0 + m11 - m22 - m33)
            w = (m32 - m23) / s
            x = 0.25 * s
            y = (m12 + m21) / s
            z = (m13 + m31) / s
        } else if (m22 > m33) {
            val s = 2.0 * sqrt(1.0 + m22 - m11 - m33)
            w = (m13 - m31) / s
            x = (m12 + m21) / s
            y = 0.25 * s
            z = (m23 + m32) / s
        } else {
            val s = 2.0 * sqrt(1.0 + m33 - m11 - m22)
            w = (m21 - m12) / s
            x = (m13 + m31) / s
            y = (m23 + m32) / s
            z = 0.25 * s
        }
        return this
    }
}

// Quaternion.setFromEuler de Three.js.
internal fun quaternionFromEuler(x: Double, y: Double, z: Double, order: String): DoubleArray {
    val c1 = cosD(x / 2)
    val c2 = cosD(y / 2)
    val c3 = cosD(z / 2)
    val s1 = sinD(x / 2)
    val s2 = sinD(y / 2)
    val s3 = sinD(z / 2)
    return when (order) {
        "YXZ" -> doubleArrayOf(s1 * c2 * c3 + c1 * s2 * s3, c1 * s2 * c3 - s1 * c2 * s3, c1 * c2 * s3 - s1 * s2 * c3, c1 * c2 * c3 + s1 * s2 * s3)
        "ZXY" -> doubleArrayOf(s1 * c2 * c3 - c1 * s2 * s3, c1 * s2 * c3 + s1 * c2 * s3, c1 * c2 * s3 + s1 * s2 * c3, c1 * c2 * c3 - s1 * s2 * s3)
        "ZYX" -> doubleArrayOf(s1 * c2 * c3 - c1 * s2 * s3, c1 * s2 * c3 + s1 * c2 * s3, c1 * c2 * s3 - s1 * s2 * c3, c1 * c2 * c3 + s1 * s2 * s3)
        "YZX" -> doubleArrayOf(s1 * c2 * c3 + c1 * s2 * s3, c1 * s2 * c3 + s1 * c2 * s3, c1 * c2 * s3 - s1 * s2 * c3, c1 * c2 * c3 - s1 * s2 * s3)
        "XZY" -> doubleArrayOf(s1 * c2 * c3 - c1 * s2 * s3, c1 * s2 * c3 - s1 * c2 * s3, c1 * c2 * s3 + s1 * s2 * c3, c1 * c2 * c3 + s1 * s2 * s3)
        else -> doubleArrayOf(s1 * c2 * c3 + c1 * s2 * s3, c1 * s2 * c3 - s1 * c2 * s3, c1 * c2 * s3 + s1 * s2 * c3, c1 * c2 * c3 - s1 * s2 * s3)
    }
}

// Color de Three.js en el espacio de trabajo (sRGB lineal).
class Col(@JvmField var r: Double = 1.0, @JvmField var g: Double = 1.0, @JvmField var b: Double = 1.0) {
    fun clone(): Col = Col(r, g, b)

    fun lerp(c: Col, alpha: Double): Col {
        r += (c.r - r) * alpha
        g += (c.g - g) * alpha
        b += (c.b - b) * alpha
        return this
    }

    fun multiplyScalar(s: Double): Col {
        r *= s
        g *= s
        b *= s
        return this
    }

    fun toArray(): DoubleArray = doubleArrayOf(r, g, b)

    // Luz de Color.getHSL (en el espacio de trabajo, lineal).
    fun lightness(): Double = (minOf(r, g, b) + maxOf(r, g, b)) / 2.0

    // Color.getHexString: pasa a sRGB y redondea, sin '#'.
    fun hexString(): String {
        fun channel(c: Double): Int {
            val s = if (c < 0.0031308) c * 12.92 else 1.055 * powD(c, 0.41666) - 0.055
            return Math.round(clampD(s * 255, 0.0, 255.0)).toInt()
        }
        return "%02x%02x%02x".format(channel(r), channel(g), channel(b))
    }

    // Para los materiales del motor, que trabajan en Float.
    fun toGl(): com.nidaria.app.gl.Color = com.nidaria.app.gl.Color(r.toFloat(), g.toFloat(), b.toFloat())

    companion object {
        private fun srgbToLinear(c: Double): Double = if (c < 0.04045) c * 0.0773993808 else powD(c * 0.9478672986 + 0.0521327014, 2.4)

        // '#rrggbb' en sRGB, como new THREE.Color('#rrggbb').
        fun hex(hex: String): Col {
            val v = hex.removePrefix("#").toInt(16)
            return Col(srgbToLinear(((v shr 16) and 255) / 255.0), srgbToLinear(((v shr 8) and 255) / 255.0), srgbToLinear((v and 255) / 255.0))
        }

        // Color.setHSL sin conversión (el ADN da el tono ya en el espacio de trabajo).
        fun hsl(h: Double, s: Double, l: Double): Col {
            val hh = ((h % 1) + 1) % 1
            val ss = clampD(s, 0.0, 1.0)
            val ll = clampD(l, 0.0, 1.0)
            if (ss == 0.0) return Col(ll, ll, ll)
            val p = if (ll <= 0.5) ll * (1 + ss) else ll + ss - (ll * ss)
            val q = (2 * ll) - p
            return Col(hue2rgb(q, p, hh + 1.0 / 3), hue2rgb(q, p, hh), hue2rgb(q, p, hh - 1.0 / 3))
        }

        private fun hue2rgb(p: Double, q: Double, tIn: Double): Double {
            var t = tIn
            if (t < 0) t += 1
            if (t > 1) t -= 1
            if (t < 1.0 / 6) return p + (q - p) * 6 * t
            if (t < 1.0 / 2) return q
            if (t < 2.0 / 3) return p + (q - p) * 6 * (2.0 / 3 - t)
            return p
        }
    }
}

// Atajos para escribir los modelos como en la web.
internal fun v(x: Double = 0.0, y: Double = 0.0, z: Double = 0.0) = V3(x, y, z)
internal fun d(vararg values: Double): DoubleArray = values
internal val AXIS_X = V3(1.0, 0.0, 0.0)
internal val AXIS_Y = V3(0.0, 1.0, 0.0)
internal val AXIS_Z = V3(0.0, 0.0, 1.0)

// CatmullRomCurve3 de Three.js (centrípeta, abierta) y su getPoints(n):
// n + 1 puntos repartidos por el parámetro, no por la longitud.
internal fun catmullRom(points: List<V3>, divisions: Int): List<V3> {
    val l = points.size
    fun poly(x0: Double, x1: Double, x2: Double, x3: Double, dt0: Double, dt1: Double, dt2: Double): DoubleArray {
        var t1 = (x1 - x0) / dt0 - (x2 - x0) / (dt0 + dt1) + (x2 - x1) / dt1
        var t2 = (x2 - x1) / dt1 - (x3 - x1) / (dt1 + dt2) + (x3 - x2) / dt2
        t1 *= dt1
        t2 *= dt1
        return doubleArrayOf(x1, t1, -3 * x1 + 3 * x2 - 2 * t1 - t2, 2 * x1 - 2 * x2 + t1 + t2)
    }
    fun calc(c: DoubleArray, t: Double): Double {
        val t2 = t * t
        val t3 = t2 * t
        return c[0] + c[1] * t + c[2] * t2 + c[3] * t3
    }
    val out = ArrayList<V3>(divisions + 1)
    for (dIndex in 0..divisions) {
        val t = dIndex.toDouble() / divisions
        val p = (l - 1) * t
        var intPoint = kotlin.math.floor(p).toInt()
        var weight = p - intPoint
        if (weight == 0.0 && intPoint == l - 1) {
            intPoint = l - 2
            weight = 1.0
        }
        val p0 = if (intPoint > 0) points[intPoint - 1] else V3().copy(points[0]).sub(points[1]).add(points[0])
        val p1 = points[intPoint]
        val p2 = points[intPoint + 1]
        val p3 = if (intPoint + 2 < l) points[intPoint + 2] else V3().copy(points[l - 1]).sub(points[l - 2]).add(points[l - 1])
        var dt0 = powD(p0.distanceToSquared(p1), 0.25)
        var dt1 = powD(p1.distanceToSquared(p2), 0.25)
        var dt2 = powD(p2.distanceToSquared(p3), 0.25)
        if (dt1 < 1e-4) dt1 = 1.0
        if (dt0 < 1e-4) dt0 = dt1
        if (dt2 < 1e-4) dt2 = dt1
        val px = poly(p0.x, p1.x, p2.x, p3.x, dt0, dt1, dt2)
        val py = poly(p0.y, p1.y, p2.y, p3.y, dt0, dt1, dt2)
        val pz = poly(p0.z, p1.z, p2.z, p3.z, dt0, dt1, dt2)
        out.add(V3(calc(px, weight), calc(py, weight), calc(pz, weight)))
    }
    return out
}
