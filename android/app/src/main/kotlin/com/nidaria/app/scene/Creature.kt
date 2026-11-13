// Construye la criatura 3D a partir de su plano y la anima. Todo son
// primitivas generadas por código: no hay modelos ni texturas externas.

package com.nidaria.app.scene

import com.nidaria.app.gl.Animator
import com.nidaria.app.gl.Color
import com.nidaria.app.gl.Eases
import com.nidaria.app.gl.Euler
import com.nidaria.app.gl.Geometry
import com.nidaria.app.gl.Group
import com.nidaria.app.gl.Material
import com.nidaria.app.gl.MaterialKind
import com.nidaria.app.gl.Mesh
import com.nidaria.app.gl.Object3D
import com.nidaria.app.gl.PI_F
import com.nidaria.app.gl.Pos
import com.nidaria.app.gl.Shape
import com.nidaria.app.gl.Timeline
import com.nidaria.app.gl.Vec2
import com.nidaria.app.gl.Vec3
import com.nidaria.app.gl.capsuleGeometry
import com.nidaria.app.gl.clampF
import com.nidaria.app.gl.coneGeometry
import com.nidaria.app.gl.cylinderGeometry
import com.nidaria.app.gl.extrudeGeometry
import com.nidaria.app.gl.latheGeometry
import com.nidaria.app.gl.sphereGeometry
import com.nidaria.app.gl.to
import com.nidaria.app.gl.torusGeometry
import com.nidaria.app.rules.Genes
import com.nidaria.app.rules.Rng
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private val AXIS_Z = Vec3(0f, 0f, 1f)
private val AXIS_Y = Vec3(0f, 1f, 0f)

internal fun hslColor(h: Float, s: Float, l: Float): Color =
    Color().setHSL((((h % 360f) + 360f) % 360f) / 360f, clampF(s, 0f, 100f) / 100f, clampF(l, 0f, 100f) / 100f)

class CreatureMaterials(genes: Genes, plan: Plan) {
    val body: Material
    val light: Material
    val accent: Material
    val eye = Material.standard(Color.hex("#3b2419"), 0.12f) {
        clearcoat = 1f
        clearcoatRoughness = 0.05f
    }
    val white = Material.basic(Color.hex("#ffffff"))
    val ivory = Material.standard(Color.hex("#fff6e6"), 0.5f)
    val mouth = Material.standard(Color.hex("#8a3443"), 0.6f)
    val tongue = Material.standard(Color.hex("#ff8fa6"), 0.6f)
    val cheek = Material.standard(Color.hex("#ff8fa9"), 0.9f) {
        transparent = true
        opacity = 0.62f
        depthWrite = false
    }
    val line = Material.standard(Color.hex("#4a2d20"), 0.7f)
    val bubble: Material
    val center = Material.standard(Color.hex("#ffd166"), 0.6f)
    // Sin luces: la aureola y la luna parecen brillar con luz propia.
    val glow = Material.basic(Color.hex("#ffd76e"))
    val moon = Material.basic(Color.hex("#fff0b8"))
    val cherry = Material.standard(Color.hex("#e5465e"), 0.25f) {
        clearcoat = 0.8f
        clearcoatRoughness = 0.1f
    }

    init {
        val s = genes.sat + plan.toneSat
        val l = genes.light + plan.toneLight
        val bodyColor = hslColor(genes.hue.toFloat(), s, l)
        val lightColor = hslColor(genes.hue.toFloat(), s - 14f, l + 14f)
        val accentColor = hslColor(genes.hue2.toFloat(), s + 6f, l - 4f)
        // roughness, sheen, sheenRoughness, clearcoat
        val finish = when (plan.finish) {
            "plush" -> floatArrayOf(0.82f, 1f, 0.45f, 0f)
            "matte" -> floatArrayOf(0.66f, 0.35f, 0.6f, 0f)
            else -> floatArrayOf(0.3f, 0f, 0.5f, 0.7f)
        }
        fun skin(color: Color) = Material.standard(color, finish[0]) {
            sheen = finish[1]
            sheenRoughness = finish[2]
            sheenColor.copy(color.clone().lerp(Color.hex("#ffffff"), 0.55f))
            clearcoat = finish[3]
            clearcoatRoughness = 0.25f
        }
        body = skin(bodyColor)
        light = skin(lightColor)
        accent = skin(accentColor)
        bubble = Material.standard(lightColor, 0.1f) {
            clearcoat = 1f
            transparent = true
            opacity = 0.72f
        }
    }
}

// ── Geometrías ────────────────────────────────────────────────

private val SPHERE = sphereGeometry(1f, 40, 28).also { it.shared = true }
private val SPHERE_LOW = sphereGeometry(1f, 20, 14).also { it.shared = true }

