// La habitación: escena 3D, cámara, luces, la criatura o los huevos que
// hay sobre la alfombra, las partículas y las secuencias (comer, baño,
// eclosión, evolución). Vive entera en el hilo de dibujo; la interfaz le
// habla a través de RoomView.

package com.nidaria.app.scene

import com.nidaria.app.audio.Sfx
import com.nidaria.app.gl.Animator
import com.nidaria.app.gl.Channel
import com.nidaria.app.gl.Color
import com.nidaria.app.gl.DirectionalLight
import com.nidaria.app.gl.Eases
import com.nidaria.app.gl.Geometry
import com.nidaria.app.gl.Group
import com.nidaria.app.gl.HemisphereLight
import com.nidaria.app.gl.Mat4
import com.nidaria.app.gl.Material
import com.nidaria.app.gl.MaterialKind
import com.nidaria.app.gl.Mesh
import com.nidaria.app.gl.Object3D
import com.nidaria.app.gl.PI_F
import com.nidaria.app.gl.PerspectiveCamera
import com.nidaria.app.gl.Pos
import com.nidaria.app.gl.Props
import com.nidaria.app.gl.Quat
import com.nidaria.app.gl.Scene
import com.nidaria.app.gl.Shape
import com.nidaria.app.gl.Vec3
import com.nidaria.app.gl.boxGeometry
import com.nidaria.app.gl.by
import com.nidaria.app.gl.clampF
import com.nidaria.app.gl.extrudeGeometry
import com.nidaria.app.gl.planeGeometry
import com.nidaria.app.gl.rayHitsUnitSphere
import com.nidaria.app.gl.shapeGeometry
import com.nidaria.app.gl.sphereGeometry
import com.nidaria.app.gl.tanDeg
import com.nidaria.app.gl.to
import com.nidaria.app.gl.toAll
import com.nidaria.app.data.Decor
import com.nidaria.app.rules.Creature
import com.nidaria.app.rules.EggLook
import com.nidaria.app.rules.Equipped
import com.nidaria.app.rules.FORM_LIST
import com.nidaria.app.rules.Genes
import com.nidaria.app.rules.createGenes
import com.nidaria.app.rules.eggLook
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private const val FOV = 30f
private const val FLOOR_Y = 0.03f
private const val EGG_SCALE = 1.08f

private class Palette(val wall: Color, val floor: Color, val trim: Color, val key: Color, val back: Color)

private val DAY = Palette(
    Color.hex("#ffffff"), Color.hex("#ffffff"), Color.hex("#fffdf8"), Color.hex("#fff4e2"), Color.hex("#bfe3ff"),
)
private val NIGHT = Palette(
    Color.hex("#5c6ca8"), Color.hex("#6d6b99"), Color.hex("#8d93bd"), Color.hex("#aebfff"), Color.hex("#47619f"),
)

// Dónde va cada mueble: el rincón a la derecha de la criatura y el sitio
// de la ventana a la izquierda. En el móvil, lo que pasa de medio metro en
// el rincón queda detrás de los botones de la derecha.
private val SPOT_AT = mapOf("rincon" to Vec3(1.0f, 0f, -1.8f), "ventana" to Vec3(-0.8f, 0f, -1.85f))

// Una capa de la alfombra con su color de día y el de noche.
private class RugTint(val material: Material, val day: Color, val night: Color)

private fun archShape(width: Float, straight: Float): Shape {
    val r = width / 2f
    return Shape().moveTo(-r, 0f).lineTo(-r, straight).absarc(0f, straight, r, PI_F, 0f, true).lineTo(r, 0f).closePath()
}

// Marco de la ventana: un arco con el hueco del cristal. Como el hueco
// llega hasta abajo, el contorno es una sola U invertida.
internal fun frameShape(outer: Float, inner: Float, straight: Float): Shape {
    val ro = outer / 2f
    val ri = inner / 2f
    return Shape().moveTo(-ro, 0f).lineTo(-ro, straight).absarc(0f, straight, ro, PI_F, 0f, true).lineTo(ro, 0f)
        .lineTo(ri, 0f).lineTo(ri, straight).absarc(0f, straight, ri, 0f, PI_F, false).lineTo(-ri, 0f).closePath()
}

class Room(private val painter: Painter, private val reduce: Boolean) {
    interface Listener {
        fun onPet()
        fun onTap()
        fun onEggTap()
        fun onEggPick(index: Int)
    }

    private class Hit(val x: Float, val y: Float, val rx: Float, val ry: Float, val rz: Float)
    private class Press(val id: Int, var x: Float, var y: Float, val index: Int) {
        var moved = 0f
        var stroke = 0f
        var strokes = 0
    }

    val anim = Animator()
    val scene = Scene()
    val camera = PerspectiveCamera(FOV, 1f, 0.1f, 80f)
    var listener: Listener? = null

    // ── Luces ──
    private val hemi = HemisphereLight(Color.hex("#ffffff"), Color.hex("#ffe6cc"), 1.05f)
    private val key = DirectionalLight(DAY.key, 2.1f)
    private val rim = DirectionalLight(Color.hex("#cfe6ff"), 0.7f)

    // ── Habitación ──
    // El papel pintado cambia con la decoración (ver setDecor).
    private val wallMat = Material.basic { map = wallpaperTexture(BASE_DECOR.getValue("pared")!!); uvScaleX = 60f / TILE_METERS; uvScaleY = 24f / TILE_METERS }
    private val floorMat = Material.basic { map = painter.floor(); uvScaleX = 60f / 1.4f; uvScaleY = 40f / 1.4f }
    private val trimMat = Material.basic(DAY.trim)
    // La alfombra va dentro de `rug`, que el encuadre escala.
    private val rug = Group()
    private var rugTints: List<RugTint> = emptyList()

