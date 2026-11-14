package com.nidaria.app

import com.nidaria.app.gl.Animator
import com.nidaria.app.gl.Box3
import com.nidaria.app.gl.Mesh
import com.nidaria.app.gl.Object3D
import com.nidaria.app.rules.FORM_LIST
import com.nidaria.app.rules.ITEMS
import com.nidaria.app.rules.SPOTS
import com.nidaria.app.rules.createGenes
import com.nidaria.app.rules.eggLook
import com.nidaria.app.scene.Actor
import com.nidaria.app.scene.BASE_DECOR
import com.nidaria.app.scene.TILE
import com.nidaria.app.scene.wallpaperTexture
import com.nidaria.app.scene.buildCreature
import com.nidaria.app.scene.buildEgg
import com.nidaria.app.scene.buildItem
import com.nidaria.app.scene.buildWear
import com.nidaria.app.scene.isWear
import com.nidaria.app.scene.planFor
import com.nidaria.app.sculpt.Job
import com.nidaria.app.sculpt.Sculptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Construye todo lo que la escena puede llegar a dibujar y comprueba que
// sale geometría sana: ninguna forma, artículo o prenda debe romperse.
class SceneBuildTest {
    private val seeds = listOf(1L, 7L, 101L, 202L, 303L, 12345L, 987654321L, 4294967295L)

    private fun check(root: Object3D, what: String): Int {
        root.updateMatrixWorld()
        var meshes = 0
        root.traverse { node ->
            if (node !is Mesh) return@traverse
            meshes++
            val g = node.geometry
            assertTrue("$what: malla sin vértices", g.vertexCount > 0)
            assertEquals("$what: normales", g.positions.size, g.normals.size)
            for (value in g.positions) assertTrue("$what: posición no finita", value.isFinite())
            for (value in g.normals) assertTrue("$what: normal no finita", value.isFinite())
            g.indices?.let { idx ->
                assertEquals("$what: índices", 0, idx.size % 3)
                for (i in idx) assertTrue("$what: índice fuera de rango", i in 0 until g.vertexCount)
            }
            for (value in node.matrixWorld.e) assertTrue("$what: matriz no finita", value.isFinite())
        }
        val box = Box3().setFromObject(root)
        val size = box.size()
        assertTrue("$what: caja vacía", size.x > 0f && size.y > 0f && size.z > 0f)
        assertTrue("$what: caja desmesurada", size.x < 6f && size.y < 6f && size.z < 6f)
        return meshes
    }

    @Test
    fun everyFormBuilds() {
        var built = 0
        // Esculpir cuesta: cada forma con unas pocas semillas, a poca resolución.
        for (form in FORM_LIST) {
            if (form.stage == "huevo") continue
            for (seed in seeds.take(3)) {
                val genes = createGenes(seed, form.family)
                val creature = buildCreature(form.id, genes, "tiny")
                val meshes = check(creature.obj, "${form.id} con semilla $seed")
                assertTrue("${form.id}: pocas piezas", meshes >= 10)
                assertTrue("${form.id}: altura", creature.height > 0.4f && creature.height < 2.2f)
                assertEquals(2, creature.rig.eyes.size)
                assertEquals(planFor(form.id, genes).arms, creature.rig.arms.isNotEmpty())
                built++
            }
        }
        assertEquals((FORM_LIST.size - 1) * 3, built)
        assertEquals(78, FORM_LIST.size - 1)
    }

    // La pieza de adulta puede ir fundida con el cuerpo (melena) o aparte
    // (alas, corona, cuernos): en los dos casos hay más formas que esculpir.
    @Test
    fun adultsHaveTheirExtraPiece() {
        val genes = createGenes(101, "pelusa")
        fun shapes(formId: String): Int {
            val jobs = ArrayList<Job>()
            Sculptor.capture = jobs
            try {
                buildCreature(formId, genes, "tiny")
            } finally {
                Sculptor.capture = null
            }
            return jobs.sumOf { it.ops.size }
        }
        val young = shapes("joven-pelusa-tranquilo")
        for (branch in listOf("gloton", "jugueton", "tranquilo", "arisco")) {
            assertTrue("La adulta $branch debe llevar más piezas que la joven", shapes("adulto-pelusa-tranquilo-$branch") > young)
        }
        assertEquals(2, buildCreature("adulto-gota-jugueton-jugueton", createGenes(303, "gota")).rig.wings.size)
    }

