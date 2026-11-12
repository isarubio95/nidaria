// Árbol genealógico: todas las formas que puede tomar una criatura y la
// regla que decide, según la crianza, por qué rama evoluciona.

package com.nidaria.app.rules

import kotlin.math.max
import kotlinx.serialization.Serializable

val STAGES = listOf("huevo", "cria", "joven", "adulto")

class StageInfo(val name: String, val durationMs: Double?)

val STAGE_INFO = mapOf(
    "huevo" to StageInfo("Huevo", 45.0 * 1000),
    "cria" to StageInfo("Cría", 60.0 * 60 * 1000),
    "joven" to StageInfo("Joven", 24.0 * 60 * 60 * 1000),
    "adulto" to StageInfo("Adulta", null),
)

const val ADULT_LIFE_MS = 14.0 * 24 * 60 * 60 * 1000
const val ADULT_LIFE_BONUS_MS = 7.0 * 24 * 60 * 60 * 1000

val BRANCHES = listOf("gloton", "jugueton", "tranquilo", "arisco")

class BranchInfo(val name: String, val hint: String)

val BRANCH_INFO = mapOf(
    "gloton" to BranchInfo("Glotona", "Dale muchos dulces y tenla siempre bien comida."),
    "jugueton" to BranchInfo("Juguetona", "Juega mucho con ella: minijuegos y juguetes."),
    "tranquilo" to BranchInfo("Tranquila", "Mímala, báñala y déjala dormir bien."),
    "arisco" to BranchInfo("Arisca", "Sale cuando pasa hambre, sueño o aburrimiento a menudo."),
)

private val BABY_NAMES = mapOf("pelusa" to "Pelusín", "hoja" to "Brotín", "gota" to "Gotín")

private val YOUNG_NAMES = mapOf(
    "pelusa" to mapOf("gloton" to "Bolón", "jugueton" to "Brincol", "tranquilo" to "Arrullo", "arisco" to "Zarzo"),
    "hoja" to mapOf("gloton" to "Calabazo", "jugueton" to "Trepín", "tranquilo" to "Musguel", "arisco" to "Cardo"),
    "gota" to mapOf("gloton" to "Globul", "jugueton" to "Chapotín", "tranquilo" to "Remanso", "arisco" to "Erizal"),
)

private val ADULT_EPITHETS = mapOf(
    "pelusa" to mapOf("gloton" to "Goloso", "jugueton" to "Saltarín", "tranquilo" to "Sereno", "arisco" to "Bravo"),
    "hoja" to mapOf("gloton" to "Frondoso", "jugueton" to "Trepador", "tranquilo" to "Florido", "arisco" to "Espinoso"),
    "gota" to mapOf("gloton" to "Orondo", "jugueton" to "Saltaolas", "tranquilo" to "Manso", "arisco" to "Bravío"),
)

class Form(
    val id: String,
    val stage: String,
    val family: String?,
    val parent: String?,
    val branch: String?,
    val path: List<String>,
    val name: String,
)

private fun buildForms(): LinkedHashMap<String, Form> {
    val forms = LinkedHashMap<String, Form>()
    fun add(form: Form) { forms[form.id] = form }
    add(Form("huevo", "huevo", null, null, null, emptyList(), "Huevo"))
    for (family in FAMILIES) {
        val babyId = "cria-$family"
        add(Form(babyId, "cria", family, "huevo", null, emptyList(), BABY_NAMES.getValue(family)))
        for (b1 in BRANCHES) {
            val youngId = "joven-$family-$b1"
            val youngName = YOUNG_NAMES.getValue(family).getValue(b1)
            add(Form(youngId, "joven", family, babyId, b1, listOf(b1), youngName))
            for (b2 in BRANCHES) {
                add(
                    Form(
                        "adulto-$family-$b1-$b2", "adulto", family, youngId, b2, listOf(b1, b2),
                        "$youngName ${ADULT_EPITHETS.getValue(family).getValue(b2)}",
                    ),
                )
            }
        }
    }
    return forms
}

