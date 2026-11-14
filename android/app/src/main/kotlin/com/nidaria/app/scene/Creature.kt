// Construye la criatura 3D a partir de su plano y la anima, como
// creature.js en la web. El cuerpo se esculpe como si fuera plastilina
// (ver sculpt/): cabeza, cuerpo, orejas, patas y cola se funden en una
// sola malla lisa con sus colores pintados y sus sombras de pliegue. Unos
// huesos la mueven (cabeza, orejas, brazos y cola) y la cara va pintada
// encima: ojos de cuenta brillante, boca, coloretes y cejas.
//
// Las cuentas van en Double y con las mismas operaciones que la web: la
// prueba de paridad compara los encargos de escultura uno a uno.

package com.nidaria.app.scene

import com.nidaria.app.gl.Animator
import com.nidaria.app.gl.Color
import com.nidaria.app.gl.Eases
import com.nidaria.app.gl.Group
import com.nidaria.app.gl.Material
import com.nidaria.app.gl.Mesh
import com.nidaria.app.gl.Object3D
import com.nidaria.app.gl.PI_F
import com.nidaria.app.gl.Pos
import com.nidaria.app.gl.Quat
import com.nidaria.app.gl.SkinnedMesh
import com.nidaria.app.gl.Texture
import com.nidaria.app.gl.Timeline
import com.nidaria.app.gl.Vec2
import com.nidaria.app.gl.Vec3
import com.nidaria.app.gl.clampF
import com.nidaria.app.gl.sphereGeometry
import com.nidaria.app.gl.to
import com.nidaria.app.gl.torusGeometry
import com.nidaria.app.rules.Genes
import com.nidaria.app.rules.Rng
import com.nidaria.app.sculpt.AXIS_Y
import com.nidaria.app.sculpt.AXIS_Z
import com.nidaria.app.sculpt.Col
import com.nidaria.app.sculpt.Q
import com.nidaria.app.sculpt.Sculpt
import com.nidaria.app.sculpt.Sh
import com.nidaria.app.sculpt.V3
import com.nidaria.app.sculpt.clampD
import com.nidaria.app.sculpt.cosD
import com.nidaria.app.sculpt.rot
import com.nidaria.app.sculpt.sinD
import com.nidaria.app.sculpt.surfacePatch
import com.nidaria.app.sculpt.v
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private const val BODY = 0
private const val HEAD = 1
private const val EAR_L = 2
private const val EAR_R = 3
private const val ARM_L = 4
private const val ARM_R = 5
private const val TAIL = 6
private const val BONES = 7
private val PARENT = intArrayOf(-1, BODY, HEAD, HEAD, BODY, BODY, BODY)

internal fun V3.gl(): Vec3 = Vec3(x.toFloat(), y.toFloat(), z.toFloat())
internal fun Object3D.place(p: V3): Object3D {
    position.set(p.x.toFloat(), p.y.toFloat(), p.z.toFloat())
    return this
}
internal fun Object3D.turn(q: Q): Object3D {
    quaternion.set(q.x.toFloat(), q.y.toFloat(), q.z.toFloat(), q.w.toFloat())
    return this
}

private fun hsl(h: Double, s: Double, l: Double): Col =
    Col.hsl((((h % 360) + 360) % 360) / 360, clampD(s, 0.0, 100.0) / 100, clampD(l, 0.0, 100.0) / 100)

// Colores de juguete: el ADN da el tono; aquí se llevan a pastel.
private class CreaturePalette(genes: Genes, plan: Plan) {
    val s = clampD((genes.sat + plan.toneSat) * 0.95, 20.0, 82.0)
    val l = clampD(genes.light + plan.toneLight + 5, 48.0, 84.0)
    val body = hsl(genes.hue.toDouble(), s, l)
    val light = hsl(genes.hue.toDouble(), s - 14, min(l + 9, 93.0))
    val accent = hsl(genes.hue2.toDouble(), s + 6, l - 6)
    val inner = light.clone().lerp(Col.hex("#ffb0c0"), 0.55)
    val leaf = hsl(if (plan.family == "hoja") genes.hue.toDouble() else 112.0, if (plan.family == "hoja") s + 12 else 48.0, l - 16)
    val ivory = Col.hex("#fff1dc")
    val nose = body.clone().lerp(Col.hex("#e27c8f"), 0.7)
}

private fun decalMaterial(map: Texture, build: (Material.() -> Unit)? = null): Material = Material.standard(Color(1f, 1f, 1f), 0.7f) {
    this.map = map
    transparent = true
    depthWrite = false
    polygonOffset = true
    build?.invoke(this)
}

private val EYE = Material.standard(Color.hex("#1e120d"), 0.18f) {
    clearcoat = 1f
    clearcoatRoughness = 0.06f
    shared = true
}
private val GLINT = Material.basic(Color.hex("#ffffff")).also { it.shared = true }
internal val BALL = sphereGeometry(1f, 24, 16).keep()

// Base ortonormal con z = normal y la y lo más hacia arriba posible.
private fun faceFrame(normal: V3, up: V3 = AXIS_Y): Q {
    val z = normal.clone().normalize()
    val x = V3().crossVectors(up, z).normalize()
    val y = V3().crossVectors(z, x)
    return Q().setFromBasis(x, y, z)
}

private fun quatFromTo(from: V3, to: V3): Q = Q().setFromUnitVectors(from, to.clone().normalize())

// ── Anclajes para la ropa (en Double, como en la web) ──
class Host(val center: V3, val rx: Double, val ry: Double, val rz: Double)
class HatAnchor(val y: Double, val r: Double)
class FaceAnchor(val y: Double, val z: Double, val gap: Double, val r: Double, val host: Host, val pitch: Double)
class NeckAnchor(val y: Double, val rx: Double, val rz: Double)
class Anchors(val hat: HatAnchor, val face: FaceAnchor, val neck: NeckAnchor)

class Eye(val group: Group, val open: Group, val closed: Mesh, val side: Int, val r: Float, val tall: Float, val content: Boolean, val restLid: Float) {
    var lidValue = 0f
}

class Limb(val pivot: Object3D, val side: Int, val rest: Float)

