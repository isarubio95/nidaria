// Economía: dinero que llega con el tiempo, energía de juego que limita
// las partidas y recompensa de los minijuegos según la dificultad.

package com.nidaria.app.rules

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

const val INCOME_PER_HOUR = 6
const val INCOME_CAP_HOURS = 12
const val INCOME_CAP = INCOME_PER_HOUR * INCOME_CAP_HOURS
const val TICKETS_MAX = 5
const val TICKET_REGEN_MS = 15.0 * 60 * 1000

private const val HOUR_MS = 60.0 * 60 * 1000

fun pendingIncome(incomeAt: Double, now: Double, scale: Double = 1.0): Int {
    val hours = max(0.0, ((now - incomeAt) * scale) / HOUR_MS)
    return min(INCOME_CAP.toDouble(), floor(hours * INCOME_PER_HOUR)).toInt()
}

class Tickets(val tickets: Int, val ticketsAt: Double, val nextAt: Double?)

// Energía de juego actual y momento de la siguiente recarga.
fun ticketsNow(tickets: Int, ticketsAt: Double, now: Double, scale: Double = 1.0): Tickets {
    if (tickets >= TICKETS_MAX) return Tickets(TICKETS_MAX, now, null)
    val regen = TICKET_REGEN_MS / scale
    val gained = floor((now - ticketsAt) / regen).toInt()
    val total = min(TICKETS_MAX, tickets + gained)
    if (total >= TICKETS_MAX) return Tickets(TICKETS_MAX, now, null)
    val at = ticketsAt + gained * regen
    return Tickets(total, at, at + regen)
}

class Difficulty(val id: String, val name: String, val mult: Double)

val DIFFICULTIES = linkedMapOf(
    "facil" to Difficulty("facil", "Fácil", 1.0),
    "normal" to Difficulty("normal", "Normal", 2.0),
    "dificil" to Difficulty("dificil", "Difícil", 3.5),
)

// Parámetros de un nivel: `target` es la puntuación para ganar.
class Level(val target: Int, internal val values: Map<String, Double>) {
    fun num(key: String): Double = values.getValue(key)
    fun int(key: String): Int = values.getValue(key).toInt()

    // Para los valores que solo traen las ventajas del carácter.
    fun numOr(key: String, default: Double): Double = values[key] ?: default
}

// `branch`: solo pueden jugarlo las criaturas criadas por esa rama.
class MiniGame(
    val id: String, val name: String, val blurb: String, val base: Int, val levels: Map<String, Level>, val branch: String? = null,
)

private fun level(target: Int, vararg values: Pair<String, Double>) = Level(target, mapOf(*values))

val MINIGAMES = listOf(
    MiniGame(
        "fruta", "Lluvia de fruta", "Mueve la cesta y recoge la fruta buena. La podrida quita una vida.", 14,
        mapOf(
            "facil" to level(12, "seconds" to 30.0, "fall" to 190.0, "every" to 900.0, "rotten" to 0.12, "lives" to 3.0),
            "normal" to level(18, "seconds" to 30.0, "fall" to 260.0, "every" to 700.0, "rotten" to 0.2, "lives" to 3.0),
            "dificil" to level(24, "seconds" to 30.0, "fall" to 340.0, "every" to 540.0, "rotten" to 0.28, "lives" to 2.0),
        ),
    ),
    MiniGame(
        "parejas", "Parejas", "Destapa dos cartas iguales. Encuentra todas antes de que acabe el tiempo.", 15,
        mapOf(
            "facil" to level(6, "pairs" to 6.0, "cols" to 3.0, "seconds" to 60.0),
            "normal" to level(8, "pairs" to 8.0, "cols" to 4.0, "seconds" to 60.0),
            "dificil" to level(10, "pairs" to 10.0, "cols" to 4.0, "seconds" to 55.0),
        ),
    ),
    MiniGame(
        "charcos", "Salta charcos", "Toca para saltar cada charco. Si pisas uno, se acabó.", 16,
        mapOf(
            "facil" to level(12, "speed" to 230.0, "gapMin" to 1.5, "gapMax" to 2.2, "grow" to 3.0),
            "normal" to level(20, "speed" to 290.0, "gapMin" to 1.2, "gapMax" to 1.9, "grow" to 5.0),
            "dificil" to level(30, "speed" to 350.0, "gapMin" to 1.0, "gapMax" to 1.6, "grow" to 7.0),
        ),
    ),
    MiniGame(
        "eco", "Eco", "Mira la secuencia de luces y repítela. Cada ronda añade una más.", 15,
        mapOf(
            "facil" to level(5, "beat" to 620.0),
            "normal" to level(7, "beat" to 470.0),
            "dificil" to level(9, "beat" to 350.0),
        ),
    ),
    MiniGame(
        "burbujas", "Explota burbujas", "Explota las burbujas de color. Las grises restan dos puntos.", 14,
        mapOf(
            "facil" to level(14, "seconds" to 30.0, "every" to 760.0, "life" to 1500.0, "grey" to 0.12),
            "normal" to level(22, "seconds" to 30.0, "every" to 560.0, "life" to 1150.0, "grey" to 0.2),
            "dificil" to level(30, "seconds" to 30.0, "every" to 420.0, "life" to 900.0, "grey" to 0.28),
        ),
    ),
    // `swaps` son los cambios de sitio de la primera ronda, que dura
    // `swapMs` cada uno; cada ronda añade uno más y va un poco más rápido.
    MiniGame(
        "escondite", "Al escondite", "Se esconde bajo un cojín y los cojines se mezclan. Encuéntrala.", 18,
        mapOf(
            "facil" to level(5, "cushions" to 3.0, "swaps" to 3.0, "swapMs" to 560.0, "lives" to 1.0),
            "normal" to level(7, "cushions" to 4.0, "swaps" to 5.0, "swapMs" to 440.0, "lives" to 1.0),
            "dificil" to level(9, "cushions" to 5.0, "swaps" to 7.0, "swapMs" to 340.0, "lives" to 1.0),
        ),
        branch = "jugueton",
    ),
)

