// Monstruos, como monster.js en la web: Pelusa es un zorro de fuego con
// cola de llama; Hoja, un dinosaurio con hojas por cresta y por cola;
// Gota, un anfibio con aletas. De crías son una bola con patitas; de
// jóvenes y adultos se ponen de pie, con garras, y ganan piezas según la
// crianza. Las cuentas van en Double y en el mismo orden que la web: la
// prueba de paridad compara los encargos de escultura uno a uno.

package com.nidaria.app.scene

import com.nidaria.app.gl.Color
import com.nidaria.app.gl.Geometry
import com.nidaria.app.gl.Group
import com.nidaria.app.gl.Material
import com.nidaria.app.gl.Mesh
import com.nidaria.app.gl.Object3D
import com.nidaria.app.gl.PI_F
import com.nidaria.app.gl.SkinnedMesh
import com.nidaria.app.gl.sphereGeometry
import com.nidaria.app.gl.torusGeometry
import com.nidaria.app.rules.Genes
import com.nidaria.app.rules.Rng
import com.nidaria.app.sculpt.AXIS_Y
import com.nidaria.app.sculpt.AXIS_Z
import com.nidaria.app.sculpt.Col
import com.nidaria.app.sculpt.Q
import com.nidaria.app.sculpt.Sculpt
import com.nidaria.app.sculpt.Sh
import com.nidaria.app.sculpt.Split
import com.nidaria.app.sculpt.V3
import com.nidaria.app.sculpt.catmullRom
import com.nidaria.app.sculpt.clampD
import com.nidaria.app.sculpt.cosD
import com.nidaria.app.sculpt.rot
import com.nidaria.app.sculpt.sinD
import com.nidaria.app.sculpt.surfacePatch
import com.nidaria.app.sculpt.v
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private val IRIS = mapOf(
    "pelusa" to listOf("#c23a26", "#2f63c9", "#d99a1c"),
    "hoja" to listOf("#c23a26", "#e0761a", "#d99a1c"),
    "gota" to listOf("#d99a1c", "#cf4a26", "#1f2f7a"),
)

// Colores con fuerza: el ADN da el tono; aquí se llevan a juguete vivo,
// con barriga clara, marcas oscuras y un acento que contrasta.
private class MonsterPalette(genes: Genes, plan: Plan) {
    val warm = plan.family == "pelusa"
    val s = clampD(genes.sat + (if (plan.family == "hoja") 0.0 else 10.0) + plan.toneSat, if (plan.family == "pelusa") 64.0 else 44.0, 86.0)
    val l = clampD(genes.light - (if (plan.family == "pelusa") 33.0 else 30.0) + plan.toneLight, 28.0, 46.0)
    val hue = genes.hue.toDouble()
    val body = hsl(hue, s, l)
    val dark = hsl(hue + (if (warm) -8 else 6), clampD(s - 10, 30.0, 80.0), l - 22)
    val light = hsl(if (warm) 40.0 else hue + 20, if (warm) 70.0 else 45.0, 80.0)
    val accent = hsl(genes.hue2.toDouble(), clampD(s + 6, 50.0, 92.0), clampD(l + 4, 42.0, 64.0))
    val flame = if (warm) hsl(hue + 28, 95.0, 60.0) else hsl(genes.hue2.toDouble(), 90.0, 62.0)
    val leaf = hsl(if (plan.family == "hoja") hue + 18 else 118.0, 62.0, clampD(l - 4, 30.0, 50.0))
    val ivory = Col.hex("#fff2dc")
    val claw = Col.hex("#f3e6d0")
    val horn = hsl(hue, 18.0, 22.0)
    val nose = hsl(hue, 30.0, 16.0)
    val iris = IRIS.getValue(plan.family)[genes.eyeStyle % 3]
}

// Base ortonormal con y = `up` y z lo más hacia `forward` posible.
internal fun basis(up: V3, forward: V3): Q {
    val yv = up.clone().normalize()
    val zv = forward.clone().addScaledVector(yv, -forward.dot(yv)).normalize()
    val xv = V3().crossVectors(yv, zv)
    return Q().setFromBasis(xv, yv, zv)
}

// Malla con esqueleto a partir de las posiciones de los huesos.
internal fun skin(geometry: Geometry, material: Material, bonePos: Array<V3>): Pair<SkinnedMesh, List<Group>> {
    val bones = List(BONES) { Group() }
    for ((i, bone) in bones.withIndex()) {
        val parent = PARENT[i]
        bone.place(bonePos[i])
        if (parent >= 0) {
            bone.position.sub(bonePos[parent].gl())
            bones[parent].add(bone)
        }
    }
    val mesh = SkinnedMesh(geometry, material, bones)
    mesh.castShadow = true
    mesh.receiveShadow = false
    mesh.add(bones[BODY])
    mesh.bind()
    return mesh to bones
}

private val NOSE_BALL = sphereGeometry(1f, 20, 14).keep()

