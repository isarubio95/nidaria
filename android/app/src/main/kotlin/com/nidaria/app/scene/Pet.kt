// Perros y gatos esculpidos, como pet.js en la web. La raza (Breeds.kt,
// generado desde shared/breeds.js) da las proporciones, el hocico, las
// orejas, el pelo, la cola y la capa; la etapa los lleva de cachorro a
// adulto y la crianza les da su aire. Todo se funde en una figura de
// arcilla sentada. Las cuentas van en Double y en el mismo orden que la
// web: la prueba de paridad compara los encargos uno a uno.

package com.nidaria.app.scene

import com.nidaria.app.gl.Color
import com.nidaria.app.gl.Group
import com.nidaria.app.gl.Material
import com.nidaria.app.gl.Mesh
import com.nidaria.app.gl.Object3D
import com.nidaria.app.gl.PI_F
import com.nidaria.app.gl.sphereGeometry
import com.nidaria.app.gl.torusGeometry
import com.nidaria.app.rules.BreedInfo
import com.nidaria.app.rules.CoatPaint
import com.nidaria.app.rules.Genes
import com.nidaria.app.rules.Rng
import com.nidaria.app.rules.breedsOf
import com.nidaria.app.rules.getBreed
import com.nidaria.app.rules.getForm
import com.nidaria.app.sculpt.AXIS_Y
import com.nidaria.app.sculpt.Col
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

private val STAGE_KID = mapOf("cria" to 1.0, "joven" to 0.45, "adulto" to 0.0)
private val PET_STAGE_SCALE = mapOf("cria" to 0.7, "joven" to 0.85, "adulto" to 1.0)

// Colores de las formas ocultas que cambian la capa.
private const val NIGHT_BASE = "#2f3a5e"
private const val NIGHT_LIGHT = "#c9d3ee"
private const val NIGHT_MARK = "#ffe9a6"
private const val GOLD = "#f2c14e"

private val WHISKER = sphereGeometry(1f, 8, 6).keep()

private fun mix(a: Double, b: Double, t: Double) = a + (b - a) * t

// Lo que cambia la crianza sobre la raza (el objeto `m` de pet.js).
private class PetLook(breed: BreedInfo, genes: Genes) {
    val g = genes.build
    var chunk = breed.body.chunk + (g.round - 0.5) * 0.16
    var leg = breed.body.leg
    var chest = breed.body.chest
    val long = breed.body.long
    val head = breed.head.size + (g.head - 0.5) * 0.12
    val dome = breed.head.dome
    var width = breed.head.width
    var snout = breed.snout.len
    val snoutW = breed.snout.width
    val drop = breed.snout.drop
    val stop = breed.snout.stop
    var earType = breed.ears.type
    var earSize = breed.ears.size + (g.ears - 0.5) * 0.14
    var earSet = breed.ears.set
    var tailType = breed.tail.type
    var tailSize = breed.tail.size + (g.tail - 0.5) * 0.2
    val coatType = breed.coat
    val extras = breed.extras.toSet()
    var eyes = 1 + (g.eyes - 0.5) * 0.14
    val eyeGap = (g.eyeGap - 0.5) * 0.08
    var fluff = 0.0
    var lids = 0.0
    var brows = false
    var fang = false
    var scruff = 0.0
    var scar = false
    var scale = 1.0
    var look: String? = null
}

private class PetPlan(
    val species: String, val family: String, val breed: BreedInfo, val cat: Boolean, val kid: Double, val stage: String,
    val m: PetLook, val bw: Double, val legH: Double, val torsoLen: Double, val sh: Double, val hr: Double, val headW: Double,
    val snoutLen: Double, val mw: Double, val mh: Double, val scale: Double,
)

private fun petPlan(formId: String, genes: Genes, breed: BreedInfo): PetPlan {
    val form = getForm(formId)
    val cat = breed.species == "gato"
    val kid = STAGE_KID[form.stage] ?: 0.0
    val b1 = form.path.getOrNull(0)
    val b2 = form.path.getOrNull(1)
    val m = PetLook(breed, genes)

    // La crianza: la rama de joven pesa menos en la adulta que la suya.
    fun branch(id: String, w: Double) {
        when (id) {
            "gloton" -> {
                m.chunk += 0.42 * w
                m.chest += 0.18 * w
                m.width += 0.2 * w
                m.scale *= 1 + 0.06 * w
            }
            "jugueton" -> {
                m.leg += 0.16 * w
                m.chunk -= 0.1 * w
                m.earSize += 0.1 * w
                m.tailSize += 0.2 * w
            }
            "tranquilo" -> {
                m.fluff += 0.3 * w
                m.lids = max(m.lids, 0.36 * w)
            }
            "arisco" -> {
                m.brows = true
                m.scruff += w
                m.fang = m.fang || w >= 0.9
            }
        }
    }
    if (b1 != null) branch(b1, if (form.stage == "joven") 1.0 else 0.55)
    if (b2 != null) branch(b2, 1.0)
    if (b2 == "jugueton") m.tailSize += 0.25
    if (b2 == "tranquilo") m.fluff += 0.25
    if (b2 == "arisco") m.scar = true

    when (form.secret) {
        "radiante" -> {
            m.look = "radiante"
            m.eyes *= 1.06
            m.fluff += 0.2
        }
        "sibarita" -> {
            m.look = "sibarita"
            m.chunk = 1.15
            m.chest += 0.2
            m.scale *= 1.06
        }
        "lunar" -> {
            m.look = "lunar"
            m.lids = max(m.lids, 0.42)
            m.fluff += 0.2
        }
        "mestiza" -> {
            // Mezcla: orejas y cola de la raza de la que viene.
            val other = getBreed(genes.origin)
            if (other != null && other.species == breed.species) {
                val size = (other.ears.size + m.earSize) / 2
                m.earType = other.ears.type
                m.earSize = size
                m.earSet = other.ears.set
                m.tailType = other.tail.type
                m.tailSize = other.tail.size
                m.snout = (m.snout + other.snout.len) / 2
                m.look = "mestiza"
            }
        }
    }

    m.chunk = clampD(m.chunk, 0.0, 1.2)
    m.leg = clampD(m.leg, 0.0, 1.0)

    // Medidas. Las crías tienen cabeza grande, patas cortas y hocico corto.
    val bw = (if (cat) 0.135 else 0.15) + m.chunk * (if (cat) 0.075 else 0.085)
    val legH = (0.19 + m.leg * 0.2) * (1 - kid * 0.32)
    val torsoLen = 0.15 + m.long * 0.06 + m.chunk * 0.02
    val sh = legH + 0.07
    val hr = (0.205 + m.head * 0.085) * (1 + kid * 0.26)
    val headW = 1 + (m.width - 0.5) * 0.26
    val snoutLen = hr * (0.06 + m.snout * 1.0) * (1 - kid * 0.42)
    val mw = hr * (0.26 + m.snoutW * 0.22) * (if (cat) 0.92 else 1.0)
    val mh = hr * (0.2 + m.snoutW * 0.08)
    return PetPlan(
        breed.species, form.family ?: genes.family, breed, cat, kid, form.stage, m,
        bw, legH, torsoLen, sh, hr, headW, snoutLen, mw, mh,
        PET_STAGE_SCALE.getValue(form.stage) * (0.88 + breed.size * 0.22) * m.scale,
    )
}

// La capa: color de fondo y zonas pintadas, ya con lo que cambian las
// formas ocultas.
private class Coat(val base: String, val paint: List<CoatPaint>)

