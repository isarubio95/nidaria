package com.nidaria.app

import com.nidaria.app.rules.Care
import com.nidaria.app.rules.Creature
import com.nidaria.app.rules.FORM_LIST
import com.nidaria.app.rules.Genes
import com.nidaria.app.rules.ITEMS
import com.nidaria.app.rules.MINIGAMES
import com.nidaria.app.rules.Rng
import com.nidaria.app.rules.baseReward
import com.nidaria.app.rules.branchScores
import com.nidaria.app.rules.chooseBranch
import com.nidaria.app.rules.createGenes
import com.nidaria.app.rules.hashString
import com.nidaria.app.rules.hintFor
import com.nidaria.app.rules.mix
import com.nidaria.app.rules.moodOf
import com.nidaria.app.rules.pendingIncome
import com.nidaria.app.rules.roll
import com.nidaria.app.rules.simulate
import com.nidaria.app.rules.ticketsNow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Compara las reglas de Kotlin con vectores generados por el JavaScript
// original (tools/golden.mjs).
class RulesParityTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val golden: JsonObject = json.parseToJsonElement(
        checkNotNull(javaClass.classLoader?.getResourceAsStream("golden.json")) { "Falta golden.json" }.bufferedReader().readText(),
    ).jsonObject

    private fun list(name: String): JsonArray = golden.getValue(name).jsonArray

    @Test
    fun rngSequencesMatch() {
        for (entry in list("rng")) {
            val o = entry.jsonObject
            val rng = Rng(o.getValue("seed").jsonPrimitive.long)
            for (value in o.getValue("values").jsonArray) assertEquals(value.jsonPrimitive.double, rng.next(), 0.0)
        }
    }

    @Test
    fun hashesMatch() {
        for (entry in list("hashes")) {
            val o = entry.jsonObject
            assertEquals(o.getValue("hash").jsonPrimitive.long, hashString(o.getValue("text").jsonPrimitive.content))
        }
        for (entry in list("mixes")) {
            val o = entry.jsonObject
            val a = o.getValue("a").jsonPrimitive.long
            val b = o.getValue("b").jsonPrimitive.long
            assertEquals(o.getValue("mix").jsonPrimitive.long, mix(a, b))
            assertEquals(o.getValue("roll").jsonPrimitive.double, roll(a, b), 0.0)
        }
    }

    @Test
    fun genesMatch() {
        for (entry in list("genes")) {
            val o = entry.jsonObject
            val family = o.getValue("family").let { if (it is JsonNull) null else it.jsonPrimitive.content }
            val expected = json.decodeFromJsonElement<Genes>(o.getValue("genes"))
            assertEquals(expected, createGenes(o.getValue("seed").jsonPrimitive.long, family))
        }
    }

    @Test
    fun formsMatch() {
        val forms = list("forms")
        assertEquals(forms.size, FORM_LIST.size)
        for ((index, entry) in forms.withIndex()) {
            val o = entry.jsonObject
            val form = FORM_LIST[index]
            assertEquals(o.getValue("id").jsonPrimitive.content, form.id)
            assertEquals(o.getValue("name").jsonPrimitive.content, form.name)
            assertEquals(o.getValue("stage").jsonPrimitive.content, form.stage)
            if (form.stage != "huevo") assertEquals(o.getValue("hint").jsonPrimitive.content, hintFor(form.id))
        }
    }

    @Test
    fun simulationMatches() {
        val sims = list("sims")
        assertTrue(sims.size > 50)
        for ((index, entry) in sims.withIndex()) {
            val o = entry.jsonObject
            val input = json.decodeFromJsonElement<Creature>(o.getValue("input"))
            val expected = json.decodeFromJsonElement<Creature>(o.getValue("output"))
            val result = simulate(input, o.getValue("now").jsonPrimitive.double, o.getValue("scale").jsonPrimitive.double)
            val got = result.creature
            val where = "simulación $index"
            assertEquals(where, expected.formId, got.formId)
            assertEquals(where, expected.stage, got.stage)
            assertEquals(where, expected.alive, got.alive)
            assertEquals(where, expected.asleep, got.asleep)
            assertEquals(where, expected.sick, got.sick)
            assertEquals(where, expected.deathCause, got.deathCause)
            assertEquals(where, expected.history, got.history)
            for (need in listOf("hunger", "energy", "hygiene", "fun", "health")) {
                assertEquals("$where $need", expected.need(need), got.need(need), 1e-9)
            }
            assertEquals(where, expected.stageAt, got.stageAt, 1e-6)
            assertEquals(where, expected.tickAt, got.tickAt, 1e-6)
            assertEquals(where, expected.lifeEndsAt ?: -1.0, got.lifeEndsAt ?: -1.0, 1e-3)
            assertEquals(where, expected.care.hours, got.care.hours, 1e-9)
            assertEquals(where, expected.care.mistakes, got.care.mistakes, 1e-9)
            assertEquals(where, expected.care.sumFun, got.care.sumFun, 1e-6)
            assertEquals(where, expected.lifeCare.sum, got.lifeCare.sum, 1e-6)
            val events = o.getValue("events").jsonArray
            assertEquals(where, events.map { it.jsonObject.getValue("type").jsonPrimitive.content }, result.events.map { it.type })
            assertEquals(where, o.getValue("mood").jsonPrimitive.content, moodOf(got))
        }
    }

    @Test
    fun branchesMatch() {
        for (entry in list("branches")) {
            val o = entry.jsonObject
            val care = json.decodeFromJsonElement<Care>(o.getValue("care"))
            val genes = json.decodeFromJsonElement<Genes>(o.getValue("genes"))
            assertEquals(o.getValue("branch").jsonPrimitive.content, chooseBranch(care, genes))
            val scores = branchScores(care, genes)
            for ((branch, value) in o.getValue("scores").jsonObject) assertEquals(value.jsonPrimitive.double, scores.getValue(branch), 1e-9)
        }
    }

    @Test
    fun economyMatches() {
        val economy = golden.getValue("economy").jsonObject
        for (entry in economy.getValue("income").jsonArray) {
            val o = entry.jsonObject
            assertEquals(
                o.getValue("value").jsonPrimitive.int,
                pendingIncome(o.getValue("at").jsonPrimitive.double, o.getValue("now").jsonPrimitive.double, o.getValue("scale").jsonPrimitive.double),
            )
        }
        for (entry in economy.getValue("tickets").jsonArray) {
            val o = entry.jsonObject
            val value = o.getValue("value").jsonObject
            val got = ticketsNow(
                o.getValue("tickets").jsonPrimitive.int, o.getValue("at").jsonPrimitive.double,
                o.getValue("now").jsonPrimitive.double, o.getValue("scale").jsonPrimitive.double,
            )
            assertEquals(value.getValue("tickets").jsonPrimitive.int, got.tickets)
            val next = value.getValue("nextAt")
            if (next is JsonNull) assertEquals(null, got.nextAt) else assertEquals(next.jsonPrimitive.double, got.nextAt!!, 1e-6)
        }
        for (entry in economy.getValue("rewards").jsonArray) {
            val o = entry.jsonObject
            val game = MINIGAMES.first { it.id == o.getValue("game").jsonPrimitive.content }
            val difficulty = o.getValue("difficulty").jsonPrimitive.content
            assertEquals(o.getValue("reward").jsonPrimitive.int, baseReward(game, difficulty))
            assertEquals(o.getValue("target").jsonPrimitive.int, game.levels.getValue(difficulty).target)
        }
    }

    @Test
    fun catalogMatches() {
        val items = list("items")
        assertEquals(items.size, ITEMS.size)
        for ((index, entry) in items.withIndex()) {
            val o = entry.jsonObject
            assertEquals(o.getValue("id").jsonPrimitive.content, ITEMS[index].id)
            assertEquals(o.getValue("name").jsonPrimitive.content, ITEMS[index].name)
            assertEquals(o.getValue("price").jsonPrimitive.int, ITEMS[index].price)
            assertEquals(o.getValue("kind").jsonPrimitive.content, ITEMS[index].kind)
        }
    }

    @Suppress("unused")
    private fun JsonObject.flag(name: String) = getValue(name).jsonPrimitive.boolean
}
