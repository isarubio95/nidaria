// Modelos 3D de los artículos de la tienda y del huevo, como props.js en
// la web. Casi todos se esculpen como las criaturas: formas blandas
// fundidas, con el color pintado y las sombras de pliegue horneadas. Los
// mismos modelos sirven para la escena y para las miniaturas.

package com.nidaria.app.scene

import android.graphics.DashPathEffect
import android.graphics.Paint
import com.nidaria.app.gl.Color
import com.nidaria.app.gl.Geometry
import com.nidaria.app.gl.Group
import com.nidaria.app.gl.Material
import com.nidaria.app.gl.Mesh
import com.nidaria.app.gl.Object3D
import com.nidaria.app.gl.PI_F
import com.nidaria.app.gl.Shape
import com.nidaria.app.gl.Vec3
import com.nidaria.app.gl.extrudeGeometry
import com.nidaria.app.gl.group
import com.nidaria.app.gl.planeGeometry
import com.nidaria.app.gl.sphereGeometry
import com.nidaria.app.gl.torusGeometry
import com.nidaria.app.rules.EggLook
import com.nidaria.app.rules.Rng
import com.nidaria.app.sculpt.AXIS_Y
import com.nidaria.app.sculpt.AXIS_Z
import com.nidaria.app.sculpt.Col
import com.nidaria.app.sculpt.Q
import com.nidaria.app.sculpt.Sculpt
import com.nidaria.app.sculpt.Sh
import com.nidaria.app.sculpt.V3
import com.nidaria.app.sculpt.cosD
import com.nidaria.app.sculpt.powD
import com.nidaria.app.sculpt.rot
import com.nidaria.app.sculpt.sinD
import com.nidaria.app.sculpt.v
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private object C {
    const val red = "#f2605c"; const val apple = "#ee5252"; const val orange = "#ff9f43"; const val carrot = "#ff8c3a"
    const val yellow = "#ffd166"; const val green = "#6cc47f"; const val leaf = "#4fae7c"
    const val blue = "#7fb2f0"; const val navy = "#4d6fb8"; const val pink = "#ff8fb1"; const val cream = "#fff3dc"; const val white = "#fffdf8"
    const val tan = "#e2b279"; const val crust = "#dc9a55"; const val brown = "#a8744c"; const val cocoa = "#6b4632"; const val grey = "#b8c4cc"
    const val gold = "#f2c14e"; const val mint = "#7ed6a5"; const val violet = "#a98ee0"; const val terracotta = "#e8875f"
}

private fun xyz(x: Double, y: Double, z: Double) = doubleArrayOf(x, y, z)
private fun quatTo(dir: V3): Q = Q().setFromUnitVectors(AXIS_Y, dir.clone().normalize())

// ── Utilidades ────────────────────────────────────────────────

// Mallas esculpidas guardadas por nombre: los artículos no cambian. La
// ropa depende de cada criatura: no se guardan sin límite.
private val geometries = object : LinkedHashMap<String, Geometry>(64, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Geometry>): Boolean {
        if (size <= 160) return false
        eldest.value.dispose()
        return true
    }
}

// Para las pruebas: olvidar las mallas guardadas.
internal fun forgetModels() = synchronized(geometries) { geometries.clear() }

private fun sculpted(
    key: String,
    finish: Any,
    cell: Double = 0.02,
    ao: Double = 1.0,
    aoReach: Double = 0.12,
    ground: Double? = 0.0,
    groundK: Double = 0.02,
    make: (Sculpt) -> Unit,
): Mesh {
    val geometry = synchronized(geometries) {
        geometries.getOrPut(key) {
            val s = Sculpt(ground, groundK)
            make(s)
            s.build(cell = cell, ao = ao, aoReach = aoReach).also { it.shared = true }
        }
    }
    val mesh = Mesh(geometry, if (finish is String) clay(finish) else finish as Material)
    mesh.castShadow = true
    mesh.receiveShadow = true
    return mesh
}

private val BALL28 = sphereGeometry(1f, 28, 20).keep()

// Cuenco: un cilindro redondeado hueco con pie.
private fun bowl(s: Sculpt, color: String, r: Double = 0.5, h: Double = 0.4, band: String? = null) {
    s.add(Sh.cylinder(xyz(0.0, h / 2, 0.0), r, h / 2, r * 0.32), color, k = 0.0)
    s.sub(Sh.cylinder(xyz(0.0, h / 2 + h * 0.62, 0.0), r - 0.05, h / 2 + 0.02, r * 0.26), k = 0.025)
    s.add(Sh.cylinder(xyz(0.0, 0.025, 0.0), r * 0.55, 0.025, 0.012), color, k = 0.03)
    if (band != null) s.paint(Sh.torus(xyz(0.0, h * 0.62, 0.0), r, 0.04), band, soft = 0.006)
}

// Anillo elíptico hecho de cápsulas (para el cuello).
private fun ellipseRing(s: Sculpt, cx: Double, cy: Double, cz: Double, rx: Double, rz: Double, r: Double, color: String, k: Double = 0.03, segments: Int = 20) {
    var prev: DoubleArray? = null
    for (i in 0..segments) {
        val a = (i / segments.toDouble()) * PI * 2
        val p = xyz(cx + sinD(a) * rx, cy, cz + cosD(a) * rz)
        if (prev != null) s.add(Sh.capsule(prev, p, r), color, k = k)
        prev = p
    }
}

// ── Comida, cuidados y juguetes ───────────────────────────────