    // Sitios para los muebles que pone el jugador, y lo que hay puesto.
    private val spots = SPOT_AT.mapValues { (_, at) -> Group().also { it.position.copy(at) } }
    private val placed = HashMap<String, String?>()
    private val shadowMat = Material(MaterialKind.Shadow).also {
        it.color.setHex("#5b3a29")
        it.opacity = 0.22f
        it.transparent = true
    }
    private val skyMap = painter.newSky()
    private val patchMat = Material.basic(Color.hex("#fffdf2")) {
        transparent = true
        opacity = 0.5f
        doubleSide = true
        depthWrite = false
    }

    // ── Soporte de la criatura ──
    private val mount = Group()
    private val fx = Group()
    private val blobMat = Material.basic { map = painter.blob(); transparent = true; depthWrite = false }
    private val blob = Mesh(planeGeometry(1f, 1f), blobMat)
    private var blobBase = 1f

    // Baja a cero cuando la criatura se despide, para que su sombra no
    // se quede sola en la alfombra.
    private var blobFade = 1f
    private var hits: List<Hit> = emptyList()

    private var actor: Actor? = null
    private var eggs: List<Group> = emptyList()
    private var mode = "none"
    private var currentKey = ""
    private var eggProgress = 0f
    private var selectedEgg = -1
    private var night = false
    var paused = false

    // ── Partículas ──
    private val heartGeo: Geometry = extrudeGeometry(
        Shape().moveTo(0f, -0.45f).bezierCurveTo(-0.95f, 0.1f, -0.5f, 0.78f, 0f, 0.3f).bezierCurveTo(0.5f, 0.78f, 0.95f, 0.1f, 0f, -0.45f),
        0.14f, true, 0.05f, 0.05f, 2,
    ).center().also { it.shared = true }
    private val starGeo: Geometry = run {
        val s = Shape()
        for (i in 0 until 8) {
            val r = if (i % 2 == 1) 0.17f else 0.5f
            val a = (i / 8f) * PI_F * 2f + PI_F / 2f
            if (i == 0) s.moveTo(cos(a) * r, sin(a) * r) else s.lineTo(cos(a) * r, sin(a) * r)
        }
        s.closePath()
        extrudeGeometry(s, 0.08f).center().also { it.shared = true }
    }
    private val dotGeo = sphereGeometry(0.5f, 14, 10).also { it.shared = true }
    private val spriteGeo = planeGeometry(1f, 1f).also { it.shared = true }
    private val zMap = painter.z()

    // ── Día y noche ──
    private var dimK = 0f
    private var skyPhase = "day"
    // El tiempo que hace fuera: «lluvia», «nieve»… o null para el cielo de siempre.
    private var weather: String? = null
    private var skyAt = -1000f

    // ── Encuadre ── (medidas en dp, como los píxeles CSS de la web)
    private var focusTop = 0f
    private var focusBottom = 0f
    private var subjectH = 1.55f
    private var subjectW = 1.3f
    private val focusTarget = Any()
    private val subjectTarget = Any()
    private var width = 1f
    private var height = 1f

    private var press: Press? = null
    private var last = 0f
    private var lastReal = 0f
    private var snoreAt = 0f
    private var now = 0f

    init {
        scene.background = DAY.back.clone()
        scene.environmentIntensity = 0.5f
        scene.hemisphere = hemi
        key.position.set(-2.6f, 5.2f, 4.2f)
        key.target.set(0f, 0.5f, 0f)
        key.castShadow = true
        key.shadowMapSize = 1024
        key.shadowLeft = -2.4f
        key.shadowRight = 2.4f
        key.shadowTop = 2.6f
        key.shadowBottom = -1.6f
        key.shadowNear = 1f
        key.shadowFar = 14f
        key.shadowBias = -0.0006f
        key.shadowNormalBias = 0.02f
        rim.position.set(3.2f, 2.6f, -2.2f)
        scene.lights.add(key)
        scene.lights.add(rim)

        val wall = Mesh(planeGeometry(60f, 24f), wallMat)
        wall.position.set(0f, 12f, -2.4f)
        val floor = Mesh(planeGeometry(60f, 40f), floorMat)
        floor.rotation.x = -PI_F / 2f
        floor.position.z = 17.6f
        val baseboard = Mesh(boxGeometry(60f, 0.16f, 0.06f), trimMat)
        baseboard.position.set(0f, 0.08f, -2.37f)


        val shadowPlane = Mesh(planeGeometry(12f, 10f), shadowMat)
        shadowPlane.rotation.x = -PI_F / 2f
        shadowPlane.position.y = FLOOR_Y + 0.004f
        shadowPlane.receiveShadow = true

        // Ventana de arco
        val win = Group()
        win.position.set(-0.68f, 0.98f, -2.39f)
        val paneGeo = shapeGeometry(archShape(0.86f, 0.62f), 20)
        paneGeo.uvs?.let { uv ->
            for (i in 0 until paneGeo.vertexCount) {
                uv[i * 2] = (paneGeo.positions[i * 3] + 0.43f) / 0.86f
                uv[i * 2 + 1] = paneGeo.positions[i * 3 + 1] / 1.05f
            }
        }
        val pane = Mesh(paneGeo, Material.basic { map = skyMap })
        pane.position.z = 0.012f
        val frameMat = Material.standard(Color.hex("#fffdf8"), 0.75f)
        val casing = Mesh(extrudeGeometry(frameShape(1.02f, 0.86f, 0.62f), 0.07f, curveSegments = 20), frameMat)
        val barV = Mesh(boxGeometry(0.035f, 1.04f, 0.035f), frameMat)
        barV.position.set(0f, 0.52f, 0.04f)
        val barH = Mesh(boxGeometry(0.86f, 0.035f, 0.035f), frameMat)
        barH.position.set(0f, 0.62f, 0.04f)
        val sill = Mesh(boxGeometry(1.2f, 0.07f, 0.18f), frameMat)
        sill.position.set(0f, -0.035f, 0.07f)
        win.add(pane, casing, barV, barH, sill)

        // Luz de la ventana sobre el suelo
        val patchGeo = shapeGeometry(archShape(0.8f, 0.5f), 16).rotateX(PI_F / 2f)
        run {
            val pos = patchGeo.positions
            var i = 0
            while (i < pos.size) {
                pos[i] += pos[i + 2] * 0.55f
                i += 3
            }
        }
        val patch = Mesh(patchGeo, patchMat)
        patch.position.set(-0.62f, 0.006f, -2.1f)

        // Muebles fijos: solo asoman en pantallas anchas.
        fun lit(color: String, roughness: Float = 0.7f) = Material.standard(Color.hex(color), roughness)
        val shelf = Group()
        shelf.add(Mesh(boxGeometry(1.0f, 0.05f, 0.24f), lit("#e2b279")))
        val books = listOf(
            Triple("#ff8fb1", 0.26f, -0.34f) to 0f, Triple("#9db7ff", 0.3f, -0.24f) to 0f,
            Triple("#ffd166", 0.22f, -0.15f) to 0f, Triple("#7ed6a5", 0.26f, -0.02f) to 0.32f,
        )
        for ((spec, tilt) in books) {
            val (color, tall, x) = spec
            val book = Mesh(boxGeometry(0.07f, tall, 0.17f), lit(color))
            book.position.set(x, 0.025f + tall / 2f, 0f)
            book.rotation.z = -tilt
            shelf.add(book)
        }
        val globe = Mesh(sphereGeometry(0.1f, 20, 14), lit("#ff7a7a", 0.5f))
        globe.position.set(0.3f, 0.125f, 0f)
        shelf.add(globe)
        shelf.position.set(1.9f, 1.5f, -2.27f)

        val pouf = Group()
        val cushion = Mesh(sphereGeometry(1f, 28, 18), lit("#7ed6a5", 0.9f))
        cushion.scale.set(0.42f, 0.24f, 0.42f)
        cushion.position.y = 0.24f
        cushion.castShadow = true
        val button = Mesh(sphereGeometry(0.05f, 12, 8), lit("#4fae7c"))
        button.position.y = 0.47f
        pouf.add(cushion, button)
        pouf.position.set(-1.75f, 0f, -1.25f)

        scene.add(wall, floor, baseboard, patch, rug, shadowPlane, win, spots.getValue("rincon"), spots.getValue("ventana"), shelf, pouf)

        mount.position.y = FLOOR_Y
        scene.add(mount, fx)
        blob.rotation.x = -PI_F / 2f
        blob.position.y = FLOOR_Y + 0.008f
        blob.visible = false
        scene.add(blob)

        setDecor(Decor())
        refreshSky()
    }

