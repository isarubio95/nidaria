package com.nidaria.app

import com.nidaria.app.rules.createGenes
import com.nidaria.app.scene.Glb
import com.nidaria.app.scene.Models
import com.nidaria.app.scene.buildGlbCreature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

// Los modelos de criatura (.glb) son los de la web: se leen de public/models.
class GlbModelTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setup() {
            Glb.open = { name -> File("../../public/models/$name").readBytes() }
        }
    }

    @Test
    fun `cada glb trae una malla con esqueleto bien formada y sus datos de atado`() {
        val names = File("../../public/models").list()!!.filter { it.endsWith(".glb") }.sorted()
        assertEquals(listOf("ascua", "bosque", "brasa", "brote", "chapo", "gotita", "marea", "mascota", "trepa").map { "$it.glb" }, names)
        for (name in names) {
            val model = Glb.load(name)
            val vertices = model.positions.size / 3
            assertTrue(name, vertices > 1000)
            assertEquals(name, vertices * 3, model.normals.size)
            assertEquals(name, vertices * 2, model.uvs.size)
            assertEquals(name, 0, model.indices.size % 3)
            assertTrue(name, model.indices.all { it in 0 until vertices })
            assertTrue(name, model.skinIndex.all { it in 0f..6f })
            for (i in 0 until vertices) {
                val sum = (0 until 4).sumOf { model.skinWeight[i * 4 + it].toDouble() }
                assertEquals(name, 1.0, sum, 0.02)
            }
            assertNotNull(name, model.colorImage)
            assertEquals(name, 1024 to 1024, Glb.imageSize(model.colorImage!!))
            for (key in listOf("bones", "eyes", "height")) assertTrue("$name: $key", model.extras.containsKey(key))
            // De pie, con los pies en el suelo y a 1 de alto.
            val ys = (0 until vertices).map { model.positions[it * 3 + 1] }
            assertEquals(name, 0.0, ys.min().toDouble(), 0.01)
            assertEquals(name, 1.0, ys.max().toDouble(), 0.01)
        }
    }

    @Test
    fun `con el interruptor apagado no hay modelos, y encendido un modelo por etapa de cada monstruo`() {
        val pelusa = createGenes(101, "pelusa")
        assertNull(Models.modelFor("cria-pelusa", pelusa, on = false))
        assertEquals("mascota", Models.modelFor("cria-pelusa", pelusa, on = true)?.id)
        assertEquals("ascua", Models.modelFor("joven-pelusa-gloton", pelusa, on = true)?.id)
        assertEquals("brasa", Models.modelFor("adulto-pelusa-gloton-arisco", pelusa, on = true)?.id)
        assertEquals("brasa", Models.modelFor("oculta-pelusa-radiante", pelusa, on = true)?.id)
        val hoja = createGenes(202, "hoja")
        assertEquals("brote", Models.modelFor("cria-hoja", hoja, on = true)?.id)
        assertEquals("trepa", Models.modelFor("joven-hoja-arisco", hoja, on = true)?.id)
        assertEquals("bosque", Models.modelFor("oculta-hoja-gota", hoja, on = true)?.id)
        val gota = createGenes(303, "gota")
        assertEquals("gotita", Models.modelFor("cria-gota", gota, on = true)?.id)
        assertEquals("chapo", Models.modelFor("joven-gota-tranquilo", gota, on = true)?.id)
        assertEquals("marea", Models.modelFor("adulto-gota-tranquilo-gloton", gota, on = true)?.id)
        assertNull(Models.modelFor("cria-perro", createGenes(404, "perro"), on = true))
        assertNull(Models.modelFor("cria-gato", createGenes(505, "gato"), on = true))
    }

    @Test
    fun `la criatura de modelo trae lo que anima Actor`() {
        for ((formId, ears) in listOf("cria-pelusa" to 2, "joven-pelusa-jugueton" to 2, "adulto-pelusa-jugueton-arisco" to 0)) {
            for (hue in listOf(18, 205)) {
                val genes = createGenes(1234, "pelusa").copy(hue = hue)
                val spec = Models.modelFor(formId, genes, on = true)!!
                val built = buildGlbCreature(formId, genes, spec)
                assertEquals(formId, spec.height.toFloat(), built.height, 1e-6f)
                assertEquals(formId, 2, built.rig.eyes.size)
                assertEquals(formId, ears, built.rig.ears.size)
                assertEquals(formId, 2, built.rig.arms.size)
                assertNotNull(formId, built.rig.fx)
                assertEquals(formId, 4, built.rig.mouths.size)
                assertTrue(formId, built.rig.mouth.geometry.vertexCount > 0)
                assertEquals(formId, 7, built.rig.mesh.bones.size)
                // La cola lleva su llama.
                assertTrue(formId, built.rig.tail.children.isNotEmpty())
                // El color se mueve con el ADN: el azul, entero.
                val turn = built.rig.mesh.material.tint[0]
                if (hue == 205) assertTrue(formId, kotlin.math.abs(turn) > 1f) else assertTrue(formId, kotlin.math.abs(turn) < 0.3f)
                val fx = built.rig.fx!!
                fx.mood("dormida")
                fx.update(1f, 0.1f)
            }
        }
    }

    @Test
    fun `las hoja y las gota de modelo se montan enteras y el ADN mueve poco su color`() {
        val cases = listOf("cria-hoja" to 2, "joven-hoja-gloton" to 0, "adulto-hoja-gloton-gloton" to 0,
            "cria-gota" to 2, "joven-gota-gloton" to 2, "adulto-gota-gloton-gloton" to 2)
        for ((formId, ears) in cases) {
            val family = formId.split("-")[1]
            val genes = createGenes(77, family)
            val spec = Models.modelFor(formId, genes, on = true)!!
            val built = buildGlbCreature(formId, genes, spec)
            assertEquals(formId, 2, built.rig.eyes.size)
            assertEquals(formId, ears, built.rig.ears.size)
            assertEquals(formId, 2, built.rig.arms.size)
            assertEquals(formId, 7, built.rig.mesh.bones.size)
            // Con el freno de tono, nunca más de 30 grados.
            assertTrue(formId, kotlin.math.abs(built.rig.mesh.material.tint[0]) < Math.toRadians(30.0).toFloat())
        }
    }
}