val FORMS: Map<String, Form> = buildForms()
val FORM_LIST: List<Form> = FORMS.values.toList()

fun getForm(id: String): Form = FORMS[id] ?: throw IllegalArgumentException("Forma desconocida: $id")

fun childrenOf(id: String): List<Form> = FORM_LIST.filter { it.parent == id }

@Serializable
data class Care(
    var hours: Double = 0.0,
    var sumHunger: Double = 0.0,
    var sumEnergy: Double = 0.0,
    var sumHygiene: Double = 0.0,
    var sumFun: Double = 0.0,
    var feeds: Double = 0.0,
    var sweets: Double = 0.0,
    var plays: Double = 0.0,
    var pets: Double = 0.0,
    var baths: Double = 0.0,
    var heals: Double = 0.0,
    var mistakes: Double = 0.0,
)

class CareAverages(val hunger: Double, val energy: Double, val hygiene: Double, val fun_: Double)

fun careAverages(care: Care): CareAverages {
    val hours = max(care.hours, 0.0001)
    return CareAverages(care.sumHunger / hours, care.sumEnergy / hours, care.sumHygiene / hours, care.sumFun / hours)
}

// Puntúa cada rama con lo que el jugador ha hecho en esta etapa.
fun branchScores(care: Care, genes: Genes?): Map<String, Double> {
    val avg = careAverages(care)
    fun perDay(count: Double) = (count / max(care.hours, 0.5)) * 24
    val mean = (avg.hunger + avg.energy + avg.hygiene + avg.fun_) / 4
    val scores = linkedMapOf(
        "gloton" to perDay(care.sweets) * 1.4 + perDay(care.feeds) * 0.25 + (if (avg.hunger > 78) 3 else 0),
        "jugueton" to perDay(care.plays) * 1.1 + (if (avg.fun_ > 70) 3 else 0),
        "tranquilo" to perDay(care.pets) * 0.35 + perDay(care.baths) * 1.2 +
            (if (avg.energy > 62) 2.5 else 0.0) + (if (avg.hygiene > 68) 2.5 else 0.0),
        "arisco" to perDay(care.mistakes) * 2.2 + (if (mean < 38) 8 else 0),
    )
    val traits = genes?.traits ?: emptyList()
    if ("gloton" in traits) scores["gloton"] = scores.getValue("gloton") + 1
    if ("travieso" in traits || "curioso" in traits) scores["jugueton"] = scores.getValue("jugueton") + 1
    if ("mimoso" in traits || "dormilon" in traits) scores["tranquilo"] = scores.getValue("tranquilo") + 1
    return scores
}

fun chooseBranch(care: Care, genes: Genes?): String {
    val scores = branchScores(care, genes)
    var best = "tranquilo"
    var bestScore = Double.NEGATIVE_INFINITY
    for (branch in listOf("tranquilo", "jugueton", "gloton", "arisco")) {
        if (scores.getValue(branch) > bestScore) {
            best = branch
            bestScore = scores.getValue(branch)
        }
    }
    return best
}

// Forma siguiente a partir de la actual, la crianza y el ADN.
fun nextFormId(formId: String, care: Care, genes: Genes): String? {
    val form = getForm(formId)
    return when (form.stage) {
        "huevo" -> "cria-${genes.family}"
        "cria" -> "joven-${form.family}-${chooseBranch(care, genes)}"
        "joven" -> "adulto-${form.family}-${form.path[0]}-${chooseBranch(care, genes)}"
        else -> null
    }
}

// Pista para el Criadex: cómo se llega a una forma aún no descubierta.
fun hintFor(formId: String): String {
    val form = getForm(formId)
    if (form.stage == "cria") return "Nace de un huevo de la familia ${form.family}."
    val branch = form.branch ?: return ""
    return BRANCH_INFO.getValue(branch).hint
}
