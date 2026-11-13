package com.nidaria.app

import com.nidaria.app.audio.SOUND_NAMES
import com.nidaria.app.audio.renderSound
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Compara los sonidos de Kotlin con los de la web (tools/golden.mjs). El
// seno de la JVM y el de JavaScript pueden diferir en la última cifra, así
// que se admite un margen muy pequeño.
class SoundsParityTest {
    private val golden: JsonObject = Json.parseToJsonElement(
        checkNotNull(javaClass.classLoader?.getResourceAsStream("golden.json")) { "Falta golden.json" }.bufferedReader().readText(),
    ).jsonObject

    @Test
    fun sameSoundsAsTheWeb() {
        val entries = golden.getValue("sounds").jsonArray.map { it.jsonObject }
        assertEquals(entries.map { it.getValue("name").jsonPrimitive.content }, SOUND_NAMES)
        for (entry in entries) {
            val name = entry.getValue("name").jsonPrimitive.content
            val samples = checkNotNull(renderSound(name, entry.getValue("rate").jsonPrimitive.int)) { name }
            assertEquals(name, entry.getValue("length").jsonPrimitive.int, samples.size)
            val picks = entry.getValue("picks").jsonArray.map { it.jsonPrimitive.int }
            val values = entry.getValue("values").jsonArray.map { it.jsonPrimitive.double }
            picks.zip(values).forEach { (index, value) ->
                assertEquals("$name[$index]", value, samples[index].toDouble(), 1e-5)
            }
            var sum = 0.0
            for (sample in samples) sum += sample.toDouble() * sample
            assertEquals("$name rms", entry.getValue("rms").jsonPrimitive.double, sqrt(sum / samples.size), 1e-6)
        }
    }

    @Test
    fun soundsStayInRange() {
        for (name in SOUND_NAMES) {
            val samples = checkNotNull(renderSound(name, 44100))
            assertTrue(name, samples.isNotEmpty())
            assertTrue(name, samples.all { it.isFinite() && abs(it) <= 0.9f + 1e-6f })
        }
        assertNull(renderSound("no-existe"))
    }
}