private val BUILDERS: Map<String, () -> Object3D> = mapOf(
    "manzana" to {
        sculpted("manzana", "gloss", cell = 0.016) { s ->
            s.add(Sh.sphere(xyz(0.0, 0.44, 0.0), 0.43), C.apple, k = 0.0)
            s.add(Sh.ellipsoid(xyz(0.0, 0.36, 0.0), xyz(0.47, 0.34, 0.47)), C.apple, k = 0.14)
            s.sub(Sh.sphere(xyz(0.0, 0.92, 0.0), 0.12), k = 0.16)
            s.paint(Sh.sphere(xyz(-0.3, 0.62, 0.3), 0.32), "#ff8f74", soft = 0.18, amount = 0.55)
            s.paint(Sh.sphere(xyz(0.0, 0.85, 0.0), 0.16), "#c7d65a", soft = 0.1, amount = 0.5)
            s.add(Sh.capsule(xyz(0.0, 0.7, 0.0), xyz(0.04, 1.0, 0.0), 0.026), C.cocoa, k = 0.02)
            s.add(Sh.leaf(xyz(0.04, 0.9, 0.0), rot(0.3, 0.0, -1.05), 0.3, 0.16, 0.026, 0.2), C.green, k = 0.015)
        }
    },
    "zanahoria" to {
        sculpted("zanahoria", "matte", cell = 0.016, ground = null) { s ->
            val t = -0.35
            fun turn(x: Double, y: Double, z: Double) = xyz(x * cosD(t) - y * sinD(t), x * sinD(t) + y * cosD(t) + 0.02, z)
            s.add(Sh.cone(turn(0.0, 0.88, 0.0), turn(0.0, 0.08, 0.0), 0.2, 0.04), C.carrot, k = 0.0)
            for (h in doubleArrayOf(0.28, 0.42, 0.56, 0.7)) {
                val r = 0.04 + (0.2 - 0.04) * ((h - 0.08) / 0.8)
                s.sub(Sh.torus(turn(0.0, h, 0.0), r + 0.004, 0.011, rot(0.0, 0.0, t)), k = 0.012)
            }
            for (leaf in arrayOf(doubleArrayOf(-0.45, 0.32), doubleArrayOf(0.0, 0.4), doubleArrayOf(0.45, 0.3))) {
                s.add(Sh.leaf(turn(0.0, 0.96, 0.0), rot(0.25, 0.0, t + leaf[0]), leaf[1], 0.12, 0.03, 0.15), C.green, k = 0.03)
            }
        }
    },
    "pan" to {
        sculpted("pan", "matte") { s ->
            s.add(Sh.ellipsoid(xyz(0.0, 0.28, 0.0), xyz(0.56, 0.32, 0.36)), C.crust, k = 0.0)
            s.paint(Sh.ellipsoid(xyz(0.0, 0.55, 0.0), xyz(0.62, 0.2, 0.42)), "#c57d3c", soft = 0.12, amount = 0.75)
            for (x in doubleArrayOf(-0.26, 0.0, 0.26)) {
                val a = xyz(x - 0.07, 0.6, -0.2)
                val b = xyz(x + 0.07, 0.6, 0.2)
                s.sub(Sh.capsule(a, b, 0.032), k = 0.035)
                s.paint(Sh.capsule(a, b, 0.052), "#ffe0aa", soft = 0.02)
            }
        }
    },
    "arroz" to {
        group(
            sculpted("arroz-cuenco", "glaze") { s -> bowl(s, C.blue, 0.5, 0.4, C.white) },
            sculpted("arroz", "matte", cell = 0.012) { s ->
                s.add(Sh.ellipsoid(xyz(0.0, 0.36, 0.0), xyz(0.43, 0.2, 0.43)), C.white, k = 0.0)
                val rng = Rng(77)
                for (i in 0 until 46) {
                    val a = rng.range(0.0, PI * 2)
                    val r = sqrt(rng.range(0.0, 1.0)) * 0.38
                    val y = 0.36 + 0.2 * sqrt(max(0.0, 1 - powD(r / 0.43, 2.0)))
                    val center = xyz(cosD(a) * r, y, sinD(a) * r)
                    val rx = rng.range(0.0, 3.0)
                    val ry = rng.range(0.0, 3.0)
                    s.add(Sh.ellipsoid(center, xyz(0.034, 0.017, 0.017), rot(rx, ry, 0.0)), C.white, k = 0.012)
                }
                s.add(Sh.sphere(xyz(0.05, 0.58, 0.04), 0.065), "#e2506a", k = 0.02)
            },
            sculpted("palillos", "gloss", cell = 0.008, ground = null) { s ->
                s.add(Sh.capsule(xyz(-0.55, 0.47, 0.2), xyz(0.42, 0.6, -0.12), 0.018), C.tan, k = 0.0)
                s.add(Sh.capsule(xyz(-0.55, 0.44, 0.28), xyz(0.44, 0.56, -0.02), 0.018), C.tan, k = 0.0)
            },
        )
    },
    "sopa" to {
        group(
            sculpted("sopa-cuenco", "glaze") { s -> bowl(s, C.cream, 0.52, 0.38, C.pink) },
            sculpted("sopa", "gloss", cell = 0.01, ground = null) { s ->
                s.add(Sh.cylinder(xyz(0.0, 0.29, 0.0), 0.46, 0.025, 0.02), "#ffae5c", k = 0.0)
                val rng = Rng(31)
                for (i in 0 until 7) {
                    val a = rng.range(0.0, PI * 2)
                    val r = rng.range(0.08, 0.34)
                    val p = xyz(cosD(a) * r, 0.315, sinD(a) * r)
                    when (i % 3) {
                        0 -> s.add(Sh.box(p, xyz(0.035, 0.02, 0.035), 0.012, rot(0.0, a, 0.0)), C.carrot, k = 0.01)
                        1 -> s.add(Sh.leaf(p, rot(PI / 2, 0.0, a), 0.08, 0.05, 0.012, 0.0), C.green, k = 0.01)
                        else -> s.add(Sh.capsule(p, xyz(p[0] + 0.1, 0.315, p[2] + 0.04), 0.014), C.cream, k = 0.01)
                    }
                }
            },
            sculpted("cuchara", "glaze", cell = 0.01, ground = null) { s ->
                s.add(Sh.capsule(xyz(0.18, 0.33, 0.12), xyz(0.62, 0.62, -0.1), 0.025), C.white, k = 0.0)
                s.add(Sh.ellipsoid(xyz(0.14, 0.32, 0.14), xyz(0.1, 0.03, 0.08), rot(0.0, 0.45, -0.3)), C.white, k = 0.03)
            },
        )
    },
    "pescado" to {
        sculpted("pescado", "gloss", cell = 0.014) { s ->
            s.add(Sh.ellipsoid(xyz(0.0, 0.3, 0.0), xyz(0.48, 0.25, 0.17)), "#89b6ec", k = 0.0)
            s.add(Sh.leaf(xyz(-0.4, 0.3, 0.0), rot(0.0, 0.0, PI / 2), 0.36, 0.42, 0.04, 0.0), "#6a98d8", k = 0.06)
            s.add(Sh.leaf(xyz(-0.05, 0.5, 0.0), rot(0.0, 0.0, 0.9), 0.22, 0.18, 0.03, 0.0), "#6a98d8", k = 0.04)
            s.paint(Sh.ellipsoid(xyz(0.02, 0.18, 0.0), xyz(0.42, 0.14, 0.3)), "#e8f2ff", soft = 0.05)
            for (x in doubleArrayOf(-0.18, 0.0, 0.18)) s.paint(Sh.capsule(xyz(x - 0.06, 0.2, 0.2), xyz(x + 0.08, 0.42, 0.2), 0.02), "#6b4a3a", soft = 0.01, amount = 0.8)
            s.add(Sh.sphere(xyz(0.3, 0.36, 0.11), 0.05), C.white, k = 0.01)
            s.paint(Sh.sphere(xyz(0.32, 0.365, 0.16), 0.026), "#2a1a14", soft = 0.004)
            s.sub(Sh.sphere(xyz(0.5, 0.27, 0.0), 0.035), k = 0.03)
        }
    },
    "galleta" to {
        val cookie = sculpted("galleta", "matte", cell = 0.012, ground = null) { s ->
            s.add(Sh.cylinder(xyz(0.0, 0.0, 0.0), 0.46, 0.06, 0.055), C.tan, k = 0.0)
            s.sub(Sh.sphere(xyz(0.44, 0.0, 0.22), 0.14), k = 0.03)
            s.paint(Sh.cylinder(xyz(0.0, 0.0, 0.0), 0.5, 0.2, 0.0), "#d79a58", soft = 0.06, amount = 0.25)
            for (chip in arrayOf(doubleArrayOf(0.15, 0.1), doubleArrayOf(-0.2, 0.05), doubleArrayOf(0.02, -0.22), doubleArrayOf(-0.05, 0.26), doubleArrayOf(0.22, -0.14), doubleArrayOf(-0.24, -0.18))) {
                s.add(Sh.ellipsoid(xyz(chip[0], 0.06, chip[1]), xyz(0.055, 0.04, 0.05), rot(0.0, chip[0] * 4, 0.0)), C.cocoa, k = 0.02)
            }
        }
        cookie.rotation.x = 0.7f
        cookie.position.y = 0.36f
        group(cookie)
    },
    "helado" to {
        group(
            sculpted("helado-cono", "matte", cell = 0.012) { s ->
                s.add(Sh.cone(xyz(0.0, 0.03, 0.0), xyz(0.0, 0.62, 0.0), 0.03, 0.25), "#e4ad6a", k = 0.0)
                for (h in doubleArrayOf(0.2, 0.32, 0.44, 0.56)) {
                    val r = 0.03 + 0.22 * ((h - 0.03) / 0.59)
                    s.paint(Sh.torus(xyz(0.0, h, 0.0), r, 0.011), "#c98a45", soft = 0.004)
                }
                for (i in 0 until 10) {
                    val a = (i / 10.0) * PI * 2
                    s.paint(Sh.capsule(xyz(sinD(a) * 0.03, 0.03, cosD(a) * 0.03), xyz(sinD(a) * 0.25, 0.62, cosD(a) * 0.25), 0.01), "#c98a45", soft = 0.004)
                }
            },
            sculpted("helado-bolas", "gloss", cell = 0.012, ground = null) { s ->
                s.add(Sh.sphere(xyz(0.0, 0.72, 0.0), 0.27), C.pink, k = 0.0)
                for (i in 0 until 7) {
                    val a = (i / 7.0) * PI * 2 + 0.3
                    s.add(Sh.ellipsoid(xyz(sinD(a) * 0.24, 0.6 - (i % 2) * 0.04, cosD(a) * 0.24), xyz(0.06, 0.09, 0.06)), C.pink, k = 0.06)
                }
                s.add(Sh.sphere(xyz(0.0, 1.0, 0.0), 0.21), C.cream, k = 0.06)
                s.add(Sh.sphere(xyz(0.02, 1.25, 0.02), 0.07), "#e5465e", k = 0.01)
                s.add(Sh.capsule(xyz(0.03, 1.3, 0.02), xyz(0.08, 1.42, -0.02), 0.01), C.cocoa, k = 0.01)
                val rng = Rng(5)
                val sprinkles = arrayOf("#ff6f9c", "#7ed6a5", "#9db7ff", "#ffd166")
                for (i in 0 until 14) {
                    val a = rng.range(0.0, PI * 2)
                    val p = rng.range(0.1, 1.2)
                    s.paint(
                        Sh.capsule(
                            xyz(sinD(a) * cosD(p) * 0.21, 1.0 + sinD(p) * 0.21, cosD(a) * cosD(p) * 0.21),
                            xyz(sinD(a + 0.25) * cosD(p) * 0.21, 1.0 + sinD(p) * 0.21, cosD(a + 0.25) * cosD(p) * 0.21),
                            0.012,
                        ),
                        sprinkles[i % 4], soft = 0.004,
                    )
                }
            },
        )
    },
    "tarta" to {
        sculpted("tarta", "matte", cell = 0.014) { s ->
            val axis = xyz(0.0, 0.28, -0.45)
            val a = 0.42
            s.add(Sh.cylinder(axis, 0.8, 0.27, 0.03), "#ffe6b8", k = 0.0)
            s.sub(Sh.box(xyz(axis[0] + cosD(a) * 1.0, 0.3, axis[2] - sinD(a) * 1.0), xyz(1.0, 1.0, 1.5), 0.0, rot(0.0, a, 0.0)), k = 0.01)
            s.sub(Sh.box(xyz(axis[0] - cosD(a) * 1.0, 0.3, axis[2] - sinD(a) * 1.0), xyz(1.0, 1.0, 1.5), 0.0, rot(0.0, PI - a, 0.0)), k = 0.01)
            s.paint(Sh.cylinder(xyz(0.0, 0.24, -0.45), 0.9, 0.03, 0.0), "#ff7f9f", soft = 0.008)
            s.paint(Sh.cylinder(xyz(0.0, 0.4, -0.45), 0.9, 0.022, 0.0), "#fff6ea", soft = 0.008)
            s.paint(Sh.cylinder(xyz(0.0, 0.6, -0.45), 0.9, 0.06, 0.0), "#fffaf2", soft = 0.01)
            for (i in 0 until 4) s.add(Sh.sphere(xyz(-0.18 + i * 0.12, 0.56, 0.18 - abs(i - 1.5) * 0.06), 0.06), "#fffaf2", k = 0.04)
            s.add(Sh.ellipsoid(xyz(0.0, 0.68, 0.05), xyz(0.09, 0.11, 0.09)), "#e5465e", k = 0.02)
            s.add(Sh.leaf(xyz(0.0, 0.77, 0.05), rot(0.3, 0.0, 0.6), 0.08, 0.05, 0.015, 0.0), C.green, k = 0.01)
        }
    },
    "festin" to {
        group(
            sculpted("festin-plato", "glaze", cell = 0.016) { s ->
                s.add(Sh.cylinder(xyz(0.0, 0.03, 0.0), 0.74, 0.03, 0.03), C.white, k = 0.0)
                s.add(Sh.torus(xyz(0.0, 0.06, 0.0), 0.68, 0.035), C.white, k = 0.04)
                s.paint(Sh.torus(xyz(0.0, 0.075, 0.0), 0.68, 0.012), C.pink, soft = 0.004)
            },
            sculpted("festin", "matte", cell = 0.014) { s ->
                s.add(Sh.ellipsoid(xyz(-0.26, 0.2, 0.08), xyz(0.26, 0.15, 0.18)), C.crust, k = 0.0)
                s.paint(Sh.ellipsoid(xyz(-0.26, 0.32, 0.08), xyz(0.3, 0.08, 0.22)), "#c57d3c", soft = 0.06)
                s.add(Sh.sphere(xyz(0.27, 0.24, 0.12), 0.19), C.apple, k = 0.0)
                s.add(Sh.capsule(xyz(0.27, 0.4, 0.12), xyz(0.29, 0.5, 0.12), 0.016), C.cocoa, k = 0.01)
                for (i in 0 until 8) {
                    val a = (i / 6.0) * PI * 2
                    val layer = if (i < 6) 0 else 1
                    s.add(Sh.sphere(xyz(0.05 + sinD(a) * 0.07 * (1 - layer), 0.13 + layer * 0.08, -0.3 + cosD(a) * 0.07 * (1 - layer)), 0.06), "#a77fd6", k = 0.01)
                }
                s.add(Sh.box(xyz(-0.18, 0.12, -0.32), xyz(0.12, 0.08, 0.09), 0.02, rot(0.0, 0.4, 0.0)), C.yellow, k = 0.0)
                s.sub(Sh.sphere(xyz(-0.24, 0.16, -0.24), 0.03), k = 0.01)
                s.sub(Sh.sphere(xyz(-0.1, 0.1, -0.24), 0.025), k = 0.01)
                s.add(Sh.cone(xyz(0.12, 0.1, 0.38), xyz(0.36, 0.12, 0.3), 0.045, 0.02), C.carrot, k = 0.0)
            },
        )
    },

    // ── Cuidados ──
    "medicina" to {
        sculpted("medicina", "glaze", cell = 0.012) { s ->
            s.add(Sh.cylinder(xyz(0.0, 0.3, 0.0), 0.3, 0.3, 0.12), C.white, k = 0.0)
            s.add(Sh.cylinder(xyz(0.0, 0.64, 0.0), 0.17, 0.06, 0.03), C.white, k = 0.05)
            s.add(Sh.cylinder(xyz(0.0, 0.76, 0.0), 0.21, 0.09, 0.04), "#f2605c", k = 0.01)
            for (i in 0 until 14) {
                val a = (i / 14.0) * PI * 2
                s.sub(Sh.capsule(xyz(sinD(a) * 0.215, 0.7, cosD(a) * 0.215), xyz(sinD(a) * 0.215, 0.82, cosD(a) * 0.215), 0.012), k = 0.008)
            }
            s.paint(Sh.cylinder(xyz(0.0, 0.32, 0.0), 0.32, 0.17, 0.0), "#ffe3ec", soft = 0.006)
            s.paint(Sh.capsule(xyz(-0.09, 0.32, 0.3), xyz(0.09, 0.32, 0.3), 0.03), "#f2605c", soft = 0.004)
            s.paint(Sh.capsule(xyz(0.0, 0.23, 0.3), xyz(0.0, 0.41, 0.3), 0.03), "#f2605c", soft = 0.004)
        }
    },
    "vitaminas" to {
        sculpted("vitaminas", "glaze", cell = 0.012) { s ->
            val t = -0.7
            fun end(d: Double) = xyz(cosD(t) * d, 0.34 + sinD(t) * d, 0.0)
            s.add(Sh.capsule(end(-0.24), end(0.24), 0.19), C.white, k = 0.0)
            s.paint(Sh.box(xyz(cosD(t) * 0.3, 0.34 + sinD(t) * 0.3, 0.0), xyz(0.3, 0.6, 0.4), 0.0, rot(0.0, 0.0, t)), C.yellow, soft = 0.006)
            s.add(Sh.cylinder(xyz(0.38, 0.06, 0.18), 0.12, 0.05, 0.04), C.mint, k = 0.0)
            s.sub(Sh.capsule(xyz(0.3, 0.11, 0.18), xyz(0.46, 0.11, 0.18), 0.01), k = 0.006)
        }
    },
    "jabon" to {
        val bar = sculpted("jabon", "gloss", cell = 0.014) { s ->
            s.add(Sh.box(xyz(0.0, 0.15, 0.0), xyz(0.42, 0.14, 0.27), 0.13), C.mint, k = 0.0)
            s.sub(Sh.sphere(xyz(-0.05, 0.3, 0.02), 0.06), k = 0.02)
            s.sub(Sh.sphere(xyz(0.05, 0.3, 0.02), 0.06), k = 0.02)
            s.sub(Sh.cone(xyz(0.0, 0.3, 0.13), xyz(0.0, 0.3, 0.04), 0.012, 0.075), k = 0.02)
        }
        val film = physical("#e8f6ff", 0.02f) {
            transparent = true
            opacity = 0.2f
            clearcoat = 1f
            clearcoatRoughness = 0f
            depthWrite = false
        }
        val g = group(bar)
        for (b in arrayOf(floatArrayOf(0.24f, 0.56f, 0.05f, 0.16f), floatArrayOf(-0.14f, 0.5f, 0.1f, 0.1f), floatArrayOf(0.04f, 0.8f, 0f, 0.07f), floatArrayOf(-0.3f, 0.72f, -0.05f, 0.05f))) {
            val bubble = Mesh(BALL28, film)
            bubble.scale.setScalar(b[3])
            bubble.position.set(b[0], b[1], b[2])
            g.add(bubble)
        }
        g
    },

    // ── Juguetes ──
    "pelota" to {
        sculpted("pelota", "gloss", cell = 0.016) { s ->
            s.add(Sh.sphere(xyz(0.0, 0.45, 0.0), 0.45), C.pink, k = 0.0)
            s.paint(Sh.torus(xyz(0.0, 0.45, 0.0), 0.45, 0.075, rot(0.45, 0.0, 0.35)), C.white, soft = 0.006)
            s.paint(Sh.sphere(xyz(0.12, 0.62, 0.38), 0.1), C.yellow, soft = 0.006)
            s.paint(Sh.sphere(xyz(-0.3, 0.3, 0.28), 0.07), C.yellow, soft = 0.006)
        }
    },
    "peonza" to {
        val top = sculpted("peonza", "gloss", cell = 0.014, ground = null) { s ->
            s.add(Sh.cone(xyz(0.0, 0.04, 0.0), xyz(0.0, 0.46, 0.0), 0.03, 0.4), C.violet, k = 0.0)
            s.add(Sh.cylinder(xyz(0.0, 0.52, 0.0), 0.42, 0.05, 0.04), C.yellow, k = 0.04)
            s.add(Sh.ellipsoid(xyz(0.0, 0.58, 0.0), xyz(0.38, 0.14, 0.38)), C.pink, k = 0.04)
            s.add(Sh.capsule(xyz(0.0, 0.62, 0.0), xyz(0.0, 0.94, 0.0), 0.045), C.cocoa, k = 0.04)
            s.paint(Sh.torus(xyz(0.0, 0.3, 0.0), 0.27, 0.02), C.white, soft = 0.005)
            for (i in 0 until 6) {
                val a = (i / 6.0) * PI * 2
                s.paint(Sh.sphere(xyz(sinD(a) * 0.3, 0.66, cosD(a) * 0.3), 0.045), C.white, soft = 0.005)
            }
        }
        top.rotation.z = 0.22f
        group(top)
    },
    "peluche" to {
        sculpted("peluche", "plush", cell = 0.012) { s ->
            val fur = "#b98258"
            val light = "#f1d3b0"
            s.add(Sh.ellipsoid(xyz(0.0, 0.32, 0.0), xyz(0.3, 0.3, 0.26)), fur, k = 0.0)
            s.add(Sh.sphere(xyz(0.0, 0.74, 0.02), 0.27), fur, k = 0.08)
            for (side in intArrayOf(-1, 1)) {
                s.add(Sh.ellipsoid(xyz(side * 0.2, 0.96, 0.0), xyz(0.1, 0.1, 0.06)), fur, k = 0.03)
                s.paint(Sh.ellipsoid(xyz(side * 0.2, 0.96, 0.05), xyz(0.06, 0.06, 0.04)), "#f0b0a8", soft = 0.008)
                s.add(Sh.cone(xyz(side * 0.24, 0.5, 0.08), xyz(side * 0.34, 0.28, 0.18), 0.08, 0.085), fur, k = 0.04)
                s.add(Sh.ellipsoid(xyz(side * 0.16, 0.1, 0.18), xyz(0.11, 0.1, 0.15)), fur, k = 0.04)
                s.paint(Sh.ellipsoid(xyz(side * 0.16, 0.1, 0.32), xyz(0.07, 0.07, 0.03)), light, soft = 0.008)
                s.add(Sh.sphere(xyz(side * 0.1, 0.79, 0.26), 0.032), "#2a1a14", k = 0.008)
            }
            s.add(Sh.ellipsoid(xyz(0.0, 0.68, 0.22), xyz(0.12, 0.09, 0.08)), light, k = 0.04)
            s.add(Sh.ellipsoid(xyz(0.0, 0.72, 0.3), xyz(0.04, 0.028, 0.025)), "#3a2118", k = 0.01)
            s.paint(Sh.ellipsoid(xyz(0.0, 0.3, 0.24), xyz(0.17, 0.18, 0.08)), light, soft = 0.01)
            s.add(Sh.torus(xyz(0.0, 0.53, 0.02), 0.16, 0.025), C.pink, k = 0.01)
            s.add(Sh.ellipsoid(xyz(0.07, 0.52, 0.2), xyz(0.06, 0.04, 0.03), rot(0.0, 0.0, 0.4)), C.pink, k = 0.01)
            s.add(Sh.ellipsoid(xyz(-0.07, 0.52, 0.2), xyz(0.06, 0.04, 0.03), rot(0.0, 0.0, -0.4)), C.pink, k = 0.01)
        }
    },
    "cometa" to {
        val outline = Shape()
        outline.moveTo(0f, 0.6f)
        outline.lineTo(0.36f, 0.12f)
        outline.lineTo(0f, -0.5f)
        outline.lineTo(-0.36f, 0.12f)
        outline.closePath()
        val geo = extrudeGeometry(outline, 0.012f, bevelEnabled = true, bevelThickness = 0.015f, bevelSize = 0.025f, bevelSegments = 4)
        val pos = geo.positions
        geo.uvs = FloatArray(pos.size / 3 * 2) { i -> if (i % 2 == 0) (pos[i / 2 * 3] + 0.4f) / 0.8f else (pos[i / 2 * 3 + 1] + 0.54f) / 1.18f }
        val sail = Mesh(geo, Material.standard(Color(1f, 1f, 1f), 0.75f) {
            map = kiteTexture
            sheen = 0.5f
            sheenColor.set(0.3f, 0.3f, 0.3f)
        })
        sail.castShadow = true
        val frame = sculpted("cometa-varillas", "wood", cell = 0.008, ground = null) { s ->
            s.add(Sh.capsule(xyz(-0.36, 0.12, 0.035), xyz(0.36, 0.12, 0.035), 0.014), C.tan, k = 0.0)
            s.add(Sh.capsule(xyz(0.0, -0.5, 0.035), xyz(0.0, 0.6, 0.035), 0.014), C.tan, k = 0.0)
        }
        val tail = sculpted("cometa-cola", "fabric", cell = 0.008, ground = null) { s ->
            val pts = arrayOf(xyz(0.0, -0.5, 0.0), xyz(0.06, -0.66, 0.0), xyz(0.1, -0.82, 0.0), xyz(0.04, -0.98, 0.0), xyz(-0.06, -1.12, 0.0))
            for (i in 1 until pts.size) s.add(Sh.capsule(pts[i - 1], pts[i], 0.008), C.white, k = 0.01)
            arrayOf(doubleArrayOf(0.06, -0.66), doubleArrayOf(0.1, -0.82), doubleArrayOf(0.04, -0.98)).forEachIndexed { i, (x, y) ->
                val color = arrayOf(C.pink, C.yellow, C.mint)[i]
                s.add(Sh.ellipsoid(xyz(x - 0.05, y, 0.0), xyz(0.055, 0.03, 0.02), rot(0.0, 0.0, 0.3)), color, k = 0.02)
                s.add(Sh.ellipsoid(xyz(x + 0.05, y, 0.0), xyz(0.055, 0.03, 0.02), rot(0.0, 0.0, -0.3)), color, k = 0.02)
            }
        }
        val g = group(sail, frame, tail)
        g.rotation.z = 0.3f
        g.position.y = 0.95f
        group(g)
    },
)

