// Matemática del motor 3D: vectores, cuaterniones, ángulos de Euler,
// matrices y color. Sigue los convenios de Three.js (matrices por
// columnas, mano derecha) para que la escena se porte línea a línea.

package com.nidaria.app.gl

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

const val PI_F = PI.toFloat()

fun clampF(value: Float, lo: Float, hi: Float): Float = min(hi, max(lo, value))

class Vec2(var x: Float = 0f, var y: Float = 0f) {
    fun set(x: Float, y: Float): Vec2 {
        this.x = x
        this.y = y
        return this
    }

    fun lerp(v: Vec2, t: Float): Vec2 {
        x += (v.x - x) * t
        y += (v.y - y) * t
        return this
    }

    fun distanceTo(v: Vec2): Float = sqrt((x - v.x) * (x - v.x) + (y - v.y) * (y - v.y))
}

class Vec3(var x: Float = 0f, var y: Float = 0f, var z: Float = 0f) {
    fun set(x: Float, y: Float, z: Float): Vec3 {
        this.x = x
        this.y = y
        this.z = z
        return this
    }

    fun setScalar(s: Float): Vec3 = set(s, s, s)
    fun copy(v: Vec3): Vec3 = set(v.x, v.y, v.z)
    fun clone(): Vec3 = Vec3(x, y, z)

    fun add(v: Vec3): Vec3 = set(x + v.x, y + v.y, z + v.z)
    fun sub(v: Vec3): Vec3 = set(x - v.x, y - v.y, z - v.z)
    fun addScaledVector(v: Vec3, s: Float): Vec3 = set(x + v.x * s, y + v.y * s, z + v.z * s)
    fun multiplyScalar(s: Float): Vec3 = set(x * s, y * s, z * s)

    fun dot(v: Vec3): Float = x * v.x + y * v.y + z * v.z
    fun length(): Float = sqrt(x * x + y * y + z * z)
    fun lengthSq(): Float = x * x + y * y + z * z
    fun distanceTo(v: Vec3): Float = sqrt((x - v.x) * (x - v.x) + (y - v.y) * (y - v.y) + (z - v.z) * (z - v.z))

    fun normalize(): Vec3 {
        val l = length()
        return if (l > 0f) multiplyScalar(1f / l) else this
    }

    fun cross(v: Vec3): Vec3 = set(y * v.z - z * v.y, z * v.x - x * v.z, x * v.y - y * v.x)

    fun lerp(v: Vec3, t: Float): Vec3 = set(x + (v.x - x) * t, y + (v.y - y) * t, z + (v.z - z) * t)

    fun applyQuat(q: Quat): Vec3 {
        // v + 2w(q × v) + 2 q × (q × v)
        val tx = 2f * (q.y * z - q.z * y)
        val ty = 2f * (q.z * x - q.x * z)
        val tz = 2f * (q.x * y - q.y * x)
        return set(
            x + q.w * tx + q.y * tz - q.z * ty,
            y + q.w * ty + q.z * tx - q.x * tz,
            z + q.w * tz + q.x * ty - q.y * tx,
        )
    }

    // Punto por una matriz 4x4, con división de perspectiva.
    fun applyMat4(m: Mat4): Vec3 {
        val e = m.e
        val w = 1f / (e[3] * x + e[7] * y + e[11] * z + e[15])
        return set(
            (e[0] * x + e[4] * y + e[8] * z + e[12]) * w,
            (e[1] * x + e[5] * y + e[9] * z + e[13]) * w,
            (e[2] * x + e[6] * y + e[10] * z + e[14]) * w,
        )
    }

    // Dirección por la parte 3x3 de una matriz (sin traslación).
    fun transformDirection(m: Mat4): Vec3 {
        val e = m.e
        return set(
            e[0] * x + e[4] * y + e[8] * z,
            e[1] * x + e[5] * y + e[9] * z,
            e[2] * x + e[6] * y + e[10] * z,
        ).normalize()
    }

    override fun toString(): String = "($x, $y, $z)"