class Rig(
    val root: Group,
    val bounce: Group,
    val squash: Group,
    val headPivot: Object3D,
    val eyes: List<Eye>,
    val mouths: Map<String, Texture>,
    val mouth: Mesh,
    val cheeks: List<Mesh>,
    val ears: List<Limb>,
    val arms: List<Limb>,
    val tail: Object3D,
    val wings: List<Limb>,
    val top: Object3D,
    val halo: Mesh?,
    val haloY: Float,
    val mesh: SkinnedMesh,
)

class Built(val obj: Group, val plan: Plan, val height: Float, val anchors: Anchors, val rig: Rig) {
    fun dispose() {
        obj.traverse { node -> if (node is Mesh && !node.geometry.shared) node.geometry.dispose() }
    }
}

// `detail`: 'high' para la habitación, 'low' para miniaturas y 'tiny' para
// las siluetas del Criadex, que solo necesitan el contorno.
fun buildCreature(formId: String, genes: Genes, detail: String = "high"): Built {
    val plan = planFor(formId, genes)
    val pal = CreaturePalette(genes, plan)
    val rng = Rng((genes.seed.toInt() xor 0x9e3779b9.toInt()).toLong())
    val cell = when (detail) {
        "high" -> 0.02
        "tiny" -> 0.046
        else -> 0.032
    }
    val sock = if (plan.pattern == "calcetines") pal.accent else pal.body
    val w = plan.bodyW
    val h = plan.bodyH
    val dep = plan.bodyD

    val S = Sculpt(ground = 0.0, groundK = 0.04)
    val bonePos = Array(BONES) { V3() }
    val headY = if (plan.blob) h * 0.42 else h * 0.86
    bonePos[HEAD].set(0.0, headY, 0.0)

    // `host` es el elipsoide que lleva la cara: la cabeza, o el cuerpo
    // entero en las crías. En coordenadas del cuerpo (pies en y = 0).
    val host: Host
    if (plan.blob) {
        host = Host(v(0.0, h * 0.5, 0.0), w / 2, h / 2, dep / 2)
        S.add(Sh.ellipsoid(doubleArrayOf(0.0, h * 0.5, 0.0), doubleArrayOf(w / 2, h / 2, dep / 2)), pal.body, k = 0.0, bone = HEAD)
        S.add(Sh.ellipsoid(doubleArrayOf(0.0, h * 0.3, 0.0), doubleArrayOf(w * 0.52, h * 0.3, dep * 0.5)), pal.body, k = 0.16, bone = HEAD)
    } else {
        val rx = plan.headRx
        val ry = plan.headRy
        val rz = plan.headRz
        host = Host(v(0.0, headY + ry * 0.72, 0.02), rx, ry, rz)
        S.add(Sh.ellipsoid(doubleArrayOf(0.0, h * 0.52, 0.0), doubleArrayOf(w * 0.46, h * 0.52, dep * 0.46)), pal.body, k = 0.0, bone = BODY)
        S.add(Sh.ellipsoid(doubleArrayOf(0.0, h * 0.32, 0.01), doubleArrayOf(w * 0.52, h * 0.32, dep * 0.5)), pal.body, k = 0.12, bone = BODY)
        S.add(Sh.ellipsoid(host.center.toArray(), doubleArrayOf(rx, ry, rz)), pal.body, k = 0.18, bone = HEAD)
    }
    val unit = host.rx / 0.5
    val facePitch = if (plan.blob) 0.2 else 0.02

    fun onHost(yaw: Double, pitch: Double): V3 {
        val cp = cosD(pitch)
        return v(host.rx * sinD(yaw) * cp, host.ry * sinD(pitch), host.rz * cosD(yaw) * cp).add(host.center)
    }
    fun hostNormal(p: V3): V3 {
        val q = p.clone().sub(host.center)
        return v(q.x / (host.rx * host.rx), q.y / (host.ry * host.ry), q.z / (host.rz * host.rz)).normalize()
    }

    // Orejas: las ramas las agrandan, con un tope para que no tapen la cabeza.
    val earSize = 0.2 * unit * min(plan.earSize, 1.32)
    val droop = plan.earDroop
    for (side in intArrayOf(-1, 1)) {
        val bone = if (side < 0) EAR_L else EAR_R
        when (plan.earType) {
            "round" -> {
                val s = earSize * 0.72
                val base = onHost(side * 1.0, 0.8 - droop * 0.32)
                val dir = hostNormal(base).add(v(0.0, 0.5, 0.0)).normalize().applyAxisAngle(AXIS_Z, -side * droop * 0.85)
                val center = base.clone().addScaledVector(dir, s * 0.62)
                val q = quatFromTo(AXIS_Y, dir).multiply(Q().setFromAxisAngle(AXIS_Y, -side * 0.35))
                S.add(Sh.ellipsoid(center.toArray(), doubleArrayOf(s, s * 0.94, s * 0.48), rot(q)), sock, k = 0.04, bone = bone)
                val inner = center.clone().add(v(0.0, 0.0, s * 0.32).applyQuaternion(q)).addScaledVector(dir, s * 0.04)
                S.paint(Sh.ellipsoid(inner.toArray(), doubleArrayOf(s * 0.56, s * 0.54, s * 0.3), rot(q)), pal.inner, soft = 0.01)
                bonePos[bone].copy(base)
            }
            "pointy" -> {
                val s = earSize * 0.8
                val base = onHost(side * 0.78, 0.84 - droop * 0.2)
                val dir = hostNormal(base).add(v(0.0, 0.9, 0.0)).normalize().applyAxisAngle(AXIS_Z, -side * droop * 0.8)
                val tip = base.clone().addScaledVector(dir, s * 1.85)
                S.add(Sh.cone(base.clone().addScaledVector(dir, -s * 0.2).toArray(), tip.toArray(), s * 0.6, s * 0.1), sock, k = 0.04, bone = bone)
                val fwd = v(0.0, 0.0, s * 0.24).applyQuaternion(quatFromTo(AXIS_Y, hostNormal(base)))
                S.paint(Sh.cone(base.clone().add(fwd).addScaledVector(dir, s * 0.25).toArray(), tip.clone().add(fwd).addScaledVector(dir, -s * 0.4).toArray(), s * 0.34, s * 0.05), pal.inner, soft = 0.01)
                bonePos[bone].copy(base)
            }
            "leaf" -> {
                val s = earSize * 0.82
                val base = onHost(side * 1.0, 0.7 - droop * 0.25)
                val dir = hostNormal(base).multiplyScalar(1.1).add(v(0.0, 1.0, 0.0)).normalize().applyAxisAngle(AXIS_Z, -side * droop * 1.1)
                val q = quatFromTo(AXIS_Y, dir).multiply(Q().setFromAxisAngle(AXIS_Y, -side * 0.5))
                S.add(Sh.leaf(base.clone().addScaledVector(dir, -s * 0.15).toArray(), rot(q), s * 2.4, s * 1.2, 0.055 * unit, 0.18), pal.accent, k = 0.035, bone = bone, colorK = 0.03)
                bonePos[bone].copy(base)
            }
            else -> {
                // Branquias de ajolote: tres ramitas a cada lado.
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
            }
        }
    }

    // Remate de la cabeza (lo que va fundido con ella)
    val crown = onHost(0.0, PI / 2 - 0.14)
    val ts = plan.topSize * unit
    when (plan.topType) {
        "tuft" -> {
            // Un rizo de pelo: una gota gorda que se dobla hacia delante y otra más pequeña al lado.
            val base = crown.clone().add(v(0.0, -0.03 * ts, 0.02 * ts))
            val mid = base.clone().add(v(0.01 * ts, 0.12 * ts, 0.02 * ts))
            S.add(Sh.cone(base.toArray(), mid.toArray(), 0.075 * ts, 0.05 * ts), pal.body, k = 0.07, bone = HEAD)
            S.add(Sh.cone(mid.toArray(), mid.clone().add(v(0.035 * ts, 0.05 * ts, 0.07 * ts)).toArray(), 0.05 * ts, 0.03 * ts), pal.body, k = 0.03, bone = HEAD)
            val sideTuft = crown.clone().add(v(-0.08 * ts, -0.04 * ts, 0.0))
            S.add(Sh.cone(sideTuft.toArray(), sideTuft.clone().add(v(-0.06 * ts, 0.09 * ts, 0.03 * ts)).toArray(), 0.05 * ts, 0.026 * ts), pal.body, k = 0.05, bone = HEAD)
        }
        "drop" -> S.add(Sh.cone(crown.clone().add(v(0.0, -0.02 * ts, 0.0)).toArray(), crown.clone().add(v(0.0, 0.27 * ts, -0.02 * ts)).toArray(), 0.11 * ts, 0.018 * ts), pal.accent, k = 0.06, bone = HEAD, colorK = 0.025)
        "spikes" -> for (spike in arrayOf(doubleArrayOf(-0.15, 0.5, 0.22), doubleArrayOf(0.0, 0.0, 0.32), doubleArrayOf(0.15, -0.5, 0.22))) {
            val base = crown.clone().add(v(spike[0] * ts, -0.04 * ts, 0.0))
            val dir = v(-sinD(spike[1]), cosD(spike[1]), -0.1).normalize()
            S.add(Sh.cone(base.toArray(), base.clone().addScaledVector(dir, spike[2] * ts).toArray(), 0.075 * ts, 0.012 * ts), pal.accent, k = 0.035, bone = HEAD, colorK = 0.02)
        }
    }

    // Patas
    for (side in intArrayOf(-1, 1)) {
        val f = plan.feet
        S.add(Sh.ellipsoid(doubleArrayOf(side * w * 0.24, 0.045 * f, dep * (if (plan.blob) 0.3 else 0.28)), doubleArrayOf(0.12 * f, 0.08 * f, 0.15 * f)), sock, k = 0.035, bone = BODY)
    }
    if (plan.arms) {
        for (side in intArrayOf(-1, 1)) {
            val bone = if (side < 0) ARM_L else ARM_R
            val shoulder = v(side * w * 0.42, h * 0.6, dep * 0.1)
            val paw = v(side * w * 0.39, h * 0.46, dep * 0.3)
            S.add(Sh.cone(shoulder.toArray(), paw.toArray(), 0.062, 0.074), sock, k = 0.055, bone = bone)
            bonePos[bone].copy(shoulder)
        }
    }

    // Cola: asoma por un lado para verse desde delante.
    val tsz = plan.tailSize
    val tailBase = v(w * 0.24, h * 0.26, -dep * 0.4)
    bonePos[TAIL].copy(tailBase)
    val tailDir = v(0.55, 0.5, -0.65).normalize()
    when (plan.tailType) {
        "pom" -> S.add(Sh.sphere(tailBase.clone().addScaledVector(tailDir, 0.08 * tsz).toArray(), 0.12 * tsz), pal.light, k = 0.04, bone = TAIL)
        "leaf" -> S.add(Sh.leaf(tailBase.toArray(), rot(quatFromTo(AXIS_Y, tailDir)), 0.44 * tsz, 0.22 * tsz, 0.05, 0.2), pal.accent, k = 0.03, bone = TAIL, colorK = 0.03)
        "drop" -> S.add(Sh.cone(tailBase.toArray(), tailBase.clone().addScaledVector(tailDir, 0.36 * tsz).toArray(), 0.1 * tsz, 0.02 * tsz), pal.accent, k = 0.04, bone = TAIL, colorK = 0.03)
        else -> S.add(Sh.cone(tailBase.toArray(), tailBase.clone().addScaledVector(tailDir, 0.4 * tsz).toArray(), 0.09 * tsz, 0.01 * tsz), pal.accent, k = 0.03, bone = TAIL, colorK = 0.02)
    }

    // Piezas de adulta que van fundidas con el cuerpo
    if (plan.extra == "mane" && plan.family == "pelusa") {
        for (i in 0 until 12) {
            val a = (i / 12.0) * PI * 2
            val r = w * 0.42
            S.add(Sh.sphere(doubleArrayOf(sinD(a) * r, h * 0.9 + (i % 2) * 0.02, cosD(a) * r * 0.92), 0.13), pal.light, k = 0.06, bone = BODY)
        }
    } else if (plan.extra == "horns") {
        for (i in 0 until 3) {
            val base = v(0.0, h * (0.78 - i * 0.2), -dep * (0.44 + i * 0.03))
            val dir = v(0.0, 0.45 - i * 0.12, -1.0).normalize()
            S.add(Sh.cone(base.toArray(), base.clone().addScaledVector(dir, 0.2 - i * 0.03).toArray(), 0.07, 0.012), pal.accent, k = 0.03, bone = BODY, colorK = 0.02)
        }
    }

    // Dibujo del ADN
    if (plan.pattern == "motas") {
        val count = 4 + Math.round(plan.patternAmt * 6).toInt()
        for (i in 0 until count) {
            val size = rng.range(0.05, 0.09) * unit
            val yaw = rng.range(0.85, 2.3) * (if (rng.chance(0.5)) 1 else -1)
            S.paint(Sh.sphere(onHost(yaw, rng.range(-0.1, 1.05)).toArray(), size), pal.accent, soft = 0.008)
        }
    } else if (plan.pattern == "rayas") {
        // Tres rayas que pasan por encima de la cabeza, de la frente a la nuca.
        val width = 0.032 * unit * (0.7 + plan.patternAmt * 0.5)
        for (yaw in doubleArrayOf(-0.36, 0.0, 0.36)) {
            var prev: V3? = null
            for (i in 0..6) {
                val pitch = (if (plan.blob) 0.9 else 0.8) + i * 0.22
                val p = onHost(if (pitch > PI / 2) yaw + PI else yaw, if (pitch > PI / 2) PI - pitch else pitch)
                if (prev != null) S.paint(Sh.capsule(prev.toArray(), p.toArray(), width), pal.accent, soft = 0.008)
                prev = p
            }
        }
    }
    if (plan.belly) {
        val center = if (plan.blob) doubleArrayOf(0.0, h * 0.27, dep * 0.46) else doubleArrayOf(0.0, h * 0.42, dep * 0.47)
        val radii = if (plan.blob) doubleArrayOf(w * 0.3, h * 0.2, dep * 0.16) else doubleArrayOf(w * 0.3, h * 0.3, dep * 0.16)
        S.paint(Sh.ellipsoid(center, radii), pal.light, soft = 0.012)
    }

    // ── Malla y huesos ──
    val bodyGeometry = S.build(cell = cell, bones = BONES, aoReach = 0.2, ao = if (detail == "tiny") 0.0 else 1.0)
    val finish = if (plan.finish == "plush" || plan.finish == "gloss") plan.finish else "matte"
    val bones = List(BONES) { Group() }
    for ((i, bone) in bones.withIndex()) {
        val parent = PARENT[i]
        bone.place(bonePos[i])
        if (parent >= 0) {
            bone.position.sub(bonePos[parent].gl())
            bones[parent].add(bone)
        }
    }
    val mesh = SkinnedMesh(bodyGeometry, clay(finish), bones)
    // Sin sombra propia: la oclusión horneada ya marca los pliegues.
    mesh.castShadow = true
    mesh.receiveShadow = false
    mesh.add(bones[BODY])
    mesh.bind()

    val root = Group()
    val bounce = Group()
    val squash = Group()
    root.add(bounce)
    bounce.add(squash)
    squash.add(mesh)
    root.scale.setScalar(plan.scale.toFloat())
    val headPivot = bones[HEAD]
    val headBase = bonePos[HEAD]
    // Lo que cuelga de la cabeza se calcula en coordenadas del cuerpo y se
    // pasa a las de la cabeza restando dónde está su hueso.
    fun toHead(obj: Object3D): Object3D {
        obj.position.sub(headBase.gl())
        headPivot.add(obj)
        return obj
    }
    fun decal(center: V3, normal: V3, width: Double, height: Double, material: Material, up: V3 = AXIS_Y): Mesh {
        val geometry = surfacePatch(S, center, normal, up, width, height, 8, 0.0018 * unit)
        geometry.translate(-headBase.x.toFloat(), -headBase.y.toFloat(), -headBase.z.toFloat())
        val m = Mesh(geometry, material)
        headPivot.add(m)
        return m
    }

    // Ojos
    val gap = plan.eyeGap * 1.08
    val eyeR = 0.044 * unit * plan.eyeSize
    val tall = if (plan.eyeStyle == 1) 1.32 else 1.14
    val content = plan.eyeLids >= 0.35
    val eyes = ArrayList<Eye>()
    val r = eyeR.toFloat()
    val tl = tall.toFloat()
    for (side in intArrayOf(-1, 1)) {
        val at = S.project(onHost(side * gap, facePitch))
        val group = Group()
        group.place(at.point).turn(faceFrame(at.normal))
        toHead(group)
        val open = Group()
        val bead = Mesh(BALL, EYE)
        bead.scale.set(r, r * tl, r * 0.62f)
        bead.position.z = -r * 0.12f
        val glint = Mesh(BALL, GLINT)
        glint.scale.set(r * 0.3f, r * 0.34f, r * 0.12f)
        glint.position.set(-r * 0.32f, r * 0.42f * tl, r * 0.42f)
        open.add(bead, glint)
        if (plan.eyeStyle == 2) {
            val spark = Mesh(BALL, GLINT)
            spark.scale.set(r * 0.15f, r * 0.15f, r * 0.08f)
            spark.position.set(r * 0.3f, -r * 0.3f * tl, r * 0.46f)
            open.add(spark)
        }
        group.add(open)
        val closed = decal(at.point, at.normal, eyeR * 3.4, eyeR * 2.55, decalMaterial(lidTexture("sleep")))
        closed.visible = content
        open.visible = !content
        eyes.add(Eye(group, open, closed, side, r, tl, content, if (content) 0f else plan.eyeLids.toFloat()))
    }

    // Boca
    val mouthKind = if (plan.family == "pelusa") "cat" else "smile"
    val mouthAt = S.project(onHost(0.0, facePitch - (if (plan.blob) 0.22 else 0.25)))
    val mouth = decal(mouthAt.point, mouthAt.normal, 0.2 * unit, 0.125 * unit, decalMaterial(mouthTexture(mouthKind, plan.fang)))
    val mouths = mapOf("smile" to mouthKind, "sad" to "sad", "open" to "open", "flat" to "flat").mapValues { (_, kind) -> mouthTexture(kind, plan.fang) }

    // Nariz de las peludas
    if (plan.family == "pelusa") {
        val at = S.project(onHost(0.0, facePitch - 0.1))
        val nose = Mesh(BALL, physical("#ffffff", 0.3f) {
            color.copy(pal.nose.toGl())
            clearcoat = 0.8f
            clearcoatRoughness = 0.2f
        })
        nose.scale.set((0.026 * unit).toFloat(), (0.018 * unit).toFloat(), (0.016 * unit).toFloat())
        nose.place(at.point.clone().addScaledVector(at.normal, 0.004 * unit)).turn(faceFrame(at.normal))
        toHead(nose)
    }

    // Coloretes
    val cheeks = ArrayList<Mesh>()
    for (side in intArrayOf(-1, 1)) {
        val at = S.project(onHost(side * (gap + 0.3), facePitch - 0.15))
        val material = decalMaterial(blushTexture()) {
            color.setHex("#ff8fa9")
            opacity = 0.62f
        }
        cheeks.add(decal(at.point, at.normal, 0.2 * unit * plan.cheeks, 0.13 * unit * plan.cheeks, material))
    }

    if (plan.brows) {
        val lift = if (plan.eyeLids > 0.2) 0.34 else 0.24
        for (side in intArrayOf(-1, 1)) {
            val at = S.project(onHost(side * gap * 0.95, facePitch + lift))
            decal(at.point, at.normal, eyeR * 3.3, eyeR * 1.65, decalMaterial(browTexture(side)))
        }
    }

    // ── Piezas aparte ──
    fun partMesh(material: Material, make: () -> com.nidaria.app.gl.Geometry): Mesh {
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
                val tip = crown.clone().add(v(0.0, 0.17 * ts, 0.01 * ts))
                p.add(Sh.capsule(base.toArray(), tip.toArray(), 0.022 * ts), pal.leaf, k = 0.0)
                for (side in intArrayOf(-1, 1)) {
                    val dir = v(side * 0.95, 0.55, 0.12).normalize()
                    p.add(Sh.leaf(tip.toArray(), rot(quatFromTo(AXIS_Y, dir).multiply(Q().setFromAxisAngle(AXIS_Y, side * 0.25))), 0.27 * ts, 0.16 * ts, 0.026 * ts, 0.18), pal.leaf, k = 0.02)
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
                p.add(Sh.sphere(c.toArray(), 0.072 * ts), "#e5465e", k = 0.0)
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

    // Alas
    val wings = ArrayList<Limb>()
    if (plan.extra == "wings") {
        // Las dos alas comparten la malla (en la web, `once` por nombre).
        val wingGeometry = run {
            val p = Sculpt()
            when (plan.family) {
                "pelusa" -> for (i in 0 until 3) {
                    val a = -i * 0.42
                    val len = 0.22 - i * 0.035
                    p.add(Sh.ellipsoid(doubleArrayOf(sinD(-a) * len * 0.6, cosD(a) * len * 0.75, 0.0), doubleArrayOf(0.075, len * 0.75, 0.04), rot(0.0, 0.0, a)), pal.light, k = 0.03)
                }
                "hoja" -> {
                    p.add(Sh.leaf(doubleArrayOf(0.0, 0.0, 0.0), rot(0.0, 0.0, -0.15), 0.5, 0.27, 0.04, 0.12), pal.accent, k = 0.0)
                    p.paint(Sh.capsule(doubleArrayOf(0.0, 0.02, 0.0), doubleArrayOf(0.02, 0.42, 0.03), 0.008), pal.leaf, soft = 0.006)
                }
                else -> for (i in 0 until 3) {
                    val a = 0.2 - i * 0.42
                    p.add(Sh.leaf(doubleArrayOf(0.0, 0.0, 0.0), rot(0.0, 0.0, a), 0.42 - i * 0.06, 0.2, 0.035, 0.1), pal.accent, k = 0.04)
                }
            }
            p.build(cell = 0.009, aoReach = 0.06)
        }
        for (side in intArrayOf(-1, 1)) {
            val pivot = Group()
            pivot.position.set((side * w * 0.32).toFloat(), (h * 0.72).toFloat(), (-dep * 0.3).toFloat())
            val wing = partMesh(clay(if (plan.family == "pelusa") "plush" else finish)) { wingGeometry }
            wing.scale.x = side.toFloat()
            pivot.add(wing)
            pivot.rotation.z = side * -0.95f
            bones[BODY].add(pivot)
            wings.add(Limb(pivot, side, side * -0.95f))
        }
    }

    // Corona de adorno
    if (plan.extra == "crown") {
        val ringFinish = when (plan.family) {
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
                if (plan.family == "hoja") {
                    val q = faceFrame(at.normal, AXIS_Z)
                    for (k in 0 until 5) {
                        val a = (k / 5.0) * PI * 2
                        val offset = v(cosD(a) * 0.045 * unit, sinD(a) * 0.045 * unit, 0.0).applyQuaternion(q)
                        p.add(Sh.ellipsoid(c.clone().add(offset).toArray(), doubleArrayOf(0.04 * unit, 0.028 * unit, 0.018 * unit), rot(q.clone().multiply(Q().setFromAxisAngle(AXIS_Z, a)))), pal.accent, k = 0.01)
                    }
                    p.add(Sh.sphere(c.clone().addScaledVector(at.normal, 0.01 * unit).toArray(), 0.024 * unit), "#ffd166", k = 0.006)
                } else {
                    p.add(Sh.sphere(c.toArray(), 0.055 * unit), if (plan.family == "gota") pal.ivory else pal.light, k = 0.0)
                }
            }
            p.build(cell = 0.008 * unit, aoReach = 0.05)
        }
        toHead(ring)
    }

    if (plan.extra == "horns") {
        toHead(partMesh(clay("gloss")) {
            val p = Sculpt()
            for (side in intArrayOf(-1, 1)) {
                val base = onHost(side * 0.6, 1.08)
                val mid = base.clone().add(v(side * 0.05 * unit, 0.15 * unit, -0.02 * unit))
                val tip = mid.clone().add(v(side * 0.1 * unit, 0.09 * unit, -0.04 * unit))
                p.add(Sh.cone(base.clone().add(v(0.0, -0.04 * unit, 0.0)).toArray(), mid.toArray(), 0.07 * unit, 0.05 * unit), pal.ivory, k = 0.0)
                p.add(Sh.cone(mid.toArray(), tip.toArray(), 0.05 * unit, 0.012 * unit), pal.ivory, k = 0.03)
            }
            p.build(cell = 0.008 * unit, aoReach = 0.05)
        })
    }

    if (plan.extra == "mane" && plan.family != "pelusa") {
        val collar = if (plan.family == "hoja") {
            partMesh(clay("matte")) {
                val p = Sculpt()
                for (i in 0 until 11) {
                    val a = (i / 11.0) * PI * 2
                    val rr = w * 0.36
                    val base = v(sinD(a) * rr, h * 0.92, cosD(a) * rr * 0.92)
                    val dir = v(sinD(a), -0.35, cosD(a)).normalize()
                    p.add(Sh.leaf(base.clone().addScaledVector(dir, -0.04).toArray(), rot(quatFromTo(AXIS_Y, dir)), 0.3, 0.19, 0.035, -0.12), if (i % 2 == 1) pal.accent else pal.leaf, k = 0.02)
                }
                p.build(cell = 0.009, aoReach = 0.06)
            }
        } else {
            val bubble = physical("#ffffff", 0.12f) {
                color.copy(pal.light.clone().lerp(Col(1.0, 1.0, 1.0), 0.35).toGl())
                clearcoat = 1f
                clearcoatRoughness = 0.05f
                transparent = true
                opacity = 0.86f
                sheen = 0.6f
                sheenColor.setHex("#e8f6ff")
            }
            partMesh(bubble) {
                val p = Sculpt()
                for (i in 0 until 11) {
                    val a = (i / 11.0) * PI * 2
                    val rr = w * 0.42
                    p.add(Sh.sphere(doubleArrayOf(sinD(a) * rr, h * 0.9, cosD(a) * rr * 0.92), 0.1 + (i % 3) * 0.012), pal.light, k = 0.0)
                }
                p.build(cell = 0.012, ao = 0.5, aoReach = 0.05)
            }
        }
        bones[BODY].add(collar)
    }

    var halo: Mesh? = null
    var haloY = 0f
    if (plan.extra == "halo") {
        val u = unit.toFloat()
        halo = Mesh(torusGeometry(0.22f * u, 0.026f * u, 16, 48), Material.standard(Color.hex("#ffe08a"), 0.3f) {
            metalness = 0.2f
            emissive.setHex("#ffcf5c")
            emissiveIntensity = 0.9f
        })
        halo.rotation.set(PI_F / 2f - 0.22f, 0f, 0.1f)
        val lift = if (plan.topType == "sprout") 0.46 else 0.32
        halo.position.set(0f, (host.center.y + host.ry + lift * unit).toFloat(), (-0.03 * unit).toFloat())
        toHead(halo)
        haloY = halo.position.y
    }

    // ── Anclajes para la ropa ──
    val hostLocal = Host(host.center.clone().sub(headBase), host.rx, host.ry, host.rz)
    val anchors = Anchors(
        // En coordenadas de la cabeza, salvo `neck`, que va en las del cuerpo.
        hat = HatAnchor(hostLocal.center.y + host.ry, host.rx),
        face = FaceAnchor(hostLocal.center.y + host.ry * sinD(facePitch), host.rz, gap, host.rx, hostLocal, facePitch),
        neck = neckOf(S, plan, host),
    )
    val height = ((host.center.y + host.ry) * plan.scale).toFloat()

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
    )
    return Built(root, plan, height, anchors, rig)
}

