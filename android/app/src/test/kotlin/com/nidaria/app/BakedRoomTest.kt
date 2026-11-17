package com.nidaria.app

import com.nidaria.app.scene.Glb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// La habitación horneada y los muebles de modelo son los de la web
// (public/rooms y public/models/items): se leen tal cual.
class BakedRoomTest {
    private fun read(path: String) = File("../../public/$path").readBytes()

    @Test
    fun `la habitación trae sus cinco piezas con la UV del mapa de luz`() {
        val parts = Glb.parseStatic(read("rooms/room.glb"))
        assertEquals(setOf("floor", "wall", "atlas", "trims", "shell"), parts.map { it.node }.toSet())
        for (part in parts) {
            val vertices = part.positions.size / 3
            assertNotNull(part.material, part.uvs2)
            assertEquals(part.material, vertices * 2, part.uvs2!!.size)
            assertEquals(part.material, vertices * 3, part.normals.size)
            assertTrue(part.material, part.indices.all { it in 0 until vertices })
        }
        // El suelo, a ras de suelo y hasta la pared.
        val floor = parts.first { it.node == "floor" }
        val ys = (1 until floor.positions.size step 3).map { floor.positions[it] }
        assertEquals(0f, ys.max(), 1e-4f)
        val zs = (2 until floor.positions.size step 3).map { floor.positions[it] }
        assertEquals(-2.4f, zs.min(), 1e-3f)
        val names = parts.map { it.material }.toSet()
        for (name in listOf("floor", "wallpaper", "wainscot", "curtain", "painting_art")) assertTrue(name, name in names)
    }

    // Vienen cuantizados (KHR_mesh_quantization, ver art/props/pack_glb.py):
    // enteros con signo y la escala en la matriz del nodo.
    @Test
    fun `los artículos de modelo salen de pie, con su altura y normales de verdad`() {
        val heights = mapOf(
            "planta" to 0.75f, "cactus" to 1.0f, "girasol" to 1.35f, "lampara" to 1.5f,
            "pecera" to 1.05f, "mesita" to 1.0f, "baul" to 0.78f,
        )
        val ids = File("../../public/models/items").listFiles()!!.map { it.nameWithoutExtension }
        assertTrue(ids.containsAll(heights.keys))
        for (id in ids) {
            val height = heights[id]
            val parts = Glb.parseStatic(read("models/items/$id.glb"))
            assertTrue(id, parts.isNotEmpty())
            val ys = parts.flatMap { p -> (1 until p.positions.size step 3).map { p.positions[it] } }
            assertEquals(id, 0f, ys.min(), 0.003f)
            if (height != null) assertEquals(id, height, ys.max(), 0.012f)
            for (part in parts) {
                val vertices = part.positions.size / 3
                for (i in 0 until vertices) {
                    val (x, y, z) = Triple(part.normals[i * 3], part.normals[i * 3 + 1], part.normals[i * 3 + 2])
                    assertEquals(id, 1f, Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat(), 1e-3f)
                }
                assertTrue(id, part.uvs!!.all { it in 0f..1f })
                assertTrue(id, part.indices.all { it in 0 until vertices })
                assertNotNull(id, part.colorImage)
            }
        }
    }

    @Test
    fun `lee el tamaño de los webp de los mapas de luz y de la tarima`() {
        assertEquals(1024 to 1024, Glb.imageSize(read("rooms/light-floor.webp")))
        assertEquals(1024 to 512, Glb.imageSize(read("rooms/light-wall.webp")))
        assertEquals(2048 to 64, Glb.imageSize(read("rooms/light-trims.webp")))
        assertEquals(1024 to 1024, Glb.imageSize(read("rooms/floor-wood.webp")))
    }
}