    companion object {
        val X get() = Vec3(1f, 0f, 0f)
        val Y get() = Vec3(0f, 1f, 0f)
        val Z get() = Vec3(0f, 0f, 1f)
    }
}

class Quat(x: Float = 0f, y: Float = 0f, z: Float = 0f, w: Float = 1f) {
    var x = x
        private set
    var y = y
        private set
    var z = z
        private set
    var w = w
        private set

    internal var onChange: (() -> Unit)? = null

    fun set(x: Float, y: Float, z: Float, w: Float, notify: Boolean = true): Quat {
        this.x = x
        this.y = y
        this.z = z
        this.w = w
        if (notify) onChange?.invoke()
        return this
    }

    fun copy(q: Quat): Quat = set(q.x, q.y, q.z, q.w)

    fun setFromEuler(e: Euler, notify: Boolean = true): Quat {
        val c1 = cos(e.x / 2f)
        val c2 = cos(e.y / 2f)
        val c3 = cos(e.z / 2f)
        val s1 = sin(e.x / 2f)
        val s2 = sin(e.y / 2f)
        val s3 = sin(e.z / 2f)
        return if (e.order == Euler.Order.YXZ) {
            set(
                s1 * c2 * c3 + c1 * s2 * s3,
                c1 * s2 * c3 - s1 * c2 * s3,
                c1 * c2 * s3 - s1 * s2 * c3,
                c1 * c2 * c3 + s1 * s2 * s3,
                notify,
            )
        } else {
            set(
                s1 * c2 * c3 + c1 * s2 * s3,
                c1 * s2 * c3 - s1 * c2 * s3,
                c1 * c2 * s3 + s1 * s2 * c3,
                c1 * c2 * c3 - s1 * s2 * s3,
                notify,
            )
        }
    }

    fun setFromAxisAngle(axis: Vec3, angle: Float): Quat {
        val s = sin(angle / 2f)
        return set(axis.x * s, axis.y * s, axis.z * s, cos(angle / 2f))
    }

    // Giro que lleva el vector unitario `from` sobre `to`.
    fun setFromUnitVectors(from: Vec3, to: Vec3): Quat {
        var r = from.dot(to) + 1f
        if (r < 1e-6f) {
            r = 0f
            if (abs(from.x) > abs(from.z)) set(-from.y, from.x, 0f, r, false) else set(0f, -from.z, from.y, r, false)
        } else {
            set(
                from.y * to.z - from.z * to.y,
                from.z * to.x - from.x * to.z,
                from.x * to.y - from.y * to.x,
                r,
                false,
            )
        }
        return normalize()
    }

    fun setFromRotationMatrix(m: Mat4): Quat {
        val e = m.e
        val m11 = e[0]; val m12 = e[4]; val m13 = e[8]
        val m21 = e[1]; val m22 = e[5]; val m23 = e[9]
        val m31 = e[2]; val m32 = e[6]; val m33 = e[10]
        val trace = m11 + m22 + m33
        return if (trace > 0f) {
            val s = 0.5f / sqrt(trace + 1f)
            set((m32 - m23) * s, (m13 - m31) * s, (m21 - m12) * s, 0.25f / s)
        } else if (m11 > m22 && m11 > m33) {
            val s = 2f * sqrt(1f + m11 - m22 - m33)
            set(0.25f * s, (m12 + m21) / s, (m13 + m31) / s, (m32 - m23) / s)
        } else if (m22 > m33) {
            val s = 2f * sqrt(1f + m22 - m11 - m33)
            set((m12 + m21) / s, 0.25f * s, (m23 + m32) / s, (m13 - m31) / s)
        } else {
            val s = 2f * sqrt(1f + m33 - m11 - m22)
            set((m13 + m31) / s, (m23 + m32) / s, 0.25f * s, (m21 - m12) / s)
        }
    }

    fun normalize(): Quat {
        val l = sqrt(x * x + y * y + z * z + w * w)
        return if (l == 0f) set(0f, 0f, 0f, 1f) else set(x / l, y / l, z / l, w / l)
    }