// Radio de la figura a la altura `y` en la dirección (dx, dz).
private fun radiusAt(S: Sculpt, y: Double, dx: Double, dz: Double): Double {
    var inside = 0.0
    var outside = 1.5
    if (S.sdf(0.0, y, 0.0) > 0) return 0.0
    var t = 0.02
    while (t < 1.5) {
        if (S.sdf(dx * t, y, dz * t) > 0) {
            outside = t
            inside = t - 0.02
            break
        }
        t += 0.02
    }
    for (i in 0 until 12) {
        val mid = (inside + outside) / 2
        if (S.sdf(dx * mid, y, dz * mid) > 0) outside = mid else inside = mid
    }
    return (inside + outside) / 2
}

// Altura y radios del cuello, donde se atan pañuelos y cascabeles. En
// coordenadas del cuerpo.
private fun neckOf(S: Sculpt, plan: Plan, host: Host): NeckAnchor {
    val h = plan.bodyH
    if (plan.blob) {
        val y = h * 0.3
        return NeckAnchor(y, radiusAt(S, y, 1.0, 0.0) + 0.012, (radiusAt(S, y, 0.0, 1.0) + radiusAt(S, y, 0.0, -1.0)) / 2 + 0.012)
    }
    var bestY = h * 0.8
    var bestR = Double.POSITIVE_INFINITY
    var y = h * 0.6
    while (y < host.center.y) {
        val r = radiusAt(S, y, 1.0, 0.0)
        if (r < bestR) {
            bestY = y
            bestR = r
        }
        y += 0.01
    }
    return NeckAnchor(bestY, bestR + 0.014, (radiusAt(S, bestY, 0.0, 1.0) + radiusAt(S, bestY, 0.0, -1.0)) / 2 + 0.014)
}

