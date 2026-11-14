// Criatura de modelo (un .glb con esqueleto, ver Models.kt) con la misma
// forma que devuelven buildPet y buildMonster, para que Actor la anime
// igual. Repite scene/glb-creature.js de la web: los huesos se rehacen sin
// giro (Actor pone giros absolutos), los ojos son los de siempre (makeEye)
// con un párpado del color del pelo, la boca es un calco que se cambia con
// el humor, las llamas cuelgan de la cola y el color se mueve con el ADN.

package com.nidaria.app.scene

import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.nidaria.app.gl.Color
import com.nidaria.app.gl.Geometry
import com.nidaria.app.gl.Group
import com.nidaria.app.gl.Material
import com.nidaria.app.gl.Mesh
import com.nidaria.app.gl.Texture
import com.nidaria.app.gl.planeGeometry
import com.nidaria.app.gl.sphereGeometry
import com.nidaria.app.rules.FAMILY_INFO
import com.nidaria.app.rules.Genes
import com.nidaria.app.rules.getForm
import com.nidaria.app.sculpt.AXIS_Y
import com.nidaria.app.sculpt.Col
import com.nidaria.app.sculpt.V3
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

private const val INK = "#3a2118"

// ── Imágenes del archivo ──────────────────────────────────────

// Se descodifican al subirlas a la tarjeta gráfica (Texture.source), así
// que construir la criatura no toca Android y se puede probar.
private fun imageTexture(bytes: ByteArray): Texture {
    val (w, h) = Glb.imageSize(bytes)
    return Texture(w, h, mipmaps = true) {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val argb = IntArray(w * h)
        bitmap.getPixels(argb, 0, w, 0, 0, w, h)
        bitmap.recycle()
        val out = ByteArray(w * h * 4)
        for (i in argb.indices) {
            val c = argb[i]
            out[i * 4] = (c shr 16).toByte()
            out[i * 4 + 1] = (c shr 8).toByte()
            out[i * 4 + 2] = c.toByte()
            out[i * 4 + 3] = (c ushr 24).toByte()
        }
        out
    }
}

// Las texturas de cada archivo, compartidas por todas sus criaturas.
private val textures = HashMap<String, Pair<Texture?, Texture?>>()

private fun texturesOf(file: String, model: GlbModel): Pair<Texture?, Texture?> = synchronized(textures) {
    textures.getOrPut(file) {
        model.colorImage?.let(::imageTexture) to model.glowImage?.let(::imageTexture)
    }
}

// ── Color según el ADN ────────────────────────────────────────

// Tono girado (en grados) de un color, como Color.offsetHSL de Three.js.
internal fun offsetHue(c: Col, degrees: Double): Col {
    val max = maxOf(c.r, c.g, c.b)
    val min = minOf(c.r, c.g, c.b)
    val l = (max + min) / 2
    if (max == min) return c.clone()
    val d = max - min
    val s = if (l > 0.5) d / (2 - max - min) else d / (max + min)
    val h = when (max) {
        c.r -> (c.g - c.b) / d + (if (c.g < c.b) 6 else 0)
        c.g -> (c.b - c.r) / d + 2
        else -> (c.r - c.g) / d + 4
    } / 6
    return Col.hsl(h + degrees / 360, s, l)
}

private class Tint(val shift: Double, val sat: Double, val light: Double)

// Dentro de su banda de la familia (4 a 40 en las pelusas) el tono se mueve
// poco; si el ADN cae en otra banda (las azules), entero, y se apaga un poco.
private fun tintFor(spec: ModelSpec, genes: Genes, family: String): Tint {
    val bands = FAMILY_INFO[family]?.hues ?: emptyList()
    val home = bands.firstOrNull { spec.hue >= it[0] && spec.hue <= it[1] } ?: bands.firstOrNull()
    var shift = 0.0
    if (home != null) {
        val middle = (home[0] + home[1]) / 2.0
        shift = if (genes.hue >= home[0] && genes.hue <= home[1]) (genes.hue - middle) * 0.6
        else ((genes.hue - spec.hue + 540) % 360) - 180
    }
    var sat = 0.82 + ((genes.sat - 52) / 22.0) * 0.33
    val light = 0.95 + ((genes.light - 62) / 12.0) * 0.1
    if (abs(shift) > 60) sat *= 0.6
    return Tint(shift, sat, light)
}

// ── Boca ──────────────────────────────────────────────────────

