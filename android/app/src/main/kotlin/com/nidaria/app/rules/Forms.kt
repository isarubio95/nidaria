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

// Formas ocultas: adultas fuera de las cuatro ramas, para crianzas raras.
// Se comprueban en este orden al pasar de joven a adulta; la primera que
// se cumple gana. Las mestizas son una por cada pareja de familias.
val SECRETS = listOf("radiante", "sibarita", "lunar", "mestiza")

class SecretInfo(val name: String, val hint: String, val reason: String)

val SECRET_INFO = mapOf(
    "radiante" to SecretInfo(
        "Radiante",
        "Solo aparece con una crianza perfecta: ni un descuido en toda su vida y siempre bien atendida.",
        "La has criado sin un solo descuido.",
    ),
    "sibarita" to SecretInfo(
        "Sibarita",
        "Dicen que de joven no quería comer nada más que una cosa: la que más le gusta.",
        "De joven solo ha comido su comida favorita.",
    ),
    "lunar" to SecretInfo(
        "Lunar",
        "Nace de un huevo elegido de madrugada y crece en calma.",
        "Nació de madrugada y la has criado tranquila.",
    ),
    "mestiza" to SecretInfo(
        "Mestiza",
        "Sale de un huevo heredero que cambió de familia, si se la cuida bien.",
        "Lleva sangre de dos familias y la has cuidado bien.",
    ),
)

private val SECRET_NAMES = mapOf(
    "pelusa" to mapOf("radiante" to "Nimbo Radiante", "sibarita" to "Merengue Sibarita", "lunar" to "Plumón Lunar"),
    "hoja" to mapOf("radiante" to "Girasol Radiante", "sibarita" to "Higo Sibarita", "lunar" to "Helecho Lunar"),
    "gota" to mapOf("radiante" to "Rocío Radiante", "sibarita" to "Almíbar Sibarita", "lunar" to "Marea Lunar"),
)

// Familia de la criatura y familia de la que viene.
private val MIXED_NAMES = mapOf(
    "pelusa" to mapOf("hoja" to "Musgopelo", "gota" to "Espumón"),
    "hoja" to mapOf("pelusa" to "Cardolana", "gota" to "Nenúfar"),
    "gota" to mapOf("pelusa" to "Nubarrón", "hoja" to "Alguita"),
)

// Hora local (0-23) a la que hay que elegir el huevo para que cuente como
// nacida de madrugada. La decide el servidor; aquí solo por coherencia.
val NIGHT_HOURS = 0 until 6

// Calidad mínima de la crianza (media de las necesidades, de 0 a 1).
private const val RADIANT_QUALITY = 0.7
private const val MIXED_QUALITY = 0.6
private const val GOURMET_FEEDS = 8.0

class Form(
    val id: String,
    val stage: String,
    val family: String?,
    val parent: String?,
    val branch: String?,
    val path: List<String>,
    val name: String,
    val secret: String? = null,
    val origin: String? = null,
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
        // Las ocultas cuelgan de la cría: pueden salir de cualquier joven.
        fun secret(id: String, kind: String, name: String, origin: String? = null) =
            add(Form(id, "adulto", family, babyId, null, emptyList(), name, kind, origin))
        for (kind in listOf("radiante", "sibarita", "lunar")) {
            secret("oculta-$family-$kind", kind, SECRET_NAMES.getValue(family).getValue(kind))
        }
        for (origin in FAMILIES.filter { it != family }) {
            secret("oculta-$family-$origin", "mestiza", MIXED_NAMES.getValue(family).getValue(origin), origin)
        }
    }
    return forms
}

val FORMS: Map<String, Form> = buildForms()
val FORM_LIST: List<Form> = FORMS.values.toList()

fun getForm(id: String): Form = FORMS[id] ?: throw IllegalArgumentException("Forma desconocida: $id")

// Hijas dentro del árbol normal: las ocultas van aparte.
fun childrenOf(id: String): List<Form> = FORM_LIST.filter { it.parent == id && it.secret == null }

fun secretsOf(family: String): List<Form> = FORM_LIST.filter { it.family == family && it.secret != null }

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
    var favs: Double = 0.0,
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

// Calidad de toda la vida hasta ahora (crías y jóvenes), de 0 a 1.
private fun lifeQuality(creature: Creature): Double {
    val care = creature.care
    val life = creature.lifeCare
    val hours = max(life.hours + care.hours, 0.0001)
    val sum = life.sum + care.sumHunger + care.sumEnergy + care.sumHygiene + care.sumFun
    return sum / hours / 400
}

// Forma oculta que le toca al dejar de ser joven, o null. `branch` es la
// rama normal que seguiría.
fun secretFor(creature: Creature, branch: String): String? {
    val care = creature.care
    val genes = creature.genes
    val family = genes.family
    val mistakes = creature.lifeCare.mistakes + care.mistakes
    if (mistakes == 0.0 && lifeQuality(creature) >= RADIANT_QUALITY) return "oculta-$family-radiante"
    if (care.feeds >= GOURMET_FEEDS && care.favs == care.feeds) return "oculta-$family-sibarita"
    if (creature.bornNight && branch == "tranquilo") return "oculta-$family-lunar"
    val origin = genes.origin
    if (origin != null && origin != family && lifeQuality(creature) >= MIXED_QUALITY) return "oculta-$family-$origin"
    return null
}

// Forma siguiente a partir de la actual, la crianza y el ADN.
fun nextFormId(creature: Creature): String? {
    val care = creature.care
    val genes = creature.genes
    val form = getForm(creature.formId)
    return when (form.stage) {
        "huevo" -> "cria-${genes.family}"
        "cria" -> "joven-${form.family}-${chooseBranch(care, genes)}"
        "joven" -> {
            val branch = chooseBranch(care, genes)
            secretFor(creature, branch) ?: "adulto-${form.family}-${form.path[0]}-$branch"
        }
        else -> null
    }
}

// Pista para el Criadex: cómo se llega a una forma aún no descubierta.
fun hintFor(formId: String): String {
    val form = getForm(formId)
    if (form.stage == "cria") return "Nace de un huevo de la familia ${form.family}."
    form.secret?.let { return SECRET_INFO.getValue(it).hint }
    val branch = form.branch ?: return ""
    return BRANCH_INFO.getValue(branch).hint
}