    // ── Decoración ──

    // Lo que manda el servidor; lo que vale null es lo de base.
    fun setDecor(decor: Decor) {
        fun want(spot: String) = decor.spot(spot) ?: BASE_DECOR[spot]
        val pared = want("pared")!!
        if (placed["pared"] != pared) {
            placed["pared"] = pared
            wallMat.map = wallpaperTexture(pared)
        }
        val alfombra = want("alfombra")!!
        if (placed["alfombra"] != alfombra) {
            placed["alfombra"] = alfombra
            for (child in rug.children.toList()) rug.remove(child)
            val built = buildRug(alfombra)
            rug.add(built.obj)
            rugTints = built.layers.map { RugTint(it.material, it.day, Color(it.day.r * RUG_NIGHT.r, it.day.g * RUG_NIGHT.g, it.day.b * RUG_NIGHT.b)) }
        }
        for ((spot, holder) in spots) {
            val id = want(spot)
            if (spot in placed && placed[spot] == id) continue
            placed[spot] = id
            for (child in holder.children.toList()) {
                holder.remove(child)
                freeGeometries(child)
            }
            id?.let { buildItem(it) }?.let { holder.add(it) }
        }
        applyDim()
    }

    // ── Criatura y huevos sobre la alfombra ──

    private fun freeGeometries(node: Object3D) {
        node.traverse { if (it is Mesh && !it.geometry.shared) it.geometry.dispose() }
    }

    private fun clearMount() {
        actor?.let {
            it.stop()
            mount.remove(it.obj)
            it.dispose()
        }
        actor = null
        for (egg in eggs) {
            anim.killTweensOf(egg.scale)
            anim.killTweensOf(egg.position)
            anim.killTweensOf(egg.rotation)
            mount.remove(egg)
            freeGeometries(egg)
        }
        eggs = emptyList()
        blob.visible = false
        anim.killTweensOf(blobMat)
        blobFade = 1f
    }

    private fun dress(target: Actor, equipped: Equipped?) {
        if (equipped == null) return
        for (itemId in listOf(equipped.cabeza, equipped.cara, equipped.cuello)) {
            if (itemId == null) continue
            val wear = buildWear(itemId, target.anchors) ?: continue
            if (wear.parent == "head") {
                target.rig.headPivot.add(wear.obj)
            } else if (target.plan.blob) {
                // En las crías el cuerpo cuelga de la cabeza: mismo sitio, otro padre.
                wear.obj.position.y -= target.rig.headPivot.position.y
                target.rig.headPivot.add(wear.obj)
            } else {
                target.rig.squash.add(wear.obj)
            }
        }
    }

    private fun putActor(formId: String, genes: Genes, equipped: Equipped?, mood: String?) {
        val made = Actor(buildCreature(formId, genes), anim)
        actor = made
        dress(made, equipped)
        mount.add(made.obj)
        made.setMood(mood ?: "bien")
        val w = made.plan.bodyW * made.plan.scale
        hits = listOf(Hit(0f, made.height * 0.5f, w * 0.62f + 0.12f, made.height * 0.56f, 0.5f))
        blob.visible = true
        blob.scale.set(w * 1.5f, w * 1.1f, 1f)
        blobBase = w
        val wide = max(made.plan.bodyW, made.plan.headRx * 2f) * made.plan.scale
        setSubject(clampF(made.height + 0.44f, 1.12f, 1.9f), clampF(wide + 0.52f, 1.0f, 1.7f))
    }