fun getGame(id: String): MiniGame? = MINIGAMES.firstOrNull { it.id == id }

// Juego del día: paga más y cambia cada día. Cuál toca lo dice el
// servidor (`daily.featured`).
const val FEATURED_MULT = 1.5

// Monedas por ganar con la puntuación justa, sin bonificación. `mult`
// es el extra del juego del día.
fun baseReward(game: MiniGame, difficulty: String, mult: Double = 1.0): Int =
    jsRoundInt(game.base * DIFFICULTIES.getValue(difficulty).mult * mult)

// Ventajas según el carácter: cada rasgo ayuda en un juego. Cambian el
// nivel (`set` pone un valor, `scale` multiplica uno que ya tiene), nunca
// el objetivo ni el premio. Los valores que el nivel no trae valen:
// basket 1, shield 0, jump 1, peek 0.
class Perk(val game: String, val text: String, val set: Map<String, Double> = emptyMap(), val scale: Map<String, Double> = emptyMap())

val PERKS = linkedMapOf(
    "gloton" to Perk("fruta", "Atrapa la fruta desde más lejos.", set = mapOf("basket" to 1.35)),
    "timido" to Perk("fruta", "Cae menos fruta podrida.", scale = mapOf("rotten" to 0.6)),
    "fuerte" to Perk("charcos", "Aguanta un charco sin caerse.", set = mapOf("shield" to 1.0)),
    "travieso" to Perk("charcos", "Salta más alto.", set = mapOf("jump" to 1.12)),
    "curioso" to Perk("parejas", "Mira todas las cartas un momento al empezar.", set = mapOf("peek" to 1.6)),
    "dormilon" to Perk("eco", "La secuencia va más despacio.", scale = mapOf("beat" to 1.2)),
    "presumido" to Perk("burbujas", "Salen menos burbujas grises.", scale = mapOf("grey" to 0.5)),
    "mimoso" to Perk("escondite", "Si fallas una vez, te deja buscarla otra vez.", set = mapOf("lives" to 2.0)),
)

// Rasgos (de los de la criatura) que ayudan en este juego.
fun perksFor(gameId: String, traits: List<String>?): List<String> = traits.orEmpty().filter { PERKS[it]?.game == gameId }

// Nivel con las ventajas de la criatura ya aplicadas.
fun levelFor(game: MiniGame, difficulty: String, traits: List<String>?): Level {
    val level = game.levels.getValue(difficulty)
    val values = LinkedHashMap(level.values)
    for (trait in perksFor(game.id, traits)) {
        val perk = PERKS.getValue(trait)
        for ((key, value) in perk.set) values[key] = value
        for ((key, factor) in perk.scale) values[key] = jsFixed4(values.getValue(key) * factor)
    }
    return Level(level.target, values)
}

// Como `+(x).toFixed(4)` en JavaScript.
private fun jsFixed4(x: Double): Double = java.math.BigDecimal(x).setScale(4, java.math.RoundingMode.HALF_UP).toDouble()

internal fun jsRoundInt(x: Double): Int = floor(x + 0.5).toInt()