private fun ball(material: Material, rx: Float, ry: Float = rx, rz: Float = rx, low: Boolean = false): Mesh {
    val mesh = Mesh(if (low) SPHERE_LOW else SPHERE, material)
    mesh.scale.set(rx, ry, rz)
    mesh.castShadow = true
    return mesh
}

// Cuerpo en forma de pera: más ancho abajo, apoyado en el suelo.
private fun pearGeometry(w: Float, h: Float, d: Float, taper: Float = 0.26f): Geometry {
    val geometry = sphereGeometry(1f, 44, 30)
    val pos = geometry.positions
    var i = 0
    while (i < pos.size) {
        val y = pos[i + 1]
        val widen = 1f + taper * (-y) * 0.5f + (if (y < -0.6f) (y + 0.6f) * 0.5f else 0f)
        pos[i] = pos[i] * widen * (w / 2f)
        pos[i + 1] = y * (h / 2f)
        pos[i + 2] = pos[i + 2] * widen * (d / 2f)
        i += 3
    }
    return geometry.computeVertexNormals()
}

// Radio del cuerpo de pera a la altura `y` (misma fórmula que su geometría).
private fun pearRadius(y: Float, h: Float, half: Float, taper: Float = 0.26f): Float {
    val yn = (y - h / 2f) / (h / 2f)
    if (abs(yn) >= 1f) return 0f
    val widen = 1f + taper * (-yn) * 0.5f + (if (yn < -0.6f) (yn + 0.6f) * 0.5f else 0f)
    return half * widen * sqrt(1f - yn * yn)
}

class Host(val center: Vec3, val rx: Float, val ry: Float, val rz: Float)

class NeckAnchor(val y: Float, val rx: Float, val rz: Float)
class HatAnchor(val y: Float, val r: Float)
class FaceAnchor(val y: Float, val z: Float, val gap: Float, val r: Float, val host: Host, val pitch: Float)
class Anchors(val hat: HatAnchor, val face: FaceAnchor, val neck: NeckAnchor)

// Altura y radios del cuello: donde el cuerpo empieza a ser más ancho
// que la cabeza. Coordenadas del grupo `squash`.
private fun neckOf(plan: Plan, host: Host, headY: Float): NeckAnchor {
    val w = plan.bodyW
    val h = plan.bodyH
    val d = plan.bodyD
    if (plan.blob) {
        val y = h * 0.3f
        return NeckAnchor(y, pearRadius(y, h, w / 2f) + 0.005f, pearRadius(y, h, d / 2f) + 0.005f)
    }
    val centerY = headY + host.center.y
    fun headAt(y: Float, r: Float) = r * sqrt(max(0f, 1f - ((y - centerY) / host.ry).pow(2)))
    var y = h * 0.75f
    var probe = h
    while (probe > h * 0.5f) {
        if (pearRadius(probe, h, w / 2f) >= headAt(probe, host.rx)) {
            y = probe
            break
        }
        probe -= 0.01f
    }
    return NeckAnchor(
        y,
        max(pearRadius(y, h, w / 2f), headAt(y, host.rx)) + 0.012f,
        max(pearRadius(y, h, d / 2f), headAt(y, host.rz)) + 0.012f,
    )
}

internal fun leafGeometry(length: Float, width: Float, thick: Float = 0.05f): Geometry {
    val shape = Shape().moveTo(0f, 0f)
        .bezierCurveTo(width * 0.75f, length * 0.2f, width * 0.55f, length * 0.8f, 0f, length)
        .bezierCurveTo(-width * 0.55f, length * 0.8f, -width * 0.75f, length * 0.2f, 0f, 0f)
    return extrudeGeometry(shape, thick, true, thick * 0.5f, thick * 0.5f, 3, 14).translate(0f, 0f, -thick / 2f)
}

private fun finGeometry(length: Float, width: Float, thick: Float = 0.05f): Geometry {
    val shape = Shape().moveTo(-width * 0.5f, 0f)
        .quadraticCurveTo(-width * 0.1f, length * 0.7f, width * 0.35f, length)
        .quadraticCurveTo(width * 0.6f, length * 0.35f, width * 0.5f, 0f)
        .quadraticCurveTo(0f, -length * 0.12f, -width * 0.5f, 0f)
    return extrudeGeometry(shape, thick, true, thick * 0.5f, thick * 0.5f, 3, 12).translate(0f, 0f, -thick / 2f)
}

// Gota con la punta hacia arriba; la base queda en y = 0.
private fun dropGeometry(radius: Float, height: Float): Geometry {
    val points = ArrayList<Vec2>()
    for (i in 0..24) {
        val t = (i / 24f) * PI_F
        points.add(Vec2(radius * sin(t) * sin(t / 2f).pow(1.25f) * 1.35f, height * (1f - (1f - cos(t)) / 2f)))
    }
    points.reverse()
    return latheGeometry(points, 22)
}