    private fun putEgg(genes: Genes) {
        val egg = buildEgg(genes.eggLook())
        egg.scale.setScalar(EGG_SCALE)
        mount.add(egg)
        eggs = listOf(egg)
        hits = listOf(Hit(0f, 0.5f, 0.5f, 0.6f, 0.5f))
        blob.visible = true
        blob.scale.set(1.15f, 0.85f, 1f)
        blobBase = 0.8f
        setSubject(1.4f, 1.15f)
    }

    private fun wearKey(view: Creature) = "${view.equipped.cabeza}|${view.equipped.cara}|${view.equipped.cuello}"

    fun showCreature(view: Creature) {
        val key = "${view.id}|${view.formId}|${wearKey(view)}"
        if (key == currentKey) {
            actor?.setMood(view.mood)
            return
        }
        val redress = actor != null && currentKey.startsWith("${view.id}|${view.formId}|")
        currentKey = key
        clearMount()
        if (view.stage == "huevo") {
            mode = "egg"
            putEgg(view.genes)
        } else {
            mode = "creature"
            putActor(view.formId, view.genes, view.equipped, view.mood)
            if (redress && !reduce) actor?.hop(1, 0.12f)
        }
    }

    fun showDemo() {
        // Las ocultas no salen en la demo: se descubren jugando.
        val adults = FORM_LIST.filter { it.stage == "adulto" && it.secret == null }
        val form = adults[Random.nextInt(adults.size)]
        val genes = createGenes(Random.nextLong(0, 4294967296L), form.family)
        currentKey = "demo"
        clearMount()
        mode = "demo"
        putActor(form.id, genes, null, "feliz")
    }

    fun showEggs(offer: List<EggLook>) {
        val key = "eggs|" + offer.joinToString("|") { "${it.hue}-${it.hue2}" }
        if (key == currentKey) return
        currentKey = key
        clearMount()
        mode = "eggs"
        selectedEgg = -1
        eggs = offer.mapIndexed { index, look ->
            val egg = buildEgg(look)
            egg.position.x = (index - 1) * 0.7f
            egg.scale.setScalar(0.8f)
            mount.add(egg)
            egg
        }
        setSubject(1.3f, 2.25f)
        hits = offer.indices.map { Hit((it - 1) * 0.7f, 0.42f, 0.33f, 0.48f, 0.4f) }
    }

    fun selectEgg(index: Int) {
        selectedEgg = index
        eggs.forEachIndexed { i, egg ->
            val scale = if (i == index) 0.98f else 0.78f
            anim.to(egg.scale.toAll(scale), if (reduce) 0f else 0.5f, Eases.backOut(2.2f), overwrite = true)
            if (i == index && !reduce) {
                anim.killTweensOf(egg.position)
                egg.position.y = 0f
                anim.to(egg.position.to(y = 0.16f), 0.18f, Eases.power2Out, repeat = 1, yoyo = true)
            }
        }
    }

    fun setEggProgress(value: Float) {
        eggProgress = clampF(value, 0f, 1f)
    }

    fun hasActor(): Boolean = actor != null
    fun setMood(mood: String) = actor?.setMood(mood)
    fun say(seconds: Float) = actor?.say(seconds)

    // ── Partículas ──

    private fun rand(a: Float, b: Float) = a + Random.nextFloat() * (b - a)

    private fun particle(geometry: Geometry, color: Color, position: Vec3, opacity: Float = 1f): Mesh {
        val mesh = Mesh(geometry, Material.basic(color) { transparent = true; this.opacity = opacity; depthWrite = false })
        mesh.position.copy(position)
        mesh.scale.setScalar(0.001f)
        fx.add(mesh)
        return mesh
    }

    private fun retire(obj: Object3D): () -> Unit = { fx.remove(obj) }

    private fun hearts(position: Vec3, count: Int = 1) {
        if (reduce) return
        for (i in 0 until count) {
            val mesh = particle(heartGeo, Color.hex("#ff6f9c"), position.clone().add(Vec3(rand(-0.15f, 0.15f), rand(-0.05f, 0.1f), 0.1f)))
            val size = rand(0.1f, 0.15f)
            anim.timeline(delay = i * 0.07f, onComplete = retire(mesh))
                .to(mesh.scale.toAll(size), 0.25f, Eases.backOut(2.5f))
                .to(mesh.position.by(x = rand(-0.2f, 0.2f), y = rand(0.4f, 0.65f)), 1.1f, Eases.power1Out, Pos.at(0f))
                .to(mesh.rotation.to(z = rand(-0.5f, 0.5f)), 1.1f, at = Pos.at(0f))
                .to(mesh.material::opacity.to(0f, mesh.material), 0.4f, Eases.power1In, Pos.at(0.7f))
        }
    }

    private fun sparks(center: Vec3, count: Int = 10, spread: Float = 0.7f, color: String = "#ffd166") {
        if (reduce) return
        for (i in 0 until count) {
            val mesh = particle(starGeo, Color.hex(if (i % 3 == 0) "#fffdf8" else color), center)
            val angle = (i / count.toFloat()) * PI_F * 2f + rand(-0.2f, 0.2f)
            val reach = spread * rand(0.7f, 1.15f)
            val size = rand(0.09f, 0.16f)
            anim.timeline(onComplete = retire(mesh))
                .to(mesh.scale.toAll(size), 0.2f, Eases.backOut(3f))
                .to(mesh.position.to(x = center.x + cos(angle) * reach, y = center.y + sin(angle) * reach * 0.9f), 0.8f, Eases.power3Out, Pos.at(0f))
                .to(mesh.rotation.to(z = rand(-2f, 2f)), 0.9f, at = Pos.at(0f))
                .to(mesh.scale.toAll(0.001f), 0.3f, Eases.power2In, Pos.at(0.6f))
        }
    }