private val kiteTexture by lazy {
    painted(256, 256, mipmaps = true) { canvas, paint ->
        val colors = arrayOf("#7fb2f0", "#ff8fb1", "#ffd166", "#7ed6a5")
        val w = 256f
        val h = 256f
        val cx = w / 2f
        val cy = h * (1f - 0.66f / 1.18f)
        val quads = arrayOf(floatArrayOf(0f, 0f, cx, cy), floatArrayOf(cx, 0f, w, cy), floatArrayOf(cx, cy, w, h), floatArrayOf(0f, cy, cx, h))
        quads.forEachIndexed { i, q ->
            paint.color = css(colors[i])
            canvas.drawRect(q[0], q[1], q[2], q[3], paint)
        }
        paint.style = Paint.Style.STROKE
        paint.color = rgba(255, 255, 255, 0.7f)
        paint.strokeWidth = 3f
        paint.pathEffect = DashPathEffect(floatArrayOf(6f, 6f), 0f)
        canvas.drawRect(14f, 14f, w - 14f, h - 14f, paint)
    }
}

// ── Casa ──────────────────────────────────────────────────────
// Muebles para el rincón (al lado de la criatura, a la derecha) y para
// debajo de la ventana. Se apoyan en el suelo en y = 0 y miden de verdad.