private fun cone(material: Material, radius: Float, height: Float): Mesh {
    val mesh = Mesh(coneGeometry(radius, height, 18, 1).translate(0f, height / 2f, 0f), material)
    mesh.castShadow = true
    return mesh
}

private fun shaped(geometry: Geometry, material: Material): Mesh {
    val mesh = Mesh(geometry, material)
    mesh.castShadow = true
    return mesh
}

// Punto y normal sobre un elipsoide. `yaw` gira desde el frente (+z)
// hacia la derecha; `pitch` sube desde el ecuador.
internal fun onSurface(host: Host, yaw: Float, pitch: Float): Pair<Vec3, Vec3> {
    val cp = cos(pitch)
    val point = Vec3(host.rx * sin(yaw) * cp, host.ry * sin(pitch), host.rz * cos(yaw) * cp)
    val normal = Vec3(point.x / (host.rx * host.rx), point.y / (host.ry * host.ry), point.z / (host.rz * host.rz)).normalize()
    point.add(host.center)
    return point to normal
}

private fun place(obj: Object3D, host: Host, yaw: Float, pitch: Float, sink: Float = 0f, axis: Vec3 = AXIS_Z): Vec3 {
    val (point, normal) = onSurface(host, yaw, pitch)
    obj.position.copy(point).addScaledVector(normal, -sink)
    obj.quaternion.setFromUnitVectors(axis, normal)
    return normal
}

// ── Construcción ──────────────────────────────────────────────

class Eye(val group: Group, val open: Group, val closed: Mesh, val lid: Mesh, val side: Int, val r: Float, val restLid: Float) {
    var lidValue = restLid
}

class Limb(val pivot: Object3D, val side: Int, val rest: Float)

class Rig(
    val root: Group,
    val bounce: Group,
    val squash: Group,
    val headPivot: Group,
    val eyes: List<Eye>,
    val mouths: Map<String, Object3D>,
    val mouth: Group,
    val cheeks: List<Mesh>,
    val ears: List<Limb>,
    val arms: List<Limb>,
    val feet: List<Mesh>,
    val tail: Group,
    val wings: List<Limb>,
    val top: Group,
    // Aureola de las radiantes, con su altura de reposo.
    val halo: Mesh? = null,
    val haloY: Float = 0f,
)

class Built(val obj: Group, val plan: Plan, val height: Float, val anchors: Anchors, val rig: Rig) {
    fun dispose() {
        obj.traverse { node ->
            if (node is Mesh && !node.geometry.shared) node.geometry.dispose()
        }
    }
}

