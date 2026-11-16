// Muebles de modelo (.glb de public/models/items, los mismos de la web; ver
// item-models.js) en vez de esculpidos. buildItemModel devuelve null para
// los artículos que aún no tienen modelo, que se esculpen como siempre.

package com.nidaria.app.scene

import com.nidaria.app.gl.Color
import com.nidaria.app.gl.Geometry
import com.nidaria.app.gl.Group
import com.nidaria.app.gl.Material
import com.nidaria.app.gl.Mesh

private val ITEMS = setOf("planta", "cactus", "girasol", "lampara", "pecera", "cojin", "mesita", "baul", "camita")

// Cada archivo se lee una vez; mallas y materiales se comparten.
private val parts = HashMap<String, List<Pair<Geometry, Material>>>()

private fun partsOf(id: String): List<Pair<Geometry, Material>> = synchronized(parts) {
    parts.getOrPut(id) {
        Glb.parseStatic(Glb.open("items/$id.glb")).map { part ->
            val geometry = Geometry(part.positions, part.normals, part.uvs, part.indices).also { it.shared = true }
            val material = Material.standard(Color(1f, 1f, 1f), 0.78f) {
                map = part.colorImage?.let { colorTexture(it) }
                shared = true
            }
            geometry to material
        }
    }
}

fun hasItemModel(id: String) = id in ITEMS

fun buildItemModel(id: String): Group? {
    if (id !in ITEMS) return null
    val list = try {
        partsOf(id)
    } catch (e: Exception) {
        android.util.Log.w("Nidaria", "Sin modelo para $id; se esculpe", e)
        return null
    }
    val group = Group()
    for ((geometry, material) in list) {
        val mesh = Mesh(geometry, material)
        mesh.castShadow = true
        mesh.receiveShadow = true
        group.add(mesh)
    }
    return group
}