// ── Animación ─────────────────────────────────────────────────

// Envuelve una criatura construida y le da vida: respira, parpadea,
// mira y reacciona. `update` se llama en cada fotograma.
class Actor(private val built: Built, private val anim: Animator, private val still: Boolean = false) {
    val obj: Group get() = built.obj
    val plan: Plan get() = built.plan
    val height: Float get() = built.height
    val anchors: Anchors get() = built.anchors
    val rig: Rig get() = built.rig

    var mood = "bien"
        private set
    private var blinkAt = 1.5f
    private var blink = 0f
    private val gaze = Vec2()
    private val gazeTarget = Vec2()
    private var gazeAt = 0f
    private var lean = 0f
    var leanTarget = 0f
    var busy = false
    // Un gesto de reposo: se puede cortar con un toque o un cuidado.
    var fidgeting = false
        private set
    private var talk = 0f
    private var shiver = 0f
    private var squint = false
    private var face = "smile"
    private var twitchAt = 3f + Random.nextFloat() * 4f
    private var twitch = 0f
    private var twitchSide = 0
    private var idleAt = 6f + Random.nextFloat() * 6f
    private var now = 0f
    var timeline: Timeline? = null
        private set

    fun dispose() = built.dispose()

    fun setMouth(name: String) {
        if (face == name) return
        face = name
        rig.mouth.material.map = rig.mouths[name] ?: rig.mouths.getValue("smile")
    }