// Para hocicos con nariz grande (el trazo y los colores de petMouthTexture):
// surco corto bajo la nariz y comisuras abiertas hasta los lados.
private fun wideMouthTexture(kind: String): Texture = synchronized(mouthTextures) {
    mouthTextures.getOrPut(kind) {
        painted(256, 160, mipmaps = true) { canvas, paint ->
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            paint.style = Paint.Style.STROKE
            paint.color = css(INK)
            paint.strokeWidth = 9f
            val cx = 128f
            val drop = 34f
            val spread = 98f
            canvas.drawLine(cx, 2f, cx, drop, paint)
            if (kind == "open") {
                val wide = 70f
                val deep = 70f
                val path = Path()
                path.moveTo(cx - wide, drop - 8f)
                path.quadTo(cx - wide * 0.4f, drop + 8f, cx, drop)
                path.quadTo(cx + wide * 0.4f, drop + 8f, cx + wide, drop - 8f)
                path.cubicTo(cx + wide * 0.9f, deep, cx - wide * 0.9f, deep, cx - wide, drop - 8f)
                path.close()
                val fill = Paint(Paint.ANTI_ALIAS_FLAG)
                fill.color = css("#6e2630")
                canvas.drawPath(path, fill)
                canvas.save()
                canvas.clipPath(path)
                fill.color = css("#ef7f8e")
                canvas.drawOval(RectF(cx - wide * 0.6f, deep - 6f - 18f, cx + wide * 0.6f, deep - 6f + 18f), fill)
                canvas.restore()
                paint.strokeWidth = 8f
                canvas.drawPath(path, paint)
                return@painted
            }
            val lift = when (kind) { "smile" -> -22f; "sad" -> 18f; else -> -4f }
            val curve = when (kind) { "smile" -> 26f; "sad" -> -6f; else -> 8f }
            for (side in intArrayOf(-1, 1)) {
                val path = Path()
                path.moveTo(cx, drop)
                path.quadTo(cx + side * spread * 0.45f, drop + curve, cx + side * spread, drop + lift)
                canvas.drawPath(path, paint)
            }
        }
    }
}

private val mouthTextures = HashMap<String, Texture>()

// Parche pegado a la malla del modelo: una cuadrícula frente a `center`
// cuyos puntos se proyectan sobre la malla a lo largo de `normal` (de cada
// rayo vale el choque más cercano al plano), como en la web.
private fun meshPatch(model: GlbModel, center: V3, normal: V3, up: V3, width: Double, height: Double, segments: Int, lift: Double): Geometry {
    val n = normal.clone().normalize()
    val right = V3().crossVectors(up, n).normalize()
    val top = V3().crossVectors(n, right).normalize()
    // Solo los triángulos cercanos.
    val reach = sqrt(width * width + height * height) * 0.75 + 0.03
    val p = model.positions
    val near = ArrayList<Int>()
    for (t in 0 until model.indices.size / 3) {
        for (k in 0 until 3) {
            val i = model.indices[t * 3 + k] * 3
            val dx = p[i] - center.x
            val dy = p[i + 1] - center.y
            val dz = p[i + 2] - center.z
            if (dx * dx + dy * dy + dz * dz < reach * reach) {
                near.add(t)
                break
            }
        }
    }
    val positions = ArrayList<Float>()
    val uvs = ArrayList<Float>()
    for (row in 0..segments) {
        for (col in 0..segments) {
            val u = col.toDouble() / segments
            val v = row.toDouble() / segments
            val q = center.clone().addScaledVector(right, (u - 0.5) * width).addScaledVector(top, (0.5 - v) * height)
            // Rayo desde delante del plano hacia dentro.
            val ox = q.x + n.x * 0.08
            val oy = q.y + n.y * 0.08
            val oz = q.z + n.z * 0.08
            var best = Double.NaN
            for (t in near) {
                val a = model.indices[t * 3] * 3
                val b = model.indices[t * 3 + 1] * 3
                val c = model.indices[t * 3 + 2] * 3
                val e1x = p[b] - p[a]; val e1y = p[b + 1] - p[a + 1]; val e1z = p[b + 2] - p[a + 2]
                val e2x = p[c] - p[a]; val e2y = p[c + 1] - p[a + 1]; val e2z = p[c + 2] - p[a + 2]
                val px = -n.y * e2z + n.z * e2y
                val py = -n.z * e2x + n.x * e2z
                val pz = -n.x * e2y + n.y * e2x
                val det = e1x * px + e1y * py + e1z * pz
                if (abs(det) < 1e-12) continue
                val inv = 1 / det
                val tx = ox - p[a]; val ty = oy - p[a + 1]; val tz = oz - p[a + 2]
                val bu = (tx * px + ty * py + tz * pz) * inv
                if (bu < 0 || bu > 1) continue
                val qx = ty * e1z - tz * e1y
                val qy = tz * e1x - tx * e1z
                val qz = tx * e1y - ty * e1x
                val bv = -(n.x * qx + n.y * qy + n.z * qz) * inv
                if (bv < 0 || bu + bv > 1) continue
                val dist = (e2x * qx + e2y * qy + e2z * qz) * inv
                if (dist < 0 || dist > 0.16) continue
                // El choque más cercano al plano (a 0,08 del origen).
                if (best.isNaN() || abs(dist - 0.08) < abs(best - 0.08)) best = dist
            }
            val hit = if (best.isNaN()) q else V3(ox - n.x * best, oy - n.y * best, oz - n.z * best).addScaledVector(n, lift)
            positions.add(hit.x.toFloat()); positions.add(hit.y.toFloat()); positions.add(hit.z.toFloat())
            uvs.add(u.toFloat()); uvs.add((1 - v).toFloat())
        }
    }
    val indices = ArrayList<Int>()
    for (row in 0 until segments) {
        for (col in 0 until segments) {
            val a = row * (segments + 1) + col
            val b = a + 1
            val c = a + segments + 1
            val d = c + 1
            indices.add(a); indices.add(c); indices.add(b)
            indices.add(b); indices.add(c); indices.add(d)
        }
    }
    return Geometry(positions.toFloatArray(), FloatArray(positions.size), uvs.toFloatArray(), indices.toIntArray()).computeVertexNormals()
}

