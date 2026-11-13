// Cliente de la API del servidor de Nidaria. Cada llamada devuelve un
// Payload o lanza un ApiError con un mensaje listo para enseñar.
//
// La sesión es la misma cookie HttpOnly que usa la web: se guarda en el
// teléfono y viaja en cada petición. Al cambiar de servidor se borra.

package com.nidaria.app.data

import android.content.Context
import androidx.core.content.edit
import com.nidaria.app.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val PREFS = "nidaria"
private const val KEY_SERVER = "servidor"
private const val KEY_SESSION = "sesion"
private const val COOKIE = "nid_session"
private const val OFFLINE = "No hay conexión con el servidor. Comprueba tu red e inténtalo de nuevo."
private const val BROKEN = "Algo ha fallado. Inténtalo de nuevo."

class Api(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // Dirección del servidor, p. ej. http://192.168.1.20:3000. Cambiarla
    // cierra la sesión, porque la cuenta vive en el servidor.
    var server: String
        get() = prefs.getString(KEY_SERVER, null) ?: BuildConfig.DEFAULT_SERVER
        set(value) {
            prefs.edit {
                putString(KEY_SERVER, value)
                remove(KEY_SESSION)
            }
        }

    private var session: String?
        get() = prefs.getString(KEY_SESSION, null)
        set(value) = prefs.edit { if (value == null) remove(KEY_SESSION) else putString(KEY_SESSION, value) }

    // Dificultad elegida para cada minijuego. Sin `value`, solo la lee.
    fun level(gameId: String, value: String? = null): String {
        val key = "nivel:$gameId"
        if (value != null) prefs.edit { putString(key, value) }
        return prefs.getString(key, null) ?: "facil"
    }

    private suspend fun call(method: String, path: String, body: JsonObject? = null): Payload = withContext(Dispatchers.IO) {
        val connection = try {
            (URL("$server/api$path").openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 8000
                readTimeout = 15000
                useCaches = false
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/json")
                session?.let { setRequestProperty("Cookie", "$COOKIE=$it") }
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
            }
        } catch (error: IOException) {
            throw ApiError(OFFLINE, 0)
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toString().toByteArray()) }
            val status = connection.responseCode
            remember(connection.headerFields["Set-Cookie"] ?: connection.headerFields["set-cookie"])
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            val json = runCatching { NidJson.parseToJsonElement(text) }.getOrNull()
            if (status !in 200..299) {
                val message = (json as? JsonObject)?.get("error")?.jsonPrimitive?.content ?: BROKEN
                throw ApiError(message, status)
            }
            if (json == null) throw ApiError(BROKEN, status)
            decode(json)
        } catch (error: IOException) {
            throw ApiError(OFFLINE, 0)
        } finally {
            connection.disconnect()
        }
    }

    // Guarda la sesión que manda el servidor (o la borra si la caduca).
    private fun remember(cookies: List<String>?) {
        for (header in cookies ?: return) {
            val first = header.substringBefore(';').trim()
            if (!first.startsWith("$COOKIE=")) continue
            val value = first.substringAfter('=')
            val expired = header.contains("Max-Age=0", ignoreCase = true)
            session = if (value.isEmpty() || expired) null else value
        }
    }

    // Las respuestas sin `state` (salir, avisos) también son un Payload.
    private fun decode(json: JsonElement): Payload {
        val obj = json as? JsonObject ?: return Payload()
        return if ("state" in obj || "events" in obj) NidJson.decodeFromJsonElement(Payload.serializer(), obj)
        else Payload(result = obj)
    }

    private fun body(vararg pairs: Pair<String, Any?>) = buildJsonObject {
        for ((key, value) in pairs) {
            when (value) {
                null -> put(key, JsonNull)
                is String -> put(key, value)
                is Number -> put(key, JsonPrimitive(value))
                is Boolean -> put(key, value)
                is JsonElement -> put(key, value)
                else -> put(key, value.toString())
            }
        }
    }

    suspend fun state() = call("GET", "/state")
    suspend fun memorial() = call("GET", "/memorial")
    suspend fun register(name: String, password: String) =
        call("POST", "/auth/register", body("name" to name, "password" to password, "timeZone" to localTimeZone()))
    suspend fun login(name: String, password: String) =
        call("POST", "/auth/login", body("name" to name, "password" to password, "timeZone" to localTimeZone()))

    // Contraseña nueva con el código de recuperación. La respuesta trae el
    // código siguiente en `result.recoveryCode`, igual que el registro.
    suspend fun recover(name: String, code: String, password: String) =
        call("POST", "/auth/recover", body("name" to name, "code" to code, "password" to password, "timeZone" to localTimeZone()))

    // Código nuevo desde la ficha; pide la contraseña (403 si no es).
    suspend fun renewRecovery(password: String) = call("POST", "/account/recovery", body("password" to password))

    // Sin conexión también se sale: la sesión se borra del teléfono.
    suspend fun logout(): Payload = try {
        call("POST", "/auth/logout", body())
    } finally {
        session = null
    }

    suspend fun pickEgg(index: Int, name: String) = call("POST", "/egg/pick", body("index" to index, "name" to name))
    suspend fun warmEgg(taps: Int) = call("POST", "/egg/warm", body("taps" to taps))
    suspend fun feed(itemId: String) = call("POST", "/creature/feed", body("itemId" to itemId))
    suspend fun sleep(asleep: Boolean) = call("POST", "/creature/sleep", body("asleep" to asleep))
    suspend fun bathe(itemId: String?) = call("POST", "/creature/bathe", body("itemId" to itemId))
    suspend fun heal(itemId: String) = call("POST", "/creature/heal", body("itemId" to itemId))
    suspend fun pet() = call("POST", "/creature/pet", body())
    suspend fun toy(itemId: String) = call("POST", "/creature/toy", body("itemId" to itemId))
    suspend fun rename(name: String) = call("POST", "/creature/rename", body("name" to name))
    suspend fun equip(slot: String, itemId: String?) = call("POST", "/creature/equip", body("slot" to slot, "itemId" to itemId))
    suspend fun decorate(spot: String, itemId: String?) = call("POST", "/decor/place", body("spot" to spot, "itemId" to itemId))
    suspend fun buy(itemId: String, qty: Int) = call("POST", "/shop/buy", body("itemId" to itemId, "qty" to qty))
    suspend fun collect() = call("POST", "/income/collect", body())
    suspend fun startGame(gameId: String, difficulty: String) =
        call("POST", "/games/start", body("gameId" to gameId, "difficulty" to difficulty))
    suspend fun finishGame(playId: String, score: Int) = call("POST", "/games/finish", body("playId" to playId, "score" to score))
    suspend fun claim(missionId: String) = call("POST", "/daily/claim", body("id" to missionId))
    suspend fun setTimeZone(timeZone: String) = call("POST", "/settings", body("timeZone" to timeZone))
    suspend fun setAlert(group: String, on: Boolean) =
        call("POST", "/settings", body("notify" to buildJsonObject { put(group, on) }))
    suspend fun devSkip(hours: Int) = call("POST", "/dev/skip", body("hours" to hours))

    companion object {
        private val ADDRESS = Regex("""^(https?://)?([A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?|\[[0-9A-Fa-f:.]+])(:(\d{1,5}))?/?$""")

        // Lo que una persona escribiría («192.168.1.20:3000») pasado a una
        // dirección completa, o null si no se entiende.
        fun cleanServer(text: String): String? {
            val match = ADDRESS.matchEntire(text.trim()) ?: return null
            val scheme = match.groupValues[1].ifEmpty { "http://" }
            val host = match.groupValues[2]
            val port = match.groupValues[5]
            if (port.isNotEmpty() && port.toInt() !in 1..65535) return null
            return scheme + host + (if (port.isNotEmpty()) ":$port" else "")
        }

        // Zona horaria del teléfono, p. ej. «Europe/Madrid». Decide cuándo
        // cambian las misiones del día.
        fun localTimeZone(): String = TimeZone.getDefault().id
    }
}