    private fun bubbles(count: Int = 14) {
        if (reduce) return
        val top = actor?.height ?: 1f
        for (i in 0 until count) {
            val start = Vec3(rand(-0.55f, 0.55f), FLOOR_Y + rand(0.1f, top * 0.9f), rand(0.25f, 0.55f))
            val mesh = particle(dotGeo, Color.hex(if (i % 2 == 1) "#ffffff" else "#e3f6ff"), start, 0.75f)
            val size = rand(0.07f, 0.17f)
            anim.timeline(delay = i * 0.08f, onComplete = retire(mesh))
                .to(mesh.scale.toAll(size), 0.3f, Eases.backOut(2f))
                .to(mesh.position.by(x = rand(-0.12f, 0.12f), y = rand(0.35f, 0.7f)), 1.1f, Eases.sineOut, Pos.at(0f))
                .to(mesh.scale.toAll(size * 1.35f), 0.08f, at = Pos.at(1.02f))
                .to(mesh.material::opacity.to(0f, mesh.material), 0.1f, at = Pos.at(1.04f))
        }
    }

    private fun crumbs(position: Vec3, color: String = "#e2b279") {
        if (reduce) return
        for (i in 0 until 5) {
            val mesh = particle(dotGeo, Color.hex(color), position)
            mesh.scale.setScalar(rand(0.03f, 0.05f))
            anim.timeline(onComplete = retire(mesh))
                .to(mesh.position.by(x = rand(-0.28f, 0.28f)), 0.5f, Eases.power1Out, Pos.at(0f))
                .to(mesh.position.by(y = rand(0.1f, 0.22f)), 0.18f, Eases.power2Out, Pos.at(0f))
                .to(mesh.position.to(y = FLOOR_Y + 0.03f), 0.3f, Eases.power2In, Pos.at(0.18f))
                .to(mesh.material::opacity.to(0f, mesh.material), 0.25f, at = Pos.at(0.5f))
        }
    }

    private fun snore() {
        val a = actor ?: return
        if (reduce) return
        val material = Material.basic { map = zMap; transparent = true; opacity = 0f; depthWrite = false }
        val sprite = Mesh(spriteGeo, material)
        sprite.billboard = true
        sprite.position.set(0.3f, FLOOR_Y + a.height * 0.92f, 0.3f)
        sprite.scale.setScalar(0.12f)
        fx.add(sprite)
        anim.timeline(onComplete = retire(sprite))
            .to(material::opacity.to(0.95f, material), 0.3f)
            .to(sprite.position.by(x = 0.3f, y = 0.5f), 2.2f, Eases.sineOut, Pos.at(0f))
            .to(sprite.scale.to(x = 0.26f, y = 0.26f), 2.2f, Eases.sineOut, Pos.at(0f))
            .to(material::opacity.to(0f, material), 0.6f, at = Pos.at(1.6f))
    }

    private fun headCenter() = Vec3(0f, FLOOR_Y + (actor?.let { it.height * 0.78f } ?: 0.7f), 0.35f)
    private fun bodyCenter() = Vec3(0f, FLOOR_Y + (actor?.let { it.height * 0.5f } ?: 0.55f), 0.2f)

    // ── Secuencias ──

    fun feed(itemId: String, reaction: String?, done: () -> Unit) {
        val a = actor ?: return done()
        val item = buildItem(itemId)
        val react = {
            if (reaction == "favorita") Sfx.play("encanta")
            else if (reaction == "asco") Sfx.play("puaj")
        }
        if (item == null || reduce) {
            Sfx.play("bocado")
            react()
            if (reaction == "asco") a.shake()
            return done()
        }
        val holder = Group()
        holder.add(item)
        holder.scale.setScalar(0.36f)
        holder.position.set(0f, FLOOR_Y + 0.9f, 0.62f)
        scene.add(holder)
        val finish = {
            scene.remove(holder)
            freeGeometries(holder)
            done()
        }
        anim.timeline()
            .to(holder.position.to(y = FLOOR_Y), 0.42f, Eases.bounceOut)
            .call {
                val eater = actor
                if (eater == null) {
                    finish()
                } else {
                    val mouthful = Vec3(0f, FLOOR_Y + 0.25f, 0.6f)
                    eater.eat { bite ->
                        val left = 0.36f * floatArrayOf(0.72f, 0.42f, 0.001f)[bite]
                        anim.to(holder.scale.toAll(left), 0.12f)
                        crumbs(mouthful)
                        Sfx.play("bocado")
                    }.onComplete = {
                        eater.busy = false
                        eater.setMouth(eater.restMouth())
                        react()
                        if (reaction == "favorita") {
                            hearts(headCenter(), 4)
                            eater.hop(2, 0.22f)
                        } else if (reaction == "asco") {
                            eater.shake()
                        }
                        finish()
                    }
                }
            }
    }

    fun playToy(itemId: String, done: () -> Unit) {
        val a = actor
        val item = buildItem(itemId)
        if (a != null) Sfx.play("juguete")
        if (a == null || item == null || reduce) return done()
        val holder = Group()
        holder.add(item)
        holder.scale.setScalar(0.001f)
        holder.position.set(0.5f, FLOOR_Y, 0.42f)
        scene.add(holder)
        a.hop(3, 0.24f)
        anim.timeline(onComplete = {
            scene.remove(holder)
            freeGeometries(holder)
            done()
        })
            .to(holder.scale.toAll(0.42f), 0.3f, Eases.backOut(2f))
            .to(holder.position.to(y = FLOOR_Y + 0.5f), 0.24f, Eases.power2Out, Pos.at(0.2f), repeat = 5, yoyo = true)
            .to(holder.rotation.to(y = PI_F * 2f), 1.4f, Eases.none, Pos.at(0.2f))
            .call(Pos.at(1.0f)) { sparks(bodyCenter(), 8, 0.6f) }
            .to(holder.scale.toAll(0.001f), 0.25f, Eases.backIn(2f), Pos.at(1.7f))
    }

