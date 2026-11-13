// Simulación del paso del tiempo: cómo bajan las necesidades, cuándo
// enferma, evoluciona o muere una criatura. El servidor es la autoridad;
// la app usa lo mismo para mover las barras entre visitas.

package com.nidaria.app.rules

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlinx.serialization.Serializable

val NEEDS = listOf("hunger", "energy", "hygiene", "fun")

val NEED_NAMES = mapOf(
    "hunger" to "Comida", "energy" to "Energía", "hygiene" to "Higiene", "fun" to "Diversión", "health" to "Salud",
)

const val CRITICAL = 15.0
const val LOW = 35.0

private const val HOUR = 60.0 * 60 * 1000
private const val STEP_MS = 5.0 * 60 * 1000

private val RATES_AWAKE = mapOf("hunger" to -6.0, "energy" to -4.2, "hygiene" to -3.2, "fun" to -5.0)
private val RATES_ASLEEP = mapOf("hunger" to -2.5, "energy" to 12.5, "hygiene" to -1.2, "fun" to -0.8)
private val STAGE_FACTOR = mapOf("huevo" to 0.0, "cria" to 3.0, "joven" to 1.3, "adulto" to 1.0)

@Serializable
data class LifeCare(var hours: Double = 0.0, var sum: Double = 0.0, var mistakes: Double = 0.0)

@Serializable
data class Equipped(val cabeza: String? = null, val cara: String? = null, val cuello: String? = null) {
    fun slot(id: String): String? = when (id) {
        "cabeza" -> cabeza
        "cara" -> cara
        else -> cuello
    }
}

@Serializable
data class Known(val fav: Boolean = false, val hate: Boolean = false)

// La criatura tal como la manda el servidor, con lo que añade su vista.
@Serializable
data class Creature(
    val id: String = "",
    val name: String = "",
    val genes: Genes = Genes(),
    var formId: String = "huevo",
    var stage: String = "huevo",
    val bornAt: Double = 0.0,
    var stageAt: Double = 0.0,
    var lifeEndsAt: Double? = null,
    var hunger: Double = 100.0,
    var energy: Double = 100.0,
    var hygiene: Double = 100.0,
    var `fun`: Double = 100.0,
    var health: Double = 100.0,
    var asleep: Boolean = false,
    var sick: Boolean = false,
    var alive: Boolean = true,
    var diedAt: Double? = null,
    var deathCause: String? = null,
    var tickAt: Double = 0.0,
    var care: Care = Care(),
    var lifeCare: LifeCare = LifeCare(),
    val equipped: Equipped = Equipped(),
    val cooldowns: Map<String, Double> = emptyMap(),
    var history: List<String> = listOf("huevo"),
    val generation: Int = 1,
    val parentId: String? = null,
    val pets: Double = 0.0,
    val lastPetAt: Double = 0.0,
    // Vínculo (ver Bond.kt). Lo sube el servidor; la simulación lo baja.
    var bond: Double = 0.0,
    val bondDay: Double = 0.0,
    val bondToday: Double = 0.0,
    // Si el huevo se eligió de madrugada (lo decide el servidor).
    val bornNight: Boolean = false,
    val known: Known = Known(),
    val code: String = "",
    val formName: String = "",
    val stageName: String = "",
    val nextStageAt: Double? = null,
    var mood: String = "bien",
    val tendency: String? = null,
) {
    fun need(id: String): Double = when (id) {
        "hunger" -> hunger
        "energy" -> energy
        "hygiene" -> hygiene
        "fun" -> `fun`
        else -> health
    }

    internal fun setNeed(id: String, value: Double) {
        when (id) {
            "hunger" -> hunger = value
            "energy" -> energy = value
            "hygiene" -> hygiene = value
            "fun" -> `fun` = value
            else -> health = value
        }
    }

    fun deepCopy(): Creature = copy(care = care.copy(), lifeCare = lifeCare.copy())
}

class SimEvent(val type: String, val at: Double, val from: String? = null, val to: String? = null, val cause: String? = null)

class SimResult(val creature: Creature, val events: List<SimEvent>)