private fun pot(s: Sculpt, color: String, band: String?, r: Double = 0.19, h: Double = 0.3, soil: String = "#7a5238") {
    s.add(Sh.cone(xyz(0.0, 0.02, 0.0), xyz(0.0, h - 0.02, 0.0), r * 0.76, r), color, k = 0.0)
    s.add(Sh.torus(xyz(0.0, h - 0.01, 0.0), r * 0.98, 0.028), band ?: color, k = 0.015)
    s.sub(Sh.cylinder(xyz(0.0, h + 0.08, 0.0), r * 0.86, 0.1, 0.02), k = 0.012)
    s.add(Sh.cylinder(xyz(0.0, h - 0.04, 0.0), r * 0.88, 0.012, 0.008), soil, k = 0.0)
    if (band != null) s.paint(Sh.cylinder(xyz(0.0, h - 0.05, 0.0), r * 1.2, 0.05, 0.0), band, soft = 0.006)
}

private val DECOR: Map<String, () -> Object3D> = mapOf(
    // La planta de siempre.
    "planta" to {
        group(
            sculpted("planta-maceta", "glaze", cell = 0.012) { s -> pot(s, "#fffaf2", "#ffd166") },
            sculpted("planta-hojas", "matte", cell = 0.009, aoReach = 0.08) { s ->
                val rng = Rng(8)
                val greens = arrayOf("#4fae7c", "#5fbf88", "#3f9c6c")
                for (i in 0 until 9) {
                    val a = (i / 9.0) * PI * 2 + rng.range(-0.2, 0.2)
                    val tilt = if (i == 0) 0.05 else rng.range(0.22, 0.6)
                    val dir = v(sinD(a) * sinD(tilt), cosD(tilt), cosD(a) * sinD(tilt))
                    val q = quatTo(dir).multiply(Q().setFromAxisAngle(AXIS_Y, -a + PI / 2))
                    val len = rng.range(0.42, 0.62) * (1 - tilt * 0.35)
                    val base = xyz(sinD(a) * 0.04, 0.2, cosD(a) * 0.04)
                    val width = rng.range(0.13, 0.17)
                    val bend = rng.range(0.1, 0.25)
                    s.add(Sh.leaf(base, rot(q), len, width, 0.024, bend), greens[i % 3], k = 0.02)
                    val tip = v(base[0], base[1], base[2]).addScaledVector(dir, len * 0.95)
                    s.paint(Sh.capsule(xyz(base[0], base[1] + 0.05, base[2]), tip.toArray(), 0.012), "#a6e3b9", soft = 0.008, amount = 0.7)
                }
            },
        )
    },
    "cactus" to {
        group(
            sculpted("cactus-maceta", "matte", cell = 0.012) { s -> pot(s, C.terracotta, "#d9724c", r = 0.2, h = 0.3) },
            sculpted("cactus", "matte", cell = 0.01) { s ->
                s.add(Sh.capsule(xyz(0.0, 0.28, 0.0), xyz(0.0, 0.82, 0.0), 0.15), "#6cc47f", k = 0.0)
                for (arm in arrayOf(doubleArrayOf(-1.0, 0.5, 0.2), doubleArrayOf(1.0, 0.62, 0.14))) {
                    val side = arm[0]
                    val y = arm[1]
                    val elbow = xyz(side * 0.25, y, 0.0)
                    s.add(Sh.capsule(xyz(side * 0.08, y - 0.04, 0.0), elbow, 0.07), "#6cc47f", k = 0.05)
                    s.add(Sh.capsule(elbow, xyz(side * 0.26, y + arm[2], 0.0), 0.07), "#6cc47f", k = 0.04)
                }
                for (i in 0 until 10) {
                    val a = (i / 10.0) * PI * 2
                    s.paint(Sh.capsule(xyz(sinD(a) * 0.15, 0.32, cosD(a) * 0.15), xyz(sinD(a) * 0.15, 0.85, cosD(a) * 0.15), 0.012), "#55ad69", soft = 0.01)
                }
                val rng = Rng(19)
                for (i in 0 until 26) {
                    val a = rng.range(0.0, PI * 2)
                    val y = rng.range(0.34, 0.86)
                    s.paint(Sh.sphere(xyz(sinD(a) * 0.152, y, cosD(a) * 0.152), 0.011), C.cream, soft = 0.003)
                }
                for (i in 0 until 5) {
                    val a = (i / 5.0) * PI * 2
                    s.add(Sh.ellipsoid(xyz(sinD(a) * 0.045, 0.98, cosD(a) * 0.045), xyz(0.04, 0.025, 0.04), rot(0.0, 0.0, 0.0)), "#ff8fb1", k = 0.015)
                }
                s.add(Sh.sphere(xyz(0.0, 0.99, 0.0), 0.025), C.yellow, k = 0.01)
            },
        )
    },
    "girasol" to {
        group(
            sculpted("girasol-maceta", "glaze", cell = 0.012) { s -> pot(s, "#fffaf2", C.mint) },
            sculpted("girasol", "matte", cell = 0.009) { s ->
                s.add(Sh.capsule(xyz(0.0, 0.26, 0.0), xyz(0.02, 1.02, 0.02), 0.024), "#4fae7c", k = 0.0)
                s.add(Sh.leaf(xyz(0.0, 0.6, 0.0), rot(0.3, 0.0, 1.0), 0.3, 0.16, 0.02, 0.2), "#5fbf88", k = 0.02)
                s.add(Sh.leaf(xyz(0.01, 0.74, 0.0), rot(0.3, 0.0, -1.0), 0.28, 0.15, 0.02, 0.2), "#4fae7c", k = 0.02)
                val c = v(0.02, 1.1, 0.06)
                val q = Q().setFromEuler(-0.3, 0.25, 0.0)
                s.add(Sh.ellipsoid(c.toArray(), xyz(0.13, 0.13, 0.055), rot(q)), "#8a5a32", k = 0.03)
                val rng = Rng(3)
                for (i in 0 until 30) {
                    val a = i * 2.39996
                    val r = sqrt(i / 30.0) * 0.11
                    val p = v(cosD(a) * r, sinD(a) * r, 0.052).applyQuaternion(q).add(c)
                    s.paint(Sh.sphere(p.toArray(), 0.012 + rng.range(0.0, 0.004)), "#5e3a22", soft = 0.004)
                }
                for (i in 0 until 14) {
                    val a = (i / 14.0) * PI * 2
                    val p = v(cosD(a) * 0.19, sinD(a) * 0.19, -0.01).applyQuaternion(q).add(c)
                    val pq = q.clone().multiply(Q().setFromAxisAngle(v(0.0, 0.0, 1.0), a))
                    s.add(Sh.ellipsoid(p.toArray(), xyz(0.085, 0.035, 0.014), rot(pq)), if (i % 2 == 1) "#ffcf3f" else "#ffd95e", k = 0.015)
                }
            },
        )
    },
    "lampara" to {
        val base = sculpted("lampara-pie", "glaze", cell = 0.01) { s ->
            s.add(Sh.cylinder(xyz(0.0, 0.03, 0.0), 0.19, 0.03, 0.025), C.white, k = 0.0)
            s.add(Sh.capsule(xyz(0.0, 0.04, 0.0), xyz(0.0, 1.16, 0.0), 0.022), C.tan, k = 0.03)
            s.add(Sh.sphere(xyz(0.0, 0.5, 0.0), 0.035), C.gold, k = 0.02)
        }
        val shadeMat = Material.standard(Color(1f, 1f, 1f), 0.9f) {
            vertexColors = true
            sheen = 0.6f
            sheenColor.set(0.3f, 0.3f, 0.3f)
            emissive.setHex("#ffcf88")
            emissiveIntensity = 0.32f
            doubleSide = true
        }
        val shade = sculpted("lampara-pantalla", shadeMat, cell = 0.009, ao = 0.4, ground = null) { s ->
            s.add(Sh.cone(xyz(0.0, 1.1, 0.0), xyz(0.0, 1.42, 0.0), 0.27, 0.15), "#ffe9b8", k = 0.0)
            s.sub(Sh.cone(xyz(0.0, 1.06, 0.0), xyz(0.0, 1.46, 0.0), 0.25, 0.13), k = 0.005)
            s.paint(Sh.torus(xyz(0.0, 1.12, 0.0), 0.265, 0.016), "#ff9fb8", soft = 0.004)
            s.paint(Sh.torus(xyz(0.0, 1.4, 0.0), 0.155, 0.014), "#ff9fb8", soft = 0.004)
        }
        val bulb = Mesh(BALL28, Material.basic(Color.hex("#fff3d0")))
        bulb.scale.set(0.07f, 0.08f, 0.07f)
        bulb.position.y = 1.18f
        group(base, shade, bulb)
    },
    "pecera" to {
        val stand = sculpted("pecera-mesa", "wood", cell = 0.012) { s ->
            s.add(Sh.cylinder(xyz(0.0, 0.02, 0.0), 0.2, 0.02, 0.015), C.tan, k = 0.0)
            s.add(Sh.cone(xyz(0.0, 0.03, 0.0), xyz(0.0, 0.53, 0.0), 0.05, 0.04), C.tan, k = 0.03)
            s.add(Sh.cylinder(xyz(0.0, 0.555, 0.0), 0.3, 0.025, 0.02), "#e8bd87", k = 0.03)
        }
        val inside = sculpted("pecera-dentro", "gloss", cell = 0.007, ground = null) { s ->
            val stones = arrayOf(
                Triple(-0.07, 0.03, C.grey), Triple(0.06, -0.02, C.pink), Triple(0.0, 0.08, "#d9c8b4"), Triple(-0.1, -0.08, "#d9c8b4"), Triple(0.1, 0.07, C.grey),
            )
            for ((x, z, color) in stones) s.add(Sh.ellipsoid(xyz(x, 0.6, z), xyz(0.045, 0.028, 0.04)), color, k = 0.01)
            s.add(Sh.leaf(xyz(-0.06, 0.6, -0.06), rot(0.0, 0.0, 0.15), 0.18, 0.06, 0.015, 0.2), C.leaf, k = 0.01)
            s.add(Sh.leaf(xyz(-0.03, 0.6, -0.08), rot(0.0, 0.0, -0.3), 0.14, 0.05, 0.015, 0.2), C.green, k = 0.01)
            val f = xyz(0.03, 0.8, 0.1)
            s.add(Sh.ellipsoid(f, xyz(0.07, 0.048, 0.035), rot(0.0, -0.4, 0.0)), C.orange, k = 0.0)
            s.add(Sh.leaf(xyz(f[0] - 0.05, f[1], f[2] + 0.03), rot(0.0, -0.4, PI / 2), 0.07, 0.07, 0.012, 0.0), "#ff7a3a", k = 0.02)
            s.paint(Sh.sphere(xyz(f[0] + 0.04, f[1] + 0.012, f[2] + 0.02), 0.012), "#2a1a14", soft = 0.003)
        }
        val water = Mesh(BALL28, physical("#8cc8f5", 0.1f) {
            transparent = true
            opacity = 0.4f
            depthWrite = false
        })
        water.scale.set(0.215f, 0.16f, 0.215f)
        water.position.y = 0.76f
        val glass = Mesh(BALL28, physical("#eaf6ff", 0.04f) {
            clearcoat = 1f
            clearcoatRoughness = 0.02f
            transparent = true
            opacity = 0.2f
            depthWrite = false
            doubleSide = true
        })
        glass.scale.set(0.25f, 0.24f, 0.25f)
        glass.position.y = 0.82f
        val rim = Mesh(torusGeometry(0.15f, 0.014f, 12, 40), plain("#eaf6ff", "glaze", "pecera") {
            transparent = true
            opacity = 0.6f
        })
        rim.rotation.x = PI_F / 2f
        rim.position.y = 1.04f
        group(stand, inside, water, glass, rim)
    },
    "cojin" to {
        group(
            sculpted("cojin", "fabric", cell = 0.012) { s ->
                s.add(Sh.box(xyz(0.0, 0.15, 0.0), xyz(0.4, 0.14, 0.34), 0.13), C.pink, k = 0.0)
                s.sub(Sh.sphere(xyz(0.0, 0.32, 0.0), 0.05), k = 0.08)
                s.add(Sh.sphere(xyz(0.0, 0.28, 0.0), 0.025), "#e2648b", k = 0.01)
                for (corner in arrayOf(doubleArrayOf(-0.36, -0.3), doubleArrayOf(0.36, -0.3), doubleArrayOf(-0.36, 0.3), doubleArrayOf(0.36, 0.3))) {
                    val x = corner[0]
                    val z = corner[1]
                    s.add(Sh.cone(xyz(x * 0.98, 0.16, z * 0.98), xyz(x * 1.08, 0.1, z * 1.08), 0.016, 0.03), C.yellow, k = 0.02)
                }
                s.add(Sh.box(xyz(0.08, 0.36, 0.0), xyz(0.19, 0.06, 0.16), 0.06, rot(0.0, 0.4, -0.18)), C.mint, k = 0.02)
                s.paint(Sh.capsule(xyz(-0.1, 0.42, -0.08), xyz(0.26, 0.36, 0.1), 0.012), "#ffffff", soft = 0.004, amount = 0.6)
            },
        )
    },
    "mesita" to {
        group(
            sculpted("mesita", "glaze", cell = 0.009) { s ->
                s.add(Sh.cylinder(xyz(0.0, 0.46, 0.0), 0.27, 0.025, 0.022), C.white, k = 0.0)
                for (i in 0 until 3) {
                    val a = (i / 3.0) * PI * 2 + 0.5
                    s.add(Sh.cone(xyz(cosD(a) * 0.17, 0.44, sinD(a) * 0.17), xyz(cosD(a) * 0.2, 0.0, sinD(a) * 0.2), 0.026, 0.017), C.tan, k = 0.02)
                }
                s.add(Sh.cone(xyz(0.0, 0.49, 0.0), xyz(0.0, 0.66, 0.0), 0.075, 0.05), C.blue, k = 0.01)
                s.add(Sh.torus(xyz(0.0, 0.68, 0.0), 0.045, 0.015), C.blue, k = 0.02)
                s.sub(Sh.cylinder(xyz(0.0, 0.72, 0.0), 0.035, 0.06, 0.01), k = 0.01)
                s.paint(Sh.torus(xyz(0.0, 0.56, 0.0), 0.07, 0.012), C.white, soft = 0.004)
            },
            sculpted("tulipanes", "matte", cell = 0.006, ground = null) { s ->
                val flowers = arrayOf(
                    arrayOf<Any>(-0.03, 0.3, 0.22, C.pink), arrayOf<Any>(0.0, 0.0, 0.28, C.yellow), arrayOf<Any>(0.03, -0.32, 0.22, "#ff7a7a"),
                )
                for (flower in flowers) {
                    val x = flower[0] as Double
                    val tilt = flower[1] as Double
                    val height = flower[2] as Double
                    val color = flower[3] as String
                    val base = v(x, 0.66, 0.0)
                    val dir = v(-sinD(tilt), cosD(tilt), 0.05)
                    val top = base.clone().addScaledVector(dir, height)
                    s.add(Sh.capsule(base.toArray(), top.toArray(), 0.008), C.leaf, k = 0.0)
                    val head = top.clone().addScaledVector(dir, 0.035)
                    s.add(Sh.ellipsoid(head.toArray(), xyz(0.042, 0.058, 0.042), rot(0.0, 0.0, tilt)), color, k = 0.01)
                    for (p in 0 until 3) {
                        val a = (p / 3.0) * PI * 2
                        s.sub(Sh.sphere(head.clone().add(v(sinD(a) * 0.012, 0.07, cosD(a) * 0.012)).toArray(), 0.02), k = 0.01)
                    }
                }
                s.add(Sh.leaf(xyz(0.0, 0.68, 0.02), rot(0.2, 0.0, 0.5), 0.14, 0.05, 0.012, 0.2), C.green, k = 0.01)
            },
        )
    },
    "baul" to {
        val box = sculpted("baul", "wood", cell = 0.012) { s ->
            s.add(Sh.box(xyz(0.0, 0.18, 0.0), xyz(0.34, 0.17, 0.2), 0.04), C.tan, k = 0.0)
            s.sub(Sh.box(xyz(0.0, 0.3, 0.0), xyz(0.3, 0.15, 0.16), 0.02), k = 0.01)
            for (y in doubleArrayOf(0.06, 0.3)) s.paint(Sh.box(xyz(0.0, y, 0.0), xyz(0.4, 0.025, 0.3), 0.0), "#b97f4e", soft = 0.004)
            s.paint(Sh.box(xyz(0.0, 0.18, 0.2), xyz(0.07, 0.17, 0.02), 0.0), "#c98c58", soft = 0.004)
        }
        val lid = sculpted("baul-tapa", "wood", cell = 0.012, ground = null) { s ->
            s.add(Sh.box(xyz(0.0, 0.03, 0.2), xyz(0.35, 0.035, 0.2), 0.03), C.tan, k = 0.0)
            s.add(Sh.cylinder(xyz(0.0, 0.05, 0.2), 0.2, 0.34, 0.03, rot(0.0, 0.0, PI / 2)), C.tan, k = 0.02)
            s.sub(Sh.box(xyz(0.0, -0.1, 0.2), xyz(0.4, 0.13, 0.25), 0.0), k = 0.005)
            s.paint(Sh.box(xyz(0.32, 0.1, 0.2), xyz(0.025, 0.2, 0.3), 0.0), "#b97f4e", soft = 0.004)
            s.paint(Sh.box(xyz(-0.32, 0.1, 0.2), xyz(0.025, 0.2, 0.3), 0.0), "#b97f4e", soft = 0.004)
        }
        lid.position.set(0f, 0.35f, -0.2f)
        lid.rotation.x = -0.6f
        val toys = sculpted("baul-juguetes", "gloss", cell = 0.01, ground = null) { s ->
            s.add(Sh.sphere(xyz(-0.14, 0.36, 0.02), 0.11), C.pink, k = 0.0)
            s.paint(Sh.torus(xyz(-0.14, 0.36, 0.02), 0.11, 0.02, rot(0.5, 0.0, 0.3)), C.white, soft = 0.004)
            s.add(Sh.box(xyz(0.13, 0.38, 0.03), xyz(0.07, 0.07, 0.07), 0.025, rot(0.3, 0.5, 0.2)), C.blue, k = 0.0)
            s.add(Sh.ellipsoid(xyz(0.02, 0.4, -0.06), xyz(0.07, 0.07, 0.05)), C.yellow, k = 0.0)
            s.add(Sh.box(xyz(0.0, 0.13, 0.205), xyz(0.035, 0.045, 0.012), 0.01), C.gold, k = 0.0)
        }
        group(box, lid, toys)
    },
    "camita" to {
        sculpted("camita", "fabric", cell = 0.012) { s ->
            s.add(Sh.torus(xyz(0.0, 0.12, 0.0), 0.34, 0.11, null, 0.9), C.blue, k = 0.0)
            s.add(Sh.cylinder(xyz(0.0, 0.05, 0.0), 0.4, 0.05, 0.04), C.blue, k = 0.05)
            s.add(Sh.ellipsoid(xyz(0.0, 0.1, 0.0), xyz(0.3, 0.06, 0.28)), C.cream, k = 0.03)
            s.add(Sh.box(xyz(0.1, 0.16, 0.06), xyz(0.16, 0.012, 0.13), 0.01, rot(0.05, 0.5, 0.06)), C.yellow, k = 0.03)
            s.paint(Sh.box(xyz(0.1, 0.17, 0.06), xyz(0.16, 0.05, 0.03), 0.0, rot(0.0, 0.5, 0.0)), "#ffffff", soft = 0.005, amount = 0.7)
            s.add(Sh.ellipsoid(xyz(-0.14, 0.17, -0.1), xyz(0.11, 0.05, 0.08), rot(0.0, 0.4, 0.0)), C.pink, k = 0.03)
            s.paint(Sh.torus(xyz(0.0, 0.2, 0.0), 0.34, 0.03), "#9cc4f5", soft = 0.02, amount = 0.6)
        }
    },
)