    fun setMood(value: String) {
        mood = value
        applyEyes()
        if (!busy) setMouth(restMouth())
        val pale = value == "enferma"
        for (cheek in rig.cheeks) {
            cheek.material.color.setHex(if (pale) "#9ec3ea" else "#ff8fa9")
            cheek.material.opacity = if (value == "feliz") 0.8f else 0.62f
        }
    }

    // Ojos abiertos (cuentas) o cerrados (arcos pintados).
    private fun applyEyes() {
        val asleep = mood == "dormida"
        for (eye in rig.eyes) {
            val shut = asleep || eye.content || squint
            eye.open.visible = !shut
            eye.closed.visible = shut
            if (shut) eye.closed.material.map = lidTexture(if (squint && !asleep) "happy" else "sleep")
        }
    }

    private fun setSquint(on: Boolean) {
        if (squint == on) return
        squint = on
        applyEyes()
    }

    fun restMouth(): String = when (mood) {
        "dormida" -> "flat"
        "mal", "fatal", "enferma" -> "sad"
        "regular" -> "flat"
        else -> "smile"
    }

    fun lookAt(x: Float, y: Float) {
        gazeTarget.set(clampF(x, -1f, 1f), clampF(y, -1f, 1f))
        gazeAt = now + 2.5f
    }