private class TraitFactors(private val genes: Genes) {
    private fun has(trait: String) = trait in genes.traits
    val need = mapOf(
        "hunger" to (if (has("gloton")) 1.25 else 1.0),
        "energy" to (if (has("dormilon")) 1.2 else 1.0),
        "hygiene" to (if (has("presumido")) 1.25 else 1.0),
        "fun" to (if (has("travieso")) 1.25 else 1.0),
    )
    val healthLoss = if (has("fuerte")) 0.7 else 1.0
    val sleepGain = if (has("dormilon")) 1.1 else 1.0
}

fun stageDuration(stage: String, scale: Double = 1.0): Double? = STAGE_INFO.getValue(stage).durationMs?.let { it / scale }

fun nextStageAt(creature: Creature, scale: Double = 1.0): Double? = stageDuration(creature.stage, scale)?.let { creature.stageAt + it }

fun criticalNeeds(creature: Creature): List<String> {
    val list = ArrayList<String>()
    for (need in NEEDS) {
        if (need == "energy" && creature.asleep) continue
        if (creature.need(need) < CRITICAL) list.add(need)
    }
    return list
}

// Estado de ánimo, de mejor a peor: decide la cara, la postura y lo que dice.
fun moodOf(creature: Creature): String {
    if (!creature.alive) return "muerta"
    if (creature.stage == "huevo") return "huevo"
    if (creature.asleep) return "dormida"
    if (creature.sick) return "enferma"
    val critical = criticalNeeds(creature)
    if (critical.size >= 2 || creature.health < 30) return "fatal"
    if (critical.size == 1) return "mal"
    val mean = (creature.hunger + creature.energy + creature.hygiene + creature.`fun`) / 4
    if (mean > 78 && creature.health > 70) return "feliz"
    if (mean > 50) return "bien"
    return "regular"
}

private fun evolve(creature: Creature, at: Double, scale: Double, events: MutableList<SimEvent>) {
    val from = creature.formId
    val to = nextFormId(creature) ?: return
    val form = getForm(to)
    if (creature.stage == "huevo") {
        creature.hunger = 45.0
        creature.energy = 72.0
        creature.hygiene = 64.0
        creature.`fun` = 40.0
        creature.health = 100.0
    }
    creature.lifeCare.hours += creature.care.hours
    creature.lifeCare.sum += creature.care.sumHunger + creature.care.sumEnergy + creature.care.sumHygiene + creature.care.sumFun
    creature.lifeCare.mistakes += creature.care.mistakes
    creature.formId = to
    creature.stage = form.stage
    creature.stageAt = at
    creature.care = Care()
    creature.history = creature.history + to
    if (form.stage == "adulto") {
        val hours = max(creature.lifeCare.hours, 0.0001)
        val quality = clamp(creature.lifeCare.sum / hours / 400, 0.0, 1.0)
        creature.lifeEndsAt = at + (ADULT_LIFE_MS + ADULT_LIFE_BONUS_MS * quality) / scale
    }
    events.add(SimEvent("evolved", at, from = from, to = to))
}

private fun die(creature: Creature, at: Double, cause: String, events: MutableList<SimEvent>) {
    creature.alive = false
    creature.diedAt = at
    creature.deathCause = cause
    creature.asleep = false
    events.add(SimEvent("died", at, cause = cause))
}

