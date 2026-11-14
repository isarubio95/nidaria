// Plano corporal de un monstruo, como plan.js en la web. La familia pone
// la base (zorro de fuego, dinosaurio de hoja o anfibio de agua), la etapa
// las proporciones (cría redonda, joven y adulto de pie), la rama de joven
// cambia el cuerpo y la de adulto añade una pieza grande. Las formas
// ocultas tienen su aspecto propio. El ADN afina proporciones y color.

package com.nidaria.app.scene

import com.nidaria.app.rules.Genes
import com.nidaria.app.rules.getForm
import kotlin.math.max

class Plan(
    val stage: String,
    val family: String,
    val finish: String,
    var scale: Double,
    val blob: Boolean,
    var bodyW: Double,
    var bodyH: Double,
    var bodyD: Double,
    var legs: Double,
    val headRx: Double,
    val headRy: Double,
    val headRz: Double,
    val snout: Double,
    var earType: String,
    var earSize: Double,
    var earDroop: Double,
    var topType: String,
    var topSize: Double,
    var tailType: String,
    var tailSize: Double,
    var eyeSize: Double,
    val eyeGap: Double,
    var eyeStyle: Int,
    var eyeLids: Double,
    var fang: Boolean,
    var brows: Boolean,
    var cheeks: Double,
    var spikes: Double,
    val belly: Boolean,
    val pattern: String,
    val patternAmt: Double,
    val arms: Boolean,
    var extra: String?,
    var toneSat: Double,
    var toneLight: Double,
)

class FamilyBase(val ears: String, val top: String, val tail: String, val finish: String, val snout: Double)

val FAMILY_BASE = mapOf(
    "pelusa" to FamilyBase("fox", "flame", "flame", "matte", 0.55),
    "hoja" to FamilyBase("leaf", "leaf", "leaf", "matte", 0.45),
    "gota" to FamilyBase("fin", "fin", "fish", "gloss", 0.12),
)

private val STAGE_SCALE = mapOf("cria" to 0.8, "joven" to 0.9, "adulto" to 1.12)

fun planFor(formId: String, genes: Genes): Plan {
    val form = getForm(formId)
    val family = form.family ?: genes.family
    val base = FAMILY_BASE[family] ?: FAMILY_BASE.getValue("pelusa")
    val build = genes.build
    val b1 = form.path.getOrNull(0)
    val b2 = form.path.getOrNull(1)
    val adult = form.stage == "adulto"

    val plan = Plan(
        stage = form.stage,
        family = family,
        finish = base.finish,
        scale = STAGE_SCALE[form.stage] ?: 1.0,
        blob = form.stage == "cria",
        bodyW = if (adult) 0.26 + build.round * 0.05 else 0.22 + build.round * 0.04,
        bodyH = if (adult) 0.52 else 0.33,
        bodyD = if (adult) 0.24 + build.round * 0.03 else 0.21 + build.round * 0.03,
        legs = if (adult) 0.25 else 0.14,
        headRx = if (adult) 0.31 + build.head * 0.03 else 0.35 + build.head * 0.03,
        headRy = if (adult) 0.28 + build.head * 0.025 else 0.32 + build.head * 0.025,
        headRz = if (adult) 0.28 + build.head * 0.03 else 0.31 + build.head * 0.03,
        snout = base.snout,
        earType = base.ears,
        earSize = 0.85 + build.ears * 0.35,
        earDroop = 0.0,
        topType = if (form.stage == "cria" && family == "hoja") "sprout" else base.top,
        topSize = 1.0,
        tailType = base.tail,
        tailSize = 0.8 + build.tail * 0.45,
        eyeSize = 0.95 + build.eyes * 0.2,
        eyeGap = 0.36 + build.eyeGap * 0.1,
        eyeStyle = genes.eyeStyle,
        eyeLids = 0.0,
        fang = adult,
        brows = false,
        cheeks = 1.0,
        spikes = 0.0,
        belly = true,
        pattern = genes.pattern,
        patternAmt = genes.patternAmt,
        arms = form.stage != "cria",
        extra = null,
        toneSat = 0.0,
        toneLight = 0.0,
    )

    if (plan.blob) {
        plan.bodyW = 0.92 + build.round * 0.1
        plan.bodyH = 0.82
        plan.bodyD = 0.84 + build.round * 0.06
        plan.earSize *= 0.9
        plan.eyeSize *= 1.12
        plan.tailSize *= 0.8
    }

    when (b1) {
        "gloton" -> {
            plan.bodyW *= 1.25
            plan.bodyD *= 1.18
            plan.legs *= 0.85
            plan.cheeks = 1.4
            plan.tailSize *= 0.85
        }
        "jugueton" -> {
            plan.bodyW *= 0.9
            plan.legs *= 1.3
            plan.earSize *= 1.25
            plan.tailSize *= 1.4
        }
        "tranquilo" -> {
            plan.earSize *= 1.12
            plan.earDroop = 0.8
            plan.topSize = 1.3
            plan.eyeLids = 0.38
        }
        "arisco" -> {
            plan.earSize *= 1.1
            plan.spikes = 1.0
            plan.brows = true
            plan.fang = true
            plan.cheeks = 0.4
            plan.toneSat = 6.0
            plan.toneLight = -7.0
        }
    }

    when (b2) {
        "gloton" -> {
            plan.extra = "mane"
            plan.scale *= 1.06
        }
        "jugueton" -> {
            plan.extra = "wings"
            plan.tailSize *= 1.15
        }
        "tranquilo" -> {
            plan.extra = "crown"
            plan.eyeLids = max(plan.eyeLids, 0.28)
        }
        "arisco" -> {
            plan.extra = "horns"
            plan.brows = true
            plan.spikes = max(plan.spikes, 0.7)
            plan.toneSat += 4
            plan.toneLight -= 4
        }
    }

    if (form.secret != null) secretLook(plan, form.secret, form.origin)
    return plan
}

private fun secretLook(plan: Plan, secret: String, origin: String?) {
    when (secret) {
        "radiante" -> {
            // Aureola dorada, colores vivos y ojos con doble brillo.
            plan.extra = "halo"
            plan.toneSat = 6.0
            plan.toneLight = 6.0
            plan.eyeStyle = 2
            plan.earSize *= 1.12
            plan.topSize = 1.25
            plan.tailSize *= 1.3
            plan.scale *= 1.04
        }
        "sibarita" -> {
            // Redonda como un postre, con nata y guinda en la cabeza.
            plan.bodyW *= 1.35
            plan.bodyD *= 1.25
            plan.legs *= 0.8
            plan.cheeks = 1.8
            plan.eyeLids = 0.3
            plan.topType = "cherry"
            plan.topSize = 1.1
            plan.tailSize *= 0.75
            plan.scale *= 1.06
        }
        "lunar" -> {
            // Una luna creciente en la cabeza, colores de noche y ojos de sueño.
            plan.topType = "moon"
            plan.topSize = 1.35
            plan.eyeLids = 0.42
            plan.earSize *= 1.15
            plan.earDroop = 0.6
            plan.tailSize *= 1.3
            plan.toneSat = -6.0
            plan.toneLight = -8.0
        }
        "mestiza" -> {
            // Orejas y cola de la familia de la que viene.
            val other = FAMILY_BASE.getValue(origin ?: plan.family)
            plan.earType = other.ears
            plan.earSize *= 1.1
            plan.tailType = other.tail
            plan.tailSize *= 1.2
            plan.bodyW *= 1.05
            plan.scale *= 1.04
        }
    }
}