// Muestra de papel pintado enmarcada, para la tienda.
private fun swatch(id: String): Object3D {
    val face = Mesh(planeGeometry(0.9f, 0.9f), Material.standard(Color(1f, 1f, 1f), 0.9f) {
        map = wallpaperTexture(id)
        uvScaleX = 1.25f
        uvScaleY = 1.25f
    })
    face.position.set(0f, 0.5f, 0.032f)
    val frame = sculpted("marco", "wood", cell = 0.012, ground = null) { s ->
        s.add(Sh.box(xyz(0.0, 0.5, 0.0), xyz(0.5, 0.5, 0.03), 0.025), "#e8bf8c", k = 0.0)
        s.sub(Sh.box(xyz(0.0, 0.5, 0.05), xyz(0.45, 0.45, 0.05), 0.01), k = 0.01)
    }
    return group(frame, face)
}

// Alfombra de muestra, inclinada hacia la cámara para que se vea entera.
private fun rugSample(id: String): Object3D {
    val rug = buildRug(id).obj
    rug.rotation.x = 0.95f
    return group(rug)
}

fun buildDecor(itemId: String): Object3D? = when {
    DECOR.containsKey(itemId) -> DECOR.getValue(itemId)()
    isWallpaper(itemId) -> swatch(itemId)
    isRug(itemId) -> rugSample(itemId)
    else -> null
}