fun buildMonster(formId: String, genes: Genes, detail: String = "high"): Built {
    val plan = planFor(formId, genes)
    val pal = MonsterPalette(genes, plan)
    val rng = Rng((genes.seed.toInt() xor 0x9e3779b9.toInt()).toLong() and 0xffffffffL)
    val cell = when (detail) {
        "high" -> 0.016
        "high-thumb" -> 0.018
        "tiny" -> 0.04
        else -> 0.026
    }
    val sock = if (plan.pattern == "calcetines") pal.accent else pal.body
    val fam = plan.family

    val S = Sculpt(ground = 0.0, groundK = 0.03)
    val bonePos = Array(BONES) { V3() }

    // ── Cuerpo ──
    val host: Host
    val bodyTop: Double
    val w = plan.bodyW
    val h = plan.bodyH
    val d = plan.bodyD
    val legH = plan.legs

    fun foot(p: V3, s: Double, bone: Int) {
        S.add(Sh.ellipsoid(doubleArrayOf(p.x, 0.05 * s, p.z), doubleArrayOf(0.085 * s, 0.055 * s, 0.12 * s)), sock, k = 0.04, bone = bone)
        for (t in intArrayOf(-1, 0, 1)) {
            val c = v(p.x + t * 0.04 * s, 0.025 * s, p.z + 0.1 * s - abs(t) * 0.015)
            S.add(Sh.cone(c.toArray(), c.clone().add(v(t * 0.008, -0.005, 0.035 * s)).toArray(), 0.018 * s, 0.005), pal.claw, k = 0.008, bone = bone, colorK = 0.004)
        }
    }

    if (plan.blob) {
        host = Host(v(0.0, h * 0.5, 0.0), w / 2, h / 2, d / 2)
        bonePos[HEAD].set(0.0, h * 0.42, 0.0)
        S.add(Sh.ellipsoid(doubleArrayOf(0.0, h * 0.5, 0.0), doubleArrayOf(w / 2, h / 2, d / 2)), pal.body, k = 0.0, bone = HEAD)
        S.add(Sh.ellipsoid(doubleArrayOf(0.0, h * 0.3, 0.0), doubleArrayOf(w * 0.52, h * 0.3, d * 0.5)), pal.body, k = 0.16, bone = HEAD)
        for (side in intArrayOf(-1, 1)) {
            foot(v(side * w * 0.24, 0.0, d * 0.3), 1.15, HEAD)
            S.add(Sh.ellipsoid(doubleArrayOf(side * w * 0.47, h * 0.4, d * 0.14), doubleArrayOf(0.07, 0.1, 0.075), rot(0.0, 0.0, side * 0.5)), sock, k = 0.05, bone = HEAD)
        }
        bodyTop = h
    } else {
        val ty = legH + h * 0.46
        bonePos[HEAD].set(0.0, legH + h * 0.9, 0.02)
        S.add(Sh.ellipsoid(doubleArrayOf(0.0, ty, 0.01), doubleArrayOf(w * 0.92, h * 0.54, d * 0.95)), pal.body, k = 0.0, bone = BODY)
        S.add(Sh.ellipsoid(doubleArrayOf(0.0, legH + h * 0.26, 0.0), doubleArrayOf(w * 1.0, h * 0.34, d * 1.0)), pal.body, k = 0.08, bone = BODY)
        for (side in intArrayOf(-1, 1)) {
            S.add(Sh.ellipsoid(doubleArrayOf(side * w * 0.58, legH * 0.95, 0.0), doubleArrayOf(0.11 + w * 0.12, legH * 0.66 + 0.05, 0.125)), pal.body, k = 0.06, bone = BODY)
            foot(v(side * w * 0.62, 0.0, 0.05), 1.18, BODY)
            val bone = if (side < 0) ARM_L else ARM_R
            val shoulder = v(side * w * 0.88, legH + h * 0.8, 0.04)
            val hand = v(side * w * 1.18, legH + h * 0.4, 0.14)
            bonePos[bone].copy(shoulder)
            S.add(Sh.cone(shoulder.toArray(), hand.toArray(), 0.07, 0.058), pal.body, k = 0.05, bone = bone)
            S.add(Sh.ellipsoid(hand.toArray(), doubleArrayOf(0.068, 0.072, 0.065)), if (fam == "pelusa") pal.dark else sock, k = 0.03, bone = bone)
            for (t in intArrayOf(-1, 0, 1)) {
                val c = hand.clone().add(v(side * 0.01 + t * 0.022, -0.045, 0.03))
                S.add(Sh.cone(c.toArray(), c.clone().add(v(t * 0.006, -0.03, 0.022)).toArray(), 0.014, 0.004), pal.claw, k = 0.008, bone = bone, colorK = 0.004)
            }
        }
        val center = v(0.0, legH + h + plan.headRy * 0.68, 0.03)
        host = Host(center, plan.headRx * 1.06, plan.headRy, plan.headRz)
        S.add(Sh.ellipsoid(host.center.toArray(), doubleArrayOf(host.rx, plan.headRy, plan.headRz)), pal.body, k = 0.045, bone = HEAD)
        bodyTop = legH + h
    }
    val unit = host.rx / 0.5
    val facePitch = if (plan.blob) 0.2 else 0.06

    fun onHost(yaw: Double, pitch: Double): V3 {
        val cp = cosD(pitch)
        return v(host.rx * sinD(yaw) * cp, host.ry * sinD(pitch), host.rz * cosD(yaw) * cp).add(host.center)
    }
    fun hostNormal(p: V3): V3 {
        val q = p.clone().sub(host.center)
        return v(q.x / (host.rx * host.rx), q.y / (host.ry * host.ry), q.z / (host.rz * host.rz)).normalize()
    }
    fun tube(points: List<V3>, r0: Double, r1: Double, color: Col, bone: Int, k: Double = 0.03, k0: Double = 0.05) {
        for (i in 0 until points.size - 1) {
            val a = r0 + (r1 - r0) * (i.toDouble() / (points.size - 1))
            val b = r0 + (r1 - r0) * ((i + 1).toDouble() / (points.size - 1))
            S.add(Sh.cone(points[i].toArray(), points[i + 1].toArray(), a, b), color, k = if (i == 0) k0 else k, bone = bone, colorK = 0.02)
        }
    }

    // ── Hocico ──
    var snoutTip: V3? = null
    if (plan.snout > 0.2) {
        val s = plan.snout * (if (plan.blob) 0.6 else 1.0)
        val fox = fam == "pelusa"
        val c = onHost(0.0, facePitch - (if (fox) 0.36 else 0.42))
        val len = host.rz * (if (fox) 0.55 else 0.34) * s
        val sc = c.clone().add(v(0.0, 0.0, len * 0.55))
        val sw = host.rx * (if (fox) 0.3 else 0.38)
        S.add(Sh.ellipsoid(sc.toArray(), doubleArrayOf(sw, host.ry * (if (fox) 0.2 else 0.24), host.rz * 0.18 + len * 0.6), rot(if (fox) 0.12 else 0.05, 0.0, 0.0)), pal.body, k = 0.07, bone = HEAD)
        snoutTip = sc.clone().add(v(0.0, host.ry * (if (fox) 0.06 else 0.1), host.rz * 0.18 + len * 0.6))
    }

    // ── Orejas ──
    val earSize = 0.2 * unit * min(plan.earSize, 1.4)
    val droop = plan.earDroop
    for (side in intArrayOf(-1, 1)) {
        val bone = if (side < 0) EAR_L else EAR_R
        val type = plan.earType
        if (type == "fox") {
            val base = onHost(side * 0.7, 0.7 - droop * 0.3)
            val dir = v(side * (0.62 + droop * 0.6), 1 - droop * 0.5, -0.15).add(hostNormal(base).multiplyScalar(0.2)).normalize()
            val q = basis(dir, v(side * 0.25, 0.0, 1.0))
            val len = earSize * 2.1
            val start = base.clone().addScaledVector(dir, -len * 0.42)
            S.add(Sh.leaf(start.toArray(), rot(q), len * 1.55, len * 0.86, 0.07 * unit, -0.06), pal.body, k = 0.04, bone = bone)
            val inner = start.clone().addScaledVector(dir, len * 0.2).add(v(0.0, 0.0, 0.03 * unit).applyQuaternion(q))
            S.paint(Sh.leaf(inner.toArray(), rot(q), len * 1.1, len * 0.55, 0.04 * unit, -0.05), pal.dark, soft = 0.01)
            S.paint(Sh.sphere(start.clone().addScaledVector(dir, len * 1.45).toArray(), len * 0.32), pal.dark, soft = 0.02)
            bonePos[bone].copy(base)
        } else if (type == "leaf") {
            val s = earSize * 0.9
            val base = onHost(side * 1.05, 0.55 - droop * 0.25)
            val dir = hostNormal(base).multiplyScalar(1.2).add(v(0.0, 0.8, -0.3)).normalize().applyAxisAngle(AXIS_Z, -side * droop * 1.0)
            val q = quatFromTo(AXIS_Y, dir).multiply(Q().setFromAxisAngle(AXIS_Y, -side * 0.6))
            S.add(Sh.leaf(base.clone().addScaledVector(dir, -s * 0.2).toArray(), rot(q), s * 2.6, s * 1.15, 0.055 * unit, 0.22), pal.leaf, k = 0.035, bone = bone, colorK = 0.03)
            S.paint(Sh.capsule(base.toArray(), base.clone().addScaledVector(dir, s * 2.1).toArray(), 0.012 * unit), pal.dark, soft = 0.006, amount = 0.6)
            bonePos[bone].copy(base)
        } else if (plan.blob && type == "fin") {
            // Branquias de ajolote en las crías de agua.
            val s = earSize
            val base = onHost(side * 1.16, 0.4 - droop * 0.15)
            val out = hostNormal(base)
            for (f in 0 until 3) {
                val dir = out.clone().add(v(0.0, 0.8 - f * 0.68, 0.2)).normalize().applyAxisAngle(AXIS_Z, -side * droop * 0.6)
                val len = s * (1.05 - abs(f - 1) * 0.14)
                val root = base.clone().add(v(0.0, (1 - f) * s * 0.4, 0.0)).addScaledVector(out, -s * 0.12)
                S.add(Sh.cone(root.toArray(), root.clone().addScaledVector(dir, len).toArray(), s * 0.17, s * 0.27), pal.accent, k = 0.05, bone = bone, colorK = 0.035)
            }
            bonePos[bone].copy(base)
        } else {
            // Aletas de agua: hacia atrás y hacia fuera, con radios.
            val s = earSize * 0.8
            val base = onHost(side * 1.3, 0.3 - droop * 0.2)
            val dir = v(side * 0.7, 0.45 - droop * 0.6, -0.75).normalize()
            val q = basis(dir, v(side * 0.4, 1.0, 0.2))
            S.add(Sh.leaf(base.clone().addScaledVector(dir, -s * 0.3).toArray(), rot(q), s * 2.4, s * 1.3, 0.04 * unit, 0.15), pal.accent, k = 0.035, bone = bone, colorK = 0.03)
            for (t in doubleArrayOf(-0.25, 0.0, 0.25)) {
                val a = base.clone().addScaledVector(dir, s * 0.3)
                S.paint(Sh.capsule(a.toArray(), a.clone().addScaledVector(dir, s * 1.5).add(v(0.0, t * s, 0.0)).toArray(), 0.01 * unit), pal.dark, soft = 0.006, amount = 0.5)
            }
            bonePos[bone].copy(base)
        }
    }

    // ── Remate de la cabeza ──
    val crown = onHost(0.0, PI / 2 - 0.12)
    val ts = plan.topSize * unit
    if (plan.topType == "flame") {
        // Tupé de llama: tres lenguas gruesas que salen de la frente y se
        // curvan hacia delante y arriba.
        for (spec in arrayOf(doubleArrayOf(0.0, 1.0, 0.0, 0.0), doubleArrayOf(-0.1, 0.62, -0.7, 0.12), doubleArrayOf(0.1, 0.66, 0.7, 0.12))) {
            val x = spec[0]
            val len = spec[1]
            val lean = spec[2]
            val back = spec[3]
            val a = onHost(x * 2.2, 1.0 - back)
            val pts = catmullRom(listOf(
                a.clone().add(v(0.0, -0.04 * ts, -0.06 * ts)),
                a.clone().add(v(lean * 0.05 * ts, 0.16 * len * ts, 0.0)),
                a.clone().add(v(lean * 0.1 * ts, 0.3 * len * ts, 0.06 * ts)),
                a.clone().add(v(lean * 0.12 * ts, 0.36 * len * ts, 0.16 * ts)),
            ), 8)
            tube(pts, 0.11 * ts, 0.01 * ts, pal.body, HEAD, 0.025, 0.08)
            S.paint(Sh.sphere(pts.last().toArray(), 0.13 * ts * len), pal.flame, soft = 0.05)
        }
    } else if (plan.topType == "leaf" && !plan.blob) {
        // Cresta: dos hojas que nacen en la frente y se curvan hacia atrás.
        for (side in intArrayOf(-1, 1)) {
            val a = onHost(side * 0.18, 0.95)
            val dir = v(side * 0.45, 0.75, -0.5).normalize()
            val q = basis(dir, v(side * 0.6, 0.4, 0.6))
            S.add(Sh.leaf(a.clone().addScaledVector(dir, -0.04 * ts).toArray(), rot(q), 0.5 * ts, 0.26 * ts, 0.045 * ts, -0.25), pal.leaf, k = 0.04, bone = HEAD, colorK = 0.03)
            S.paint(Sh.capsule(a.toArray(), a.clone().addScaledVector(dir, 0.42 * ts).toArray(), 0.011 * ts), pal.dark, soft = 0.006, amount = 0.5)
        }
    } else if (plan.topType == "fin" && !plan.blob) {
        // Aleta dorsal de la frente a la nuca.
        val a = onHost(0.0, 0.95)
        val q = basis(v(0.0, 0.7, -0.7), v(1.0, 0.0, 0.0))
        S.add(Sh.leaf(a.clone().add(v(0.0, -0.06 * ts, 0.04 * ts)).toArray(), rot(q), 0.55 * ts, 0.28 * ts, 0.04 * ts, 0.2), pal.accent, k = 0.04, bone = HEAD, colorK = 0.03)
    } else if (plan.topType == "fin") {
        S.add(Sh.cone(crown.clone().add(v(0.0, -0.02 * ts, 0.0)).toArray(), crown.clone().add(v(0.0, 0.24 * ts, -0.04 * ts)).toArray(), 0.1 * ts, 0.02 * ts), pal.accent, k = 0.06, bone = HEAD, colorK = 0.025)
    }
    if (plan.spikes > 0) {
        // Pelo o púas de punta: en la coronilla y por la espalda.
        for (spec in arrayOf(doubleArrayOf(-0.14, 0.45, 0.2), doubleArrayOf(0.0, 0.0, 0.28), doubleArrayOf(0.14, -0.45, 0.2))) {
            val base = onHost(spec[0] * 2, PI / 2 - 0.45)
            val dir = v(-sinD(spec[1]) * 0.6, 0.7, -0.7).normalize()
            S.add(Sh.cone(base.toArray(), base.clone().addScaledVector(dir, spec[2] * unit * plan.spikes).toArray(), 0.06 * unit, 0.008), if (fam == "hoja") pal.ivory else pal.dark, k = 0.03, bone = HEAD, colorK = 0.02)
        }
        if (!plan.blob) {
            for (i in 0 until 4) {
                val y = legH + h * (0.85 - i * 0.2)
                val base = v(0.0, y, -d * 0.92)
                val dir = v(0.0, 0.35, -1.0).normalize()
                S.add(Sh.cone(base.toArray(), base.clone().addScaledVector(dir, (0.14 - i * 0.02) * plan.spikes).toArray(), 0.05, 0.006), if (fam == "hoja") pal.ivory else pal.dark, k = 0.025, bone = BODY, colorK = 0.02)
            }
        }
    }
    if (fam == "hoja" && !plan.blob) {
        // Placas de hoja por el lomo.
        for (i in 0 until 3) {
            val y = legH + h * (0.8 - i * 0.25)
            val base = v(0.0, y, -d * 0.85)
            val dir = v(0.0, 0.6, -1.0).normalize()
            val q = quatFromTo(AXIS_Y, dir).multiply(Q().setFromAxisAngle(AXIS_Y, PI / 2))
            S.add(Sh.leaf(base.clone().addScaledVector(dir, -0.04).toArray(), rot(q), 0.2 - i * 0.03, 0.12, 0.025, 0.2), pal.leaf, k = 0.03, bone = BODY, colorK = 0.02)
        }
    }
    if (fam == "pelusa" && !plan.blob) {
        // Mechones de las mejillas.
        for (side in intArrayOf(-1, 1)) {
            for (i in 0 until 2) {
                val a = onHost(side * (1.3 + i * 0.2), -0.25 - i * 0.18)
                val dir = v(side * 0.9, -0.15 + i * 0.1, -0.4).normalize()
                S.add(Sh.cone(a.toArray(), a.clone().addScaledVector(dir, (0.2 - i * 0.05) * unit).toArray(), 0.1 * unit, 0.02 * unit), pal.body, k = 0.05, bone = HEAD, colorK = 0.02)
            }
        }
    }

    // ── Cola ──
    val tsz = plan.tailSize
    val tailBase = if (plan.blob) v(0.0, h * 0.24, -d * 0.42) else v(0.0, legH + h * 0.1, -d * 0.82)
    bonePos[TAIL].copy(tailBase)
    when (plan.tailType) {
        "flame" -> {
            // Cola tupida que sube y acaba en llama.
            val k = if (plan.blob) 0.7 else 1.0
            val pts = catmullRom(listOf(tailBase, tailBase.clone().add(v(0.16 * k, 0.02 * k, -0.12 * k)), tailBase.clone().add(v(0.32 * k * tsz, 0.18 * k * tsz, -0.08 * k)), tailBase.clone().add(v(0.36 * k * tsz, 0.42 * k * tsz, 0.02 * k))), 10)
            tube(pts, 0.06 * k, 0.12 * k * tsz, pal.body, TAIL, 0.03, 0.05)
            val tip = pts.last()
            S.add(Sh.cone(tip.toArray(), tip.clone().add(v(-0.02 * k, 0.14 * k * tsz, 0.04 * k)).toArray(), 0.1 * k * tsz, 0.01), pal.flame, k = 0.04, bone = TAIL, colorK = 0.03)
            S.paint(Sh.sphere(tip.toArray(), 0.12 * k * tsz), pal.flame, soft = 0.03)
        }
        "leaf" -> {
            val k = if (plan.blob) 0.6 else 1.0
            val pts = catmullRom(listOf(tailBase, tailBase.clone().add(v(0.14 * k, -0.06 * k, -0.12 * k)), tailBase.clone().add(v(0.3 * k * tsz, -0.06 * k, -0.08 * k * tsz)), tailBase.clone().add(v(0.42 * k * tsz, 0.04 * k, 0.04 * k * tsz))), 8)
            tube(pts, 0.085 * k, 0.03 * k, pal.body, TAIL)
            val tip = pts.last()
            S.add(Sh.leaf(tip.toArray(), rot(quatFromTo(AXIS_Y, v(0.5, 0.8, 0.1).normalize())), 0.3 * k * tsz, 0.18 * k * tsz, 0.035 * k, 0.2), pal.leaf, k = 0.03, bone = TAIL, colorK = 0.02)
        }
        "fish" -> {
            val k = if (plan.blob) 0.7 else 1.0
            val pts = catmullRom(listOf(tailBase, tailBase.clone().add(v(0.12 * k, -0.04, -0.12 * k)), tailBase.clone().add(v(0.28 * k * tsz, 0.0, -0.1 * k * tsz))), 6)
            tube(pts, 0.08 * k, 0.035 * k, pal.body, TAIL)
            val tip = pts.last()
            for (side in intArrayOf(-1, 1)) {
                val dir = v(0.4, side * 0.75, -0.5).normalize()
                S.add(Sh.leaf(tip.toArray(), rot(basis(dir, v(1.0, 0.0, 0.4))), 0.24 * k * tsz, 0.15 * k * tsz, 0.03 * k, 0.15), pal.accent, k = 0.03, bone = TAIL, colorK = 0.02)
            }
        }
    }

    // ── Piezas de adulta fundidas con el cuerpo ──
    if (plan.extra == "mane") {
        // Gorguera por los hombros y la espalda; por delante queda libre la cara.
        val ny = bodyTop - 0.04
        for (i in 0 until 11) {
            val a = PI * 0.42 + (i / 10.0) * PI * 1.16
            val r = w * 0.86
            val base = v(sinD(a) * r, ny, cosD(a) * r * 0.95)
            val out = v(sinD(a), if (fam == "pelusa") 0.25 else -0.25, cosD(a) - 0.2).normalize()
            if (fam == "pelusa") {
                val tip = base.clone().addScaledVector(out, 0.2)
                S.add(Sh.cone(base.toArray(), tip.toArray(), 0.085, 0.012), pal.body, k = 0.04, bone = BODY, colorK = 0.02)
                S.paint(Sh.sphere(tip.toArray(), 0.07), pal.flame, soft = 0.03)
            } else {
                S.add(Sh.leaf(base.clone().addScaledVector(out, -0.03).toArray(), rot(quatFromTo(AXIS_Y, out)), 0.26, 0.16, 0.03, -0.12), if (i % 2 == 1) pal.accent else if (fam == "hoja") pal.leaf else pal.dark, k = 0.025, bone = BODY, colorK = 0.02)
            }
        }
    }

    // ── Dibujo del ADN ──
    if (plan.belly) {
        val c = if (plan.blob) doubleArrayOf(0.0, h * 0.3, d * 0.42) else doubleArrayOf(0.0, legH + h * 0.45, d * 0.62)
        val r = if (plan.blob) doubleArrayOf(w * 0.3, h * 0.22, d * 0.2) else doubleArrayOf(w * 0.62, h * 0.4, d * 0.5)
        S.paint(Sh.ellipsoid(c, r), pal.light, soft = 0.01)
        if (snoutTip != null) S.paint(Sh.ellipsoid(onHost(0.0, facePitch - 0.42).add(v(0.0, -0.02 * unit, 0.08 * unit)).toArray(), doubleArrayOf(host.rx * 0.4, host.ry * 0.26, host.rz * 0.5)), pal.light, soft = 0.012)
    }
    if (plan.pattern == "motas") {
        val count = 4 + Math.round(plan.patternAmt * 6).toInt()
        for (i in 0 until count) {
            val size = rng.range(0.045, 0.075) * unit
            val yaw = rng.range(0.9, 2.4) * (if (rng.chance(0.5)) 1 else -1)
            S.paint(Sh.sphere(onHost(yaw, rng.range(-0.1, 1.05)).toArray(), size), pal.dark, soft = 0.006)
        }
    } else if (plan.pattern == "rayas") {
        // Tres rayas en la frente que bajan por la nuca.
        val width = 0.03 * unit * (0.7 + plan.patternAmt * 0.5)
        for (yaw in doubleArrayOf(-0.3, 0.0, 0.3)) {
            var prev: V3? = null
            for (i in 0..6) {
                val pitch = (if (plan.blob) 0.82 else 0.66) + i * 0.22
                val p = onHost(if (pitch > PI / 2) yaw + PI else yaw, if (pitch > PI / 2) PI - pitch else pitch)
                if (prev != null) S.paint(Sh.capsule(prev.toArray(), p.toArray(), width * (1 - i * 0.06)), pal.dark, soft = 0.006)
                prev = p
            }
        }
        if (!plan.blob) {
            for (i in 0 until 3) {
                val y = legH + h * (0.75 - i * 0.2)
                S.paint(Sh.torus(doubleArrayOf(0.0, y, -d * 0.2), w * 0.75, 0.025, rot(0.2, 0.0, 0.0)), pal.dark, soft = 0.006)
            }
        }
    }

    // ── Malla y huesos ──
    val finish = if (plan.finish == "plush" || plan.finish == "gloss") plan.finish else "matte"
    val bodyGeometry = S.build(cell = cell, bones = BONES, aoReach = 0.16, ao = if (detail == "tiny") 0.0 else 1.0)
    val (mesh, bones) = skin(bodyGeometry, clay(finish), bonePos)

    val root = Group()
    val bounce = Group()
    val squash = Group()
    root.add(bounce)
    bounce.add(squash)
    squash.add(mesh)
    root.scale.setScalar(plan.scale.toFloat())
    val headPivot = bones[HEAD]
    val headBase = bonePos[HEAD]
    fun toHead(obj: Object3D): Object3D {
        obj.position.sub(headBase.gl())
        headPivot.add(obj)
        return obj
    }
    fun decal(center: V3, normal: V3, width: Double, height: Double, material: Material): Mesh {
        val geometry = surfacePatch(S, center, normal, AXIS_Y, width, height, 8, 0.0018 * unit)
        geometry.translate(-headBase.x.toFloat(), -headBase.y.toFloat(), -headBase.z.toFloat())
        val m = Mesh(geometry, material)
        headPivot.add(m)
        return m
    }

    // Ojos grandes con iris; los de las crías, aún más.
    val gap = plan.eyeGap * (if (plan.blob) 1.05 else 1.1)
    val eyeR = (if (plan.blob) 0.09 else 0.1) * unit * plan.eyeSize
    // Las de pie miran con decisión: el párpado tapa un poco el ojo.
    val restLid = if (plan.blob) plan.eyeLids else max(plan.eyeLids, 0.14)
    val content = plan.eyeLids >= 0.35 && plan.topType == "cherry"
    val eyes = ArrayList<Eye>()
    for (side in intArrayOf(-1, 1)) {
        val at = S.project(onHost(side * gap, facePitch))
        val skinColor = S.colorNear(at.point.clone().add(v(0.0, eyeR * 1.3, 0.0)))
        val eye = makeEye(at.point, at.normal, eyeR, if (plan.blob) 1.12 else 1.22, iris = pal.iris, kind = if (plan.blob) "round" else "sclera", skin = skinColor, tilt = -side * (if (plan.blob) 0.04 else 0.12), sparkle = plan.eyeStyle == 2)
        toHead(eye.group)
        if (plan.brows) eye.lid.rotation.z = side * 0.36f
        val closed = decal(at.point, at.normal, eyeR * 3.2, eyeR * 2.4, decalMaterial(lidTexture("sleep")))
        closed.visible = content
        eye.open.visible = !content
        val rest = if (plan.brows) max(restLid, 0.22) else restLid
        eyes.add(Eye(eye.group, eye.open, closed, side, eye.r, eye.tall, content, rest.toFloat(), eye.lid, eye.bead))
    }

    // Boca, en el hocico si lo tiene.
    val tip = snoutTip
    val mouthAt = if (tip != null) S.project(tip.clone().add(v(0.0, -host.ry * 0.2, 0.0))) else S.project(onHost(0.0, facePitch - (if (plan.blob) 0.26 else 0.3)))
    val mouthW = (if (tip != null) 0.2 else 0.24) * unit
    val mouth = decal(mouthAt.point, mouthAt.normal, mouthW, mouthW * 0.625, decalMaterial(mouthTexture("smile", plan.fang)))
    val mouths = listOf("smile", "sad", "open", "flat").associateWith { mouthTexture(it, plan.fang) }

    // Nariz del zorro.
    if (tip != null && fam == "pelusa") {
        val at = S.project(tip)
        val nose = Mesh(NOSE_BALL, physical("#ffffff", 0.25f) {
            color.copy(pal.nose.toGl())
            clearcoat = 1f
            clearcoatRoughness = 0.1f
        })
        nose.scale.set((0.034 * unit).toFloat(), (0.024 * unit).toFloat(), (0.022 * unit).toFloat())
        nose.place(at.point.clone().addScaledVector(at.normal, 0.006 * unit)).turn(faceFrame(at.normal))
        toHead(nose)
    }

    // Coloretes: discretos.
    val cheeks = ArrayList<Mesh>()
    for (side in intArrayOf(-1, 1)) {
        val at = S.project(onHost(side * (gap + 0.32), facePitch - 0.2))
        val material = decalMaterial(blushTexture()) {
            color.setHex("#ff8fa9")
            opacity = 0.3f
        }
        cheeks.add(decal(at.point, at.normal, 0.18 * unit * plan.cheeks, 0.11 * unit * plan.cheeks, material))
    }

    if (plan.brows) {
        for (side in intArrayOf(-1, 1)) {
            val at = S.project(onHost(side * gap * 0.92, facePitch + 0.33))
            decal(at.point, at.normal, eyeR * 2.8, eyeR * 1.4, decalMaterial(browTexture(side)))
        }
    }

    // ── Piezas aparte ──
    fun partMesh(material: Material, make: () -> Geometry): Mesh {
        val m = Mesh(make(), material)
        m.castShadow = true
        m.receiveShadow = true
        return m
    }

    var top: Object3D = Group()
    when (plan.topType) {
        "sprout" -> {
            top = partMesh(clay("matte")) {
                val p = Sculpt()
                val base = crown.clone().add(v(0.0, -0.04 * ts, 0.0))
                val tipS = crown.clone().add(v(0.0, 0.17 * ts, 0.01 * ts))
                p.add(Sh.capsule(base.toArray(), tipS.toArray(), 0.024 * ts), pal.leaf, k = 0.0)
                for (side in intArrayOf(-1, 1)) {
                    val dir = v(side * 0.95, 0.55, 0.12).normalize()
                    p.add(Sh.leaf(tipS.toArray(), rot(quatFromTo(AXIS_Y, dir).multiply(Q().setFromAxisAngle(AXIS_Y, side * 0.25))), 0.3 * ts, 0.17 * ts, 0.028 * ts, 0.18), pal.leaf, k = 0.02)
                }
                p.build(cell = 0.007 * ts, aoReach = 0.05 * ts)
            }
            toHead(top)
        }
        "cherry" -> {
            val g = Group()
            g.add(partMesh(clay("gloss")) {
                val p = Sculpt()
                val c = crown.clone().add(v(0.0, -0.01 * ts, 0.0))
                p.add(Sh.ellipsoid(c.toArray(), doubleArrayOf(0.13 * ts, 0.07 * ts, 0.13 * ts)), pal.ivory, k = 0.0)
                p.add(Sh.ellipsoid(c.clone().add(v(0.0, 0.08 * ts, 0.0)).toArray(), doubleArrayOf(0.09 * ts, 0.06 * ts, 0.09 * ts)), pal.ivory, k = 0.03)
                p.add(Sh.cone(c.clone().add(v(0.0, 0.12 * ts, 0.0)).toArray(), c.clone().add(v(0.02 * ts, 0.2 * ts, 0.0)).toArray(), 0.05 * ts, 0.01 * ts), pal.ivory, k = 0.03)
                p.build(cell = 0.008 * ts, aoReach = 0.06 * ts)
            })
            g.add(partMesh(clay("glaze")) {
                val p = Sculpt()
                val c = crown.clone().add(v(0.05 * ts, 0.25 * ts, 0.03 * ts))
                p.add(Sh.sphere(c.toArray(), 0.072 * ts), "#d63a4f", k = 0.0)
                p.sub(Sh.sphere(c.clone().add(v(0.0, 0.075 * ts, 0.0)).toArray(), 0.02 * ts), k = 0.02)
                p.add(Sh.capsule(c.clone().add(v(0.0, 0.04 * ts, 0.0)).toArray(), c.clone().add(v(0.05 * ts, 0.17 * ts, -0.01 * ts)).toArray(), 0.008 * ts), "#6b4632", k = 0.01)
                p.build(cell = 0.006 * ts, aoReach = 0.04 * ts)
            })
            top = g
            toHead(top)
        }
        "moon" -> {
            val moonMat = Material.standard(Color.hex("#fff0b8"), 0.4f) {
                emissive.setHex("#ffd76e")
                emissiveIntensity = 0.75f
            }
            top = partMesh(moonMat) {
                val p = Sculpt()
                val c = crown.clone().add(v(0.0, 0.16 * ts, 0.0))
                p.add(Sh.ellipsoid(c.toArray(), doubleArrayOf(0.13 * ts, 0.13 * ts, 0.05 * ts)), "#fff0b8", k = 0.0)
                p.sub(Sh.ellipsoid(c.clone().add(v(0.06 * ts, 0.05 * ts, 0.0)).toArray(), doubleArrayOf(0.115 * ts, 0.115 * ts, 0.08 * ts)), k = 0.01)
                p.build(cell = 0.006 * ts, ao = 0.0)
            }
            toHead(top)
        }
    }

    // Alas: de plumas encendidas, de hoja o de aleta. Las dos comparten la
    // malla (en la web, `once` por nombre).
    val wings = ArrayList<Limb>()
    if (plan.extra == "wings") {
        val wingGeometry = run {
            val p = Sculpt()
            when (fam) {
                "pelusa" -> for (i in 0 until 4) {
                    val a = 0.15 - i * 0.32
                    val len = 0.5 - i * 0.07
                    val dir = v(sinD(-a + 0.6) * 0.3, cosD(a), 0.0).normalize()
                    p.add(Sh.cone(doubleArrayOf(0.0, 0.0, 0.0), dir.clone().multiplyScalar(len).toArray(), 0.06, 0.012), if (i == 0) pal.flame else if (i % 2 == 1) pal.body else pal.light, k = 0.025)
                }
                "hoja" -> {
                    p.add(Sh.leaf(doubleArrayOf(0.0, 0.0, 0.0), rot(0.0, 0.0, -0.2), 0.68, 0.36, 0.035, 0.12), pal.leaf, k = 0.0)
                    p.paint(Sh.capsule(doubleArrayOf(0.0, 0.02, 0.0), doubleArrayOf(0.03, 0.44, 0.03), 0.008), pal.dark, soft = 0.006)
                }
                else -> for (i in 0 until 3) {
                    val a = 0.25 - i * 0.42
                    p.add(Sh.leaf(doubleArrayOf(0.0, 0.0, 0.0), rot(0.0, 0.0, a), 0.58 - i * 0.08, 0.26, 0.03, 0.1), pal.accent, k = 0.04)
                }
            }
            p.build(cell = 0.009, aoReach = 0.06)
        }
        for (side in intArrayOf(-1, 1)) {
            val pivot = Group()
            pivot.position.set((side * w * 0.62).toFloat(), (if (plan.blob) h * 0.72 else legH + h * 0.86).toFloat(), (if (plan.blob) -d * 0.3 else -d * 0.62).toFloat())
            val wing = partMesh(clay(if (fam == "pelusa") "plush" else finish)) { wingGeometry }
            wing.scale.x = side.toFloat()
            pivot.add(wing)
            pivot.rotation.set(0f, side * -0.55f, side * -1.05f)
            bones[BODY].add(pivot)
            wings.add(Limb(pivot, side, side * -1.05f))
        }
    }

    // Corona: cresta de llama, flores o perlas.
    if (plan.extra == "crown") {
        val ringFinish = when (fam) {
            "gota" -> "glaze"
            "pelusa" -> "plush"
            else -> "matte"
        }
        val ring = partMesh(clay(ringFinish)) {
            val p = Sculpt()
            val count = 7
            for (i in 0 until count) {
                val angle = (i / count.toDouble()) * PI * 2
                val at = S.project(onHost(angle, 1.0))
                val c = at.point.clone().addScaledVector(at.normal, 0.03 * unit)
                if (fam == "hoja") {
                    val q = faceFrame(at.normal, AXIS_Z)
                    for (k in 0 until 5) {
                        val a = (k / 5.0) * PI * 2
                        val offset = v(cosD(a) * 0.045 * unit, sinD(a) * 0.045 * unit, 0.0).applyQuaternion(q)
                        p.add(Sh.ellipsoid(c.clone().add(offset).toArray(), doubleArrayOf(0.04 * unit, 0.028 * unit, 0.018 * unit), rot(q.clone().multiply(Q().setFromAxisAngle(AXIS_Z, a)))), pal.accent, k = 0.01)
                    }
                    p.add(Sh.sphere(c.clone().addScaledVector(at.normal, 0.01 * unit).toArray(), 0.024 * unit), "#ffd166", k = 0.006)
                } else if (fam == "pelusa") {
                    p.add(Sh.cone(c.toArray(), c.clone().addScaledVector(at.normal, 0.05 * unit).add(v(0.0, 0.26 * unit, 0.0)).toArray(), 0.06 * unit, 0.006), if (i % 2 == 1) pal.flame else pal.accent, k = 0.01)
                } else {
                    p.add(Sh.sphere(c.toArray(), 0.07 * unit), pal.ivory, k = 0.0)
                }
            }
            p.build(cell = 0.008 * unit, aoReach = 0.05)
        }
        toHead(ring)
    }

    // Cuernos curvos.
    if (plan.extra == "horns") {
        toHead(partMesh(clay("gloss")) {
            val p = Sculpt()
            for (side in intArrayOf(-1, 1)) {
                val base = onHost(side * 0.55, 1.0)
                val pts = catmullRom(listOf(base.clone().add(v(0.0, -0.04 * unit, 0.0)), base.clone().add(v(side * 0.06 * unit, 0.14 * unit, -0.04 * unit)), base.clone().add(v(side * 0.16 * unit, 0.22 * unit, -0.12 * unit)), base.clone().add(v(side * 0.22 * unit, 0.2 * unit, -0.2 * unit))), 8)
                for (i in 0 until pts.size - 1) {
                    val a = 0.07 * unit * (1 - i.toDouble() / pts.size)
                    val b = 0.07 * unit * (1 - (i + 1).toDouble() / pts.size) + 0.004
                    p.add(Sh.cone(pts[i].toArray(), pts[i + 1].toArray(), a, b), if (i < 3) pal.horn else pal.ivory, k = 0.01)
                }
            }
            p.build(cell = 0.007 * unit, aoReach = 0.05)
        })
    }

    var halo: Mesh? = null
    var haloY = 0f
    if (plan.extra == "halo") {
        val u = unit.toFloat()
        val ring = Mesh(torusGeometry(0.22f * u, 0.026f * u, 16, 48), Material.standard(Color.hex("#ffe08a"), 0.3f) {
            metalness = 0.2f
            emissive.setHex("#ffcf5c")
            emissiveIntensity = 0.9f
        })
        ring.rotation.set(PI_F / 2f - 0.22f, 0f, 0.1f)
        ring.position.set(0f, (host.center.y + host.ry + 0.36 * unit).toFloat(), (-0.03 * unit).toFloat())
        toHead(ring)
        haloY = ring.position.y
        halo = ring
    }

    // ── Anclajes para la ropa ──
    val hostLocal = Host(host.center.clone().sub(headBase), host.rx, host.ry, host.rz)
    val anchors = Anchors(
        hat = HatAnchor(hostLocal.center.y + host.ry, host.rx),
        face = FaceAnchor(hostLocal.center.y + host.ry * sinD(facePitch), host.rz, gap, host.rx, hostLocal, facePitch),
        neck = if (plan.blob) neckOf(S, plan, host) else bipedNeck(S, bodyTop),
    )
    val height = ((host.center.y + host.ry) * plan.scale).toFloat()
    // La habitación encuadra con el ancho del cuerpo: en las de pie, el doble del radio.
    val look = Look(fam, plan.stage, plan.scale, plan.blob, if (plan.blob) w else w * 2, plan.headRx)

    val rig = Rig(
        root, bounce, squash, headPivot, eyes, mouths, mouth, cheeks,
        ears = listOf(Limb(bones[EAR_L], -1, 0f), Limb(bones[EAR_R], 1, 0f)),
        arms = if (plan.arms) listOf(Limb(bones[ARM_L], -1, 0f), Limb(bones[ARM_R], 1, 0f)) else emptyList(),
        tail = bones[TAIL],
        wings = wings,
        top = top,
        halo = halo,
        haloY = haloY,
        mesh = mesh,
        blushRest = 0.22f,
        blushHappy = 0.42f,
    )
    return Built(root, look, height, anchors, rig)
}

// Cuello de las de pie: justo donde el cuerpo se junta con la cabeza.
private fun bipedNeck(S: Sculpt, y: Double): NeckAnchor {
    val at = y - 0.03
    var r = 0.2
    var t = 0.05
    while (t < 0.6) {
        if (S.sdf(t, at, 0.0) > 0) { r = t; break }
        t += 0.01
    }
    var rz = 0.2
    t = 0.05
    while (t < 0.6) {
        if (S.sdf(0.0, at, t) > 0) { rz = t; break }
        t += 0.01
    }
    return NeckAnchor(at, r + 0.012, rz + 0.012)
}