    fun update(t: Float, dt: Float) {
        now = t
        val asleep = mood == "dormida"
        val low = mood == "mal" || mood == "fatal" || mood == "enferma"
        val happy = mood == "feliz"

        // Respiración: aplastar y estirar muy poco, más lento al dormir.
        val speed = if (asleep) 1.1f else if (low) 1.7f else 2.4f
        val depth = if (asleep) 0.034f else 0.022f
        val breath = sin(t * speed) * depth
        if (!busy) {
            rig.squash.scale.set(1f - breath * 0.6f, 1f + breath, 1f - breath * 0.6f)
            rig.root.rotation.z = sin(t * 0.9f) * (if (happy) 0.03f else 0.014f)
        }

        // Mirada: sigue al dedo un rato y luego vaga sola.
        if (t > gazeAt) {
            gazeTarget.set((Random.nextFloat() - 0.5f) * 0.9f, (Random.nextFloat() - 0.5f) * 0.4f)
            gazeAt = t + 2f + Random.nextFloat() * 3f
        }
        gaze.lerp(gazeTarget, 1f - exp(-dt * 5f))
        lean += (leanTarget - lean) * (1f - exp(-dt * 7f))
        val droop = if (asleep) 0.2f else if (low) 0.16f else 0f
        if (!busy) {
            rig.headPivot.rotation.set(
                droop - gaze.y * 0.14f,
                gaze.x * 0.3f,
                lean * 0.22f + (if (asleep) 0.1f else 0f) + sin(t * 0.7f) * 0.025f,
            )
        }
        for (eye in rig.eyes) eye.open.position.set(gaze.x * eye.r * 0.3f, gaze.y * eye.r * 0.2f, 0f)

        // Parpadeo: la cuenta se aplasta un instante.
        if (!asleep && t > blinkAt) {
            blink = 0.12f
            blinkAt = t + 2.2f + Random.nextFloat() * 3.6f
        }
        blink = max(0f, blink - dt)
        val shut = if (blink > 0f) 1f else if (low) 0.32f else 0f
        for (eye in rig.eyes) {
            val lid = max(eye.restLid, shut)
            eye.lidValue += (lid - eye.lidValue) * (1f - exp(-dt * 40f))
            eye.open.scale.y = max(0.08f, 1f - eye.lidValue * 0.95f)
        }

        // Orejas, cola, alas y brazos. De vez en cuando una oreja da un respingo.
        if (t > twitchAt) {
            twitch = 1f
            twitchSide = if (Random.nextFloat() < 0.5f) 0 else 1
            twitchAt = t + 3f + Random.nextFloat() * 5f
        }
        twitch = max(0f, twitch - dt * 4f)
        val wiggle = sin(t * 1.3f) * 0.05f + (if (happy) sin(t * 7f) * 0.04f else 0f)
        rig.ears.forEachIndexed { i, ear ->
            val flick = if (i == twitchSide && !asleep) sin(twitch * PI_F * 3f) * 0.18f * twitch else 0f
            ear.pivot.rotation.z = ear.rest + ear.side * (wiggle - (if (low) 0.22f else 0f) + flick)
        }
        rig.tail.rotation.y = sin(t * (if (happy) 7f else 2.2f)) * (if (asleep) 0.03f else if (happy) 0.5f else 0.2f)
        for (wing in rig.wings) wing.pivot.rotation.z = wing.rest + wing.side * sin(t * (if (happy) 9f else 2.6f)) * (if (asleep) 0.03f else 0.22f)
        for (arm in rig.arms) arm.pivot.rotation.z = arm.rest + arm.side * sin(t * 1.9f + arm.side) * 0.08f
        rig.halo?.let { it.position.y = rig.haloY + sin(t * 1.6f) * 0.012f }

        if (shiver > 0f || mood == "enferma") {
            shiver = max(0f, shiver - dt)
            rig.bounce.position.x = sin(t * 46f) * 0.006f
        } else {
            rig.bounce.position.x = 0f
        }

        // De vez en cuando hace algo por su cuenta, si está bien.
        if (t > idleAt) {
            idleAt = t + 7f + Random.nextFloat() * 8f
            if (!busy && !still && (mood == "bien" || happy || mood == "regular")) fidget(happy)
        }

        // Hablar: abre y cierra la boca mientras dura el bocadillo.
        if (talk > 0f && !busy) {
            talk -= dt
            setMouth(if (sin(t * 16f) > 0f) "open" else restMouth())
            if (talk <= 0f) setMouth(restMouth())
        }
    }

