package com.nidaria.app

import com.nidaria.app.gl.Mesh
import com.nidaria.app.rules.FORM_LIST
import com.nidaria.app.rules.ITEMS
import com.nidaria.app.rules.createGenes
import com.nidaria.app.rules.eggLook
import com.nidaria.app.scene.buildCreature
import com.nidaria.app.scene.buildEgg
import com.nidaria.app.scene.buildHouse
import com.nidaria.app.scene.buildItem
import com.nidaria.app.scene.buildWear
import com.nidaria.app.scene.forgetModels
import com.nidaria.app.scene.isWear
import com.nidaria.app.sculpt.ADD
import com.nidaria.app.sculpt.Job
import com.nidaria.app.sculpt.Sculptor
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
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max

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

    // Construye todo en el mismo orden que la herramienta de la web (las
    // mallas guardadas por nombre hacen que el orden importe) y compara
    // cada encargo, medida a medida.
    @Test
    fun `los modelos hacen los mismos encargos que la web`() {
        forgetModels()
        val captured = ArrayList<Job>()
        Sculptor.capture = captured
        fun <T> jobsOf(make: () -> T): Pair<T, List<Job>> {
            captured.clear()
            val result = make()
            return result to captured.toList()
        }
        try {
            val creatures = golden.getValue("creatures").jsonArray
            val forms = FORM_LIST.filter { it.stage != "huevo" }
            // Sin criaturas en la referencia: las nuevas de la web (monster.js
            // y pet.js) aún no están portadas (ver tools/sculpt-golden.mjs).
            if (creatures.isNotEmpty()) assertEquals(forms.size, creatures.size)
            for ((i, form) in forms.withIndex()) {
                if (creatures.isEmpty()) break
                val js = creatures[i].jsonObject
                val seed = js.getValue("seed").jsonPrimitive.content.toLong()
                val family = js.getValue("family").jsonPrimitive.content
                assertEquals(form.id, js.getValue("formId").jsonPrimitive.content)
                val (built, jobs) = jobsOf { buildCreature(form.id, createGenes(seed, family)) }
                compareJobs(form.id, js.getValue("jobs").jsonArray, jobs)
                near("${form.id}: altura", js.getValue("height").jsonPrimitive.double, built.height.toDouble(), 1e-5)
                val bones = js.getValue("bones").jsonArray
                built.rig.mesh.bones.forEachIndexed { b, bone ->
                    val p = doubles(bones[b])
                    near("${form.id}: hueso $b", p, doubleArrayOf(bone.position.x.toDouble(), bone.position.y.toDouble(), bone.position.z.toDouble()), 1e-5)
                }
                built.rig.eyes.forEachIndexed { e, eye ->
                    val je = js.getValue("eyes").jsonArray[e].jsonObject
                    val p = eye.group.position
                    val q = eye.group.quaternion
                    near("${form.id}: ojo $e", doubles(je.getValue("position")), doubleArrayOf(p.x.toDouble(), p.y.toDouble(), p.z.toDouble()), 1e-5)
                    near("${form.id}: giro del ojo $e", doubles(je.getValue("quaternion")), doubleArrayOf(q.x.toDouble(), q.y.toDouble(), q.z.toDouble(), q.w.toDouble()), 1e-5)
                }
                val decals = ArrayList<Mesh>()
                built.rig.headPivot.traverse { node -> if (node is Mesh && node.material.polygonOffset) decals.add(node) }
                val jsDecals = js.getValue("decals").jsonArray
                assertEquals("${form.id}: pegatinas", jsDecals.size, decals.size)
                decals.forEachIndexed { k, decal ->
                    val jd = jsDecals[k].jsonObject
                    val pos = decal.geometry.positions
                    assertEquals("${form.id}: pegatina $k", jd.getValue("count").jsonPrimitive.int, pos.size / 3)
                    near("${form.id}: pegatina $k (primer vértice)", doubles(jd.getValue("first")), DoubleArray(3) { pos[it].toDouble() }, 1e-5)
                    near("${form.id}: pegatina $k (último vértice)", doubles(jd.getValue("last")), DoubleArray(3) { pos[pos.size - 3 + it].toDouble() }, 1e-5)
                }
                val a = built.anchors
                val anchors = js.getValue("anchors").jsonObject
                near("${form.id}: sombrero", doubles(anchors.getValue("hat")), doubleArrayOf(a.hat.y, a.hat.r), 1e-9)
                near("${form.id}: cara", doubles(anchors.getValue("face")), doubleArrayOf(a.face.y, a.face.z, a.face.gap, a.face.r, a.face.pitch, a.face.host.center.x, a.face.host.center.y, a.face.host.center.z, a.face.host.rx, a.face.host.ry, a.face.host.rz), 1e-9)
                near("${form.id}: cuello", doubles(anchors.getValue("neck")), doubleArrayOf(a.neck.y, a.neck.rx, a.neck.rz), 1e-9)
            }

            for (item in golden.getValue("items").jsonArray) {
                val id = item.jsonObject.getValue("id").jsonPrimitive.content
                compareJobs(id, item.jsonObject.getValue("jobs").jsonArray, jobsOf { buildItem(id) }.second)
            }
            assertEquals(ITEMS.size + 1, golden.getValue("items").jsonArray.size)

            for (worn in golden.getValue("worn").jsonArray) {
                val w = worn.jsonObject
                val formId = w.getValue("formId").jsonPrimitive.content
                val built = buildCreature(formId, createGenes(w.getValue("seed").jsonPrimitive.content.toLong(), w.getValue("family").jsonPrimitive.content))
                for (item in w.getValue("items").jsonArray) {
                    val id = item.jsonObject.getValue("id").jsonPrimitive.content
                    assertTrue(isWear(id))
                    compareJobs("$id sobre $formId", item.jsonObject.getValue("jobs").jsonArray, jobsOf { buildWear(id, built.anchors) }.second)
                }
            }

            for (egg in golden.getValue("eggs").jsonArray) {
                val seed = egg.jsonObject.getValue("seed").jsonPrimitive.content.toLong()
                compareJobs("huevo $seed", egg.jsonObject.getValue("jobs").jsonArray, jobsOf { buildEgg(createGenes(seed).eggLook()) }.second)
            }

            compareJobs("casa", golden.getValue("house").jsonArray, jobsOf { buildHouse(null) }.second)
        } finally {
            Sculptor.capture = null
        }
    }

    companion object {
        private fun near(what: String, expected: Double, actual: Double, tolerance: Double) {
            assertTrue("$what: $actual en vez de $expected", abs(expected - actual) <= tolerance * max(1.0, abs(expected)))
        }

        private fun near(what: String, expected: DoubleArray, actual: DoubleArray, tolerance: Double) {
            assertEquals("$what: tamaño", expected.size, actual.size)
            for (i in expected.indices) near("$what[$i]", expected[i], actual[i], tolerance)
        }

        private fun compareShape(what: String, js: JsonObject, shape: Shape) {
            assertEquals("$what: forma", js.getValue("type").jsonPrimitive.content, shape.type)
            val fields = shape.fields()
            assertEquals("$what: medidas de ${shape.type}", js.keys - "type", fields.keys)
            for ((name, value) in fields) {
                val expected = js.getValue(name)
                when (value) {
                    null -> assertTrue("$what.$name debería ser null", expected is JsonNull)
                    is DoubleArray -> near("$what.$name", doubles(expected), value, 1e-9)
                    is Double -> near("$what.$name", expected.jsonPrimitive.double, value, 1e-9)
                }
            }
        }

        private fun compareJobs(what: String, js: JsonArray, jobs: List<Job>) {
            assertEquals("$what: número de encargos", js.size, jobs.size)
            for ((j, job) in jobs.withIndex()) {
                val o = js[j].jsonObject
                val where = "$what #$j"
                val ground = o["ground"]?.let { if (it is JsonNull) null else it.jsonPrimitive.double }
                assertEquals("$where: suelo", ground, job.ground)
                near("$where: groundK", o.getValue("groundK").jsonPrimitive.double, job.groundK, 1e-12)
                val m = o.getValue("mesh").jsonObject
                near("$where: celda", m.getValue("cell").jsonPrimitive.double, job.options.cell, 1e-12)
                assertEquals("$where: huesos", m.getValue("bones").jsonPrimitive.int, job.options.bones)
                near("$where: oclusión", m.getValue("ao").jsonPrimitive.double, job.options.ao, 1e-12)
                near("$where: alcance", m.getValue("aoReach").jsonPrimitive.double, job.options.aoReach, 1e-12)
                val ops = o.getValue("ops").jsonArray
                assertEquals("$where: formas", ops.size, job.ops.size)
                for ((i, op) in job.ops.withIndex()) {
                    val jo = ops[i].jsonObject
                    val at = "$where forma $i"
                    assertEquals("$at: suma o resta", jo.getValue("op").jsonPrimitive.int, op.op)
                    compareShape(at, jo.getValue("form").jsonObject, op.form)
                    near("$at: k", jo.getValue("k").jsonPrimitive.double, op.k, 1e-12)
                    if (op.op == ADD) {
                        near("$at: color", doubles(jo.getValue("color")), op.color, 1e-12)
                        assertEquals("$at: hueso", jo.getValue("bone").jsonPrimitive.int, op.bone)
                        near("$at: colorK", jo.getValue("colorK").jsonPrimitive.double, op.colorK, 1e-12)
                    }
                    val split = jo["split"] as? JsonObject
                    assertEquals("$at: reparto", split != null, op.split != null)
                }
                val paints = o.getValue("paints").jsonArray
                assertEquals("$where: pinturas", paints.size, job.paints.size)
                for ((i, paint) in job.paints.withIndex()) {
                    val jp = paints[i].jsonObject
                    val at = "$where pintura $i"
                    compareShape(at, jp.getValue("form").jsonObject, paint.form)
                    near("$at: color", doubles(jp.getValue("color")), paint.color, 1e-12)
                    near("$at: borde", jp.getValue("soft").jsonPrimitive.double, paint.soft, 1e-12)
                    near("$at: cantidad", jp.getValue("amount").jsonPrimitive.double, paint.amount, 1e-12)
                }
            }
        }

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