    @Test
    fun secretsHaveTheirLook() {
        for (family in listOf("pelusa", "hoja", "gota")) {
            val genes = createGenes(202, family)
            assertNotNull(buildCreature("oculta-$family-radiante", genes, "tiny").rig.halo)
            // Nata y guinda; la luna es una sola pieza.
            assertEquals(2, buildCreature("oculta-$family-sibarita", genes, "tiny").rig.top.children.size)
            assertTrue(buildCreature("oculta-$family-lunar", genes, "tiny").rig.top is Mesh)
            for (origin in listOf("pelusa", "hoja", "gota").filter { it != family }) {
                assertEquals(planFor("cria-$origin", genes).earType, planFor("oculta-$family-$origin", genes).earType)
            }
        }
    }

    @Test
    fun everyItemAndGarmentBuilds() {
        for (item in ITEMS) {
            val model = buildItem(item.id)
            assertNotNull("Falta el modelo de ${item.id}", model)
            check(model!!, item.id)
        }
        // La ropa se ajusta a los anclajes de cada cuerpo: cría, joven y adulta.
        val bodies = listOf("cria-hoja", "joven-pelusa-gloton", "adulto-gota-arisco-jugueton", "adulto-hoja-jugueton-tranquilo")
        for (formId in bodies) {
            for (seed in seeds.take(3)) {
                val creature = buildCreature(formId, createGenes(seed, formId.split("-")[1]))
                for (item in ITEMS.filter { it.kind == "ropa" }) {
                    assertTrue(isWear(item.id))
                    val wear = buildWear(item.id, creature.anchors)
                    assertNotNull(wear)
                    assertTrue(wear!!.parent == "head" || wear.parent == "body")
                    check(wear.obj, "${item.id} sobre $formId")
                }
            }
        }
    }

    // Lo que hay en la habitación sin decorar y los papeles pintados, que
    // se pintan en Kotlin puro y deben repetirse sin costuras.
    @Test
    fun roomDecorBuilds() {
        for (id in BASE_DECOR.values.filterNotNull()) check(buildItem(id)!!, id)
        for (item in ITEMS.filter { it.kind == "casa" }) assertTrue(item.id, SPOTS.any { it.id == item.spot })
        for (id in ITEMS.filter { it.spot == "pared" }.map { it.id } + "papel-lunares") {
            val pixels = wallpaperTexture(id).pixels!!
            assertEquals(TILE * TILE * 4, pixels.size)
            val colors = (0 until TILE * TILE).map { i -> (0 until 3).map { pixels[i * 4 + it] } }.toSet()
            assertTrue("$id: un solo color", colors.size > 1)
        }
    }

    @Test
    fun eggsBuild() {
        for (seed in seeds) check(buildEgg(createGenes(seed).eggLook()), "huevo $seed")
    }

    // Las reacciones dejan a la criatura como estaba al terminar.
    @Test
    fun reactionsReturnToRest() {
        val anim = Animator()
        val actor = Actor(buildCreature("adulto-hoja-jugueton-jugueton", createGenes(202, "hoja")), anim)
        actor.setMood("feliz")
        val reactions = listOf<() -> Unit>({ actor.hop(2) }, { actor.shake() }, { actor.wash() }, { actor.nuzzle() }, { actor.spin() }, { actor.eat { } })
        for (react in reactions) {
            react()
            assertTrue(actor.busy)
            var t = 0f
            repeat(400) {
                t += 1f / 60f
                anim.update(1f / 60f)
                actor.update(t, 1f / 60f)
            }
            assertTrue("La reacción no termina", !actor.busy)
            assertEquals(0f, actor.rig.bounce.position.y, 1e-3f)
            assertEquals(1f, actor.rig.squash.scale.y, 0.05f)
            assertEquals(0f, actor.rig.root.rotation.y, 1e-3f)
            check(actor.obj, "tras la reacción")
        }
    }
}
