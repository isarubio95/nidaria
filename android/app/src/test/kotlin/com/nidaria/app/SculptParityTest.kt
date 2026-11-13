package com.nidaria.app

import com.nidaria.app.sculpt.ADD
import com.nidaria.app.sculpt.Job
import com.nidaria.app.sculpt.MeshOptions
import com.nidaria.app.sculpt.Op
import com.nidaria.app.sculpt.Paint
import com.nidaria.app.sculpt.Shape
import com.nidaria.app.sculpt.Split
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

// El escultor de Kotlin frente al de la web (android/tools/sculpt-golden.mjs).
class SculptParityTest {
    private val golden: JsonObject by lazy {
        val text = javaClass.classLoader!!.getResource("sculpt.json")!!.readText()
        Json.parseToJsonElement(text).jsonObject
    }

    @Test
    fun `el escultor da las mismas mallas que la web, byte a byte`() {
        for ((i, case) in golden.getValue("meshes").jsonArray.withIndex()) {
            val job = jobOf(case.jsonObject.getValue("job").jsonObject)
            val mesh = job.mesh()!!
            assertEquals("malla $i", case.jsonObject.getValue("fingerprint").jsonPrimitive.content, mesh.fingerprint())
        }
    }

    companion object {
        private fun doubles(e: JsonElement): DoubleArray = e.jsonArray.map { it.jsonPrimitive.double }.toDoubleArray()

        fun shapeOf(o: JsonObject): Shape {
            val values = LinkedHashMap<String, Any?>()
            for ((name, value) in o) {
                if (name == "type") continue
                values[name] = when (value) {
                    is JsonNull -> null
                    is JsonArray -> doubles(value)
                    else -> value.jsonPrimitive.double
                }
            }
            return Shape.revive(o.getValue("type").jsonPrimitive.content, values)
        }

        fun jobOf(o: JsonObject): Job {
            val ops = o.getValue("ops").jsonArray.map { e ->
                val op = e.jsonObject
                val kind = op.getValue("op").jsonPrimitive.int
                val split = (op["split"] as? JsonObject)?.let {
                    Split(it.getValue("bone").jsonPrimitive.int, it.getValue("from").jsonPrimitive.double, it.getValue("to").jsonPrimitive.double)
                }
                Op(
                    kind,
                    shapeOf(op.getValue("form").jsonObject),
                    op["color"]?.let { doubles(it) } ?: doubleArrayOf(),
                    op.getValue("k").jsonPrimitive.double,
                    if (kind == ADD) op.getValue("bone").jsonPrimitive.int else 0,
                    if (kind == ADD) op.getValue("colorK").jsonPrimitive.double else 0.0,
                    split,
                )
            }
            val paints = o.getValue("paints").jsonArray.map { e ->
                val p = e.jsonObject
                Paint(shapeOf(p.getValue("form").jsonObject), doubles(p.getValue("color")), p.getValue("soft").jsonPrimitive.double, p.getValue("amount").jsonPrimitive.double)
            }
            val m = o.getValue("mesh").jsonObject
            val ground = o["ground"]?.let { if (it is JsonNull) null else it.jsonPrimitive.double }
            return Job(
                ground,
                o.getValue("groundK").jsonPrimitive.double,
                ops,
                paints,
                MeshOptions(m.getValue("cell").jsonPrimitive.double, m.getValue("bones").jsonPrimitive.int, m.getValue("ao").jsonPrimitive.double, m.getValue("aoReach").jsonPrimitive.double),
            )
        }
    }
}