    // this = this × q
    fun multiply(q: Quat): Quat = set(
        x * q.w + w * q.x + y * q.z - z * q.y,
        y * q.w + w * q.y + z * q.x - x * q.z,
        z * q.w + w * q.z + x * q.y - y * q.x,
        w * q.w - x * q.x - y * q.y - z * q.z,
    )
}

class Euler {
    enum class Order { XYZ, YXZ }

    internal var onChange: (() -> Unit)? = null

    var x = 0f
        set(value) {
            field = value
            onChange?.invoke()
        }
    var y = 0f
        set(value) {
            field = value
            onChange?.invoke()
        }
    var z = 0f
        set(value) {
            field = value
            onChange?.invoke()
        }
    var order = Order.XYZ
        set(value) {
            field = value
            onChange?.invoke()
        }

    fun set(x: Float, y: Float, z: Float): Euler {
        val hold = onChange
        onChange = null
        this.x = x
        this.y = y
        this.z = z
        onChange = hold
        hold?.invoke()
        return this
    }

    fun setFromQuaternion(q: Quat, notify: Boolean = true): Euler {
        val x2 = q.x + q.x
        val y2 = q.y + q.y
        val z2 = q.z + q.z
        val xx = q.x * x2; val xy = q.x * y2; val xz = q.x * z2
        val yy = q.y * y2; val yz = q.y * z2; val zz = q.z * z2
        val wx = q.w * x2; val wy = q.w * y2; val wz = q.w * z2
        val m11 = 1f - (yy + zz); val m12 = xy - wz; val m13 = xz + wy
        val m21 = xy + wz; val m22 = 1f - (xx + zz); val m23 = yz - wx
        val m31 = xz - wy; val m32 = yz + wx; val m33 = 1f - (xx + yy)
        val hold = onChange
        onChange = null
        if (order == Order.YXZ) {
            x = asin(-clampF(m23, -1f, 1f))
            if (abs(m23) < 0.9999999f) {
                y = atan2(m13, m33)
                z = atan2(m21, m22)
            } else {
                y = atan2(-m31, m11)
                z = 0f
            }
        } else {
            y = asin(clampF(m13, -1f, 1f))
            if (abs(m13) < 0.9999999f) {
                x = atan2(-m23, m33)
                z = atan2(-m12, m11)
            } else {
                x = atan2(m32, m22)
                z = 0f
            }
        }
        onChange = hold
        if (notify) hold?.invoke()
        return this
    }
}

class Mat4 {
    val e = FloatArray(16).also { it[0] = 1f; it[5] = 1f; it[10] = 1f; it[15] = 1f }

    fun identity(): Mat4 {
        e.fill(0f)
        e[0] = 1f; e[5] = 1f; e[10] = 1f; e[15] = 1f
        return this
    }

    fun copy(m: Mat4): Mat4 {
        System.arraycopy(m.e, 0, e, 0, 16)
        return this
    }

    fun compose(p: Vec3, q: Quat, s: Vec3): Mat4 {
        val x2 = q.x + q.x
        val y2 = q.y + q.y
        val z2 = q.z + q.z
        val xx = q.x * x2; val xy = q.x * y2; val xz = q.x * z2
        val yy = q.y * y2; val yz = q.y * z2; val zz = q.z * z2
        val wx = q.w * x2; val wy = q.w * y2; val wz = q.w * z2
        e[0] = (1f - (yy + zz)) * s.x; e[1] = (xy + wz) * s.x; e[2] = (xz - wy) * s.x; e[3] = 0f
        e[4] = (xy - wz) * s.y; e[5] = (1f - (xx + zz)) * s.y; e[6] = (yz + wx) * s.y; e[7] = 0f
        e[8] = (xz + wy) * s.z; e[9] = (yz - wx) * s.z; e[10] = (1f - (xx + yy)) * s.z; e[11] = 0f
        e[12] = p.x; e[13] = p.y; e[14] = p.z; e[15] = 1f
        return this
    }

