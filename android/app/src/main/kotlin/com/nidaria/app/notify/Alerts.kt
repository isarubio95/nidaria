// Qué merece un aviso en el teléfono. Es la misma regla que usa el
// servidor para los avisos de la web (server/game/alerts.js), aplicada a
// la última foto que vio el jugador en este teléfono (`saved`) simulada
// hasta ahora (`live`). Función pura: se prueba sin Android.

package com.nidaria.app.notify

import com.nidaria.app.data.AlertPrefs
import com.nidaria.app.data.Daily
import com.nidaria.app.data.User
import com.nidaria.app.rules.CRITICAL
import com.nidaria.app.rules.Creature
import com.nidaria.app.rules.NEEDS
import com.nidaria.app.rules.TICKET_REGEN_MS
import com.nidaria.app.rules.ticketsNow
import java.time.Instant
import java.time.ZoneId

class Alert(val group: String, val key: String, val title: String, val body: String)

// Sin avisos de 23:00 a 8:00 (si el jugador no lo desactiva). Lo que
// quede pendiente sale por la mañana si sigue haciendo falta.
const val QUIET_FROM = 23
const val QUIET_TO = 8
fun isQuietHour(hour: Double) = hour >= QUIET_FROM || hour < QUIET_TO

// A partir de esta hora se avisa de que la racha se pierde hoy.
const val STREAK_REMINDER_HOUR = 20

private val NEED_TEXT = mapOf(
    "hunger" to ("tiene mucha hambre" to "Dale algo de comer."),
    "energy" to ("está agotada" to "Necesita dormir un rato."),
    "hygiene" to ("necesita un baño" to "Está muy sucia y empieza a encontrarse mal."),
    "fun" to ("se aburre muchísimo" to "Juega un rato con ella."),
)

private fun days(n: Int) = "$n ${if (n == 1) "día" else "días"}"

fun localHour(now: Double, zone: ZoneId): Double {
    val time = Instant.ofEpochMilli(now.toLong()).atZone(zone)
    return time.hour + time.minute / 60.0
}

fun localDay(now: Double, zone: ZoneId): String = Instant.ofEpochMilli(now.toLong()).atZone(zone).toLocalDate().toString()

private fun dayBefore(day: String): String = java.time.LocalDate.parse(day).minusDays(1).toString()

fun alertsFor(
    saved: Creature?,
    live: Creature?,
    user: User,
    daily: Daily?,
    now: Double,
    zone: ZoneId,
    prefs: AlertPrefs,
    scale: Double = 1.0,
): List<Alert> {
    val list = mutableListOf<Alert>()
    fun add(group: String, key: String, title: String, body: String) {
        if (prefs.group(group)) list.add(Alert(group, key, title, body))
    }

    if (saved != null && live != null) {
        val name = live.name
        if (!live.alive && saved.alive) {
            add(
                "care", "died:${live.id}", "$name se ha ido",
                if (live.deathCause == "vejez") "Ha tenido una vida larga. Entra para despedirte." else "Entra para despedirte y elegir un huevo nuevo.",
            )
        } else if (live.alive && live.stage != "huevo") {
            if (live.health < 30) add("care", "weak:${live.id}", "$name está muy débil", "Si no la cuidas pronto, puede morir.")
            if (live.sick) add("care", "sick:${live.id}", "$name se ha puesto enferma", "Dale medicina para que se recupere.")
            for (need in NEEDS) {
                if (need == "energy" && live.asleep) continue
                val (what, hint) = NEED_TEXT.getValue(need)
                if (live.need(need) < CRITICAL) add("care", "need:$need:${live.id}", "$name $what", hint)
            }
            if (live.formId != saved.formId) {
                if (saved.stage == "huevo") add("growth", "form:${live.id}:${live.formId}", "¡$name ha nacido!", "Entra a conocerla.")
                else add("growth", "form:${live.id}:${live.formId}", "$name está cambiando", "Entra a ver en qué se ha convertido.")
            }
        }
    }

    // La foto puede ser de hoy o de ayer. Si es de ayer y entonces se
    // completaron, la racha sigue viva y hoy aún no se ha hecho nada.
    if (daily != null) {
        val today = localDay(now, zone)
        val streak = when {
            daily.day == today && !daily.doneToday -> daily.streak
            daily.day == dayBefore(today) && daily.doneToday -> daily.streak
            else -> 0
        }
        if (streak > 0 && localHour(now, zone) >= STREAK_REMINDER_HOUR) {
            add("streak", "streak:$today", "Tu racha de ${days(streak)} está en juego", "Completa las misiones de hoy antes de medianoche.")
        }
    }

    // Solo tras gastar casi toda la energía de juego, para no avisar
    // después de cada partida suelta.
    val nextAt = user.ticketsNextAt
    if (live?.alive == true && user.tickets <= 1 && nextAt != null) {
        val tickets = ticketsNow(user.tickets, nextAt - TICKET_REGEN_MS / scale, now, scale)
        if (tickets.tickets >= user.ticketsMax) {
            add("games", "tickets:${nextAt.toLong()}", "Ya puedes volver a jugar", "Tienes la energía de juego al máximo.")
        }
    }

    return list
}