fun buildCreature(formId: String, genes: Genes): Built {
    val plan = planFor(formId, genes)
    val mats = CreatureMaterials(genes, plan)
    val rng = Rng((genes.seed.toInt() xor 0x9e3779b9.toInt()).toLong())
    fun range(a: Float, b: Float) = rng.range(a.toDouble(), b.toDouble()).toFloat()
    val sock = if (plan.pattern == "calcetines") mats.accent else mats.body

    val root = Group()
    val bounce = Group()
    val squash = Group()
    val headPivot = Group()
    root.add(bounce)
    bounce.add(squash)
    root.scale.setScalar(plan.scale)

    val w = plan.bodyW
    val h = plan.bodyH
    val d = plan.bodyD
    val body = shaped(pearGeometry(w, h, d), mats.body)
    body.position.y = h / 2f
    body.receiveShadow = true

    // `host` es el elipsoide que lleva la cara: la cabeza, o el cuerpo
    // entero en las crías. Sus coordenadas son locales a headPivot.
    val host: Host
    if (plan.blob) {
        headPivot.position.set(0f, h * 0.42f, 0f)
        body.position.y = h / 2f - h * 0.42f
        headPivot.add(body)
        host = Host(Vec3(0f, h * 0.08f, 0f), w / 2f, h / 2f, d / 2f)
    } else {
        squash.add(body)
        headPivot.position.set(0f, h * 0.86f, 0f)
        val head = ball(mats.body, plan.headRx, plan.headRy, plan.headRz)
        head.position.y = plan.headRy * 0.72f
        head.receiveShadow = true
        headPivot.add(head)
        host = Host(head.position.clone(), plan.headRx, plan.headRy, plan.headRz)
    }
    squash.add(headPivot)
    val unit = host.rx / 0.5f

    // Cara
    val facePitch = if (plan.blob) 0.2f else 0.04f
    val eyeR = 0.074f * unit * plan.eyeSize
    val eyes = ArrayList<Eye>()
    for (side in intArrayOf(-1, 1)) {
        val group = Group()
        place(group, host, side * plan.eyeGap, facePitch, eyeR * 0.28f)
        val eyeball = ball(mats.eye, eyeR, eyeR * (if (plan.eyeStyle == 1) 1.28f else 1.06f), eyeR * 0.6f, true)
        eyeball.castShadow = false
        val glint = ball(mats.white, eyeR * 0.3f, eyeR * 0.3f, eyeR * 0.14f, true)
        glint.position.set(-eyeR * 0.3f, eyeR * 0.36f, eyeR * 0.52f)
        glint.castShadow = false
        val open = Group()
        open.add(eyeball, glint)
        if (plan.eyeStyle == 2) {
            val spark = ball(mats.white, eyeR * 0.16f, eyeR * 0.16f, eyeR * 0.1f, true)
            spark.position.set(eyeR * 0.28f, -eyeR * 0.3f, eyeR * 0.54f)
            spark.castShadow = false
            open.add(spark)
        }
        // Párpado: una tapa del color del cuerpo que baja sobre el ojo.
        val lid = ball(mats.body, eyeR * 1.1f, eyeR * 1.1f, eyeR * 0.66f, true)
        lid.castShadow = false
        lid.position.y = eyeR * (2.16f - plan.eyeLids * 2.12f)
        lid.visible = plan.eyeLids > 0.03f
        open.add(lid)
        val closed = Mesh(torusGeometry(eyeR * 0.82f, eyeR * 0.2f, 8, 20, PI_F), mats.line)
        closed.rotation.z = PI_F
        closed.position.set(0f, eyeR * 0.25f, eyeR * 0.3f)
        closed.visible = false
        group.add(open, closed)
        headPivot.add(group)
        eyes.add(Eye(group, open, closed, lid, side, eyeR, plan.eyeLids))
    }

    if (plan.brows) {
        for (side in intArrayOf(-1, 1)) {
            // Por encima del párpado caído, si lo hay, para que no lo tape.
            val lift = if (plan.eyeLids > 0.2f) 0.5f else 0.3f
            val brow = Mesh(capsuleGeometry(eyeR * 0.24f, eyeR * 1.5f, 4, 8), mats.line)
            place(brow, host, side * plan.eyeGap * 0.92f, facePitch + lift, -eyeR * 0.12f)
            brow.rotateZ(PI_F / 2f + side * 0.42f)
            headPivot.add(brow)
        }
    }

    val mouthR = 0.062f * unit
    val mouth = Group()
    place(mouth, host, 0f, facePitch - 0.27f, 0f)
    fun arc(flip: Boolean): Mesh {
        val mesh = Mesh(torusGeometry(mouthR, mouthR * 0.24f, 8, 20, PI_F), mats.line)
        mesh.rotation.z = if (flip) 0f else PI_F
        mesh.position.y = if (flip) -mouthR * 0.5f else mouthR * 0.4f
        return mesh
    }
    val mouthOpen = Group()
    val cavity = ball(mats.mouth, mouthR * 1.05f, mouthR * 0.9f, mouthR * 0.3f, true)
    val tongue = ball(mats.tongue, mouthR * 0.62f, mouthR * 0.42f, mouthR * 0.2f, true)
    tongue.position.set(0f, -mouthR * 0.42f, mouthR * 0.2f)
    cavity.castShadow = false
    tongue.castShadow = false
    mouthOpen.add(cavity, tongue)
    val flat = Mesh(capsuleGeometry(mouthR * 0.22f, mouthR * 1.3f, 4, 8), mats.line)
    flat.rotation.z = PI_F / 2f
    val mouths = linkedMapOf<String, Object3D>("smile" to arc(false), "sad" to arc(true), "open" to mouthOpen, "flat" to flat)
    for ((name, mesh) in mouths) {
        mesh.visible = name == "smile"
        mouth.add(mesh)
    }
    if (plan.fang) {
        val fang = cone(mats.ivory, mouthR * 0.26f, mouthR * 0.7f)
        fang.rotation.z = PI_F
        fang.position.set(mouthR * 0.62f, mouthR * 0.34f, mouthR * 0.1f)
        fang.castShadow = false
        mouth.add(fang)
    }
    headPivot.add(mouth)

    val cheeks = ArrayList<Mesh>()
    for (side in intArrayOf(-1, 1)) {
        val cheek = ball(mats.cheek.clone(), 0.085f * unit * plan.cheeks, 0.055f * unit * plan.cheeks, 0.02f * unit, true)
        cheek.castShadow = false
        place(cheek, host, side * (plan.eyeGap + 0.36f), facePitch - 0.2f, -0.004f)
        headPivot.add(cheek)
        cheeks.add(cheek)
    }

    // Orejas
    val ears = ArrayList<Limb>()
    val earSize = 0.2f * unit * plan.earSize
    for (side in intArrayOf(-1, 1)) {
        val pivot = Group()
        val mesh: Object3D
        when (plan.earType) {
            "round" -> {
                place(pivot, host, side * 0.95f, 0.92f, earSize * 0.3f, AXIS_Y)
                val outer = ball(sock, earSize, earSize, earSize * 0.5f)
                val inner = ball(mats.light, earSize * 0.58f, earSize * 0.58f, earSize * 0.3f, true)
                inner.position.z = earSize * 0.3f
                inner.castShadow = false
                outer.position.y = earSize * 0.6f
                inner.position.y = earSize * 0.6f
                mesh = Group().add(outer, inner)
            }
            "pointy" -> {
                place(pivot, host, side * 0.9f, 0.9f, earSize * 0.25f, AXIS_Y)
                mesh = cone(sock, earSize * 0.72f, earSize * 2f)
                mesh.scale.z = 0.6f
            }
            "leaf" -> {
                place(pivot, host, side * 1.05f, 0.8f, earSize * 0.2f, AXIS_Y)
                mesh = shaped(leafGeometry(earSize * 2.5f, earSize * 1.25f, earSize * 0.16f), mats.accent)
            }
            else -> {
                place(pivot, host, side * 1.2f, 0.62f, earSize * 0.2f, AXIS_Y)
                mesh = shaped(finGeometry(earSize * 1.55f, earSize * 1.05f, earSize * 0.16f), mats.accent)
                mesh.scale.x = side.toFloat()
            }
        }
        val rest = side * -(0.18f + plan.earDroop * 1.25f)
        pivot.rotateZ(rest * (if (plan.earType == "fin") 0.3f else 1f))
        pivot.add(mesh)
        headPivot.add(pivot)
        ears.add(Limb(pivot, side, pivot.rotation.z))
    }

    // Remate de la cabeza
    val top = Group()
    place(top, host, 0f, PI_F / 2f - 0.12f, 0.02f * unit, AXIS_Y)
    val ts = plan.topSize * unit
    when (plan.topType) {
        "tuft" -> {
            for (spec in arrayOf(floatArrayOf(0f, 0f, 0.24f), floatArrayOf(-0.07f, 0.5f, 0.18f), floatArrayOf(0.07f, -0.5f, 0.18f))) {
                val lock = shaped(dropGeometry(0.075f * ts, spec[2] * ts), mats.body)
                lock.position.x = spec[0] * ts
                lock.rotation.z = spec[1]
                top.add(lock)
            }
        }
        "sprout" -> {
            val stem = shaped(cylinderGeometry(0.022f * ts, 0.028f * ts, 0.2f * ts, 8), mats.accent)
            stem.position.y = 0.1f * ts
            top.add(stem)
            for (side in intArrayOf(-1, 1)) {
                val leaf = shaped(leafGeometry(0.26f * ts, 0.15f * ts, 0.025f * ts), mats.accent)
                leaf.position.y = 0.19f * ts
                leaf.rotation.set(0.3f, 0f, side * -1.05f)
                top.add(leaf)
            }
        }
        "drop" -> {
            val drop = shaped(dropGeometry(0.1f * ts, 0.3f * ts), mats.accent)
            drop.position.y = -0.03f * ts
            top.add(drop)
        }
        "cherry" -> {
            val cream = shaped(dropGeometry(0.12f * ts, 0.17f * ts), mats.ivory)
            cream.position.y = -0.02f * ts
            val berry = ball(mats.cherry, 0.072f * ts, 0.068f * ts, 0.072f * ts, true)
            berry.position.y = 0.19f * ts
            val stem = shaped(cylinderGeometry(0.008f * ts, 0.011f * ts, 0.13f * ts, 6), mats.line)
            stem.position.set(0.03f * ts, 0.29f * ts, 0f)
            stem.rotation.z = -0.4f
            top.add(cream, berry, stem)
        }
        "moon" -> {
            // Media luna de pie, con las puntas hacia arriba y un poco de lado.
            val moon = shaped(torusGeometry(0.12f * ts, 0.042f * ts, 10, 28, PI_F * 1.2f), mats.moon)
            moon.rotation.z = PI_F * 1.15f
            moon.position.y = 0.15f * ts
            top.add(moon)
        }
        else -> {
            for (spec in arrayOf(floatArrayOf(-0.15f, 0.5f, 0.2f), floatArrayOf(0f, 0f, 0.3f), floatArrayOf(0.15f, -0.5f, 0.2f))) {
                val spike = cone(mats.accent, 0.07f * ts, spec[2] * ts)
                spike.position.x = spec[0] * ts
                spike.rotation.z = spec[1]
                top.add(spike)
            }
        }
    }
    headPivot.add(top)

    // Dibujo del ADN
    if (plan.pattern == "motas") {
        val count = 4 + (plan.patternAmt * 6f).let { kotlin.math.floor(it + 0.5f).toInt() }
        for (i in 0 until count) {
            val size = range(0.045f, 0.085f) * unit
            val spot = ball(mats.accent, size, size, size * 0.2f, true)
            spot.castShadow = false
            val yaw = range(0.85f, 2.2f) * (if (rng.chance(0.5)) 1f else -1f)
            place(spot, host, yaw, range(-0.1f, 1.05f), -size * 0.02f)
            headPivot.add(spot)
        }
    } else if (plan.pattern == "rayas") {
        for (yaw in floatArrayOf(-0.42f, 0f, 0.42f)) {
            val stripe = ball(mats.accent, 0.036f * unit, 0.13f * unit * (0.6f + plan.patternAmt * 0.5f), 0.016f * unit, true)
            stripe.castShadow = false
            place(stripe, host, yaw, if (plan.blob) 1.02f else 0.98f, -0.004f)
            headPivot.add(stripe)
        }
    }

    if (plan.belly) {
        val belly = ball(mats.light, w * 0.3f, h * (if (plan.blob) 0.22f else 0.33f), d * 0.1f)
        belly.castShadow = false
        belly.position.set(0f, if (plan.blob) h * 0.22f else h * 0.45f, d * (if (plan.blob) 0.415f else 0.43f))
        belly.rotation.x = if (plan.blob) 0.5f else -0.12f
        squash.add(belly)
    }

    // Extremidades
    val feet = ArrayList<Mesh>()
    for (side in intArrayOf(-1, 1)) {
        val foot = ball(sock, 0.12f * plan.feet, 0.075f * plan.feet, 0.16f * plan.feet)
        foot.position.set(side * w * 0.24f, 0.06f * plan.feet, d * 0.3f)
        squash.add(foot)
        feet.add(foot)
    }
    val arms = ArrayList<Limb>()
    if (plan.arms) {
        for (side in intArrayOf(-1, 1)) {
            val pivot = Group()
            pivot.position.set(side * w * 0.44f, h * 0.62f, d * 0.1f)
            val arm = ball(sock, 0.075f, 0.15f, 0.085f)
            arm.position.y = -0.11f
            pivot.add(arm)
            pivot.rotation.z = side * 0.5f
            squash.add(pivot)
            arms.add(Limb(pivot, side, side * 0.5f))
        }
    }

    // Cola: asoma por un lado para verse desde delante.
    val tail = Group()
    tail.position.set(w * 0.36f, h * 0.2f, -d * 0.36f)
    val tsz = plan.tailSize
    when (plan.tailType) {
        "pom" -> tail.add(ball(mats.light, 0.13f * tsz).translateX(0.1f * tsz))
        "leaf" -> {
            val leaf = shaped(leafGeometry(0.4f * tsz, 0.2f * tsz, 0.03f), mats.accent)
            leaf.rotation.z = -1f
            tail.add(leaf)
        }
        "drop" -> {
            val drop = shaped(dropGeometry(0.1f * tsz, 0.34f * tsz), mats.accent)
            drop.rotation.z = -1.15f
            tail.add(drop)
        }
        else -> {
            val spike = cone(mats.accent, 0.09f * tsz, 0.38f * tsz)
            spike.rotation.z = -1.1f
            tail.add(spike)
        }
    }
    squash.add(tail)

    // Pieza de adulta
    val wings = ArrayList<Limb>()
    var halo: Mesh? = null
    when (plan.extra) {
        "halo" -> {
            val ring = shaped(torusGeometry(0.22f * unit, 0.024f * unit, 10, 36), mats.glow)
            ring.castShadow = false
            ring.rotation.set(PI_F / 2f - 0.22f, 0f, 0.1f)
            // Por encima del remate de la cabeza; el brote de las hojas es más alto.
            val lift = if (plan.topType == "sprout") 0.46f else 0.32f
            ring.position.set(0f, host.center.y + host.ry + lift * unit, -0.03f * unit)
            headPivot.add(ring)
            halo = ring
        }
        "mane" -> {
            val neckY = h * 0.9f
            val count = 11
            for (i in 0 until count) {
                val angle = (i / count.toFloat()) * PI_F * 2f
                val radius = w * 0.4f
                val piece: Mesh
                if (plan.family == "hoja") {
                    piece = shaped(leafGeometry(0.24f, 0.16f, 0.03f), mats.accent)
                    piece.rotation.set(PI_F / 2f - 0.35f, 0f, 0f)
                    piece.rotation.order = Euler.Order.YXZ
                    piece.rotation.y = angle
                } else {
                    piece = ball(if (plan.family == "gota") mats.bubble else mats.light, 0.11f, 0.1f, 0.11f, true)
                }
                piece.position.set(sin(angle) * radius, neckY, cos(angle) * radius * 0.92f)
                squash.add(piece)
            }
        }
        "wings" -> {
            for (side in intArrayOf(-1, 1)) {
                val pivot = Group()
                pivot.position.set(side * w * 0.34f, h * 0.72f, -d * 0.3f)
                val wing: Object3D
                when (plan.family) {
                    "pelusa" -> {
                        wing = Group()
                        for (i in 0 until 3) {
                            val feather = ball(mats.light, 0.07f, 0.2f - i * 0.035f, 0.03f, true)
                            feather.position.set(0f, 0.16f - i * 0.02f, 0f)
                            feather.rotation.z = -i * 0.45f
                            wing.add(feather)
                        }
                    }
                    "hoja" -> wing = shaped(leafGeometry(0.48f, 0.26f, 0.03f), mats.accent)
                    else -> wing = shaped(finGeometry(0.44f, 0.3f, 0.03f), mats.accent)
                }
                wing.scale.x = side.toFloat()
                pivot.add(wing)
                pivot.rotation.z = side * -0.95f
                squash.add(pivot)
                wings.add(Limb(pivot, side, side * -0.95f))
            }
        }
        "crown" -> {
            val count = 7
            for (i in 0 until count) {
                val angle = (i / count.toFloat()) * PI_F * 2f
                val piece = Group()
                val material = when (plan.family) {
                    "gota" -> mats.ivory
                    "hoja" -> mats.accent
                    else -> mats.light
                }
                piece.add(ball(material, 0.06f * unit, 0.06f * unit, 0.06f * unit, true))
                if (plan.family == "hoja") {
                    val heart = ball(mats.center, 0.03f * unit, 0.03f * unit, 0.03f * unit, true)
                    heart.position.z = 0.045f * unit
                    piece.add(heart)
                }
                val (point, normal) = onSurface(host, angle, 1.02f)
                piece.position.copy(point)
                piece.quaternion.setFromUnitVectors(AXIS_Z, normal)
                headPivot.add(piece)
            }
        }
        "horns" -> {
            for (side in intArrayOf(-1, 1)) {
                val horn = Group()
                place(horn, host, side * 0.62f, 1.12f, 0.03f * unit, AXIS_Y)
                val lower = cone(mats.ivory, 0.075f * unit, 0.22f * unit)
                val upper = cone(mats.ivory, 0.05f * unit, 0.2f * unit)
                upper.position.y = 0.16f * unit
                upper.rotation.z = side * 0.5f
                horn.add(lower, upper)
                horn.rotateZ(side * -0.2f)
                headPivot.add(horn)
            }
            for (i in 0 until 3) {
                val spike = cone(mats.accent, 0.07f, 0.2f - i * 0.03f)
                spike.position.set(0f, h * (0.75f - i * 0.2f), -d * (0.42f + i * 0.04f))
                spike.rotation.x = -1.1f - i * 0.2f
                squash.add(spike)
            }
        }
    }

    val hostTop = host.center.y + host.ry
    val anchors = Anchors(
        // Todo en coordenadas de headPivot, salvo `neck`, que va en `squash`.
        hat = HatAnchor(hostTop, host.rx),
        face = FaceAnchor(host.center.y + host.ry * sin(facePitch), host.rz, plan.eyeGap, host.rx, host, facePitch),
        neck = neckOf(plan, host, headPivot.position.y),
    )
    val height = (headPivot.position.y + hostTop) * plan.scale

    return Built(root, plan, height, anchors, Rig(root, bounce, squash, headPivot, eyes, mouths, mouth, cheeks, ears, arms, feet, tail, wings, top, halo, halo?.let { it.position.y } ?: 0f))
}

