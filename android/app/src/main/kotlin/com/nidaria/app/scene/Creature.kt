// Construye la criatura 3D (monstruo, perro o gato) y la anima, como
// creature.js en la web. Cada especie se esculpe en su archivo
// (Monster.kt, Pet.kt) con las piezas comunes de aquí y de Eyes.kt; aquí
// están los tipos, los huesos y la animación: respira, parpadea, mira y
// reacciona.
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
import com.nidaria.app.rules.getForm
import com.nidaria.app.rules.isPet
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

internal const val BODY = 0
internal const val HEAD = 1
internal const val EAR_L = 2
internal const val EAR_R = 3
internal const val ARM_L = 4
internal const val ARM_R = 5
internal const val TAIL = 6
internal const val BONES = 7
internal val PARENT = intArrayOf(-1, BODY, HEAD, HEAD, BODY, BODY, BODY)

internal fun V3.gl(): Vec3 = Vec3(x.toFloat(), y.toFloat(), z.toFloat())
internal fun Object3D.place(p: V3): Object3D {
    position.set(p.x.toFloat(), p.y.toFloat(), p.z.toFloat())
    return this
}
internal fun Object3D.turn(q: Q): Object3D {
    quaternion.set(q.x.toFloat(), q.y.toFloat(), q.z.toFloat(), q.w.toFloat())
    return this
}

internal fun hsl(h: Double, s: Double, l: Double): Col =
    Col.hsl((((h % 360) + 360) % 360) / 360, clampD(s, 0.0, 100.0) / 100, clampD(l, 0.0, 100.0) / 100)

internal fun decalMaterial(map: Texture, build: (Material.() -> Unit)? = null): Material = Material.standard(Color(1f, 1f, 1f), 0.7f) {
    this.map = map
    transparent = true
    depthWrite = false
    polygonOffset = true
    build?.invoke(this)
}

internal val EYE = Material.standard(Color.hex("#1e120d"), 0.18f) {
    clearcoat = 1f
    clearcoatRoughness = 0.06f
    shared = true
}
internal val GLINT = Material.basic(Color.hex("#ffffff")).also { it.shared = true }
internal val BALL = sphereGeometry(1f, 24, 16).keep()

// Base ortonormal con z = normal y la y lo más hacia arriba posible.
internal fun faceFrame(normal: V3, up: V3 = AXIS_Y): Q {
    val z = normal.clone().normalize()
    val x = V3().crossVectors(up, z).normalize()
    val y = V3().crossVectors(z, x)
    return Q().setFromBasis(x, y, z)
}

internal fun quatFromTo(from: V3, to: V3): Q = Q().setFromUnitVectors(from, to.clone().normalize())

// ── Anclajes para la ropa (en Double, como en la web) ──
class Host(val center: V3, val rx: Double, val ry: Double, val rz: Double)
class HatAnchor(val y: Double, val r: Double)
class FaceAnchor(val y: Double, val z: Double, val gap: Double, val r: Double, val host: Host, val pitch: Double)
class NeckAnchor(val y: Double, val rx: Double, val rz: Double)
class Anchors(val hat: HatAnchor, val face: FaceAnchor, val neck: NeckAnchor)

// `lid` y `ball`: el párpado y el globo de los ojos nuevos (Eyes.kt).
class Eye(val group: Group, val open: Group, val closed: Mesh, val side: Int, val r: Float, val tall: Float, val content: Boolean, val restLid: Float, val lid: Object3D? = null, val ball: Object3D? = null) {
    var lidValue = 0f
}

// `leg`: pata de perro o gato, que no se balancea como un brazo.
class Limb(val pivot: Object3D, val side: Int, val rest: Float, val leg: Boolean = false, val restX: Float = 0f)

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
    // Opacidad de los coloretes en reposo y cuando está feliz.
    val blushRest: Float = 0.62f,
    val blushHappy: Float = 0.8f,
)

// Lo que la habitación necesita saber del cuerpo para encuadrarlo.
class Look(val family: String, val stage: String, val scale: Double, val blob: Boolean, val bodyW: Double, val headRx: Double)

class Built(val obj: Group, val plan: Look, val height: Float, val anchors: Anchors, val rig: Rig) {
    fun dispose() {
        obj.traverse { node -> if (node is Mesh && !node.geometry.shared) node.geometry.dispose() }
    }
}

// `detail`: 'high' para la habitación, 'low' para miniaturas y 'tiny' para
// las siluetas del Criadex, que solo necesitan el contorno. Cada especie
// esculpe su figura en su archivo (Monster.kt, Pet.kt), como en la web.
fun buildCreature(formId: String, genes: Genes, detail: String = "high"): Built {
    val family = getForm(formId).family ?: genes.family
    return if (isPet(family)) buildPet(formId, genes, detail) else buildMonster(formId, genes, detail)
}

// Radio de la figura a la altura `y` en la dirección (dx, dz).
internal fun radiusAt(S: Sculpt, y: Double, dx: Double, dz: Double): Double {
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
internal fun neckOf(S: Sculpt, plan: Plan, host: Host): NeckAnchor {
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

// Cierra el ojo según `lidValue` (0 abierto, 1 cerrado): con párpado, lo
// baja; sin él, aplasta la cuenta.
private fun setLid(eye: Eye) {
    val lid = eye.lid
    if (lid != null) lid.rotation.x = LID_OPEN + (LID_SHUT - LID_OPEN) * eye.lidValue
    else eye.open.scale.y = max(0.08f, 1f - eye.lidValue * 0.95f)
}

// Envuelve una criatura construida y le da vida: respira, parpadea,
// mira y reacciona. `update` se llama en cada fotograma.
class Actor(private val built: Built, private val anim: Animator, private val still: Boolean = false) {
    val obj: Group get() = built.obj
    val plan: Look get() = built.plan
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
            cheek.material.opacity = if (pale) max(rig.blushHappy, 0.45f) else if (value == "feliz") rig.blushHappy else rig.blushRest
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
        for (eye in rig.eyes) {
            // Con globo, mira girándolo; sin él, mueve la cuenta.
            val ball = eye.ball
            if (ball != null) ball.rotation.set(-gaze.y * 0.3f, gaze.x * 0.42f, 0f)
            else eye.open.position.set(gaze.x * eye.r * 0.3f, gaze.y * eye.r * 0.2f, 0f)
        }

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
            setLid(eye)
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
        for (arm in rig.arms) {
            // Las patas de perros y gatos no se balancean como brazos.
            if (arm.leg) arm.pivot.rotation.x = arm.restX
            else arm.pivot.rotation.z = arm.rest + arm.side * sin(t * 1.9f + arm.side) * 0.08f
        }
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
            setLid(eye)
        }
    }
}
