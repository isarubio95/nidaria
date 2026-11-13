package com.nidaria.app

import com.nidaria.app.data.NidJson
import com.nidaria.app.data.Payload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// `payload.json` es una respuesta real del servidor (dar de comer tras la
// eclosión). Si el servidor cambia su forma, esta prueba lo dice.
class DataDecodeTest {
    private fun fixture(): Payload {
        val text = javaClass.classLoader!!.getResource("payload.json")!!.readText()
        return NidJson.decodeFromString(Payload.serializer(), text)
    }

    @Test
    fun decodesARealServerPayload() {
        val payload = fixture()
        val state = assertNotNull(payload.state).let { payload.state!! }
        assertEquals("Muestra", state.user.name)
        assertEquals("America/Bogota", state.user.timeZone)
        val creature = assertNotNull(state.creature).let { state.creature!! }
        assertEquals("Gotita", creature.name)
        assertEquals("cria", creature.stage)
        assertTrue(creature.code.isNotEmpty())
        assertEquals(1, state.owned("pan"))
        assertTrue("huevo" in state.criadex)
        assertNull(state.eggOffer)
        assertEquals("come", payload.text("reaction"))
        assertEquals("pan", payload.text("itemId"))
        assertEquals(0, payload.int("nada"))
        assertFalse(payload.flag("nada"))
    }

    // Al crear o recuperar la cuenta llega el código de recuperación, una
    // sola vez, y el estado dice si la cuenta tiene uno.
    @Test
    fun decodesTheRecoveryCode() {
        val text = """{"state":{"now":1,"user":{"name":"Olvido","hasRecovery":true}},"events":[],"result":{"recoveryCode":"ABCD-EFGH-JKMN-PQRS"}}"""
        val payload = NidJson.decodeFromString(Payload.serializer(), text)
        assertTrue(payload.state!!.user.hasRecovery)
        assertEquals("ABCD-EFGH-JKMN-PQRS", payload.text("recoveryCode"))
        // Las respuestas de antes, sin el campo: la cuenta no tiene código.
        assertFalse(fixture().state!!.user.hasRecovery)
        assertNull(fixture().text("recoveryCode"))
    }

    // La hoja de amigos y la visita (ver server/game/friends.js).
    @Test
    fun decodesFriendsAndVisits() {
        val list = """{"state":{"now":5,"social":{"code":"K7QM-4XPA","requests":1,"gifts":2}},"events":[],"result":{"max":50,
            "friends":[{"id":"a","name":"Ana","since":1,"visitedAt":null,"giftedToday":true,
              "creature":{"name":"Pipo","formId":"pelusin","formName":"Pelusín","stage":"cria","stageName":"Cría","genes":{"family":"pelusa","hue":20},"alive":true}},
              {"id":"m","name":"Marta","since":2,"visitedAt":4,"giftedToday":false,"creature":null}],
            "incoming":[{"id":"t","name":"Teo","at":3}],"outgoing":[],"gifts":[{"id":"g","itemId":"pan","from":"Ana","at":4}]}}"""
        val payload = NidJson.decodeFromString(Payload.serializer(), list)
        assertEquals("K7QM-4XPA", payload.state!!.social.code)
        assertEquals(3, payload.state!!.social.requests + payload.state!!.social.gifts)
        val friends = payload.friends()!!
        assertEquals(listOf("Ana", "Marta"), friends.friends.map { it.name })
        assertEquals("Pipo", friends.friends[0].creature!!.name)
        assertTrue(friends.friends[0].giftedToday)
        assertNull(friends.friends[0].visitedAt)
        assertNull(friends.friends[1].creature)
        assertEquals("Teo", friends.incoming.single().name)
        assertEquals("pan", friends.gifts.single().itemId)
        // Las respuestas de antes, sin el campo: sin código ni avisos.
        assertEquals("", fixture().state!!.social.code)

        val room = """{"state":{"now":5},"events":[],"result":{"visit":{"id":"a","name":"Ana",
            "decor":{"pared":"papel-rayas","alfombra":null,"rincon":null,"ventana":null},
            "creature":{"id":"c","name":"Pipo","genes":{"family":"pelusa"},"formId":"pelusin","formName":"Pelusín","stage":"cria",
              "stageName":"Cría","equipped":{"cabeza":"lazo","cara":null,"cuello":null},"mood":"feliz","alive":true,"asleep":false,
              "sick":false,"hunger":80,"energy":70.5,"hygiene":90,"fun":60,"health":100,"bornAt":1,"generation":2},
            "giftedToday":false}}}"""
        val visit = NidJson.decodeFromString(Payload.serializer(), room).visit()!!
        assertEquals("papel-rayas", visit.decor.pared)
        assertEquals("lazo", visit.creature!!.equipped.cabeza)
        assertEquals(70.5, visit.creature!!.energy, 0.0)
        assertEquals("feliz", visit.creature!!.mood)
        assertFalse(visit.giftedToday)
        val empty = """{"state":{"now":5},"events":[],"result":{"visit":{"id":"m","name":"Marta","decor":{},"creature":null,"giftedToday":false}}}"""
        assertNull(NidJson.decodeFromString(Payload.serializer(), empty).visit()!!.creature)
    }

    @Test
    fun decodesTheDailyMissions() {
        val daily = fixture().state!!.daily!!
        assertEquals(3, daily.missions.size)
        assertEquals(listOf(15, 25, 40), daily.missions.map { it.reward })
        assertTrue(daily.missions.all { it.text.isNotEmpty() && !it.claimed })
        assertEquals(0, daily.streak)
        assertEquals(20, daily.bonus)
        assertTrue(daily.resetAt > fixture().state!!.now)
    }

    // La decoración y el juego del día. Una respuesta sin ellos (como la
    // de payload.json, anterior) deja la habitación de base.
    @Test
    fun decodesTheRoomAndTheGameOfTheDay() {
        val payload = NidJson.decodeFromString(
            Payload.serializer(),
            """{"state":{"now":1,"decor":{"pared":"papel-olas","alfombra":null,"rincon":"baul","ventana":null},"daily":{"day":"2026-11-13","featured":"eco"}},"result":{"playId":"p","featured":true}}""",
        )
        val state = payload.state!!
        assertEquals("papel-olas", state.decor.spot("pared"))
        assertEquals("baul", state.decor.rincon)
        assertNull(state.decor.spot("alfombra"))
        assertNull(state.decor.spot("techo"))
        assertEquals("eco", state.daily!!.featured)
        assertTrue(payload.flag("featured"))
        val old = fixture().state!!
        assertNull(old.decor.pared)
        assertNull(old.daily!!.featured)
    }

    @Test
    fun toleratesFieldsItDoesNotKnow() {
        val payload = NidJson.decodeFromString(
            Payload.serializer(),
            """{"state":{"now":1,"timeScale":1,"user":{"name":"X","futuro":true},"algoNuevo":[1,2]},"events":[{"type":"raro","at":1,"extra":1}],"result":null}""",
        )
        assertEquals("X", payload.state!!.user.name)
        assertEquals("raro", payload.events.single().type)
        assertNull(payload.text("reaction"))
    }
}