    // Reacciones de una vez. Devuelven la línea de tiempo.
    private fun act(fn: (Timeline, Rig) -> Unit): Timeline {
        timeline?.kill()
        busy = true
        fidgeting = false
        val tl = anim.timeline(onComplete = {
            busy = false
            setSquint(false)
            setMouth(restMouth())
        })
        timeline = tl
        fn(tl, rig)
        return tl
    }

    fun stop() {
        timeline?.kill()
    }

    fun hop(times: Int = 1, height: Float = 0.28f): Timeline = act { tl, rig ->
        repeat(times) {
            tl.to(rig.squash.scale.to(1.12f, 0.86f, 1.12f), 0.09f, Eases.power2Out)
                .to(rig.squash.scale.to(0.92f, 1.12f, 0.92f), 0.12f, Eases.power2Out)
                .to(rig.bounce.position.to(y = height), 0.2f, Eases.power2Out, Pos.withPrev())
                .to(rig.bounce.position.to(y = 0f), 0.18f, Eases.power2In)
                .to(rig.squash.scale.to(1.14f, 0.84f, 1.14f), 0.07f, Eases.power1Out)
                .to(rig.squash.scale.to(1f, 1f, 1f), 0.3f, Eases.elasticOut(1f, 0.5f))
        }
        if (times > 1 && mood != "dormida") setSquint(true)
    }

