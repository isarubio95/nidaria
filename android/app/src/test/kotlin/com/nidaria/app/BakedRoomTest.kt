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

    @Test
    fun `lee el tamaño de los webp de los mapas de luz y de la tarima`() {
        assertEquals(1024 to 1024, Glb.imageSize(read("rooms/light-floor.webp")))
        assertEquals(1024 to 512, Glb.imageSize(read("rooms/light-wall.webp")))
        assertEquals(2048 to 64, Glb.imageSize(read("rooms/light-trims.webp")))
        assertEquals(1024 to 1024, Glb.imageSize(read("rooms/floor-wood.webp")))
    }
}