private fun coatOf(P: PetPlan, genes: Genes): Coat {
    val coats = P.breed.coats
    val coat = coats[(genes.coat ?: 0).coerceIn(0, coats.size - 1)]
    var base = coat.base
    var paint = coat.paint.toMutableList()
    when (P.m.look) {
        "lunar" -> {
            // Noche: el fondo pasa a azul tinta y lo claro, a plata.
            paint = paint.map { p ->
                val light = p.color != null && Col.hex(p.color).lightness() > 0.6
                when {
                    light -> CoatPaint(p.region, NIGHT_LIGHT, null)
                    p.colors != null -> CoatPaint(p.region, null, listOf(NIGHT_LIGHT))
                    else -> CoatPaint(p.region, "#1d2440", null)
                }
            }.toMutableList()
            base = NIGHT_BASE
        }
        "radiante" -> base = "#" + Col.hex(base).lerp(Col.hex(GOLD), 0.28).hexString()
        "mestiza" -> paint.add(CoatPaint("patches", null, listOf(if (P.cat) "#3a2e2a" else "#5a3a26")))
    }
    return Coat(base, paint)
}

fun buildPet(formId: String, genes: Genes, detail: String = "high"): Built {
    val form = getForm(formId)
    val breed = getBreed(genes.breed) ?: breedsOf(form.family ?: genes.family)[0]
    val P = petPlan(formId, genes, breed)
    val cat = P.cat
    val m = P.m
    val bw = P.bw
    val legH = P.legH
    val torsoLen = P.torsoLen
    val sh = P.sh
    val hr = P.hr
    val headW = P.headW
    val snoutLen = P.snoutLen
    val mw = P.mw
    val mh = P.mh
    val coat = coatOf(P, genes)
    val base = Col.hex(coat.base)
    val rng = Rng((genes.seed.toInt() xor 0x5bd1e995).toLong() and 0xffffffffL)
    val cell = when (detail) {
        "high" -> 0.0125
        "high-thumb" -> 0.015
        "tiny" -> 0.034
        else -> 0.022
    }

    // Colores de zona que también dan forma (orejas, cola).
    fun region(name: String): Col? {
        val hit = coat.paint.firstOrNull { it.region == name } ?: return null
        return hit.color?.let { Col.hex(it) }
    }
    val earColor = region("ears") ?: region("points") ?: region("cap") ?: base
    val tailColor = region("tail") ?: region("points") ?: region("cap") ?: base
    val innerEar = region("innerEar") ?: earColor.clone().lerp(Col.hex(if (cat) "#e3a19c" else "#b98a7c"), if (cat) 0.45 else 0.3)
    val plush = m.coatType in listOf("fluffy", "long", "curly", "feathered") || m.fluff > 0.3

    val S = Sculpt(ground = 0.0, groundK = 0.025)
    val bonePos = Array(BONES) { V3() }

    fun pawAt(p: V3, s: Double, bone: Int) {
        val r = (if (cat) 0.048 else 0.054) * s * (0.9 + m.chunk * 0.2)
        S.add(Sh.ellipsoid(doubleArrayOf(p.x, r * 0.72, p.z + r * 0.35), doubleArrayOf(r, r * 0.74, r * 1.32)), base, k = 0.035, bone = bone)
        // Dedos: tres bultos delante.
        for (t in intArrayOf(-1, 0, 1)) {
            S.add(Sh.sphere(doubleArrayOf(p.x + t * r * 0.52, r * 0.5, p.z + r * 1.32 - abs(t) * r * 0.16), r * 0.36), base, k = 0.018, bone = bone)
        }
    }

    // ── Cuerpo sentado ──
    val torsoY = sh * 0.6
    S.add(Sh.ellipsoid(doubleArrayOf(0.0, torsoY, -0.03), doubleArrayOf(bw, sh * 0.56 + 0.05, torsoLen), rot(0.34, 0.0, 0.0)), base, k = 0.0, bone = BODY)
    val chestZ = 0.06 + m.chest * 0.025
    S.add(Sh.ellipsoid(doubleArrayOf(0.0, sh * 0.84, chestZ), doubleArrayOf(bw * 0.84, 0.11 + m.chest * 0.045, 0.1 + m.chest * 0.035)), base, k = 0.08, bone = BODY)
    // Muslos de las patas de atrás y sus pies, asomando delante.
    val thighX = bw * 0.8
    val thighY = 0.11 + legH * 0.14
    val thighZ = -0.06
    for (side in intArrayOf(-1, 1)) {
        S.add(Sh.ellipsoid(doubleArrayOf(side * thighX, thighY, thighZ), doubleArrayOf(0.085 + m.chunk * 0.045, 0.11 + legH * 0.14, 0.14 + m.long * 0.03), rot(0.3, 0.0, -side * 0.12)), base, k = 0.07, bone = BODY)
        pawAt(v(side * (thighX + 0.01), 0.0, 0.07 + m.long * 0.02 - (if (cat) 0.02 else 0.0)), 1.05, BODY)
    }
    // Patas de delante, con su hueso.
    val legR = (if (cat) 0.036 else 0.04) + m.chunk * 0.024
    val shoulders = ArrayList<V3>()
    for (side in intArrayOf(-1, 1)) {
        val bone = if (side < 0) ARM_L else ARM_R
        val top = v(side * bw * 0.5, sh * 0.92, 0.08)
        val foot = v(side * bw * 0.44, 0.05, 0.16)
        shoulders.add(top)
        bonePos[bone].copy(top)
        S.add(Sh.cone(top.toArray(), foot.toArray(), legR, legR * 0.86), base, k = 0.05, bone = BODY, split = Split(bone, sh * 0.86, sh * 0.5))
        pawAt(foot.clone().setY(0.0), 1.0, bone)
    }

    // ── Cuello y cabeza ──
    val headC = v(0.0, sh + 0.05 + hr * 0.82, 0.05 + hr * 0.04)
    bonePos[HEAD].set(0.0, sh + 0.02, 0.05)
    S.add(Sh.ellipsoid(doubleArrayOf(0.0, sh + 0.05, 0.05), doubleArrayOf(bw * 0.72, 0.1, 0.105)), base, k = 0.07, bone = BODY, split = Split(HEAD, sh - 0.02, sh + 0.1))
    val head = Host(headC, hr * headW, hr * 0.92, hr * 0.9)
    S.add(Sh.ellipsoid(headC.toArray(), doubleArrayOf(head.rx, head.ry, head.rz)), base, k = 0.07, bone = HEAD)
    if (m.dome > 0.45) {
        val dd = 0.84 + (m.dome - 0.45) * 0.4
        S.add(Sh.ellipsoid(doubleArrayOf(0.0, headC.y + hr * 0.2, headC.z - hr * 0.06), doubleArrayOf(hr * 0.86 * dd * headW, hr * 0.8 * dd, hr * 0.84 * dd)), base, k = 0.1, bone = HEAD)
    }
    for (side in intArrayOf(-1, 1)) {
        val c = 0.55 + m.width * 0.45 + (if (cat) 0.15 else 0.0)
        S.add(Sh.ellipsoid(doubleArrayOf(side * hr * 0.44 * headW, headC.y - hr * 0.34, headC.z + hr * 0.34), doubleArrayOf(hr * 0.36 * c, hr * 0.3 * c, hr * 0.34)), base, k = 0.07, bone = HEAD)
    }

    fun onHead(yaw: Double, pitch: Double): V3 {
        val cp = cosD(pitch)
        return v(head.rx * sinD(yaw) * cp, head.ry * sinD(pitch), head.rz * cosD(yaw) * cp).add(headC)
    }
    fun headNormal(p: V3): V3 {
        val q = p.clone().sub(headC)
        return v(q.x / (head.rx * head.rx), q.y / (head.ry * head.ry), q.z / (head.rz * head.rz)).normalize()
    }

    // Hocico: parte de arriba, almohadillas de los bigotes y barbilla.
    val mBase = v(0.0, headC.y - hr * 0.3, headC.z + hr * 0.6)
    val halfL = max(snoutLen * 0.6, mh * 0.85)
    val mC = mBase.clone().add(v(0.0, -snoutLen * 0.06, snoutLen * 0.5))
    val tipZ = mC.z + halfL
    S.add(Sh.ellipsoid(mC.toArray(), doubleArrayOf(mw, mh, halfL), rot(0.06, 0.0, 0.0)), base, k = 0.06, bone = HEAD)
    if (snoutLen > hr * 0.35 && m.stop < 0.6) {
        // Caña nasal: une la frente con el hocico en las razas de cara larga.
        S.add(Sh.cone(doubleArrayOf(0.0, headC.y + hr * 0.08, headC.z + hr * 0.62), doubleArrayOf(0.0, mC.y + mh * 0.5, tipZ - mh * 0.7), mw * 0.5, mw * 0.46), base, k = 0.08, bone = HEAD)
    }
    val padDrop = m.drop * mh * 0.45
    for (side in intArrayOf(-1, 1)) {
        S.add(Sh.ellipsoid(doubleArrayOf(side * mw * 0.42, mC.y - mh * 0.34 - padDrop, tipZ - mh * 0.62), doubleArrayOf(mw * 0.56, mh * (0.6 + m.drop * 0.4), mh * 0.78)), base, k = 0.035, bone = HEAD)
    }
    S.add(Sh.ellipsoid(doubleArrayOf(0.0, mC.y - mh * 0.86 - padDrop * 0.6, tipZ - mh * 0.95 - snoutLen * 0.12), doubleArrayOf(mw * 0.5, mh * 0.42, max(halfL * 0.7, mh * 0.6))), base, k = 0.04, bone = HEAD)
    // Cejas del hueso de la frente.
    if (m.stop > 0.35) {
        for (side in intArrayOf(-1, 1)) {
            S.add(Sh.ellipsoid(doubleArrayOf(side * hr * 0.34, headC.y + hr * 0.22, headC.z + hr * 0.7), doubleArrayOf(hr * 0.22, hr * 0.11 * m.stop, hr * 0.14)), base, k = 0.06, bone = HEAD)
        }
    }

    // ── Orejas ──
    val earLen = hr * (0.55 + m.earSize * 0.7) * (1 + P.kid * 0.08)

    // Devuelve dónde va el hueso de la oreja.
    fun ear(side: Int, bone: Int, type: String): V3 {
        val set = m.earSet
        if (type == "erect" || type == "cat" || type == "tufted" || type == "bat") {
            val yaw = side * (if (cat) 0.66 else 0.7 - set * 0.14)
            val pitch = 0.58 + set * 0.26
            val root = onHead(yaw, pitch)
            val out = headNormal(root)
            val flare = (if (cat) 0.3 else 0.24) + m.earSize * (if (cat) 0.16 else 0.3)
            val dir = v(side * flare, 1.0, -0.1).add(out.clone().multiplyScalar(0.2)).normalize()
            val fwd = v(side * 0.18, -0.05, 1.0)
            val q = basis(dir, fwd)
            val len = earLen * (if (type == "bat") 0.9 else 0.82)
            val wide = len * (if (cat) 1.02 else if (type == "bat") 1.0 else 0.96)
            val thick = hr * 0.13
            val start = root.clone().addScaledVector(dir, -len * 0.42)
            if (type == "bat") {
                val c = root.clone().addScaledVector(dir, len * 0.32)
                S.add(Sh.ellipsoid(c.toArray(), doubleArrayOf(wide * 0.56, len * 0.6, thick * 0.6), rot(q)), earColor, k = 0.04, bone = bone)
                S.paint(Sh.ellipsoid(c.clone().add(v(0.0, len * 0.04, thick * 0.5).applyQuaternion(q)).toArray(), doubleArrayOf(wide * 0.38, len * 0.46, thick * 0.4), rot(q)), innerEar, soft = 0.01)
            } else {
                S.add(Sh.leaf(start.toArray(), rot(q), len * 1.42, wide, thick, -0.04), earColor, k = 0.035, bone = bone)
                val inner = start.clone().addScaledVector(dir, len * 0.18).add(v(0.0, 0.0, thick * 0.55).applyQuaternion(q))
                S.paint(Sh.leaf(inner.toArray(), rot(q), len * 1.12, wide * 0.6, thick * 0.6, -0.04), innerEar, soft = 0.008)
                if (type == "tufted") {
                    val tip = start.clone().addScaledVector(dir, len * 1.42)
                    S.add(Sh.cone(tip.clone().addScaledVector(dir, -len * 0.1).toArray(), tip.clone().addScaledVector(dir, len * 0.28).add(v(side * 0.01, 0.0, 0.0)).toArray(), thick * 0.35, 0.003), region("ears") ?: base.clone().lerp(Col.hex("#2a2523"), 0.4), k = 0.01, bone = bone)
                }
                if (cat && plush) {
                    // Mechones blancos dentro de la oreja.
                    for (t in intArrayOf(-1, 1)) {
                        val a = root.clone().add(v(t * wide * 0.12, 0.0, thick * 0.4).applyQuaternion(q))
                        S.add(Sh.cone(a.toArray(), a.clone().addScaledVector(dir, len * 0.42).add(v(-side * 0.01, 0.0, 0.012)).toArray(), thick * 0.3, 0.003), Col.hex("#f6efe4"), k = 0.012, bone = bone)
                    }
                }
            }
            return root
        }
        if (type == "fold") {
            // Botón: base de pie y la punta doblada hacia delante.
            val root = onHead(side * 0.58, 0.82)
            val dir = v(side * 0.35, 1.0, 0.0).normalize()
            val q = basis(dir, v(0.0, 0.0, 1.0))
            S.add(Sh.ellipsoid(root.clone().addScaledVector(dir, earLen * 0.12).toArray(), doubleArrayOf(earLen * 0.36, earLen * 0.3, hr * 0.06), rot(q)), earColor, k = 0.035, bone = bone)
            val flapDir = v(side * 0.25, -0.75, 0.6).normalize()
            val flap = root.clone().addScaledVector(dir, earLen * 0.34).addScaledVector(flapDir, earLen * 0.22)
            S.add(Sh.ellipsoid(flap.toArray(), doubleArrayOf(earLen * 0.34, earLen * 0.3, hr * 0.055), rot(basis(flapDir, v(0.0, 1.0, 0.3)))), earColor, k = 0.025, bone = bone)
            return root
        }
        if (type == "rose") {
            val root = onHead(side * 0.82, 0.6)
            val dir = v(side * 0.85, 0.1, -0.5).normalize()
            val q = basis(dir, v(side * 0.3, 1.0, 0.4))
            S.add(Sh.ellipsoid(root.clone().addScaledVector(dir, earLen * 0.18).toArray(), doubleArrayOf(earLen * 0.24, earLen * 0.3, hr * 0.08), rot(q)), earColor, k = 0.035, bone = bone)
            return root
        }
        if (type == "catFold") {
            val root = onHead(side * 0.5, 0.86)
            val dir = v(side * 0.3, -0.15, 1.0).normalize()
            val q = basis(dir, v(0.0, 1.0, 0.0))
            S.add(Sh.ellipsoid(root.clone().addScaledVector(dir, earLen * 0.18).add(v(0.0, earLen * 0.04, 0.0)).toArray(), doubleArrayOf(earLen * 0.34, earLen * 0.26, earLen * 0.2), rot(q)), earColor, k = 0.04, bone = bone)
            return root
        }
        // Caídas ('drop') y largas ('long'): cuelgan de lo alto de la cabeza.
        val long = type == "long"
        val root = onHead(side * (if (long) 1.12 else 1.02), if (long) 0.42 + m.earSet * 0.2 else 0.52 + m.earSet * 0.12)
        val len = earLen * (if (long) 1.35 else 0.82)
        val out = headNormal(root)
        val hang = v(side * (if (long) 0.12 else 0.28), -1.0, if (long) 0.08 else 0.16).normalize()
        val q = basis(hang, out.clone().setY(0.0).normalize())
        val top = root.clone().addScaledVector(out, hr * 0.02)
        val c = top.clone().addScaledVector(hang, len * 0.46).addScaledVector(out, hr * 0.07)
        val wide = len * (if (long) 0.5 else 0.62)
        S.add(Sh.ellipsoid(top.toArray(), doubleArrayOf(wide * 0.42, len * 0.16, hr * 0.13), rot(q)), earColor, k = 0.035, bone = bone)
        S.add(Sh.ellipsoid(c.toArray(), doubleArrayOf(wide * 0.5, len * 0.5, hr * 0.095), rot(q)), earColor, k = 0.03, bone = bone)
        if (m.coatType == "feathered" || m.coatType == "curly" || m.coatType == "long") {
            // Flecos al final de la oreja.
            for (t in intArrayOf(-1, 0, 1)) {
                val a = c.clone().addScaledVector(hang, len * 0.3).add(v(t * wide * 0.25, 0.0, 0.0).applyQuaternion(q))
                S.add(Sh.ellipsoid(a.toArray(), doubleArrayOf(wide * 0.2, len * 0.22, hr * 0.06), rot(q)), earColor, k = 0.03, bone = bone)
            }
        }
        return root
    }
    for (side in intArrayOf(-1, 1)) {
        val bone = if (side < 0) EAR_L else EAR_R
        bonePos[bone].copy(ear(side, bone, m.earType))
    }

    // ── Cola ──
    val tailBase = v(0.0, 0.1 + legH * 0.15, -torsoLen - 0.03)
    bonePos[TAIL].copy(tailBase)

    fun tube(points: List<V3>, r0: Double, r1: Double, color: Col = tailColor, k: Double = 0.035, bone: Int = TAIL) {
        for (i in 0 until points.size - 1) {
            val a = mix(r0, r1, i.toDouble() / (points.size - 1))
            val b = mix(r0, r1, (i + 1).toDouble() / (points.size - 1))
            S.add(Sh.cone(points[i].toArray(), points[i + 1].toArray(), a, b), color, k = if (i == 0) 0.04 else k, bone = bone)
        }
    }

    run {
        val type = m.tailType
        val s = 0.75 + m.tailSize * 0.5
        val tb = tailBase
        val thick = (if (cat) 0.03 else 0.036) * (0.85 + m.chunk * 0.3)
        val fluffy = plush || m.coatType == "fluffy"
        when (type) {
            "none" -> {}
            "bob" -> S.add(Sh.sphere(tb.clone().add(v(0.0, 0.02, -0.03)).toArray(), 0.045 * s * (if (fluffy) 1.3 else 1.0)), tailColor, k = 0.04, bone = TAIL)
            "carrot" -> {
                val pts = catmullRom(listOf(tb, tb.clone().add(v(0.01, 0.12 * s, -0.08)), tb.clone().add(v(0.03, 0.27 * s, -0.08)), tb.clone().add(v(0.04, 0.36 * s, -0.03))), 8)
                tube(pts, thick * 1.1, thick * 0.6)
                region("tailTip")?.let { S.paint(Sh.sphere(pts.last().toArray(), 0.05 * s), it, soft = 0.01) }
            }
            "curl" -> {
                // Enroscada sobre el lomo.
                val up = sh * 0.75
                val pts = catmullRom(listOf(tb, tb.clone().add(v(0.0, up * 0.5, -0.07)), tb.clone().add(v(0.04, up * 0.95, -0.02)), tb.clone().add(v(0.09, up * 1.02, 0.08)), tb.clone().add(v(0.12, up * 0.82, 0.1))), 10)
                tube(pts, thick * (if (fluffy) 1.7 else 1.15), thick * (if (fluffy) 1.2 else 0.6))
                if (fluffy) {
                    var i = 2
                    while (i < pts.size) {
                        S.add(Sh.sphere(pts[i].clone().add(v(0.02, 0.025, -0.01)).toArray(), thick * 1.3), tailColor, k = 0.03, bone = TAIL)
                        i += 2
                    }
                }
                region("tailUnder")?.let { under -> for (i in 1 until pts.size) S.paint(Sh.sphere(pts[i].clone().add(v(-0.03, -0.02, 0.02)).toArray(), thick * 1.15), under, soft = 0.012) }
            }
            "pom" -> {
                val pts = catmullRom(listOf(tb, tb.clone().add(v(0.0, 0.12 * s, -0.06)), tb.clone().add(v(0.01, 0.24 * s, -0.06))), 4)
                tube(pts, thick * 0.8, thick * 0.6)
                val end = pts.last()
                for (i in 0 until 7) {
                    val a = i * 2.4
                    S.add(Sh.sphere(end.clone().add(v(sinD(a) * 0.03, 0.03 + (i % 2) * 0.02, cosD(a) * 0.03)).toArray(), 0.045 * s), tailColor, k = 0.02, bone = TAIL)
                }
            }
            "cat", "whip", "bushy" -> {
                // Por el suelo y rodeando las patas, como un gato sentado.
                val r = bw + 0.07
                val pts = catmullRom(listOf(
                    tb,
                    v(r * 0.55, 0.04, -torsoLen - 0.02),
                    v(r * 1.05, 0.04, -0.02),
                    v(r * 0.95, 0.04, 0.16 * s),
                    v(r * 0.55, 0.05, 0.25 * s),
                ), 12)
                val t0 = if (type == "bushy") thick * 1.9 else if (type == "whip") thick * 0.8 else thick
                val t1 = if (type == "bushy") thick * 1.5 else if (type == "whip") thick * 0.4 else thick * 0.75
                tube(pts, t0, t1)
                if (type == "bushy") {
                    var i = 2
                    while (i < pts.size) {
                        S.add(Sh.sphere(pts[i].clone().add(v(0.0, 0.02, 0.0)).toArray(), t0 * 0.95), tailColor, k = 0.03, bone = TAIL)
                        i += 2
                    }
                }
                region("tailTip")?.let { S.paint(Sh.sphere(pts.last().toArray(), 0.05), it, soft = 0.01) }
            }
            else -> {
                // Perros: nutria ('otter') y pluma ('plume'), apoyadas en el suelo.
                val pts = catmullRom(listOf(tb, tb.clone().add(v(0.08, -0.04, -0.07)), v(bw + 0.1, 0.045, -torsoLen * 0.4), v(bw + 0.14, 0.05, 0.06 * s), v(bw + 0.1, 0.07, 0.14 * s)), 10)
                val plume = type == "plume"
                tube(pts, thick * (if (plume) 1.15 else 1.35), thick * (if (plume) 0.5 else 0.65))
                if (plume && fluffy) {
                    var i = 2
                    while (i < pts.size - 1) {
                        S.add(Sh.ellipsoid(pts[i].clone().add(v(0.035, 0.0, 0.0)).toArray(), doubleArrayOf(thick * 1.3, thick * 0.9, thick * 1.5)), tailColor, k = 0.025, bone = TAIL)
                        i += 2
                    }
                }
                region("tailTip")?.let { S.paint(Sh.sphere(pts.last().toArray(), 0.045), it, soft = 0.01) }
            }
        }
    }

    // Punto de la superficie visto desde fuera, a la altura `y` y en el ángulo `ang`.
    fun surfaceAt(y: Double, ang: Double): V3 {
        val dir = v(sinD(ang), 0.0, cosD(ang))
        return S.cast(v(0.0, y, -0.03).addScaledVector(dir, 0.9), dir.clone().negate(), 1.2).point
    }

    // ── Pelo ──
    if (m.coatType == "fluffy" || (cat && m.coatType == "long") || m.fluff > 0.2) {
        val f = (if (m.coatType == "fluffy") 1.0 else if (cat && m.coatType == "long") 1.35 else 0.5) + m.fluff
        // Golilla; en los cachorros, los mechones de la cara apenas asoman.
        val fc = min(f, 1.5) * (1 - P.kid * 0.45)
        for (side in intArrayOf(-1, 1)) {
            for (i in 0 until 3) {
                val a = onHead(side * (1.1 + i * 0.25), -0.38 - i * 0.1)
                val dir = v(side * 0.85, -0.5, -0.15 - i * 0.25).normalize()
                S.add(Sh.cone(a.toArray(), a.clone().addScaledVector(dir, hr * 0.26 * fc).toArray(), hr * 0.15 * fc, hr * 0.065 * fc), region("face") ?: base, k = 0.05, bone = HEAD)
            }
        }
        // Pechera: tres mechones redondos que asoman bajo la barbilla.
        val bibC = v(0.0, sh * 0.92, chestZ + 0.09)
        for (i in 0 until 3) {
            val x = (i - 1) * bw * 0.36
            val a = bibC.clone().add(v(x, -abs(i - 1) * 0.02, -abs(i - 1) * 0.025))
            S.add(Sh.cone(a.toArray(), a.clone().add(v(x * 0.1, -0.075 * fc, 0.035)).toArray(), 0.055 * fc, 0.03 * fc), region("chest") ?: base, k = 0.05, bone = BODY)
        }
    }
    if (m.coatType == "feathered") {
        for (side in intArrayOf(-1, 1)) {
            val top = shoulders[if (side < 0) 0 else 1]
            for (i in 0 until 3) {
                val a = top.clone().add(v(0.0, -0.06 - i * 0.07, -0.02))
                S.add(Sh.cone(a.toArray(), a.clone().add(v(side * 0.01, -0.05, -0.05)).toArray(), 0.028, 0.008), base, k = 0.03, bone = if (side < 0) ARM_L else ARM_R)
            }
        }
        val bibC = v(0.0, sh * 0.88, chestZ + 0.09)
        for (i in 0 until 3) {
            val a = bibC.clone().add(v((i - 1) * bw * 0.34, -abs(i - 1) * 0.02, 0.0))
            S.add(Sh.cone(a.toArray(), a.clone().add(v(0.0, -0.09, 0.025)).toArray(), 0.045, 0.024), region("chest") ?: base, k = 0.045, bone = BODY)
        }
    }
    if (m.coatType == "long" && !cat) {
        // Melena larga que cae por los lados de la cara y el cuerpo.
        val hair = region("head") ?: base
        for (side in intArrayOf(-1, 1)) {
            for (i in 0 until 3) {
                val a = onHead(side * (0.9 + i * 0.28), -0.1 - i * 0.08)
                val end = a.clone().add(v(side * hr * 0.12, -hr * (0.55 + i * 0.12), -hr * 0.05 * i))
                S.add(Sh.cone(a.toArray(), end.toArray(), hr * 0.16, hr * 0.07), hair, k = 0.05, bone = HEAD)
            }
        }
        for (i in 0 until 8) {
            val ang = (i / 8.0) * PI * 2 + 0.2
            val top = v(sinD(ang) * bw * 0.8, sh * 0.9, cosD(ang) * torsoLen * 0.8 - 0.02)
            val bottom = v(sinD(ang) * (bw + 0.06), 0.03, cosD(ang) * (torsoLen + 0.05) - 0.02)
            S.add(Sh.cone(top.toArray(), bottom.toArray(), 0.05, 0.075), base, k = 0.05, bone = BODY)
        }
    }
    if (m.coatType == "curly") {
        // Rizos: bultitos por el cuerpo y la cabeza, y un pompón arriba.
        fun bump(p: V3, r: Double, bone: Int) = S.add(Sh.sphere(p.toArray(), r), base, k = 0.018, bone = bone)
        for (i in 0 until 120) {
            val u = (i + 0.5) / 120
            val ang = i * 2.399
            val y = 0.03 + u * (sh + 0.05)
            val point = surfaceAt(y, ang)
            val normal = S.project(point, 1).normal
            bump(point.addScaledVector(normal, -0.008), 0.026 + rng.range(0.0, 0.008), BODY)
        }
        for (i in 0 until 46) {
            val u = (i + 0.5) / 46
            val pitch = -0.2 + u * 1.75
            val yaw = i * 2.399
            if (cosD(yaw) > 0.55 && pitch < 0.55) continue
            val at = S.project(onHead(yaw, pitch))
            bump(at.point.addScaledVector(at.normal, -0.006), hr * 0.13 + rng.range(0.0, hr * 0.03), HEAD)
        }
        for (i in 0 until 7) {
            val a = i * 2.399
            val r = if (i == 0) 0.0 else hr * 0.26
            S.add(Sh.sphere(doubleArrayOf(sinD(a) * r, headC.y + hr * 0.95 + (i % 3) * hr * 0.05, headC.z - hr * 0.08 + cosD(a) * r * 0.8), hr * 0.2), base, k = 0.03, bone = HEAD)
        }
    }
    if ("topknot" in m.extras && m.coatType != "curly") {
        val t = onHead(0.0, PI / 2 - 0.2)
        S.add(Sh.cone(t.toArray(), t.clone().add(v(0.0, hr * 0.38, -hr * 0.05)).toArray(), hr * 0.16, hr * 0.06), region("head") ?: base, k = 0.05, bone = HEAD)
    }
    if ("beard" in m.extras || m.coatType == "wire") {
        val beard = region("muzzle") ?: base
        S.add(Sh.ellipsoid(doubleArrayOf(0.0, mC.y - mh * 1.05, tipZ - mh * 0.7), doubleArrayOf(mw * 0.7, mh * 0.7, mh * 0.7)), beard, k = 0.04, bone = HEAD)
        for (side in intArrayOf(-1, 1)) {
            S.add(Sh.cone(doubleArrayOf(side * mw * 0.45, mC.y - mh * 0.5, tipZ - mh * 0.4), doubleArrayOf(side * mw * 0.7, mC.y - mh * 1.6, tipZ - mh * 0.5), mh * 0.3, mh * 0.08), beard, k = 0.03, bone = HEAD)
        }
    }
    if ("wrinkles" in m.extras) {
        // Pliegues de la frente y el rollo sobre la nariz.
        for (i in 0 until 2) {
            val pitch = 0.36 + i * 0.12
            var prev: V3? = null
            for (j in -2..2) {
                val p = S.project(onHead(j * 0.1, pitch - abs(j) * 0.03)).point
                if (prev != null) S.add(Sh.capsule(prev.toArray(), p.toArray(), hr * 0.022), base, k = 0.02, bone = HEAD)
                prev = p
            }
        }
        if (snoutLen < hr * 0.3) {
            S.add(Sh.capsule(doubleArrayOf(-mw * 0.55, mC.y + mh * 0.75, tipZ - mh * 0.25), doubleArrayOf(mw * 0.55, mC.y + mh * 0.75, tipZ - mh * 0.25), mh * 0.2), base, k = 0.03, bone = HEAD)
        }
    }
    if ("mane" in m.extras) {
        // Melena de león alrededor de la cara.
        for (i in 0 until 14) {
            val a = (i / 14.0) * PI * 2
            if (cosD(a) > 0.55) continue
            val p = v(sinD(a) * head.rx * 1.0, headC.y - hr * 0.05 + cosD(a) * hr * 0.92, headC.z - hr * 0.42)
            S.add(Sh.sphere(p.toArray(), hr * 0.27), base, k = 0.05, bone = HEAD)
        }
        S.add(Sh.ellipsoid(doubleArrayOf(0.0, sh + 0.02, 0.05), doubleArrayOf(bw * 1.25, 0.16, 0.16)), base, k = 0.07, bone = BODY, split = Split(HEAD, sh - 0.04, sh + 0.1))
    }
    if ("brows" in m.extras || m.coatType == "wire") {
        for (side in intArrayOf(-1, 1)) {
            val a = S.project(onHead(side * 0.32, 0.36)).point
            S.add(Sh.cone(a.toArray(), a.clone().add(v(side * hr * 0.12, hr * 0.06, hr * 0.2)).toArray(), hr * 0.1, hr * 0.03), region("brows") ?: base, k = 0.03, bone = HEAD)
        }
    }
    if ("folds" in m.extras) {
        S.add(Sh.torus(doubleArrayOf(0.0, sh + 0.0, 0.04), bw * 0.85, 0.035, rot(0.3, 0.0, 0.0)), base, k = 0.04, bone = BODY)
        S.add(Sh.torus(doubleArrayOf(0.0, sh * 0.62, -0.02), bw * 1.02, 0.03, rot(0.3, 0.0, 0.0)), base, k = 0.04, bone = BODY)
    }
    if (m.coatType == "rex") {
        for (i in 0 until 60) {
            val ang = i * 2.399
            val p = surfaceAt(0.05 + ((i + 0.5) / 60) * (sh + 0.04), ang)
            val normal = S.project(p, 1).normal
            S.add(Sh.ellipsoid(p.addScaledVector(normal, -0.006).toArray(), doubleArrayOf(0.02, 0.012, 0.02)), base, k = 0.016, bone = BODY)
        }
    }
    if (m.scruff > 0) {
        // Pelo de punta en la coronilla.
        val n = 4
        for (i in 0 until n) {
            val a = onHead((i - (n - 1) / 2.0) * 0.2, PI / 2 - 0.35)
            val dir = headNormal(a).add(v(0.0, 0.4, -0.5)).normalize()
            S.add(Sh.cone(a.toArray(), a.clone().addScaledVector(dir, hr * 0.28 * m.scruff).toArray(), hr * 0.09, 0.004), base, k = 0.025, bone = HEAD)
        }
    }

    // ── Capa ──
    fun paintPath(points: List<V3>, radius: Double, color: Col, softness: Double = 0.008) {
        var prev: V3? = null
        for (p in points) {
            val q = S.project(p).point
            if (prev != null) S.paint(Sh.capsule(prev.toArray(), q.toArray(), radius), color, soft = softness)
            prev = q
        }
    }
    fun paintPatches(colors: List<String>, count: Int) {
        for (i in 0 until count) {
            val ang = rng.range(0.0, PI * 2)
            val y = 0.08 + rng.next() * (headC.y + hr * 0.6)
            val p = surfaceAt(y, ang)
            if (p.z > headC.z + hr * 0.4 && p.y > headC.y - hr * 0.6) continue
            S.paint(Sh.sphere(p.toArray(), rng.range(0.07, 0.12)), Col.hex(colors[i % colors.size]), soft = 0.012)
        }
    }
    val pawSpots = listOf(v(-bw * 0.44, 0.0, 0.18), v(bw * 0.44, 0.0, 0.18), v(-thighX, 0.0, 0.1), v(thighX, 0.0, 0.1))
    fun paintRegion(name: String, entry: CoatPaint) {
        if (name == "ears" || name == "tail" || name == "innerEar" || name == "tailUnder" || name == "tailTip") return
        entry.colors?.let { return paintPatches(it, if (name == "patches") 6 else 4) }
        val c = Col.hex(entry.color!!)
        when (name) {
            "muzzle" -> S.paint(Sh.ellipsoid(doubleArrayOf(0.0, mC.y - mh * 0.2, mC.z + halfL * 0.15), doubleArrayOf(mw * 1.18, mh * 1.45, halfL * 1.05 + mh * 0.3)), c, soft = 0.014)
            "face" -> {
                S.paint(Sh.ellipsoid(doubleArrayOf(0.0, headC.y - hr * 0.42, headC.z + hr * 0.45), doubleArrayOf(head.rx * 1.02, hr * 0.5, hr * 0.85)), c, soft = 0.02)
                S.paint(Sh.ellipsoid(doubleArrayOf(0.0, sh + 0.02, 0.1), doubleArrayOf(bw * 0.7, 0.12, 0.12)), c, soft = 0.02)
            }
            "mask" -> {
                S.paint(Sh.ellipsoid(doubleArrayOf(0.0, mC.y, mC.z + halfL * 0.2), doubleArrayOf(mw * 1.25, mh * 1.6, halfL * 1.1 + mh * 0.4)), c, soft = 0.02)
                for (side in intArrayOf(-1, 1)) {
                    S.paint(Sh.sphere(S.project(onHead(side * (0.4 + m.eyeGap), 0.14)).point.toArray(), hr * 0.2), c, soft = 0.025)
                }
            }
            "blaze" -> paintPath(listOf(v(0.0, mC.y + mh * 0.9, tipZ - mh * 0.4), v(0.0, headC.y, headC.z + hr * 0.9), v(0.0, headC.y + hr * 0.5, headC.z + hr * 0.75), v(0.0, headC.y + hr * 0.85, headC.z + hr * 0.3)), hr * 0.1, c, 0.01)
            "brows" -> for (side in intArrayOf(-1, 1)) {
                val p = S.project(onHead(side * (0.36 + m.eyeGap), 0.42)).point
                S.paint(Sh.sphere(p.toArray(), hr * 0.09), c, soft = 0.006)
            }
            "cheeks" -> for (side in intArrayOf(-1, 1)) {
                S.paint(Sh.sphere(doubleArrayOf(side * mw * 0.85, mC.y - mh * 0.2, tipZ - mh * 0.9), mh * 0.75), c, soft = 0.012)
            }
            "head" -> S.paint(Sh.ellipsoid(headC.toArray(), doubleArrayOf(head.rx * 1.3, head.ry * 1.25, head.rz * 1.3)), c, soft = 0.03)
            "chest" -> S.paint(Sh.ellipsoid(doubleArrayOf(0.0, sh * 0.8, chestZ + 0.1), doubleArrayOf(bw * 0.6, 0.17, 0.09)), c, soft = 0.02)
            "belly" -> S.paint(Sh.ellipsoid(doubleArrayOf(0.0, sh * 0.32, 0.1), doubleArrayOf(bw * 0.62, sh * 0.34, 0.14)), c, soft = 0.02)
            "collar" -> S.paint(Sh.torus(doubleArrayOf(0.0, sh + 0.03, 0.04), bw * 0.72, 0.045, rot(0.25, 0.0, 0.0)), c, soft = 0.015)
            "paws" -> for (p in pawSpots) {
                S.paint(Sh.ellipsoid(doubleArrayOf(p.x, 0.04, p.z), doubleArrayOf(0.075, 0.07, 0.11)), c, soft = 0.01)
            }
            "legs" -> for (side in intArrayOf(-1, 1)) {
                S.paint(Sh.capsule(doubleArrayOf(side * bw * 0.46, 0.0, 0.17), doubleArrayOf(side * bw * 0.48, sh * 0.62, 0.11), legR * 1.5), c, soft = 0.01)
                S.paint(Sh.ellipsoid(doubleArrayOf(side * thighX, 0.04, 0.1), doubleArrayOf(0.075, 0.08, 0.12)), c, soft = 0.01)
            }
            "saddle" -> {
                S.paint(Sh.ellipsoid(doubleArrayOf(0.0, sh * 0.72, -torsoLen * 0.95), doubleArrayOf(bw * 1.4, sh * 0.5, torsoLen * 0.95)), c, soft = 0.03)
                S.paint(Sh.ellipsoid(doubleArrayOf(0.0, sh * 0.82, -torsoLen * 0.5), doubleArrayOf(bw * 1.1, sh * 0.32, torsoLen * 0.7)), c, soft = 0.03)
            }
            "back" -> S.paint(Sh.ellipsoid(doubleArrayOf(0.0, sh * 0.82, -torsoLen * 0.85), doubleArrayOf(bw * 0.8, sh * 0.42, torsoLen * 0.65)), c, soft = 0.03)
            "points" -> {
                // Puntas de los siameses: cara y patas, difuminadas.
                S.paint(Sh.ellipsoid(doubleArrayOf(0.0, mC.y + mh * 0.1, mC.z + halfL * 0.3), doubleArrayOf(mw * 1.35, mh * 1.9, halfL * 1.1 + mh * 0.5)), c, soft = 0.045)
                for (p in pawSpots) {
                    S.paint(Sh.ellipsoid(doubleArrayOf(p.x, 0.05, p.z), doubleArrayOf(0.08, 0.1, 0.12)), c, soft = 0.04)
                }
            }
            "cap" -> S.paint(Sh.ellipsoid(doubleArrayOf(0.0, headC.y + hr * 0.75, headC.z - hr * 0.1), doubleArrayOf(head.rx * 1.05, hr * 0.55, head.rz * 1.1)), c, soft = 0.02)
            "eyePatch" -> {
                val p = S.project(onHead(-(0.4 + m.eyeGap), 0.16)).point
                S.paint(Sh.sphere(p.toArray(), hr * 0.32), c, soft = 0.01)
            }
            "spots" -> for (i in 0 until 130) {
                val ang = rng.range(0.0, PI * 2)
                val y = 0.04 + rng.next() * (headC.y + hr * 0.9)
                val p = surfaceAt(y, ang)
                if (p.y > headC.y - hr * 0.3 && p.z > headC.z + hr * 0.45) continue
                S.paint(Sh.sphere(p.toArray(), rng.range(0.013, 0.024)), c, soft = 0.004)
            }
            "rosettes" -> for (i in 0 until 60) {
                val ang = rng.range(0.0, PI * 2)
                val y = 0.06 + rng.next() * sh * 1.05
                val p = surfaceAt(y, ang)
                val r = rng.range(0.024, 0.036)
                S.paint(Sh.sphere(p.toArray(), r), c, soft = 0.004)
                S.paint(Sh.sphere(p.toArray(), r * 0.55), base.clone().lerp(c, 0.35), soft = 0.004)
            }
            "stripes" -> {
                // Rayas de atigrado: la M de la frente y las mejillas (solo
                // gatos), el lomo y las patas.
                val w = hr * 0.045
                if (cat) {
                    for (x in doubleArrayOf(-0.18, 0.0, 0.18)) paintPath(listOf(onHead(x, 0.5), onHead(x * 1.2, 0.72), onHead(x * 1.3, 0.95)), w, c, 0.006)
                    for (side in intArrayOf(-1, 1)) {
                        for (dy in doubleArrayOf(0.0, -0.12)) paintPath(listOf(onHead(side * 0.95, 0.1 + dy), onHead(side * 1.25, 0.02 + dy), onHead(side * 1.5, -0.05 + dy)), w * 0.9, c, 0.006)
                    }
                }
                for (i in 0 until 7) {
                    val z = -torsoLen * 0.9 + i * 0.045
                    val pts = ArrayList<V3>()
                    var a = -1.6
                    while (a <= 1.6) {
                        pts.add(v(sinD(a) * (bw + 0.1), sh * 0.6 + cosD(a) * 0.25, z + abs(a) * 0.02))
                        a += 0.4
                    }
                    paintPath(pts, w * 1.15, c, 0.007)
                }
                for (side in intArrayOf(-1, 1)) {
                    for (i in 0 until 3) {
                        val y = 0.08 + i * 0.07
                        S.paint(Sh.torus(doubleArrayOf(side * bw * 0.46, y, 0.15), legR * 1.02, w * 0.8, rot(0.15, 0.0, 0.0)), c, soft = 0.005)
                    }
                }
            }
        }
    }
    for (entry in coat.paint) paintRegion(entry.region, entry)

    // Marcas de las formas ocultas.
    if (m.look == "radiante" || m.look == "lunar") {
        val markColor = if (m.look == "radiante") Col.hex(GOLD) else Col.hex(NIGHT_MARK)
        val at = S.project(onHead(0.0, 0.62))
        val q = faceFrame(at.normal)
        if (m.look == "radiante") {
            for (i in 0 until 5) {
                val a = (i / 5.0) * PI * 2
                val tip = at.point.clone().add(v(sinD(a) * hr * 0.16, cosD(a) * hr * 0.16, 0.0).applyQuaternion(q))
                S.paint(Sh.capsule(at.point.toArray(), tip.toArray(), hr * 0.05), markColor, soft = 0.004)
            }
        } else {
            S.paint(Sh.sphere(at.point.toArray(), hr * 0.15), markColor, soft = 0.004)
            S.paint(Sh.sphere(at.point.clone().add(v(hr * 0.07, hr * 0.05, 0.0).applyQuaternion(q)).toArray(), hr * 0.14), Col.hex(coat.base), soft = 0.004)
        }
    }

    // ── Malla y huesos ──
    val finish = if (m.coatType == "hairless") "gloss" else if (plush) "plush" else "matte"
    val bodyGeometry = S.build(cell = cell, bones = BONES, aoReach = 0.12, ao = if (detail == "tiny") 0.0 else 1.0)
    val (mesh, bones) = skin(bodyGeometry, clay(finish, wrap = 0.36f, sss = "#ff9d80"), bonePos)

    val root = Group()
    val bounce = Group()
    val squash = Group()
    root.add(bounce)
    bounce.add(squash)
    squash.add(mesh)
    root.scale.setScalar(P.scale.toFloat())
    val headPivot = bones[HEAD]
    val headBase = bonePos[HEAD]
    fun toHead(obj: Object3D): Object3D {
        obj.position.sub(headBase.gl())
        headPivot.add(obj)
        return obj
    }
    fun decal(center: V3, normal: V3, width: Double, height: Double, material: Material): Mesh {
        val geometry = surfacePatch(S, center, normal, AXIS_Y, width, height, 8, 0.0015)
        geometry.translate(-headBase.x.toFloat(), -headBase.y.toFloat(), -headBase.z.toFloat())
        val mm = Mesh(geometry, material)
        headPivot.add(mm)
        return mm
    }

    // ── Nariz ──
    val noseAt = S.project(v(0.0, mC.y + mh * 0.42, tipZ + 0.02))
    val ns = if (cat) mw * 0.3 else mw * (0.5 + (1 - m.snout) * 0.05)
    val noseColor = if (m.look == "radiante") "#3a2a20" else breed.nose
    val noseGeometry = run {
        val N = Sculpt()
        if (cat) {
            N.add(Sh.ellipsoid(doubleArrayOf(0.0, 0.0, 0.0), doubleArrayOf(ns, ns * 0.62, ns * 0.55)), noseColor, k = 0.0)
            N.add(Sh.cone(doubleArrayOf(0.0, -ns * 0.2, ns * 0.1), doubleArrayOf(0.0, -ns * 0.62, ns * 0.2), ns * 0.42, ns * 0.12), noseColor, k = 0.02)
            for (side in intArrayOf(-1, 1)) N.sub(Sh.ellipsoid(doubleArrayOf(side * ns * 0.42, -ns * 0.18, ns * 0.42), doubleArrayOf(ns * 0.16, ns * 0.12, ns * 0.2)), k = 0.01)
        } else {
            N.add(Sh.ellipsoid(doubleArrayOf(0.0, 0.0, 0.0), doubleArrayOf(ns, ns * 0.66, ns * 0.62)), noseColor, k = 0.0)
            N.add(Sh.ellipsoid(doubleArrayOf(0.0, ns * 0.12, ns * 0.12), doubleArrayOf(ns * 0.9, ns * 0.5, ns * 0.55)), noseColor, k = 0.03)
            for (side in intArrayOf(-1, 1)) N.sub(Sh.ellipsoid(doubleArrayOf(side * ns * 0.4, -ns * 0.1, ns * 0.55), doubleArrayOf(ns * 0.2, ns * 0.13, ns * 0.22), rot(0.0, 0.0, side * -0.5)), k = 0.012)
            N.sub(Sh.capsule(doubleArrayOf(0.0, -ns * 0.2, ns * 0.62), doubleArrayOf(0.0, -ns * 0.7, ns * 0.5), ns * 0.05), k = 0.008)
        }
        N.build(cell = ns * 0.09, ao = 0.6, aoReach = ns * 0.4)
    }
    val nose = Mesh(noseGeometry, clay("glaze", wrap = 0.1f))
    nose.place(noseAt.point.clone().addScaledVector(noseAt.normal, ns * 0.12))
    nose.turn(faceFrame(v(0.0, 0.25, 1.0).lerp(noseAt.normal, 0.5).normalize()))
    nose.castShadow = true
    toHead(nose)

    // ── Boca ──
    val mouthW = if (cat) mw * 1.3 else mw * 1.55
    val mouthH = mouthW * (160.0 / 256)
    val noseBottom = noseAt.point.clone().add(v(0.0, -ns * (if (cat) 0.55 else 0.6), 0.0))
    val mouthAt = S.project(noseBottom.clone().add(v(0.0, -mouthH * 0.5, 0.0)))
    val species = P.species
    val mouths = listOf("smile", "sad", "open", "flat").associateWith { petMouthTexture(species, it) }
    val mouth = decal(mouthAt.point, mouthAt.normal, mouthW, mouthH, decalMaterial(mouths.getValue("smile")))

    // Bigotes de los gatos: tres puntos en cada almohadilla.
    if (cat) {
        val dotMat = Material.standard(base.clone().lerp(Col.hex("#2a1d18"), 0.55).toGl(), 0.8f)
        for (side in intArrayOf(-1, 1)) {
            for (i in 0 until 3) {
                val p = S.project(v(side * mw * (0.45 + (i % 2) * 0.12), mC.y - mh * 0.15 - i * mh * 0.12, tipZ)).point
                val dot = Mesh(WHISKER, dotMat)
                dot.scale.setScalar((hr * 0.012).toFloat())
                dot.place(p)
                toHead(dot)
            }
        }
    }

    // ── Ojos ──
    val irisList = breed.eyeColors
    val iris = irisList[(genes.eyes ?: 0).coerceIn(0, irisList.size - 1)]
    val eyeR = hr * (if (cat) 0.19 else 0.175) * (1 + P.kid * 0.2) * m.eyes * (if (m.snout < 0.15) 1.12 else 1.0)
    val gap = (if (cat) 0.42 else 0.4) + m.eyeGap + (if (m.snout < 0.15) 0.04 else 0.0)
    val eyePitch = if (cat) 0.16 else 0.14
    val eyes = ArrayList<Eye>()
    val content = m.look == "sibarita"
    for (side in intArrayOf(-1, 1)) {
        val at = S.project(onHead(side * gap, eyePitch))
        val skinColor = S.colorNear(at.point.clone().add(v(0.0, eyeR * 1.2, 0.0)))
        val irisColor = if (iris == "odd") (if (side < 0) "#4a9ad8" else "#9a6a2a") else if (m.look == "radiante") "#d9a52b" else iris
        val eye = makeEye(at.point, at.normal, eyeR, if (cat) 1.02 else 1.08, iris = irisColor, kind = if (cat) "slit" else "round", skin = skinColor, tilt = if (cat) -side * 0.16 else -side * 0.04, sparkle = m.look == "radiante")
        toHead(eye.group)
        val closed = decal(at.point, at.normal, eyeR * 3, eyeR * 2.2, decalMaterial(lidTexture("sleep")))
        closed.visible = content
        eye.open.visible = !content
        if (m.brows) eye.lid.rotation.z = side * 0.32f
        val rest = if (m.brows) max(m.lids, 0.22) else m.lids
        eyes.add(Eye(eye.group, eye.open, closed, side, eye.r, eye.tall, content, rest.toFloat(), eye.lid, eye.bead))
    }

    // Coloretes: apenas, solo cuando está feliz.
    val cheeks = ArrayList<Mesh>()
    for (side in intArrayOf(-1, 1)) {
        val at = S.project(onHead(side * (gap + 0.38), -0.1))
        val material = decalMaterial(blushTexture()) {
            color.setHex("#ff8f9f")
            opacity = 0f
        }
        cheeks.add(decal(at.point, at.normal, hr * 0.4, hr * 0.26, material))
    }

    if (m.brows) {
        for (side in intArrayOf(-1, 1)) {
            val at = S.project(onHead(side * gap * 0.92, eyePitch + 0.36))
            decal(at.point, at.normal, eyeR * 2.6, eyeR * 1.3, decalMaterial(browTexture(side)))
        }
    }
    if (m.scar) {
        val at = S.project(onHead(-(gap + 0.04), eyePitch + 0.02))
        decal(at.point, at.normal, eyeR * 1.2, eyeR * 3.2, decalMaterial(scarTexture()))
    }

    // Aureola de la radiante.
    var halo: Mesh? = null
    var haloY = 0f
    if (m.look == "radiante") {
        val ring = Mesh(torusGeometry((hr * 0.5).toFloat(), (hr * 0.06).toFloat(), 16, 48), Material.standard(Color.hex("#ffe08a"), 0.3f) {
            metalness = 0.2f
            emissive.setHex("#ffcf5c")
            emissiveIntensity = 0.9f
        })
        ring.rotation.set(PI_F / 2f - 0.22f, 0f, 0.1f)
        ring.position.set(0f, (headC.y + hr * 1.5).toFloat(), (headC.z - hr * 0.1).toFloat())
        toHead(ring)
        haloY = ring.position.y
        halo = ring
    }

    // ── Anclajes para la ropa ──
    val hostLocal = Host(headC.clone().sub(headBase), head.rx, head.ry, head.rz)
    var neckR = Double.POSITIVE_INFINITY
    var neckY = sh + 0.04
    var y = sh - 0.04
    while (y < sh + 0.12) {
        val r = radiusAt(S, y, 1.0, 0.0)
        if (r > 0 && r < neckR) {
            neckR = r
            neckY = y
        }
        y += 0.01
    }
    val anchors = Anchors(
        hat = HatAnchor(hostLocal.center.y + head.ry * 0.96, head.rx),
        face = FaceAnchor(hostLocal.center.y + head.ry * sinD(eyePitch), head.rz, gap, head.rx, hostLocal, eyePitch),
        neck = NeckAnchor(neckY, neckR + 0.012, (radiusAt(S, neckY, 0.0, 1.0) + radiusAt(S, neckY, 0.0, -1.0)) / 2 + 0.012),
    )

    val height = ((headC.y + head.ry) * P.scale).toFloat()
    val look = Look(P.family, P.stage, P.scale, false, bw * 2.2, head.rx)
    val rig = Rig(
        root, bounce, squash, headPivot, eyes, mouths, mouth, cheeks,
        ears = listOf(Limb(bones[EAR_L], -1, 0f), Limb(bones[EAR_R], 1, 0f)),
        arms = listOf(Limb(bones[ARM_L], -1, 0f, leg = true), Limb(bones[ARM_R], 1, 0f, leg = true)),
        tail = bones[TAIL],
        wings = emptyList(),
        top = Group(),
        halo = halo,
        haloY = haloY,
        mesh = mesh,
        blushRest = 0f,
        blushHappy = 0.34f,
    )
    return Built(root, look, height, anchors, rig)
}
