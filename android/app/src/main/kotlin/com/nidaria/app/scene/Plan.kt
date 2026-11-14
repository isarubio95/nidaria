// De la forma (posición en el árbol) y el ADN sale un plano corporal:
// la familia pone la base, la rama de joven cambia el cuerpo y la rama
// de adulta añade una pieza grande. Las formas ocultas no siguen ramas:
// cada una tiene su aspecto propio. El ADN afina proporciones y color.

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
    val headRx: Double,
    val headRy: Double,
    val headRz: Double,
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
    var belly: Boolean,
    val pattern: String,
    val patternAmt: Double,
    var feet: Double,
    val arms: Boolean,
    var extra: String?,
    var toneSat: Double,
    var toneLight: Double,
)

private class FamilyBase(val ears: String, val top: String, val tail: String, val finish: String)

private val FAMILY_BASE = mapOf(
    "pelusa" to FamilyBase("round", "tuft", "pom", "plush"),
    "hoja" to FamilyBase("leaf", "sprout", "leaf", "matte"),
    "gota" to FamilyBase("fin", "drop", "drop", "gloss"),
)

private val STAGE_SCALE = mapOf("cria" to 0.74, "joven" to 0.86, "adulto" to 1.0)

fun planFor(formId: String, genes: Genes): Plan {
    val form = getForm(formId)
    val family = form.family ?: genes.family
    val base = FAMILY_BASE.getValue(family)
    val round = genes.build.round
    val head = genes.build.head
    val b1 = form.path.getOrNull(0)
    val b2 = form.path.getOrNull(1)

    val plan = Plan(
        stage = form.stage,
        family = family,
        finish = base.finish,
        scale = STAGE_SCALE[form.stage] ?: 1.0,
        blob = form.stage == "cria",
        bodyW = 0.84 + round * 0.14,
        bodyH = 0.64,
        bodyD = 0.76 + round * 0.1,
        headRx = 0.5 + head * 0.06,
        headRy = 0.45 + head * 0.04,
        headRz = 0.47 + head * 0.05,
        earType = base.ears,
        earSize = 0.85 + genes.build.ears * 0.45,
        earDroop = 0.0,
        topType = base.top,
        topSize = 1.0,
        tailType = base.tail,
        tailSize = 0.75 + genes.build.tail * 0.6,
        eyeSize = 0.92 + genes.build.eyes * 0.3,
        eyeGap = 0.33 + genes.build.eyeGap * 0.13,
        eyeStyle = genes.eyeStyle,
        eyeLids = 0.0,
        fang = false,
        brows = false,
        cheeks = 1.0,
        belly = genes.pattern == "barriga",
        pattern = genes.pattern,
        patternAmt = genes.patternAmt,
        feet = 1.0,
        arms = form.stage != "cria",
        extra = null,
        toneSat = 0.0,
        toneLight = 0.0,
    )

    if (plan.blob) {
        plan.bodyW = 0.98 + round * 0.1
        plan.bodyH = 0.86
        plan.bodyD = 0.9 + round * 0.06
        plan.earSize *= 0.9
        plan.eyeSize *= 1.08
    }

    when (b1) {
        "gloton" -> {
            plan.bodyW *= 1.2
            plan.bodyD *= 1.14
            plan.bodyH *= 0.96
            plan.belly = true
            plan.cheeks = 1.5
            plan.tailSize *= 0.8
        }
        "jugueton" -> {
            plan.bodyW *= 0.9
            plan.bodyH *= 1.1
            plan.earSize *= 1.3
            plan.tailSize *= 1.45
            plan.feet = 1.3
        }
        "tranquilo" -> {
            plan.earSize *= 1.18
            plan.earDroop = 1.0
            plan.topSize = 1.35
            plan.eyeLids = 0.42
        }
        "arisco" -> {
            if (family == "pelusa") plan.earType = "pointy"
            plan.earSize *= 1.1
            plan.topType = "spikes"
            plan.tailType = "spike"
            plan.brows = true
            plan.fang = true
            plan.cheeks = 0.5
            plan.toneSat = 8.0
            plan.toneLight = -9.0
        }
    }

    when (b2) {
        "gloton" -> {
            plan.extra = "mane"
            plan.scale *= 1.08
        }
        "jugueton" -> {
            plan.extra = "wings"
            plan.tailSize *= 1.2
        }
        "tranquilo" -> {
            plan.extra = "crown"
            plan.eyeLids = max(plan.eyeLids, 0.3)
        }
        "arisco" -> {
            plan.extra = "horns"
            plan.brows = true
            plan.toneSat += 4.0
            plan.toneLight -= 4.0
        }
    }

    if (form.secret != null) secretLook(plan, form.secret, form.origin)
    return plan
}

private fun secretLook(plan: Plan, secret: String, origin: String?) {
    when (secret) {
        "radiante" -> {
            // Aureola dorada, colores más claros y ojos con doble brillo.
            plan.extra = "halo"
            plan.toneSat = 4.0
            plan.toneLight = 6.0
            plan.eyeStyle = 2
            plan.earSize *= 1.12
            plan.topSize = 1.2
            plan.tailSize *= 1.25
            plan.cheeks = 1.2
            plan.scale *= 1.04
        }
        "sibarita" -> {
            // Redonda como un postre, con nata y guinda en la cabeza.
            plan.bodyW *= 1.3
            plan.bodyD *= 1.2
            plan.bodyH *= 0.94
            plan.belly = true
            plan.cheeks = 1.8
            plan.eyeLids = 0.28
            plan.topType = "cherry"
            plan.topSize = 1.1
            plan.tailSize *= 0.75
            plan.scale *= 1.06
        }
        "lunar" -> {
            // Una luna creciente en la cabeza, colores apagados y ojos de sueño.
            plan.topType = "moon"
            plan.topSize = 1.35
            plan.eyeLids = 0.45
            plan.earSize *= 1.15
            plan.earDroop = 0.7
            plan.tailSize *= 1.3
            plan.toneSat = -8.0
            plan.toneLight = -5.0
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