    // this = a × b
    fun multiplyMatrices(a: Mat4, b: Mat4): Mat4 {
        val ae = a.e
        val be = b.e
        val a11 = ae[0]; val a12 = ae[4]; val a13 = ae[8]; val a14 = ae[12]
        val a21 = ae[1]; val a22 = ae[5]; val a23 = ae[9]; val a24 = ae[13]
        val a31 = ae[2]; val a32 = ae[6]; val a33 = ae[10]; val a34 = ae[14]
        val a41 = ae[3]; val a42 = ae[7]; val a43 = ae[11]; val a44 = ae[15]
        val b11 = be[0]; val b12 = be[4]; val b13 = be[8]; val b14 = be[12]
        val b21 = be[1]; val b22 = be[5]; val b23 = be[9]; val b24 = be[13]
        val b31 = be[2]; val b32 = be[6]; val b33 = be[10]; val b34 = be[14]
        val b41 = be[3]; val b42 = be[7]; val b43 = be[11]; val b44 = be[15]
        e[0] = a11 * b11 + a12 * b21 + a13 * b31 + a14 * b41
        e[4] = a11 * b12 + a12 * b22 + a13 * b32 + a14 * b42
        e[8] = a11 * b13 + a12 * b23 + a13 * b33 + a14 * b43
        e[12] = a11 * b14 + a12 * b24 + a13 * b34 + a14 * b44
        e[1] = a21 * b11 + a22 * b21 + a23 * b31 + a24 * b41
        e[5] = a21 * b12 + a22 * b22 + a23 * b32 + a24 * b42
        e[9] = a21 * b13 + a22 * b23 + a23 * b33 + a24 * b43
        e[13] = a21 * b14 + a22 * b24 + a23 * b34 + a24 * b44
        e[2] = a31 * b11 + a32 * b21 + a33 * b31 + a34 * b41
        e[6] = a31 * b12 + a32 * b22 + a33 * b32 + a34 * b42
        e[10] = a31 * b13 + a32 * b23 + a33 * b33 + a34 * b43
        e[14] = a31 * b14 + a32 * b24 + a33 * b34 + a34 * b44
        e[3] = a41 * b11 + a42 * b21 + a43 * b31 + a44 * b41
        e[7] = a41 * b12 + a42 * b22 + a43 * b32 + a44 * b42
        e[11] = a41 * b13 + a42 * b23 + a43 * b33 + a44 * b43
        e[15] = a41 * b14 + a42 * b24 + a43 * b34 + a44 * b44
        return this
    }

