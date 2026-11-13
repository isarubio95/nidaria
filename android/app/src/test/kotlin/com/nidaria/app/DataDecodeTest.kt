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