// ── Lo que arde ───────────────────────────────────────────────

// Las llamas y las brasas pintadas siguen el humor (dormida, casi apagadas);
// las brasas además laten despacio. Actor llama a `mood` y a `update`.
class CreatureFx(private val flames: List<Flame>, private val glow: Material?) {
    private var power = 1f
    private var target = 1f

    fun mood(name: String) {
        target = FLAME_POWER[name] ?: 1f
        for (f in flames) f.setPower(target)
    }

    fun update(t: Float, dt: Float) {
        for (f in flames) f.update(dt)
        power += (target - power) * (1f - kotlin.math.exp(-dt * 3f))
        glow?.emissiveIntensity = power * (0.82f + 0.18f * sin(t * 1.8f))
    }
}

// ── La criatura ───────────────────────────────────────────────

fun buildGlbCreature(formId: String, genes: Genes, spec: ModelSpec): Built {
    val form = getForm(formId)
    val family = form.family ?: genes.family
    val model = Glb.load(spec.file)
    val data = model.extras
    val dna = tintFor(spec, genes, family)

    val bones = data.getValue("bones").jsonObject
    val bonePos = Array(BONES) { bones.getValue(Glb.ORDER[it]).jsonArray.v3() }
    val (colorMap, glowMap) = texturesOf(spec.file, model)
    val material = Material.standard(Color(1f, 1f, 1f), 0.85f) {
        map = colorMap
        if (glowMap != null) {
            emissiveMap = glowMap
            emissive.set(1f, 1f, 1f)
        }
        tint[0] = Math.toRadians(dna.shift).toFloat()
        tint[1] = dna.sat.toFloat()
        tint[2] = dna.light.toFloat()
    }
    val geometry = Geometry(model.positions, model.normals, model.uvs, model.indices).apply {
        skinIndex = model.skinIndex
        skinWeight = model.skinWeight
    }
    val (mesh, boneObjs) = skin(geometry, material, bonePos)

    val root = Group()
    val bounce = Group()
    val squash = Group()
    root.add(bounce)
    bounce.add(squash)
    squash.add(mesh)
    val height = data.getValue("height").jsonPrimitive.double
    val scale = spec.height / height
    root.scale.setScalar(scale.toFloat())

    val headPivot = boneObjs[HEAD]
    val headBase = bonePos[HEAD]
    fun toHead(obj: com.nidaria.app.gl.Object3D) {
        obj.position.sub(headBase.gl())
        headPivot.add(obj)
    }

    // Ojos. Lo borrado de la textura alrededor (el ojo pintado) toma el
    // color del pelo, que también se mueve con el ADN.
    val eyeData = data.getValue("eyes").jsonArray.map { it.jsonObject }
    val eyes = eyeData.mapIndexed { i, e ->
        val point = e.getValue("point").jsonArray.v3()
        val side = if (point.x < 0) -1 else 1
        val n = e.getValue("normal").jsonArray.v3()
        val normal = n.clone().lerp(V3(0.0, 0.0, 1.0), 0.55).normalize()
        val r = e.getValue("r").jsonPrimitive.double
        val tall = e.getValue("tall").jsonPrimitive.double
        val skinCol = offsetHue(Col.hex(e.getValue("skin").jsonPrimitive.content), dna.shift)
        val eye = makeEye(point, normal, r, tall, iris = e.getValue("iris").jsonPrimitive.content, kind = "round", skin = skinCol, tilt = -side * 0.04)
        toHead(eye.group)
        // Ojo cerrado: la línea de pestañas (Actor cambia su textura) sobre
        // un párpado del color del pelo que tapa el hueco.
        val rf = r.toFloat()
        val plane = planeGeometry(rf * 2.3f, rf * 1.6f).translate(0f, -rf * 0.1f, rf * 0.62f * 1.08f)
        val closed = Mesh(plane, decalMaterial(lidTexture("sleep")))
        closed.position.copy(eye.group.position)
        closed.quaternion.copy(eye.group.quaternion)
        val cover = Mesh(sphereGeometry(1f, 24, 16), Material.standard(skinCol.toGl(), 0.85f))
        cover.scale.set(rf * 1.04f, rf * eye.tall * 1.04f, rf * 0.62f * 1.04f)
        closed.add(cover)
        closed.visible = false
        headPivot.add(closed)
        Eye(eye.group, eye.open, closed, side, eye.r, eye.tall, false, 0f, eye.lid, eye.bead)
    }

    // Boca: un calco sobre el hocico, proyectado desde donde mira la cámara
    // del juego (delante y algo arriba), con las texturas que cambia Actor.
    val mouths = listOf("smile", "sad", "open", "flat").associateWith { wideMouthTexture(it) }
    val mouthData = data["mouth"]?.jsonObject
    val mouthGeo = if (mouthData != null) {
        val w = mouthData.getValue("width").jsonPrimitive.double
        val n = mouthData.getValue("normal").jsonArray.v3().lerp(V3(0.0, 0.3, 1.0).normalize(), 0.8).normalize()
        meshPatch(model, mouthData.getValue("point").jsonArray.v3(), n, AXIS_Y, w, w * 0.625, 12, 0.005)
    } else {
        planeGeometry(0.001f, 0.001f)
    }
    mouthGeo.translate(-headBase.x.toFloat(), -headBase.y.toFloat(), -headBase.z.toFloat())
    val mouth = Mesh(mouthGeo, decalMaterial(mouths.getValue("smile")))
    headPivot.add(mouth)

    // Llamas: cuelgan del hueso que diga el modelo (la cola), en su punta. El
    // fuego solo cambia de color en las de otra banda (fuego azul).
    val flames = data["flames"]?.jsonArray?.mapIndexed { i, f ->
        val fo = f.jsonObject
        val bone = Glb.ORDER.indexOf(fo.getValue("bone").jsonPrimitive.content).coerceAtLeast(0)
        val fire = makeFlame(fo.getValue("size").jsonPrimitive.double, i * 7.31f, if (abs(dna.shift) > 60) dna.shift else 0.0, scale.toFloat())
        fire.obj.place(fo.getValue("point").jsonArray.v3().sub(bonePos[bone]))
        boneObjs[bone].add(fire.obj)
        fire
    } ?: emptyList()
    val fx = CreatureFx(flames, if (glowMap != null) material else null)

    // ── Medidas para la habitación y anclajes para la ropa ──
    var minX = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    for (i in 0 until model.positions.size / 3) {
        minX = minOf(minX, model.positions[i * 3])
        maxX = maxOf(maxX, model.positions[i * 3])
    }
    val e0 = eyeData[0].getValue("point").jsonArray.v3()
    val e1 = eyeData[1].getValue("point").jsonArray.v3()
    val eyeY = (e0.y + e1.y) / 2
    val spacing = abs(e1.x - e0.x)
    val mouthY = mouthData?.getValue("point")?.jsonArray?.v3()?.y ?: (eyeY - spacing * 0.6)
    val crown = eyeY + (eyeY - mouthY) * 1.6
    val headR = spacing * 1.5
    val host = Host(V3((e0.x + e1.x) / 2 - headBase.x, eyeY - headBase.y, 0.0), headR, crown - eyeY, headR * 0.9)
    val anchors = Anchors(
        HatAnchor(crown - headBase.y, headR),
        FaceAnchor(eyeY - headBase.y, (e0.z + e1.z) / 2 - headBase.z, 0.4, headR, host, 0.14),
        NeckAnchor(headBase.y, headR * 0.75, headR * 0.7),
    )
    val plan = Look(family, form.stage, scale, false, (maxX - minX) * 0.75, headR)

    val rig = Rig(
        root = root, bounce = bounce, squash = squash, headPivot = headPivot,
        eyes = eyes, mouths = mouths, mouth = mouth, cheeks = emptyList(),
        ears = if (spec.ears) listOf(Limb(boneObjs[EAR_L], -1, 0f), Limb(boneObjs[EAR_R], 1, 0f)) else emptyList(),
        arms = listOf(Limb(boneObjs[ARM_L], -1, 0f, spec.arms == "legs"), Limb(boneObjs[ARM_R], 1, 0f, spec.arms == "legs")),
        tail = boneObjs[TAIL], wings = emptyList(), top = Group(), halo = null, haloY = 0f, mesh = mesh,
        blushRest = 0f, blushHappy = 0f, fx = fx,
    )
    return Built(root, plan, spec.height.toFloat(), anchors, rig)
}