    fun invert(): Mat4 {
        val n11 = e[0]; val n21 = e[1]; val n31 = e[2]; val n41 = e[3]
        val n12 = e[4]; val n22 = e[5]; val n32 = e[6]; val n42 = e[7]
        val n13 = e[8]; val n23 = e[9]; val n33 = e[10]; val n43 = e[11]
        val n14 = e[12]; val n24 = e[13]; val n34 = e[14]; val n44 = e[15]
        val t11 = n23 * n34 * n42 - n24 * n33 * n42 + n24 * n32 * n43 - n22 * n34 * n43 - n23 * n32 * n44 + n22 * n33 * n44
        val t12 = n14 * n33 * n42 - n13 * n34 * n42 - n14 * n32 * n43 + n12 * n34 * n43 + n13 * n32 * n44 - n12 * n33 * n44
        val t13 = n13 * n24 * n42 - n14 * n23 * n42 + n14 * n22 * n43 - n12 * n24 * n43 - n13 * n22 * n44 + n12 * n23 * n44
        val t14 = n14 * n23 * n32 - n13 * n24 * n32 - n14 * n22 * n33 + n12 * n24 * n33 + n13 * n22 * n34 - n12 * n23 * n34
        val det = n11 * t11 + n21 * t12 + n31 * t13 + n41 * t14
        if (det == 0f) {
            e.fill(0f)
            return this
        }
        val d = 1f / det
        e[0] = t11 * d
        e[1] = (n24 * n33 * n41 - n23 * n34 * n41 - n24 * n31 * n43 + n21 * n34 * n43 + n23 * n31 * n44 - n21 * n33 * n44) * d
        e[2] = (n22 * n34 * n41 - n24 * n32 * n41 + n24 * n31 * n42 - n21 * n34 * n42 - n22 * n31 * n44 + n21 * n32 * n44) * d
        e[3] = (n23 * n32 * n41 - n22 * n33 * n41 - n23 * n31 * n42 + n21 * n33 * n42 + n22 * n31 * n43 - n21 * n32 * n43) * d
        e[4] = t12 * d
        e[5] = (n13 * n34 * n41 - n14 * n33 * n41 + n14 * n31 * n43 - n11 * n34 * n43 - n13 * n31 * n44 + n11 * n33 * n44) * d
        e[6] = (n14 * n32 * n41 - n12 * n34 * n41 - n14 * n31 * n42 + n11 * n34 * n42 + n12 * n31 * n44 - n11 * n32 * n44) * d
        e[7] = (n12 * n33 * n41 - n13 * n32 * n41 + n13 * n31 * n42 - n11 * n33 * n42 - n12 * n31 * n43 + n11 * n32 * n43) * d
        e[8] = t13 * d
        e[9] = (n14 * n23 * n41 - n13 * n24 * n41 - n14 * n21 * n43 + n11 * n24 * n43 + n13 * n21 * n44 - n11 * n23 * n44) * d
        e[10] = (n12 * n24 * n41 - n14 * n22 * n41 + n14 * n21 * n42 - n11 * n24 * n42 - n12 * n21 * n44 + n11 * n22 * n44) * d
        e[11] = (n13 * n22 * n41 - n12 * n23 * n41 - n13 * n21 * n42 + n11 * n23 * n42 + n12 * n21 * n43 - n11 * n22 * n43) * d
        e[12] = t14 * d
        e[13] = (n13 * n24 * n31 - n14 * n23 * n31 + n14 * n21 * n33 - n11 * n24 * n33 - n13 * n21 * n34 + n11 * n23 * n34) * d
        e[14] = (n14 * n22 * n31 - n12 * n24 * n31 - n14 * n21 * n32 + n11 * n24 * n32 + n12 * n21 * n34 - n11 * n22 * n34) * d
        e[15] = (n12 * n23 * n31 - n13 * n22 * n31 + n13 * n21 * n32 - n11 * n23 * n32 - n12 * n21 * n33 + n11 * n22 * n33) * d
        return this
    }

    // Matriz de giro cuyo eje +Z apunta de `target` hacia `eye`.
    fun lookAt(eye: Vec3, target: Vec3, up: Vec3): Mat4 {
        val z = eye.clone().sub(target)
        if (z.lengthSq() == 0f) z.z = 1f
        z.normalize()
        var x = up.clone().cross(z)
        if (x.lengthSq() == 0f) {
            if (abs(up.z) == 1f) z.x += 0.0001f else z.z += 0.0001f
            z.normalize()
            x = up.clone().cross(z)
        }
        x.normalize()
        val y = z.clone().cross(x)
        e[0] = x.x; e[4] = y.x; e[8] = z.x
        e[1] = x.y; e[5] = y.y; e[9] = z.y
        e[2] = x.z; e[6] = y.z; e[10] = z.z
        return this
    }

    fun perspectiveFrustum(left: Float, right: Float, top: Float, bottom: Float, near: Float, far: Float): Mat4 {
        e.fill(0f)
        e[0] = 2f * near / (right - left)
        e[5] = 2f * near / (top - bottom)
        e[8] = (right + left) / (right - left)
        e[9] = (top + bottom) / (top - bottom)
        e[10] = -(far + near) / (far - near)
        e[11] = -1f
        e[14] = -2f * far * near / (far - near)
        return this
    }

    fun orthographic(left: Float, right: Float, top: Float, bottom: Float, near: Float, far: Float): Mat4 {
        e.fill(0f)
        val w = 1f / (right - left)
        val h = 1f / (top - bottom)
        val p = 1f / (far - near)
        e[0] = 2f * w
        e[5] = 2f * h
        e[10] = -2f * p
        e[12] = -(right + left) * w
        e[13] = -(top + bottom) * h
        e[14] = -(far + near) * p
        e[15] = 1f
        return this
    }

