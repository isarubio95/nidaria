// Lo que manda el servidor, tal cual llega en JSON. Cada respuesta de la
// API es un Payload: el estado de la partida, lo que ha pasado desde la
// última vez (eventos) y el resultado concreto de la llamada.

package com.nidaria.app.data

import com.nidaria.app.rules.Creature
import com.nidaria.app.rules.EggLook
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

// El servidor puede añadir campos sin que la app deje de entenderle.
val NidJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    encodeDefaults = true
}

@Serializable
data class User(
    val name: String = "",
    val coins: Int = 0,
    val tickets: Int = 0,
    val ticketsMax: Int = 5,
    val ticketsNextAt: Double? = null,
    val incomePending: Int = 0,
    val incomeCap: Int = 0,
    val incomeAt: Double = 0.0,
    val timeZone: String? = null,
)

@Serializable
data class DexEntry(val firstAt: Double = 0.0, val times: Int = 0)

@Serializable
data class Mission(
    val id: String = "",
    val goal: Int = 1,
    val progress: Int = 0,
    val reward: Int = 0,
    val claimed: Boolean = false,
    val text: String = "",
) {
    val done get() = progress >= goal
}

// Misiones del día y racha (ver shared/daily.js en el servidor).
@Serializable
data class Daily(
    val day: String = "",
    val resetAt: Double = 0.0,
    val missions: List<Mission> = emptyList(),
    val streak: Int = 0,
    val best: Int = 0,
    val doneToday: Boolean = false,
    val bonus: Int = 0,
) {
    val claimable get() = missions.count { it.done && !it.claimed }
}

// Qué avisos quiere el jugador. Son de la cuenta: los comparten la web y
// la app. `quiet`: nada de 23:00 a 8:00.
@Serializable
data class AlertPrefs(
    val care: Boolean = true,
    val growth: Boolean = true,
    val streak: Boolean = true,
    val games: Boolean = true,
    val quiet: Boolean = true,
) {
    fun group(id: String): Boolean = when (id) {
        "care" -> care
        "growth" -> growth
        "streak" -> streak
        "games" -> games
        else -> quiet
    }
}

// `key` es la clave de Web Push del navegador; la app no la usa.
@Serializable
data class PushInfo(val key: String? = null, val prefs: AlertPrefs = AlertPrefs())

@Serializable
data class GameState(
    val now: Double = 0.0,
    val timeScale: Double = 1.0,
    val devTools: Boolean = false,
    val user: User = User(),
    val creature: Creature? = null,
    val eggOffer: List<EggLook>? = null,
    val inventory: Map<String, Int> = emptyMap(),
    val criadex: Map<String, DexEntry> = emptyMap(),
    val daily: Daily? = null,
    val push: PushInfo? = null,
) {
    fun owned(itemId: String): Int = inventory[itemId] ?: 0
}

@Serializable
data class GameEvent(
    val type: String = "",
    val at: Double = 0.0,
    val from: String? = null,
    val to: String? = null,
    val cause: String? = null,
)

@Serializable
data class Payload(
    val state: GameState? = null,
    val events: List<GameEvent> = emptyList(),
    val result: JsonElement? = null,
) {
    private fun field(key: String) = (result as? JsonObject)?.get(key)?.let { runCatching { it.jsonPrimitive }.getOrNull() }

    fun text(key: String): String? = field(key)?.contentOrNull
    fun int(key: String): Int = field(key)?.intOrNull ?: field(key)?.doubleOrNull?.toInt() ?: 0
    fun flag(key: String): Boolean = field(key)?.booleanOrNull ?: false
}

// Un error con su mensaje listo para enseñar. `status` 0: sin conexión.
class ApiError(message: String, val status: Int) : Exception(message)