    fun bath() {
        val a = actor ?: return
        Sfx.play("burbujas")
        bubbles(16)
        a.wash()
        if (!reduce) anim.delayedCall(1.3f) { sparks(bodyCenter(), 8, 0.65f, "#bfe3ff") }
    }

    fun heal() {
        val a = actor ?: return
        Sfx.play("cura")
        sparks(bodyCenter(), 10, 0.6f, "#7ed6a5")
        a.hop(1, 0.2f)
    }

    fun cheer(won: Boolean) {
        val a = actor ?: return
        if (won) {
            a.spin()
            sparks(bodyCenter(), 12, 0.8f)
        } else {
            a.shake()
        }
    }

    // Un truco de los que aprende con el vínculo (ver rules/Bond.kt).
    fun trick(kind: String) {
        val a = actor ?: return
        Sfx.play("truco")
        if (kind == "saltitos") {
            a.hop(3, 0.2f)
            hearts(bodyCenter(), 4)
        } else {
            a.spin()
            sparks(bodyCenter(), 10, 0.7f)
        }
    }

    private fun swapIn(view: Creature) {
        currentKey = ""
        showCreature(view)
        val a = actor ?: return
        val scale = a.plan.scale
        a.rig.root.scale.setScalar(scale * 0.35f)
        anim.to(a.rig.root.scale.toAll(scale), 0.9f, Eases.elasticOut(1f, 0.5f))
    }

    fun hatch(view: Creature, done: () -> Unit) {
        val egg = eggs.firstOrNull()
        if (egg == null || mode != "egg" || reduce) {
            Sfx.play("nace")
            currentKey = ""
            showCreature(view)
            return done()
        }
        mode = "busy"
        for (delay in floatArrayOf(0f, 0.35f, 0.7f)) Sfx.play("crac", delay = delay)
        val shellColor = (egg.children[0] as Mesh).material.color.clone()
        anim.timeline(onComplete = done)
            .to(egg.rotation.to(z = 0.16f), 0.07f, Eases.sineInOut, repeat = 13, yoyo = true)
            .to(egg.scale.to(EGG_SCALE * 1.1f, EGG_SCALE * 0.9f, EGG_SCALE * 1.1f), 0.12f, Eases.power2In)
            .call {
                val center = Vec3(0f, FLOOR_Y + 0.55f, 0.2f)
                for (i in 0 until 9) {
                    val shard = particle(dotGeo, shellColor, center)
                    shard.scale.set(rand(0.1f, 0.16f), rand(0.08f, 0.12f), 0.03f)
                    val angle = (i / 9f) * PI_F * 2f
                    anim.timeline(onComplete = retire(shard))
                        .to(shard.position.to(x = cos(angle) * rand(0.5f, 0.9f), z = 0.2f + sin(angle) * 0.3f), 0.7f, Eases.power2Out, Pos.at(0f))
                        .to(shard.position.to(y = center.y + rand(0.3f, 0.6f)), 0.25f, Eases.power2Out, Pos.at(0f))
                        .to(shard.position.to(y = FLOOR_Y + 0.03f), 0.45f, Eases.bounceOut, Pos.at(0.25f))
                        .to(shard.rotation.to(x = rand(-3f, 3f), z = rand(-3f, 3f)), 0.7f, at = Pos.at(0f))
                        .to(shard.material::opacity.to(0f, shard.material), 0.5f, at = Pos.at(1.1f))
                }
                sparks(center, 12, 0.8f)
                Sfx.play("nace")
                swapIn(view)
            }
            .wait(0.8f)
            .call { actor?.hop(2, 0.2f) }
            .wait(0.9f)
    }

    fun evolve(view: Creature, done: () -> Unit) {
        val old = actor
        if (old == null || reduce) {
            Sfx.play("nace")
            currentKey = ""
            showCreature(view)
            return done()
        }
        Sfx.play("evoluciona")
        old.stop()
        old.busy = true
        old.setMouth("open")
        val size = old.height * 1.5f
        val glowMat = Material.basic(Color.hex("#ffffff")) { transparent = true; opacity = 0f; depthWrite = false }
        val glow = Mesh(dotGeo, glowMat)
        glow.position.set(0f, FLOOR_Y + old.height * 0.55f, 0.1f)
        glow.scale.setScalar(0.3f)
        scene.add(glow)
        anim.timeline(onComplete = {
            scene.remove(glow)
            done()
        })
            .to(old.rig.bounce.position.to(y = 0.22f), 0.5f, Eases.power2Out, Pos.at(0f))
            .to(old.rig.root.rotation.to(y = PI_F * 6f), 1.3f, Eases.power3In, Pos.at(0f))
            .to(old.rig.squash.scale.to(0.9f, 1.12f, 0.9f), 0.22f, Eases.sineInOut, Pos.at(0.2f), repeat = 3, yoyo = true)
            .to(glowMat::opacity.to(1f, glowMat), 0.5f, Eases.power2In, Pos.at(0.8f))
            .to(glow.scale.toAll(size), 0.5f, Eases.power2In, Pos.at(0.8f))
            .call {
                Sfx.play("nace")
                swapIn(view)
                sparks(Vec3(0f, FLOOR_Y + 0.7f, 0.3f), 16, 1f)
            }
            .to(glowMat::opacity.to(0f, glowMat), 0.6f, Eases.power2Out)
            .to(glow.scale.toAll(size * 1.5f), 0.6f, Eases.power2Out, Pos.withPrev())
            .call { actor?.hop(2, 0.24f) }
            .wait(1f)
    }

