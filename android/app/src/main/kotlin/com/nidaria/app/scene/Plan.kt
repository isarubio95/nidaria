// De la forma (posición en el árbol) y el ADN sale un plano corporal:
// la familia pone la base, la rama de joven cambia el cuerpo y la rama
// de adulta añade una pieza grande. El ADN afina proporciones y color.

package com.nidaria.app.scene

import com.nidaria.app.rules.Genes
import com.nidaria.app.rules.getForm
import kotlin.math.max

class Plan(
    val stage: String,
    val family: String,
    val finish: String,
    var scale: Float,
    val blob: Boolean,
    var bodyW: Float,
    var bodyH: Float,
    var bodyD: Float,
    val headRx: Float,
    val headRy: Float,
    val headRz: Float,
    var earType: String,
    var earSize: Float,
    var earDroop: Float,
    var topType: String,
    var topSize: Float,
    var tailType: String,
    var tailSize: Float,
    var eyeSize: Float,
    val eyeGap: Float,
    val eyeStyle: Int,
    var eyeLids: Float,
    var fang: Boolean,
    var brows: Boolean,
    var cheeks: Float,
    var belly: Boolean,
    val pattern: String,
    val patternAmt: Float,
    var feet: Float,
    val arms: Boolean,
    var extra: String?,
    var toneSat: Float,
    var toneLight: Float,
)

private class FamilyBase(val ears: String, val top: String, val tail: String, val finish: String)

private val FAMILY_BASE = mapOf(
    "pelusa" to FamilyBase("round", "tuft", "pom", "plush"),
    "hoja" to FamilyBase("leaf", "sprout", "leaf", "matte"),
    "gota" to FamilyBase("fin", "drop", "drop", "gloss"),
)

private val STAGE_SCALE = mapOf("cria" to 0.74f, "joven" to 0.86f, "adulto" to 1f)

fun planFor(formId: String, genes: Genes): Plan {
    val form = getForm(formId)
    val family = form.family ?: genes.family
    val base = FAMILY_BASE.getValue(family)
    val round = genes.build.round.toFloat()
    val head = genes.build.head.toFloat()
    val b1 = form.path.getOrNull(0)
    val b2 = form.path.getOrNull(1)

    val plan = Plan(
        stage = form.stage,
        family = family,
        finish = base.finish,
        scale = STAGE_SCALE[form.stage] ?: 1f,
        blob = form.stage == "cria",
        bodyW = 0.84f + round * 0.14f,
        bodyH = 0.64f,
        bodyD = 0.76f + round * 0.1f,
        headRx = 0.5f + head * 0.06f,
        headRy = 0.45f + head * 0.04f,
        headRz = 0.47f + head * 0.05f,
        earType = base.ears,
        earSize = 0.85f + genes.build.ears.toFloat() * 0.45f,
        earDroop = 0f,
        topType = base.top,
        topSize = 1f,
        tailType = base.tail,
        tailSize = 0.75f + genes.build.tail.toFloat() * 0.6f,
        eyeSize = 0.92f + genes.build.eyes.toFloat() * 0.3f,
        eyeGap = 0.33f + genes.build.eyeGap.toFloat() * 0.13f,
        eyeStyle = genes.eyeStyle,
        eyeLids = 0f,
        fang = false,
        brows = false,
        cheeks = 1f,
        belly = genes.pattern == "barriga",
        pattern = genes.pattern,
        patternAmt = genes.patternAmt.toFloat(),
        feet = 1f,
        arms = form.stage != "cria",
        extra = null,
        toneSat = 0f,
        toneLight = 0f,
    )

    if (plan.blob) {
        plan.bodyW = 0.98f + round * 0.1f
        plan.bodyH = 0.86f
        plan.bodyD = 0.9f + round * 0.06f
        plan.earSize *= 0.9f
        plan.eyeSize *= 1.08f
    }

    when (b1) {
        "gloton" -> {
            plan.bodyW *= 1.2f
            plan.bodyD *= 1.14f
            plan.bodyH *= 0.96f
            plan.belly = true
            plan.cheeks = 1.5f
            plan.tailSize *= 0.8f
        }
        "jugueton" -> {
            plan.bodyW *= 0.9f
            plan.bodyH *= 1.1f
            plan.earSize *= 1.3f
            plan.tailSize *= 1.45f
            plan.feet = 1.3f
        }
        "tranquilo" -> {
            plan.earSize *= 1.18f
            plan.earDroop = 1f
            plan.topSize = 1.35f
            plan.eyeLids = 0.42f
        }
        "arisco" -> {
            if (family == "pelusa") plan.earType = "pointy"
            plan.earSize *= 1.1f
            plan.topType = "spikes"
            plan.tailType = "spike"
            plan.brows = true
            plan.fang = true
            plan.cheeks = 0.5f
            plan.toneSat = 8f
            plan.toneLight = -9f
        }
    }

    when (b2) {
        "gloton" -> {
            plan.extra = "mane"
            plan.scale *= 1.08f
        }
        "jugueton" -> {
            plan.extra = "wings"
            plan.tailSize *= 1.2f
        }
        "tranquilo" -> {
            plan.extra = "crown"
            plan.eyeLids = max(plan.eyeLids, 0.3f)
        }
        "arisco" -> {
            plan.extra = "horns"
            plan.brows = true
            plan.toneSat += 4f
            plan.toneLight -= 4f
        }
    }

    return plan
}