// ── Ropa ──────────────────────────────────────────────────────
// Cada prenda recibe los anclajes de la criatura y devuelve el objeto y
// a qué parte se cuelga: 'head' (gira con la cabeza) o 'body'.

class Wear(val parent: String, val obj: Object3D)

private fun surface(host: Host, yaw: Double, pitch: Double): Surface2 {
    val cp = cosD(pitch)
    val point = v(host.rx * sinD(yaw) * cp, host.ry * sinD(pitch), host.rz * cosD(yaw) * cp)
    val normal = v(point.x / (host.rx * host.rx), point.y / (host.ry * host.ry), point.z / (host.rz * host.rz)).normalize()
    return Surface2(point.add(host.center), normal)
}

private class Surface2(val point: V3, val normal: V3)

private fun onHead(anchors: Anchors, yaw: Double, pitch: Double, obj: Object3D, sink: Double = 0.0): Object3D {
    val at = surface(anchors.face.host, yaw, pitch)
    obj.place(at.point.clone().addScaledVector(at.normal, -sink))
    obj.turn(Q().setFromUnitVectors(AXIS_Y, at.normal))
    return obj
}

private fun r2(n: Double): Double = Math.round(n * 100) / 100.0
private fun anchorKey(a: Anchors): String = listOf(
    a.hat.y, a.hat.r, a.face.gap, a.face.pitch, a.face.host.rx, a.face.host.ry, a.face.host.rz, a.face.host.center.y, a.face.host.center.z, a.neck.y, a.neck.rx, a.neck.rz,
).joinToString(",") { r2(it).toString() }

private fun Object3D.lift(dy: Double): Object3D {
    position.y += dy.toFloat()
    return this
}

