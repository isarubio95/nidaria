package com.nidaria.app

import com.nidaria.app.data.AlertPrefs
import com.nidaria.app.data.Daily
import com.nidaria.app.data.User
import com.nidaria.app.data.weatherKind
import com.nidaria.app.notify.alertsFor
import com.nidaria.app.notify.isQuietHour
import com.nidaria.app.notify.localDay
import com.nidaria.app.notify.localHour
import com.nidaria.app.rules.Creature
import com.nidaria.app.rules.TICKET_REGEN_MS
import com.nidaria.app.rules.createGenes
import com.nidaria.app.rules.simulate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Los mismos casos que test/daily.test.js prueba en el servidor.
class AlertsTest {
    private val hour = 60.0 * 60 * 1000
    private val madrid = ZoneId.of("Europe/Madrid")

    // 15 de enero de 2027, 10:30 en Madrid.
    private val t0 = java.time.Instant.parse("2027-01-15T09:30:00Z").toEpochMilli().toDouble()
    private val user = User(tickets = 5, ticketsMax = 5)
    private val prefs = AlertPrefs()

    private class Pet(val saved: Creature, val live: Creature, val now: Double)

    private fun pet(hoursAlone: Double): Pet {
        val egg = Creature(id = "c1", name = "Pipo", genes = createGenes(3, "pelusa"), bornAt = t0, stageAt = t0, tickAt = t0)
        val saved = simulate(egg, t0 + 60_000).creature
        val now = t0 + 60_000 + hoursAlone * hour
        return Pet(saved, simulate(saved, now).creature, now)
    }

    private fun ask(p: Pet, now: Double = p.now, daily: Daily? = null, u: User = user, prefs: AlertPrefs = this.prefs, saved: Creature = p.saved) =
        alertsFor(saved, p.live, u, daily, now, madrid, prefs)

    @Test
    fun calendarFollowsTheLocalZone() {
        assertEquals("2027-01-15", localDay(t0, madrid))
        assertEquals(10.5, localHour(t0, madrid), 1e-9)
        assertTrue(isQuietHour(23.5))
        assertTrue(isQuietHour(3.0))
        assertFalse(isQuietHour(8.0))
        assertFalse(isQuietHour(22.9))
    }

    @Test
    fun needsEvolutionAndDeathFromMostToLeastUrgent() {
        assertTrue(ask(pet(0.1)).isEmpty())

        val hungry = pet(3.0)
        val keys = ask(hungry).map { it.key }
        assertTrue(keys.joinToString(), "need:hunger:c1" in keys)
        // En tres horas también ha pasado de cría a joven.
        val form = "form:c1:${hungry.live.formId}"
        assertTrue(form in keys)
        assertTrue(keys.indexOf("need:hunger:c1") < keys.indexOf(form))
        assertTrue(ask(hungry).first().title.startsWith("Pipo "))

        val gone = pet(24.0 * 5)
        val dead = ask(gone)
        assertEquals(listOf("died:c1"), dead.map { it.key })
        // Una vez visto, ya no hay nada que avisar.
        assertTrue(ask(gone, saved = gone.live).isEmpty())

        assertTrue(ask(hungry, prefs = AlertPrefs(care = false, growth = false)).isEmpty())
    }

    @Test
    fun streakAtRiskInTheEvening() {
        val calm = pet(0.1)
        val evening = java.time.Instant.parse("2027-01-15T20:00:00Z").toEpochMilli().toDouble() // 21:00 en Madrid
        val today = Daily(day = "2027-01-15", streak = 3, doneToday = false)
        val atRisk = ask(calm, evening, today)
        assertEquals("streak:2027-01-15", atRisk.first().key)
        assertTrue(atRisk.first().title.contains("3 días"))
        assertTrue("por la mañana todavía no", ask(calm, daily = today).isEmpty())
        assertTrue(ask(calm, evening, today.copy(doneToday = true)).isEmpty())
        // Foto de ayer, con las de ayer hechas: hoy la racha sigue en juego.
        val yesterday = Daily(day = "2027-01-14", streak = 3, doneToday = true)
        assertEquals("streak:2027-01-15", ask(calm, evening, yesterday).first().key)
        assertTrue(ask(calm, evening, yesterday.copy(doneToday = false)).isEmpty())
    }

    @Test
    fun gameEnergyOnlyAfterSpendingAlmostAll() {
        val calm = pet(0.1)
        val drained = User(tickets = 0, ticketsMax = 5, ticketsNextAt = calm.now - 4 * TICKET_REGEN_MS)
        assertEquals("games", ask(calm, u = drained).first().group)
        val half = User(tickets = 0, ticketsMax = 5, ticketsNextAt = calm.now - 1 * TICKET_REGEN_MS)
        assertTrue(ask(calm, u = half).isEmpty())
        val one = User(tickets = 3, ticketsMax = 5, ticketsNextAt = calm.now - 4 * TICKET_REGEN_MS)
        assertTrue(ask(calm, u = one).isEmpty())
    }

    @Test
    fun weatherCodesBecomeWhatTheWindowShows() {
        assertEquals("despejado", weatherKind(0))
        assertEquals("nubes", weatherKind(2))
        assertEquals("cubierto", weatherKind(3))
        assertEquals("niebla", weatherKind(45))
        assertEquals("lluvia", weatherKind(61))
        assertEquals("lluvia", weatherKind(81))
        assertEquals("nieve", weatherKind(73))
        assertEquals("tormenta", weatherKind(95))
    }
}