// ── Animación ─────────────────────────────────────────────────

// Envuelve una criatura construida y le da vida: respira, parpadea,
// mira y reacciona. `update` se llama en cada fotograma.
class Actor(private val built: Built, private val anim: Animator) {
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
    private var talk = 0f
    private var face = "smile"
    private var now = 0f
    var timeline: Timeline? = null
        private set

    fun dispose() = built.dispose()

    fun setMouth(name: String) {
        if (face == name) return
        face = name
        for ((key, mesh) in rig.mouths) mesh.visible = key == name
    }

    fun setMood(value: String) {
        mood = value
        val asleep = value == "dormida"
        for (eye in rig.eyes) {
            eye.open.visible = !asleep
            eye.closed.visible = asleep
        }
        if (!busy) setMouth(restMouth())
        val pale = value == "enferma"
        for (cheek in rig.cheeks) {
            cheek.material.color.setHex(if (pale) "#9ec3ea" else "#ff8fa9")
            cheek.material.opacity = if (value == "feliz") 0.8f else 0.62f
        }
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
        val depth = if (asleep) 0.034f else 0.02f
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
                lean * 0.22f + (if (asleep) 0.1f else 0f) + sin(t * 0.7f) * 0.02f,
            )
        }
        for (eye in rig.eyes) eye.open.position.set(gaze.x * 0.012f, gaze.y * 0.008f, 0f)

        // Parpadeo
        if (!asleep && t > blinkAt) {
            blink = 0.13f
            blinkAt = t + 2.2f + Random.nextFloat() * 3.6f
        }
        blink = max(0f, blink - dt)
        val shut = if (blink > 0f) 1f else if (low) 0.3f else 0f
        for (eye in rig.eyes) {
            val lid = max(eye.restLid, shut)
            eye.lidValue += (lid - eye.lidValue) * (1f - exp(-dt * 40f))
            eye.lid.visible = eye.lidValue > 0.03f
            eye.lid.position.y = eye.r * (2.16f - eye.lidValue * 2.12f)
        }

        // Orejas, cola, alas y brazos
        val wiggle = sin(t * 1.3f) * 0.04f + (if (happy) sin(t * 7f) * 0.03f else 0f)
        for (ear in rig.ears) ear.pivot.rotation.z = ear.rest + ear.side * (wiggle - (if (low) 0.22f else 0f))
        rig.tail.rotation.y = sin(t * (if (happy) 7f else 2.2f)) * (if (asleep) 0.03f else if (happy) 0.5f else 0.18f)
        for (wing in rig.wings) {
            wing.pivot.rotation.z = wing.rest + wing.side * sin(t * (if (happy) 9f else 2.6f)) * (if (asleep) 0.03f else 0.22f)
        }
        for (arm in rig.arms) arm.pivot.rotation.z = arm.rest + arm.side * sin(t * 1.9f + arm.side) * 0.07f
        rig.halo?.let { it.position.y = rig.haloY + sin(t * 1.6f) * 0.012f }

        rig.bounce.position.x = if (mood == "enferma") sin(t * 46f) * 0.006f else 0f

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
        val tl = anim.timeline(onComplete = {
            busy = false
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
    }

    fun eat(onBite: (Int) -> Unit): Timeline {
        setMouth("open")
        return act { tl, rig ->
            for (i in 0 until 3) {
                tl.to(rig.headPivot.rotation.to(x = 0.34f), 0.14f, Eases.power2Out)
                    .call {
                        setMouth("flat")
                        onBite(i)
                    }
                    .to(rig.squash.scale.to(1.06f, 0.94f, 1.06f), 0.08f, at = Pos.withPrev())
                    .to(rig.headPivot.rotation.to(x = 0.02f), 0.16f, Eases.power2InOut)
                    .to(rig.squash.scale.to(1f, 1f, 1f), 0.14f, at = Pos.withPrev())
                    .call { setMouth(if (i < 2) "open" else "smile") }
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
            tl.to(rig.root.rotation.to(z = 0.12f), 0.12f, Eases.power1InOut, repeat = 7, yoyo = true)
                .to(rig.root.rotation.to(z = 0f), 0.2f, Eases.power2Out)
                .to(rig.squash.scale.to(1.1f, 0.9f, 1.1f), 0.1f)
                .to(rig.squash.scale.to(1f, 1f, 1f), 0.4f, Eases.elasticOut(1f, 0.45f))
        }
    }

    fun nuzzle(): Timeline {
        setMouth("smile")
        return act { tl, rig ->
            tl.to(rig.squash.scale.to(1.08f, 0.92f, 1.08f), 0.16f, Eases.power2Out)
                .to(rig.headPivot.rotation.to(x = 0.1f, z = leanTarget * 0.34f), 0.2f, Eases.power2Out, Pos.withPrev())
                .to(rig.squash.scale.to(1f, 1f, 1f), 0.5f, Eases.elasticOut(1f, 0.4f))
                .to(rig.headPivot.rotation.to(x = 0f, z = 0f), 0.35f, Eases.power2InOut, Pos.withPrev(0.1f))
        }
    }

    fun spin(): Timeline {
        setMouth("open")
        return act { tl, rig ->
            tl.to(rig.bounce.position.to(y = 0.34f), 0.26f, Eases.power2Out)
                .to(rig.root.rotation.to(y = PI_F * 2f), 0.6f, Eases.power2InOut, Pos.withPrev())
                .to(rig.bounce.position.to(y = 0f), 0.22f, Eases.power2In, Pos.afterPrev(-0.24f))
                .set(rig.root.rotation.to(y = 0f))
                .to(rig.squash.scale.to(1.14f, 0.84f, 1.14f), 0.08f)
                .to(rig.squash.scale.to(1f, 1f, 1f), 0.4f, Eases.elasticOut(1f, 0.45f))
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
            eye.lid.visible = eye.lidValue > 0.03f
            eye.lid.position.y = eye.r * (2.16f - eye.lidValue * 2.12f)
        }
    }
}