private val WEAR: Map<String, (Anchors) -> Wear> = mapOf(
    "lazo" to { a ->
        val s = r2(a.hat.r / 0.5)
        val bow = sculpted("lazo:$s", "gloss", cell = 0.006 * s, ground = null, aoReach = 0.04) { k ->
            for (side in intArrayOf(-1, 1)) {
                k.add(Sh.ellipsoid(xyz(side * 0.12 * s, 0.09 * s, 0.0), xyz(0.12 * s, 0.085 * s, 0.05 * s), rot(0.0, 0.0, side * 0.25)), C.pink, k = 0.0)
                k.sub(Sh.ellipsoid(xyz(side * 0.13 * s, 0.09 * s, 0.05 * s), xyz(0.06 * s, 0.035 * s, 0.03 * s), rot(0.0, 0.0, side * 0.25)), k = 0.02)
                k.add(Sh.cone(xyz(side * 0.02 * s, 0.07 * s, 0.0), xyz(side * 0.09 * s, -0.04 * s, 0.01 * s), 0.035 * s, 0.03 * s), "#ff7aa3", k = 0.02)
            }
            k.add(Sh.sphere(xyz(0.0, 0.09 * s, 0.01 * s), 0.055 * s), "#ff6f9c", k = 0.02)
        }
        Wear("head", onHead(a, 0.55, 1.0, group(bow)))
    },
    "gorro" to { a ->
        val s = r2(a.hat.r / 0.5)
        val hat = sculpted("gorro:$s", "fabric", cell = 0.01 * s, ground = null, aoReach = 0.06) { k ->
            k.add(Sh.ellipsoid(xyz(0.0, 0.0, 0.0), xyz(0.42 * s, 0.4 * s, 0.42 * s)), "#f2605c", k = 0.0)
            k.sub(Sh.box(xyz(0.0, -0.5 * s, 0.0), xyz(1.0, 0.5 * s, 1.0), 0.0), k = 0.02)
            k.add(Sh.torus(xyz(0.0, 0.05 * s, 0.0), 0.415 * s, 0.07 * s), "#fff3e8", k = 0.02)
            for (i in 0 until 24) {
                val ang = (i / 24.0) * PI * 2
                k.paint(Sh.capsule(xyz(sinD(ang) * 0.43 * s, 0.13 * s, cosD(ang) * 0.43 * s), xyz(sinD(ang) * 0.05 * s, 0.42 * s, cosD(ang) * 0.05 * s), 0.018 * s), "#e04f4f", soft = 0.006)
            }
            for (i in 0 until 9) {
                val ang = i * 2.4
                val r = if (i == 0) 0.0 else 0.06 * s
                k.add(Sh.sphere(xyz(sinD(ang) * r, 0.47 * s + (i % 3) * 0.02 * s, cosD(ang) * r), 0.065 * s), "#fff3e8", k = 0.03)
            }
        }
        Wear("head", group(hat).lift(a.hat.y - 0.24 * s))
    },
    "gorra" to { a ->
        val s = r2(a.hat.r / 0.5)
        val hat = sculpted("gorra:$s", "fabric", cell = 0.009 * s, ground = null, aoReach = 0.06) { k ->
            k.add(Sh.ellipsoid(xyz(0.0, 0.0, 0.0), xyz(0.41 * s, 0.34 * s, 0.41 * s)), C.blue, k = 0.0)
            k.sub(Sh.box(xyz(0.0, -0.5 * s, 0.0), xyz(1.0, 0.5 * s, 1.0), 0.0), k = 0.015)
            k.add(Sh.ellipsoid(xyz(0.0, 0.02 * s, 0.38 * s), xyz(0.3 * s, 0.025 * s, 0.24 * s), rot(-0.12, 0.0, 0.0)), C.navy, k = 0.03)
            k.add(Sh.sphere(xyz(0.0, 0.34 * s, 0.0), 0.04 * s), C.navy, k = 0.015)
            for (i in 0 until 6) {
                val ang = (i / 6.0) * PI * 2
                k.paint(Sh.capsule(xyz(sinD(ang) * 0.42 * s, 0.02 * s, cosD(ang) * 0.42 * s), xyz(0.0, 0.36 * s, 0.0), 0.008 * s), "#5d8fd6", soft = 0.004)
            }
            k.paint(Sh.sphere(xyz(0.0, 0.18 * s, 0.38 * s), 0.08 * s), "#fffaf2", soft = 0.006)
        }
        hat.rotation.x = -0.08f
        Wear("head", group(hat).lift(a.hat.y - 0.2 * s))
    },
    "paja" to { a ->
        val s = r2(a.hat.r / 0.5)
        val hat = sculpted("paja:$s", "matte", cell = 0.009 * s, ground = null, aoReach = 0.05) { k ->
            k.add(Sh.cylinder(xyz(0.0, 0.0, 0.0), 0.68 * s, 0.02 * s, 0.018 * s), "#f6d27a", k = 0.0)
            k.add(Sh.cylinder(xyz(0.0, 0.13 * s, 0.0), 0.31 * s, 0.13 * s, 0.07 * s), "#f6d27a", k = 0.05)
            k.add(Sh.torus(xyz(0.0, 0.06 * s, 0.0), 0.325 * s, 0.035 * s, null, 1.1), "#ef5a5a", k = 0.01)
            for (i in 1 until 7) k.paint(Sh.torus(xyz(0.0, 0.02 * s, 0.0), (0.32 + i * 0.055) * s, 0.008 * s), "#e3b860", soft = 0.004)
            for (i in 0 until 4) k.paint(Sh.torus(xyz(0.0, (0.14 + i * 0.03) * s, 0.0), 0.31 * s, 0.006 * s), "#e3b860", soft = 0.003)
        }
        hat.rotation.x = -0.1f
        Wear("head", group(hat).lift(a.hat.y - 0.08 * s))
    },
    "flor" to { a ->
        val s = r2(a.hat.r / 0.5)
        val flower = sculpted("flor:$s", "matte", cell = 0.005 * s, ground = null, aoReach = 0.03) { k ->
            for (i in 0 until 7) {
                val ang = (i / 7.0) * PI * 2
                k.add(Sh.ellipsoid(xyz(cosD(ang) * 0.085 * s, 0.02 * s, sinD(ang) * 0.085 * s), xyz(0.07 * s, 0.022 * s, 0.035 * s), rot(0.0, -ang, 0.1)), C.white, k = 0.01)
            }
            k.add(Sh.ellipsoid(xyz(0.0, 0.04 * s, 0.0), xyz(0.05 * s, 0.035 * s, 0.05 * s)), "#ffc93c", k = 0.02)
        }
        Wear("head", onHead(a, -0.6, 0.98, group(flower)))
    },
    "corona" to { a ->
        val s = r2(a.hat.r / 0.5)
        val crown = sculpted("corona:$s", "metal", cell = 0.006 * s, ground = null, aoReach = 0.04) { k ->
            k.add(Sh.cylinder(xyz(0.0, 0.07 * s, 0.0), 0.24 * s, 0.07 * s, 0.02 * s), "#f6c453", k = 0.0)
            k.sub(Sh.cylinder(xyz(0.0, 0.1 * s, 0.0), 0.21 * s, 0.12 * s, 0.01 * s), k = 0.01)
            for (i in 0 until 6) {
                val ang = (i / 6.0) * PI * 2
                val p = xyz(cosD(ang) * 0.225 * s, 0.13 * s, sinD(ang) * 0.225 * s)
                k.add(Sh.cone(p, xyz(p[0] * 1.04, 0.24 * s, p[2] * 1.04), 0.05 * s, 0.012 * s), "#f6c453", k = 0.02)
                k.add(Sh.sphere(xyz(p[0] * 1.05, 0.255 * s, p[2] * 1.05), 0.022 * s), "#f6c453", k = 0.01)
            }
        }
        val gems = sculpted("corona-gemas:$s", "glaze", cell = 0.005 * s, ground = null, ao = 0.0) { k ->
            for (i in 0 until 6) {
                val ang = (i / 6.0) * PI * 2 + 0.52
                k.add(Sh.sphere(xyz(cosD(ang) * 0.245 * s, 0.07 * s, sinD(ang) * 0.245 * s), 0.028 * s), if (i % 2 == 1) "#f2605c" else "#6f9cf0", k = 0.0)
            }
        }
        Wear("head", group(crown, gems).lift(a.hat.y - 0.06 * s))
    },
    "gafas" to { a -> glasses(a, false) },
    "gafasol" to { a -> glasses(a, true) },
    "bigote" to { a ->
        val s = r2(a.hat.r / 0.5)
        val tache = sculpted("bigote:$s", "matte", cell = 0.004 * s, ground = null, aoReach = 0.02) { k ->
            for (side in intArrayOf(-1, 1)) {
                k.add(Sh.ellipsoid(xyz(side * 0.07 * s, 0.0, 0.0), xyz(0.085 * s, 0.035 * s, 0.03 * s), rot(0.0, 0.0, side * -0.25)), C.cocoa, k = 0.02)
                k.add(Sh.sphere(xyz(side * 0.16 * s, 0.03 * s, -0.005 * s), 0.025 * s), C.cocoa, k = 0.04)
            }
        }
        val at = surface(a.face.host, 0.0, a.face.pitch - 0.15)
        tache.place(at.point.clone().addScaledVector(at.normal, 0.02 * s))
        tache.turn(Q().setFromUnitVectors(AXIS_Z, at.normal))
        Wear("head", group(tache))
    },
    "panuelo" to { a ->
        Wear("body", sculpted("panuelo:${anchorKey(a)}", "fabric", cell = 0.01, ground = null, aoReach = 0.05) { k ->
            val y = a.neck.y
            val rx = a.neck.rx
            val rz = a.neck.rz
            ellipseRing(k, 0.0, y, 0.0, rx, rz, 0.06, "#ef5a5a")
            k.add(Sh.leaf(xyz(0.0, y + 0.02, rz + 0.02), rot(0.25 + PI, 0.0, 0.0), 0.3, 0.32, 0.03, 0.05), "#ef5a5a", k = 0.04)
            val rng = Rng(4)
            for (i in 0 until 9) {
                val x = rng.range(-0.12, 0.12)
                val dy = rng.range(0.0, 0.22)
                k.paint(Sh.sphere(xyz(x, y - dy, rz + 0.05), 0.018), C.white, soft = 0.004)
            }
        })
    },
    "pajarita" to { a ->
        Wear("body", sculpted("pajarita:${anchorKey(a)}", "gloss", cell = 0.008, ground = null, aoReach = 0.04) { k ->
            val y = a.neck.y
            val rx = a.neck.rx
            val rz = a.neck.rz
            ellipseRing(k, 0.0, y, 0.0, rx, rz, 0.022, C.navy)
            val z = rz + 0.05
            for (side in intArrayOf(-1, 1)) k.add(Sh.ellipsoid(xyz(side * 0.09, y, z), xyz(0.09, 0.065, 0.035), rot(0.0, 0.0, side * 0.1)), C.blue, k = 0.02)
            k.add(Sh.sphere(xyz(0.0, y, z + 0.01), 0.04), C.navy, k = 0.02)
            for (side in intArrayOf(-1, 1)) k.paint(Sh.sphere(xyz(side * 0.1, y + 0.02, z + 0.035), 0.016), C.white, soft = 0.004)
        })
    },
    "bufanda" to { a ->
        Wear("body", sculpted("bufanda:${anchorKey(a)}", "fabric", cell = 0.012, ground = null, aoReach = 0.06) { k ->
            val y = a.neck.y
            val rx = a.neck.rx
            val rz = a.neck.rz
            ellipseRing(k, 0.0, y, 0.0, rx, rz, 0.09, C.mint, 0.05)
            ellipseRing(k, 0.0, y + 0.09, 0.0, rx * 0.94, rz * 0.94, 0.05, C.mint, 0.05)
            val x = rx * 0.45
            k.add(Sh.box(xyz(x, y - 0.18, rz + 0.07), xyz(0.075, 0.16, 0.025), 0.022, rot(0.1, 0.0, 0.12)), C.mint, k = 0.05)
            for (dy in doubleArrayOf(-0.12, -0.24)) k.paint(Sh.box(xyz(x, y + dy, rz + 0.07), xyz(0.2, 0.025, 0.1), 0.0, rot(0.0, 0.0, 0.12)), C.white, soft = 0.005)
            for (i in 0 until 4) k.add(Sh.capsule(xyz(x - 0.05 + i * 0.033 + 0.04, y - 0.33, rz + 0.07), xyz(x - 0.05 + i * 0.033 + 0.045, y - 0.38, rz + 0.07), 0.01), C.white, k = 0.01)
            for (i in 0 until 16) {
                val ang = (i / 16.0) * PI * 2
                k.paint(Sh.capsule(xyz(sinD(ang) * rx * 1.15, y - 0.12, cosD(ang) * rz * 1.15), xyz(sinD(ang) * rx * 1.15, y + 0.14, cosD(ang) * rz * 1.15), 0.012), "#6cc794", soft = 0.008)
            }
        })
    },
    "cascabel" to { a ->
        val y = a.neck.y
        val rx = a.neck.rx
        val rz = a.neck.rz
        val collar = sculpted("collar:${anchorKey(a)}", "gloss", cell = 0.01, ground = null, aoReach = 0.03) { k ->
            ellipseRing(k, 0.0, y, 0.0, rx, rz, 0.035, "#ef5a5a")
        }
        val bell = sculpted("cascabel", "metal", cell = 0.004, ground = null, aoReach = 0.03) { k ->
            k.add(Sh.sphere(xyz(0.0, 0.0, 0.0), 0.08), "#f6c453", k = 0.0)
            k.add(Sh.torus(xyz(0.0, 0.08, 0.0), 0.025, 0.01, rot(PI / 2, 0.0, 0.0)), "#f6c453", k = 0.01)
            k.sub(Sh.box(xyz(0.0, -0.04, 0.06), xyz(0.06, 0.008, 0.05), 0.004), k = 0.004)
            k.sub(Sh.sphere(xyz(0.0, -0.04, 0.08), 0.018), k = 0.004)
        }
        bell.position.set(0f, (y - 0.09).toFloat(), (rz + 0.05).toFloat())
        Wear("body", group(collar, bell))
    },
)