private fun step(creature: Creature, from: Double, to: Double, scale: Double, events: MutableList<SimEvent>) {
    if (creature.stage == "huevo") return
    val hours = ((to - from) * scale) / HOUR
    val factors = TraitFactors(creature.genes)
    val stageFactor = STAGE_FACTOR.getValue(creature.stage)
    val rates = if (creature.asleep) RATES_ASLEEP else RATES_AWAKE
    val before = criticalNeeds(creature).size

    for (need in NEEDS) {
        var rate = rates.getValue(need)
        if (rate < 0) rate *= stageFactor * factors.need.getValue(need) else rate *= factors.sleepGain
        if (need == "fun" && creature.sick) rate *= 1.3
        creature.setNeed(need, clamp(creature.need(need) + rate * hours, 0.0, 100.0))
    }

    creature.care.hours += hours
    creature.care.sumHunger += creature.hunger * hours
    creature.care.sumEnergy += creature.energy * hours
    creature.care.sumHygiene += creature.hygiene * hours
    creature.care.sumFun += creature.`fun` * hours

    val critical = criticalNeeds(creature)
    if (critical.size > before) {
        creature.care.mistakes += critical.size - before
        loseBond(creature, (critical.size - before).toDouble())
    }

    if (!creature.asleep && creature.energy <= 0) {
        creature.asleep = true
        creature.care.mistakes += 1
        loseBond(creature, 1.0)
        events.add(SimEvent("collapsed", to))
    } else if (creature.asleep && creature.energy >= 100) {
        creature.asleep = false
        events.add(SimEvent("woke", to))
    }

    // Una tirada de enfermedad por cada hora de juego cumplida.
    val hourBefore = floor(((from - creature.bornAt) * scale) / HOUR)
    val hourAfter = floor(((to - creature.bornAt) * scale) / HOUR)
    if (!creature.sick && hourAfter > hourBefore) {
        val chance = if (creature.hygiene < 20) 0.1 else 0.004
        if (roll(creature.genes.seed, hourAfter.toLong()) < chance) {
            creature.sick = true
            events.add(SimEvent("sick", to))
        }
    }

    val delta = if (critical.isNotEmpty() || creature.sick) {
        -(2.2 * critical.size + (if (creature.sick) 3 else 0)) * factors.healthLoss
    } else {
        val mean = (creature.hunger + creature.energy + creature.hygiene + creature.`fun`) / 4
        if (mean > 55) 3.0 else 1.0
    }
    creature.health = clamp(creature.health + delta * hours, 0.0, 100.0)

    if (creature.health <= 0) {
        val cause = if (creature.sick) "enfermedad" else if (creature.hunger < CRITICAL) "hambre" else "abandono"
        die(creature, to, cause, events)
    }
}

// Avanza la criatura hasta `now`. No modifica el original.
fun simulate(original: Creature, now: Double, scale: Double = 1.0): SimResult {
    val creature = original.deepCopy()
    val events = ArrayList<SimEvent>()
    var t = creature.tickAt
    if (!creature.alive || now <= t) {
        creature.tickAt = max(t, now)
        return SimResult(creature, events)
    }
    val stepMs = STEP_MS / scale
    while (t < now && creature.alive) {
        var end = min(now, t + stepMs)
        val evolveAt = nextStageAt(creature, scale)
        if (evolveAt != null && evolveAt > t && evolveAt < end) end = evolveAt
        // El final depende del vínculo, que puede bajar en este tramo.
        val endsAt = lifeEnd(creature, scale)
        if (endsAt != null && endsAt > t && endsAt < end) end = endsAt
        step(creature, t, end, scale, events)
        t = end
        if (!creature.alive) break
        if (evolveAt != null && t >= evolveAt) evolve(creature, t, scale, events)
        val ends = lifeEnd(creature, scale)
        if (ends != null && t >= ends) die(creature, t, "vejez", events)
    }
    creature.tickAt = now
    creature.mood = moodOf(creature)
    return SimResult(creature, events)
}

// Juegos de una rama: solo para criaturas que se han criado por ella, de
// jóvenes o de adultas. Devuelve el motivo o null.
fun gameLock(game: MiniGame, creature: Creature?): String? {
    val branch = game.branch ?: return null
    if (creature != null && branch in getForm(creature.formId).path) return null
    return "Solo lo juegan las criaturas ${BRANCH_INFO.getValue(branch).name.lowercase()}s."
}

// Requisitos para empezar un minijuego. Devuelve el motivo o null.
fun canPlayGame(creature: Creature): String? {
    if (!creature.alive) return "Ya no está aquí."
    if (creature.stage == "huevo") return "Todavía no ha salido del huevo."
    if (creature.asleep) return "Está dormida. Despiértala primero."
    if (creature.sick) return "Está enferma y no tiene ganas de jugar."
    if (creature.energy < 12) return "Está demasiado cansada para jugar."
    return null
}