    // Matriz normal (inversa traspuesta de la parte 3x3), en 9 flotantes.
    fun normalMatrix(out: FloatArray) {
        val a = e[0]; val b = e[1]; val c = e[2]
        val d = e[4]; val f = e[5]; val g = e[6]
        val h = e[8]; val i = e[9]; val j = e[10]
        val t11 = j * f - g * i
        val t12 = g * h - j * d
        val t13 = i * d - f * h
        val det = a * t11 + b * t12 + c * t13
        if (det == 0f) {
            out.fill(0f)
            return
        }
        val inv = 1f / det
        // Inversa, guardada ya traspuesta.
        out[0] = t11 * inv; out[3] = (c * i - j * b) * inv; out[6] = (g * b - c * f) * inv
        out[1] = t12 * inv; out[4] = (j * a - c * h) * inv; out[7] = (c * d - g * a) * inv
        out[2] = t13 * inv; out[5] = (b * h - i * a) * inv; out[8] = (f * a - b * d) * inv
    }
}

// Color en espacio lineal, como los guarda Three.js.
class Color(var r: Float = 1f, var g: Float = 1f, var b: Float = 1f) {
    fun set(r: Float, g: Float, b: Float): Color {
        this.r = r
        this.g = g
        this.b = b
        return this
    }

    fun copy(c: Color): Color = set(c.r, c.g, c.b)
    fun clone(): Color = Color(r, g, b)

    // Color de CSS (sRGB) pasado a lineal.
    fun setHex(hex: String): Color {
        val v = hex.removePrefix("#").toInt(16)
        return set(srgbToLinear(((v shr 16) and 255) / 255f), srgbToLinear(((v shr 8) and 255) / 255f), srgbToLinear((v and 255) / 255f))
    }

    // Igual que Color.setHSL de Three.js: el resultado se toma como
    // lineal, sin convertir. Por eso las criaturas se ven más claras que
    // el mismo hsl() en CSS.
    fun setHSL(h: Float, s: Float, l: Float): Color {
        val hh = ((h % 1f) + 1f) % 1f
        val ss = clampF(s, 0f, 1f)
        val ll = clampF(l, 0f, 1f)
        if (ss == 0f) return set(ll, ll, ll)
        val p = if (ll <= 0.5f) ll * (1f + ss) else ll + ss - ll * ss
        val q = 2f * ll - p
        return set(hue2rgb(q, p, hh + 1f / 3f), hue2rgb(q, p, hh), hue2rgb(q, p, hh - 1f / 3f))
    }

    fun lerp(c: Color, t: Float): Color = set(r + (c.r - r) * t, g + (c.g - g) * t, b + (c.b - b) * t)

    fun lerpColors(a: Color, b: Color, t: Float): Color = set(a.r + (b.r - a.r) * t, a.g + (b.g - a.g) * t, a.b + (b.b - a.b) * t)

    companion object {
        fun hex(hex: String): Color = Color().setHex(hex)

        fun srgbToLinear(c: Float): Float = if (c < 0.04045f) c * 0.0773993808f else ((c * 0.9478672986f + 0.0521327014f).toDouble().pow(2.4)).toFloat()

        fun linearToSrgb(c: Float): Float = if (c < 0.0031308f) c * 12.92f else 1.055f * c.toDouble().pow(0.41666).toFloat() - 0.055f

        private fun hue2rgb(p: Float, q: Float, tIn: Float): Float {
            var t = tIn
            if (t < 0f) t += 1f
            if (t > 1f) t -= 1f
            if (t < 1f / 6f) return p + (q - p) * 6f * t
            if (t < 0.5f) return q
            if (t < 2f / 3f) return p + (q - p) * 6f * (2f / 3f - t)
            return p
        }
    }
}

fun degToRad(deg: Float): Float = deg * PI_F / 180f

fun tanDeg(deg: Float): Float = tan(degToRad(deg))