    fun eat(onBite: (Int) -> Unit): Timeline {
        setMouth("open")
        return act { tl, rig ->
            for (i in 0 until 3) {
                tl.to(rig.headPivot.rotation.to(x = 0.34f), 0.14f, Eases.power2Out)
                    .call {
                        setMouth("flat")
                        setSquint(true)
                        onBite(i)
                    }
                    .to(rig.squash.scale.to(1.06f, 0.94f, 1.06f), 0.08f, at = Pos.withPrev())
                    .to(rig.headPivot.rotation.to(x = 0.02f), 0.16f, Eases.power2InOut)
                    .to(rig.squash.scale.to(1f, 1f, 1f), 0.14f, at = Pos.withPrev())
                    .call {
                        setSquint(false)
                        setMouth(if (i < 2) "open" else "smile")
                    }
            }
            tl.wait(0.15f)
        }
    }

    fun shake(): Timeline {
        setMouth("flat")
        return act { tl, rig ->
            tl.to(rig.headPivot.rotation.to(y = 0.4f), 0.11f, Eases.power2Out)
                .to(rig.headPivot.rotation.to(y = -0.4f), 0.18f, Eases.power2InOut)
                .to(rig.headPivot.rotation.to(y = 0.3f), 0.16f, Eases.power2InOut)
                .to(rig.headPivot.rotation.to(y = 0f), 0.2f, Eases.power2Out)
        }
    }

    fun wash(): Timeline {
        setMouth("open")
        return act { tl, rig ->
            setSquint(true)
            tl.to(rig.root.rotation.to(z = 0.12f), 0.12f, Eases.power1InOut, repeat = 7, yoyo = true)
                .to(rig.root.rotation.to(z = 0f), 0.2f, Eases.power2Out)
                .to(rig.squash.scale.to(1.1f, 0.9f, 1.1f), 0.1f)
                .to(rig.squash.scale.to(1f, 1f, 1f), 0.4f, Eases.elasticOut(1f, 0.45f))
        }
    }

    fun nuzzle(): Timeline {
        setMouth("smile")
        return act { tl, rig ->
            setSquint(true)
            tl.to(rig.squash.scale.to(1.08f, 0.92f, 1.08f), 0.16f, Eases.power2Out)
                .to(rig.headPivot.rotation.to(x = 0.1f, z = leanTarget * 0.34f), 0.2f, Eases.power2Out, Pos.withPrev())
                .to(rig.squash.scale.to(1f, 1f, 1f), 0.5f, Eases.elasticOut(1f, 0.4f))
                .to(rig.headPivot.rotation.to(x = 0f, z = 0f), 0.35f, Eases.power2InOut, Pos.withPrev(0.1f))
        }
    }

    fun spin(): Timeline {
        setMouth("open")
        return act { tl, rig ->
            setSquint(true)
            tl.to(rig.bounce.position.to(y = 0.34f), 0.26f, Eases.power2Out)
                .to(rig.root.rotation.to(y = PI_F * 2f), 0.6f, Eases.power2InOut, Pos.withPrev())
                .to(rig.bounce.position.to(y = 0f), 0.22f, Eases.power2In, Pos.afterPrev(-0.24f))
                .set(rig.root.rotation.to(y = 0f))
                .to(rig.squash.scale.to(1.14f, 0.84f, 1.14f), 0.08f)
                .to(rig.squash.scale.to(1f, 1f, 1f), 0.4f, Eases.elasticOut(1f, 0.45f))
        }
    }

    // Gestos de reposo: ladear la cabeza con curiosidad, balancearse o,
    // si está contenta, un saltito. Se pueden cortar (`fidgeting`).
    private fun fidget(happy: Boolean) {
        fidgetMove(happy)
        fidgeting = true
    }

    private fun fidgetMove(happy: Boolean) {
        val pick = Random.nextFloat()
        if (happy && pick < 0.35f) {
            hop(1, 0.1f)
            return
        }
        val side = if (Random.nextFloat() < 0.5f) -1f else 1f
        if (pick < 0.7f) {
            act { tl, rig ->
                tl.to(rig.headPivot.rotation.to(y = side * 0.18f, z = side * 0.26f), 0.45f, Eases.backOut(2f))
                    .to(rig.headPivot.rotation.to(z = side * 0.2f), 0.9f, Eases.sineInOut)
                    .to(rig.headPivot.rotation.to(y = 0f, z = 0f), 0.5f, Eases.power2InOut)
            }
            return
        }
        act { tl, rig ->
            tl.to(rig.root.rotation.to(z = side * 0.08f), 0.3f, Eases.sineInOut)
                .to(rig.root.rotation.to(z = -side * 0.08f), 0.5f, Eases.sineInOut)
                .to(rig.root.rotation.to(z = 0f), 0.4f, Eases.sineOut)
                .to(rig.squash.scale.to(1.05f, 0.95f, 1.05f), 0.12f, at = Pos.withPrev())
                .to(rig.squash.scale.to(1f, 1f, 1f), 0.4f, Eases.elasticOut(1f, 0.5f))
        }
    }

    fun say(seconds: Float) {
        talk = seconds
    }

    // Para la foto: si está a mitad de parpadeo, abre los ojos ya.
    fun openEyes() {
        if (blink <= 0f) return
        blink = 0f
        for (eye in rig.eyes) {
            eye.lidValue = eye.restLid
            eye.open.scale.y = max(0.08f, 1f - eye.lidValue * 0.95f)
        }
    }
}