private fun glasses(a: Anchors, dark: Boolean): Wear {
    val s = r2(a.hat.r / 0.5)
    val centers = ArrayList<V3>()
    val normals = ArrayList<V3>()
    for (side in intArrayOf(-1, 1)) {
        val at = surface(a.face.host, side * a.face.gap, a.face.pitch)
        centers.add(at.point.clone().addScaledVector(at.normal, 0.05 * s))
        normals.add(at.normal)
    }
    val ink = if (dark) "#3a2118" else C.navy
    val frame = sculpted("gafas:$dark:${anchorKey(a)}", "gloss", cell = 0.005 * s, ground = null, aoReach = 0.03) { k ->
        centers.forEachIndexed { i, c -> k.add(Sh.torus(c.toArray(), 0.105 * s, 0.017 * s, rot(Q().setFromUnitVectors(AXIS_Y, normals[i]))), ink, k = 0.0) }
        val mid = centers[0].clone().lerp(centers[1], 0.5).add(v(0.0, 0.03 * s, 0.01 * s))
        k.add(Sh.capsule(centers[0].clone().add(v(0.1 * s, 0.03 * s, 0.0)).toArray(), mid.toArray(), 0.013 * s), ink, k = 0.01)
        k.add(Sh.capsule(mid.toArray(), centers[1].clone().add(v(-0.1 * s, 0.03 * s, 0.0)).toArray(), 0.013 * s), ink, k = 0.01)
    }
    val lensMat = if (dark) {
        physical("#2b2240", 0.08f) {
            clearcoat = 1f
            transparent = true
            opacity = 0.88f
        }
    } else {
        physical("#eef7ff", 0.04f) {
            clearcoat = 1f
            transparent = true
            opacity = 0.18f
            depthWrite = false
        }
    }
    val g = group(frame)
    centers.forEachIndexed { i, c ->
        val lens = Mesh(BALL, lensMat)
        lens.scale.set((0.1 * s).toFloat(), (0.1 * s).toFloat(), (0.01 * s).toFloat())
        lens.place(c).turn(Q().setFromUnitVectors(AXIS_Z, normals[i]))
        g.add(lens)
    }
    return Wear("head", g)
}

private val MANNEQUIN = Anchors(
    hat = HatAnchor(0.45, 0.5),
    face = FaceAnchor(0.0, 0.0, 0.38, 0.5, Host(V3(), 0.5, 0.45, 0.47), 0.04),
    neck = NeckAnchor(0.0, 0.34, 0.3),
)

fun buildWear(itemId: String, anchors: Anchors = MANNEQUIN): Wear? = WEAR[itemId]?.invoke(anchors)

fun buildItem(itemId: String): Object3D? = BUILDERS[itemId]?.invoke() ?: buildDecor(itemId) ?: buildWear(itemId)?.obj

fun isWear(itemId: String): Boolean = itemId in WEAR

// Huevo: color de su ADN y motas del color secundario, pintadas sobre la
// cáscara. `tag` es el color de los trozos al romperse.
fun buildEgg(egg: EggLook): Group {
    val shell = Col.hsl(egg.hue / 360.0, min(0.9, (egg.sat + 2) / 100.0), min(0.88, (egg.light + 6) / 100.0))
    val spot = Col.hsl(egg.hue2 / 360.0, min(0.9, (egg.sat + 8) / 100.0), (egg.light - 4) / 100.0)
    val mesh = sculpted("egg:${egg.hue}:${egg.sat}:${egg.light}:${egg.hue2}", "gloss", cell = 0.016, groundK = 0.03) { s ->
        s.add(Sh.ellipsoid(xyz(0.0, 0.5, 0.0), xyz(0.34, 0.47, 0.34)), shell, k = 0.0)
        s.add(Sh.ellipsoid(xyz(0.0, 0.33, 0.0), xyz(0.37, 0.32, 0.37)), shell, k = 0.2)
        val rng = Rng((egg.hue * 131 + egg.hue2).toLong())
        for (i in 0 until 10) {
            val t = rng.range(0.55, 2.4)
            val yaw = rng.range(0.0, PI * 2)
            val y = 0.47 - cosD(t) * 0.46
            val r = 0.36 * sinD(t)
            s.paint(Sh.sphere(xyz(sinD(yaw) * r, y, cosD(yaw) * r), rng.range(0.04, 0.075)), spot, soft = 0.008)
        }
        // Una banda en zigzag alrededor, pegada a la cáscara.
        fun at(ang: Double, y: Double) = s.project(v(sinD(ang) * 0.45, y, cosD(ang) * 0.45), 3).point.toArray()
        for (i in 0 until 14) {
            val a0 = (i / 14.0) * PI * 2
            val a1 = ((i + 0.5) / 14) * PI * 2
            val a2 = ((i + 1) / 14.0) * PI * 2
            s.paint(Sh.capsule(at(a0, 0.6), at(a1, 0.53), 0.02), "#ffffff", soft = 0.005, amount = 0.8)
            s.paint(Sh.capsule(at(a1, 0.53), at(a2, 0.6), 0.02), "#ffffff", soft = 0.005, amount = 0.8)
        }
    }
    val g = group(mesh)
    g.tag = shell.toGl()
    return g
}

internal fun Vec3.toV3(): V3 = V3(x.toDouble(), y.toDouble(), z.toDouble())
