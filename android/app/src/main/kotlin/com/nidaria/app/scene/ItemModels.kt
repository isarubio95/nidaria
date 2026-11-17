// Artículos de modelo (.glb de public/models/items, los mismos de la web;
// ver item-models.js) en vez de esculpidos: muebles, comida, cuidados y
// juguetes. buildItemModel devuelve null para los artículos que aún no
// tienen modelo, que se esculpen como siempre.

package com.nidaria.app.scene

import com.nidaria.app.gl.Color
import com.nidaria.app.gl.Geometry
import com.nidaria.app.gl.Group
import com.nidaria.app.gl.Material
import com.nidaria.app.gl.Mesh

// Cada artículo con su rugosidad (igual que en la web): la habitación es
// mate; la fruta, el helado, los frascos y los juguetes brillan un poco.
private val ITEMS = mapOf(
    "planta" to 0.78f, "cactus" to 0.78f, "girasol" to 0.78f, "lampara" to 0.78f, "pecera" to 0.78f,
    "cojin" to 0.78f, "mesita" to 0.78f, "baul" to 0.78f, "camita" to 0.78f,
    "manzana" to 0.42f, "zanahoria" to 0.7f, "pan" to 0.85f, "arroz" to 0.6f, "sopa" to 0.55f,
    "pescado" to 0.6f, "festin" to 0.6f,
    "galleta" to 0.85f, "helado" to 0.5f, "tarta" to 0.65f,
    "medicina" to 0.4f, "vitaminas" to 0.35f, "jabon" to 0.55f,
    "pelota" to 0.4f, "peonza" to 0.5f, "peluche" to 0.9f, "cometa" to 0.75f,
)

// Cada archivo se lee una vez; mallas y materiales se comparten.
private val parts = HashMap<String, List<Pair<Geometry, Material>>>()

private fun partsOf(id: String): List<Pair<Geometry, Material>> = synchronized(parts) {
    parts.getOrPut(id) {
        Glb.parseStatic(Glb.open("items/$id.glb")).map { part ->
            val geometry = Geometry(part.positions, part.normals, part.uvs, part.indices).also { it.shared = true }
            // Por las dos caras: las mallas de TRELLIS tienen zonas giradas o
            // abiertas que, por una sola cara, se verían como agujeros.
            val material = Material.standard(Color(1f, 1f, 1f), ITEMS.getValue(id)) {
                map = part.colorImage?.let { colorTexture(it) }
                doubleSide = true
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
