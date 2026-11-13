// Avisos en el teléfono sin servidor de push. La app guarda la última foto
// de la partida que vio el jugador y, cada cuarto de hora (cuando Android
// lo deja), la simula hasta ahora con las mismas reglas del servidor: si
// la criatura tiene hambre, enferma o va a evolucionar, avisa.
//
// Si el jugador ha jugado mientras tanto desde la web, el teléfono no se
// entera hasta que se abre la app; hasta entonces avisa según lo último
// que vio aquí.

package com.nidaria.app.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.nidaria.app.MainActivity
import com.nidaria.app.R
import com.nidaria.app.data.AlertPrefs
import com.nidaria.app.data.GameState
import com.nidaria.app.data.NidJson
import com.nidaria.app.rules.simulate
import java.time.ZoneId

object Notifier {
    const val CHANNEL = "cuidados"
    private const val PREFS = "nidaria-avisos"
    private const val KEY_ON = "activos"
    private const val KEY_STATE = "estado"
    private const val KEY_SKEW = "desfase"
    private const val KEY_SENT = "enviados"
    private const val NOTIFICATION_ID = 1
    private const val REPEAT = 0
    private const val SOON = 1

    // Con el juego a la vista, los avisos sobran.
    @Volatile
    var visible = false

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // El jugador ha activado los avisos en este teléfono.
    fun enabled(context: Context) = prefs(context).getBoolean(KEY_ON, false)

    // Android deja mostrarlos (permiso concedido y canal sin bloquear).
    fun allowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit {
            putBoolean(KEY_ON, on)
            if (!on) remove(KEY_SENT)
        }
        if (on) schedule(context) else cancel(context)
    }

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Cuidados de tu criatura", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Cuando tu criatura te necesita, va a evolucionar o tu racha de misiones está en peligro."
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    // Lo último que ha visto el jugador. `skew`: reloj del servidor menos el del teléfono.
    fun remember(context: Context, state: GameState, skew: Double) {
        prefs(context).edit {
            putString(KEY_STATE, NidJson.encodeToString(GameState.serializer(), state))
            putLong(KEY_SKEW, skew.toLong())
        }
    }

    // Al salir de la cuenta, el teléfono se olvida de la partida.
    fun forget(context: Context) {
        prefs(context).edit {
            remove(KEY_STATE)
            remove(KEY_SENT)
        }
        clear(context)
    }

    private fun pending(context: Context, code: Int): PendingIntent = PendingIntent.getBroadcast(
        context, code, Intent(context, AlertReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    // Repaso cada cuarto de hora, agrupado con otras alarmas para gastar poca batería.
    fun schedule(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val every = AlarmManager.INTERVAL_FIFTEEN_MINUTES
        alarms.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + every, every, pending(context, REPEAT))
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(context, REPEAT))
    }

    // Solo para probar: un repaso dentro de unos segundos.
    fun checkSoon(context: Context, seconds: Int) {
        context.getSystemService(AlarmManager::class.java)
            .set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + seconds * 1000L, pending(context, SOON))
    }

    fun clear(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    // Mira si hay algo nuevo que avisar y, si toca, lo enseña. Devuelve el aviso.
    fun check(context: Context, clock: Long = System.currentTimeMillis()): Alert? {
        if (!enabled(context) || visible) return null
        val store = prefs(context)
        val json = store.getString(KEY_STATE, null) ?: return null
        val state = runCatching { NidJson.decodeFromString(GameState.serializer(), json) }.getOrNull() ?: return null
        val now = (clock + store.getLong(KEY_SKEW, 0)).toDouble()
        val saved = state.creature
        val live = saved?.let { simulate(it, now, state.timeScale).creature }
        val prefs = state.push?.prefs ?: AlertPrefs()
        val zone = ZoneId.systemDefault()

        val alerts = alertsFor(saved, live, state.user, state.daily, now, zone, prefs, state.timeScale)
        val active = alerts.map { it.key }.toSet()
        val sent = store.getStringSet(KEY_SENT, emptySet()).orEmpty()
        // Lo que ya no está vigente se olvida: si vuelve a pasar, se avisa otra vez.
        val keep = sent.filter { it in active }.toMutableSet()
        val fresh = alerts.filter { it.key !in sent }
        val quiet = prefs.quiet && isQuietHour(localHour(clock.toDouble(), zone))

        var shown: Alert? = null
        if (fresh.isNotEmpty() && !quiet) {
            // Un aviso cada vez: el más urgente. Al entrar se ven todos.
            shown = fresh.first()
            keep += fresh.map { it.key }
            post(context, shown)
        }
        store.edit { putStringSet(KEY_SENT, keep) }
        return shown
    }

    private fun post(context: Context, alert: Alert) {
        if (!allowed(context)) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_nidaria)
            .setColor(ContextCompat.getColor(context, R.color.pink_edge))
            .setContentTitle(alert.title)
            .setContentText(alert.body)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(if (alert.group == "care") NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}

// La alarma del repaso.
class AlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Notifier.check(context)
    }
}

// Tras reiniciar el teléfono, las alarmas se pierden: se vuelven a poner.
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && Notifier.enabled(context)) Notifier.schedule(context)
    }
}
