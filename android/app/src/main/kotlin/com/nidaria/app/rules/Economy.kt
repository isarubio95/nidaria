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
class Level(val target: Int, private val values: Map<String, Double>) {
    fun num(key: String): Double = values.getValue(key)
    fun int(key: String): Int = values.getValue(key).toInt()
}

class MiniGame(val id: String, val name: String, val blurb: String, val base: Int, val levels: Map<String, Level>)

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
)

fun getGame(id: String): MiniGame? = MINIGAMES.firstOrNull { it.id == id }

// Monedas por ganar con la puntuación justa, sin bonificación.
fun baseReward(game: MiniGame, difficulty: String): Int = jsRoundInt(game.base * DIFFICULTIES.getValue(difficulty).mult)

internal fun jsRoundInt(x: Double): Int = floor(x + 0.5).toInt()