    fun farewell(done: () -> Unit) {
        val gone = actor
        if (gone != null) Sfx.play("adios")
        if (gone == null || reduce) return done()
        gone.stop()
        gone.busy = true
        val lightMat = Material.basic(Color.hex("#fffbe6")) { transparent = true; opacity = 0f; depthWrite = false }
        val light = Mesh(dotGeo, lightMat)
        light.position.set(0f, FLOOR_Y + gone.height * 0.5f, 0.1f)
        light.scale.setScalar(0.3f)
        scene.add(light)
        anim.timeline(onComplete = {
            scene.remove(light)
            done()
        })
            .to(gone.rig.root.scale.toAll(0.001f), 1.1f, Eases.power2In, Pos.at(0f))
            .to(::blobFade.to(0f, blobMat), 1.1f, Eases.power2In, Pos.at(0f))
            .to(lightMat::opacity.to(0.95f, lightMat), 0.8f, at = Pos.at(0.2f))
            .to(light.position.to(-0.68f, 1.7f, -2.2f), 1.8f, Eases.power1InOut, Pos.at(1.0f))
            .to(light.scale.toAll(0.08f), 1.8f, Eases.power1In, Pos.at(1.0f))
            .to(lightMat::opacity.to(0f, lightMat), 0.4f, at = Pos.at(2.5f))
    }

    // ── Día y noche ──

    private fun applyDim() {
        val k = dimK
        wallMat.color.lerpColors(DAY.wall, NIGHT.wall, k)
        floorMat.color.lerpColors(DAY.floor, NIGHT.floor, k)
        for (tint in rugTints) tint.material.color.lerpColors(tint.day, tint.night, k)
        trimMat.color.lerpColors(DAY.trim, NIGHT.trim, k)
        scene.background?.lerpColors(DAY.back, NIGHT.back, k)
        key.color.lerpColors(DAY.key, NIGHT.key, k)
        key.intensity = 2.1f - 1.55f * k
        hemi.intensity = 1.05f - 0.6f * k
        rim.intensity = 0.7f + 0.2f * k
        scene.environmentIntensity = 0.5f - 0.36f * k
        shadowMat.opacity = 0.22f - 0.1f * k
        val sun = if (skyPhase == "day") 0.5f else if (skyPhase == "night") 0.14f else 0.3f
        // Con el cielo tapado entra menos luz por la ventana.
        val cover = if (weather in Painter.GREY_SKIES) 0.4f else 1f
        patchMat.opacity = sun * cover * (1f - k * 0.5f)
    }

    fun setNight(on: Boolean) {
        if (on == night) return
        night = on
        anim.to(::dimK.to(if (on) 1f else 0f, this), if (reduce) 0f else 0.9f, Eases.power2InOut, onUpdate = ::applyDim)
        if (reduce) applyDim()
    }

    fun setWeather(kind: String?) {
        if (kind == weather) return
        weather = kind
        refreshSky()
    }

    private fun drawWindow(t: Float) {
        skyPhase = painter.sky(skyMap, weather = weather, t = t, still = reduce)
        skyAt = t
    }

    private fun refreshSky() {
        skyPhase = painter.sky(skyMap, weather = weather, t = now, still = reduce)
        patchMat.color.setHex(if (skyPhase == "night") "#cfdcff" else if (skyPhase == "day") "#fffdf2" else "#ffe9cf")
        applyDim()
    }

    // ── Encuadre ──

    // Alto y ancho, en unidades de escena, de lo que hay que encuadrar.
    private fun setSubject(h: Float, w: Float) {
        if (reduce || focusBottom == 0f) {
            subjectH = h
            subjectW = w
            if (focusBottom != 0f) frame()
            return
        }
        anim.to(
            Props(subjectTarget, listOf(Channel({ subjectH }, { subjectH = it }, h), Channel({ subjectW }, { subjectW = it }, w))),
            0.7f, Eases.power3Out, overwrite = true, onUpdate = ::frame,
        )
    }

    private fun frame() {
        val bottom = if (focusBottom != 0f) focusBottom else height
        val free = max(140f, bottom - focusTop)
        val perUnit = min((free * 0.8f) / subjectH, (width * 0.78f) / subjectW)
        val distance = height / perUnit / (2f * tanDeg(FOV / 2f))
        camera.position.set(0f, 0.5f + subjectH * 0.68f, distance)
        camera.lookAt(0f, subjectH * 0.47f, 0f)
        // La alfombra acompaña al encuadre para verse siempre entera.
        rug.scale.setScalar(clampF(subjectW / 1.52f, 0.6f, 1.6f))
        camera.aspect = width / height
        val center = focusTop + free * 0.5f
        camera.setViewOffset(width, height, 0f, (height / 2f - center).roundToInt().toFloat(), width, height)
        camera.updateProjectionMatrix()
        camera.updateView()
    }

    fun resize(widthDp: Float, heightDp: Float) {
        width = max(1f, widthDp)
        height = max(1f, heightDp)
        frame()
    }

    fun setFocus(top: Float, bottom: Float, animate: Boolean = true) {
        if (!animate || reduce) {
            anim.killTweensOf(focusTarget)
            focusTop = top
            focusBottom = bottom
            frame()
            return
        }
        if (focusBottom == 0f) focusBottom = height
        anim.to(
            Props(focusTarget, listOf(Channel({ focusTop }, { focusTop = it }, top), Channel({ focusBottom }, { focusBottom = it }, bottom))),
            0.45f, Eases.power3Out, overwrite = true, onUpdate = ::frame,
        )
    }

    // ── Foto ──

    // Cámara propia que encuadra a la criatura en `w`×`h` píxeles, para la
    // tarjeta de compartir (como room.snapshot en la web).
    private val photo = PerspectiveCamera(FOV, 1f, 0.1f, 80f)

    fun photoCamera(w: Int, h: Int): PerspectiveCamera {
        val perUnit = min((h * 0.6f) / subjectH, (w * 0.6f) / subjectW)
        val distance = h / perUnit / (2f * tanDeg(FOV / 2f))
        photo.aspect = w.toFloat() / h
        photo.position.set(0f, 0.5f + subjectH * 0.62f, distance)
        photo.lookAt(0f, subjectH * 0.5f, 0f)
        photo.updateProjectionMatrix()
        photo.updateView()
        actor?.openEyes()
        return photo
    }

    // ── Puntero ── (coordenadas en dp desde la esquina superior izquierda)

