// Clima real. Si el jugador lo activa, la app usa su ubicación aproximada
// (redondeada a unos 10 km) para preguntar el tiempo a Open-Meteo. La
// ubicación se queda en el teléfono: al servidor de Nidaria no llega.

package com.nidaria.app.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.location.LocationManagerCompat
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class WeatherNow(val kind: String, val temp: Int, val at: Long)

val WEATHER_NAMES = mapOf(
    "despejado" to "despejado", "nubes" to "algunas nubes", "cubierto" to "nublado", "niebla" to "niebla",
    "lluvia" to "lluvia", "nieve" to "nieve", "tormenta" to "tormenta",
)

// Códigos WMO de Open-Meteo, agrupados en lo que se ve por la ventana.
fun weatherKind(code: Int): String = when (code) {
    2 -> "nubes"
    3 -> "cubierto"
    45, 48 -> "niebla"
    in 51..67, in 80..82 -> "lluvia"
    in 71..77, 85, 86 -> "nieve"
    in 95..99 -> "tormenta"
    else -> "despejado"
}

class Weather(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("nidaria-clima", Context.MODE_PRIVATE)

    val on: Boolean get() = prefs.getBoolean("activo", false)

    fun cached(): WeatherNow? {
        val kind = prefs.getString("tipo", null) ?: return null
        return WeatherNow(kind, prefs.getInt("temp", 0), prefs.getLong("cuando", 0))
    }

    // El tiempo de ahora: el guardado si tiene menos de media hora.
    suspend fun refresh(): WeatherNow? {
        if (!on) return null
        val cached = cached()
        if (cached != null && System.currentTimeMillis() - cached.at < MAX_AGE) return cached
        if (!prefs.contains("lat")) return cached
        val fresh = fetch(prefs.getFloat("lat", 0f).toDouble(), prefs.getFloat("lon", 0f).toDouble()) ?: return cached
        prefs.edit {
            putString("tipo", fresh.kind)
            putInt("temp", fresh.temp)
            putLong("cuando", fresh.at)
        }
        return fresh
    }

    // Pide la ubicación (el permiso ya debe estar dado). Devuelve null si no la hay.
    suspend fun enable(): WeatherNow? {
        val place = locate() ?: return null
        prefs.edit {
            putBoolean("activo", true)
            putFloat("lat", place.first.toFloat())
            putFloat("lon", place.second.toFloat())
            remove("tipo")
        }
        return refresh()
    }

    fun disable() {
        prefs.edit { clear() }
    }

    @SuppressLint("MissingPermission")
    private suspend fun locate(): Pair<Double, Double>? {
        val granted = ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!granted) return null
        val manager = app.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        val recent = providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .filter { System.currentTimeMillis() - it.time < 6 * 60 * 60 * 1000L }
            .maxByOrNull { it.time }
        val found = recent ?: providers.firstNotNullOfOrNull { provider ->
            withTimeoutOrNull(15_000) {
                suspendCancellableCoroutine<Location?> { done ->
                    val cancel = CancellationSignal()
                    done.invokeOnCancellation { cancel.cancel() }
                    val answer = androidx.core.util.Consumer<Location?> { if (done.isActive) done.resume(it) }
                    runCatching {
                        LocationManagerCompat.getCurrentLocation(manager, provider, cancel, ContextCompat.getMainExecutor(app), answer)
                    }.onFailure { if (done.isActive) done.resume(null) }
                }
            }
        } ?: return null
        val round = { value: Double -> (value * 10).roundToInt() / 10.0 }
        return round(found.latitude) to round(found.longitude)
    }

    private suspend fun fetch(lat: Double, lon: Double): WeatherNow? = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("$API?latitude=$lat&longitude=$lon&current=weather_code,temperature_2m")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 10000
            }
            try {
                if (connection.responseCode != 200) return@runCatching null
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                val current = NidJson.parseToJsonElement(text).jsonObject["current"]?.jsonObject ?: return@runCatching null
                val code = current["weather_code"]?.jsonPrimitive?.intOrNull ?: return@runCatching null
                val temp = current["temperature_2m"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                WeatherNow(weatherKind(code), temp.roundToInt(), System.currentTimeMillis())
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    private companion object {
        const val MAX_AGE = 30 * 60 * 1000L
        const val API = "https://api.open-meteo.com/v1/forecast"
    }
}