    private fun rayDirection(x: Float, y: Float): Vec3 {
        val ndc = Vec3((x / width) * 2f - 1f, -(y / height) * 2f + 1f, 0.5f)
        return camera.unproject(ndc).sub(camera.position).normalize()
    }

    private fun hitAt(x: Float, y: Float): Int {
        val direction = rayDirection(x, y)
        var best = -1
        var bestT = Float.MAX_VALUE
        val matrix = Mat4()
        val identity = Quat()
        hits.forEachIndexed { index, hit ->
            matrix.compose(Vec3(hit.x, FLOOR_Y + hit.y, 0f), identity, Vec3(hit.rx, hit.ry, hit.rz))
            val t = rayHitsUnitSphere(camera.position, direction, matrix)
            if (t >= 0f && t < bestT) {
                bestT = t
                best = index
            }
        }
        return best
    }

    // Punto de la pantalla, en dp, sobre la cabeza de la criatura.
    fun headPoint(lift: Float = 0.14f): Pair<Float, Float> {
        val top = actor?.height ?: if (eggs.isNotEmpty()) 1.2f else 1f
        val point = camera.project(Vec3(0f, FLOOR_Y + top + lift, 0f))
        return (point.x * 0.5f + 0.5f) * width to (-point.y * 0.5f + 0.5f) * height
    }

    private fun worldAt(x: Float, y: Float, depth: Float = 0.45f): Vec3 {
        val direction = rayDirection(x, y)
        val t = (depth - camera.position.z) / direction.z
        return camera.position.clone().addScaledVector(direction, t)
    }

    fun pointerDown(id: Int, x: Float, y: Float) {
        press = Press(id, x, y, hitAt(x, y))
    }

    fun pointerMove(id: Int, x: Float, y: Float) {
        val a = actor
        if (a != null) {
            val head = headPoint(-0.3f)
            a.lookAt((x - head.first) / (width * 0.45f), (head.second - y) / (height * 0.3f))
        }
        val p = press ?: return
        if (p.id != id) return
        val step = hypot(x - p.x, y - p.y)
        p.x = x
        p.y = y
        p.moved += step
        if (mode != "creature" && mode != "demo") return
        if (a == null || hitAt(x, y) < 0) return
        val head = headPoint(-0.3f)
        a.leanTarget = clampF((x - head.first) / 70f, -1f, 1f)
        p.stroke += step
        if (p.stroke > 34f) {
            p.stroke = 0f
            p.strokes += 1
            if (a.mood != "dormida") {
                if (p.strokes == 1) a.nuzzle()
                hearts(worldAt(x, y), 1)
                if (p.strokes % 3 == 1) Sfx.play("caricia", volume = 0.8f)
                for (cheek in a.rig.cheeks) cheek.material.opacity = 0.95f
            }
            listener?.onPet()
        }
    }

    fun pointerUp(id: Int, x: Float, y: Float) {
        val p = press ?: return
        if (p.id != id) return
        val tapped = p.moved < 9f
        val index = if (tapped) hitAt(x, y) else -1
        actor?.let { a ->
            a.leanTarget = 0f
            for (cheek in a.rig.cheeks) cheek.material.opacity = if (a.mood == "feliz") 0.8f else 0.62f
        }
        press = null
        if (index < 0) return
        when (mode) {
            "eggs" -> {
                Sfx.play("toque")
                listener?.onEggPick(index)
            }
            "egg" -> warm()
            "creature", "demo" -> {
                val a = actor
                if (a != null && a.mood != "dormida" && !a.busy) {
                    a.hop(1, 0.16f)
                    Sfx.play("toque")
                }
                listener?.onTap()
            }
        }
    }

    fun warm() {
        val egg = eggs.firstOrNull() ?: return
        if (mode != "egg") return
        Sfx.play("calor")
        if (!reduce) {
            anim.killTweensOf(egg.scale)
            egg.scale.set(EGG_SCALE * 1.09f, EGG_SCALE * 0.92f, EGG_SCALE * 1.09f)
            anim.to(egg.scale.toAll(EGG_SCALE), 0.5f, Eases.elasticOut(1f, 0.4f))
            sparks(Vec3(rand(-0.25f, 0.25f), FLOOR_Y + rand(0.6f, 1.1f), 0.45f), 3, 0.25f, "#ffb45c")
        }
        listener?.onEggTap()
    }

    // ── Bucle ── Avanza la escena hasta el segundo `t`.
    fun tick(t: Float) {
        anim.update(t - lastReal)
        lastReal = t
        val dt = min(0.05f, t - last)
        last = t
        now = t
        if (paused) return
        if (t - skyAt >= 60f) {
            skyAt = t
            refreshSky()
        } else if (!reduce && weather in Painter.MOVING_SKIES && t - skyAt > 0.08f) {
            // La lluvia y la nieve se mueven a unos doce dibujos por segundo.
            drawWindow(t)
        }
        actor?.let { a ->
            a.update(t, dt)
            val lift = a.rig.bounce.position.y
            val shrink = 1f - clampF(lift, 0f, 0.6f) * 0.7f
            blob.scale.set(blobBase * 1.5f * shrink, blobBase * 1.1f * shrink, 1f)
            blobMat.opacity = shrink * blobFade
            if (a.mood == "dormida" && t > snoreAt) {
                snore()
                snoreAt = t + 1.9f
            }
        }
        if (mode == "egg" && eggs.isNotEmpty()) {
            val egg = eggs[0]
            val urge = 1f + eggProgress * 2.5f
            egg.rotation.z = sin(t * 2.4f) * 0.035f * urge + (if (eggProgress > 0.75f) sin(t * 31f) * 0.02f else 0f)
        } else if (mode == "eggs") {
            eggs.forEachIndexed { index, egg ->
                egg.rotation.z = sin(t * 2f + index * 1.7f) * (if (index == selectedEgg) 0.09f else 0.035f)
            }
        }
    }
}
