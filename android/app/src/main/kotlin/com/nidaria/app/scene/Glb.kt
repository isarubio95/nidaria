// Lector de .glb (glTF binario) para los modelos de criatura de la web
// (public/models, que Gradle copia a los recursos de la app). Lee solo lo
// que traen esos modelos: una malla con esqueleto (posición, normal, UV,
// huesos y pesos), la imagen de color, la de las brasas (emisión) y los
// datos de atado que deja tools/rig.py en los extras ("nidaria").
//
// Es Kotlin puro: las imágenes se quedan en sus bytes (JPEG o PNG) y se
// descodifican al subirlas a la tarjeta gráfica (ver GlbCreature.kt), así
// que se puede probar sin Android.

package com.nidaria.app.scene

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.ByteBuffer
import java.nio.ByteOrder

// Lo que sale de un .glb, con los índices de hueso ya en el orden del juego
// (cuerpo, cabeza, orejas, brazos, cola) y la malla en la postura del atado.
class GlbModel(
    val positions: FloatArray,
    val normals: FloatArray,
    val uvs: FloatArray,
    val indices: IntArray,
    val skinIndex: FloatArray,
    val skinWeight: FloatArray,
    val colorImage: ByteArray?,
    val glowImage: ByteArray?,
    val extras: JsonObject,
)

object Glb {
    val ORDER = listOf("body", "head", "ear_l", "ear_r", "arm_l", "arm_r", "tail")

    // Cómo se leen los archivos: la app los saca de sus recursos (lo pone
    // RoomView al arrancar); las pruebas, de public/models.
    @Volatile var open: (String) -> ByteArray = { name -> error("Glb.open sin preparar: $name") }

    private val cache = HashMap<String, GlbModel>()

    // Cada archivo se lee una vez y lo comparten todas las criaturas.
    fun load(name: String): GlbModel = synchronized(cache) { cache.getOrPut(name) { parse(open(name)) } }

    fun parse(bytes: ByteArray): GlbModel {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(buf.getInt(0) == 0x46546C67) { "no es un .glb" }
        val jsonLength = buf.getInt(12)
        require(buf.getInt(16) == 0x4E4F534A) { "falta el trozo JSON" }
        val gltf = Json.parseToJsonElement(String(bytes, 20, jsonLength, Charsets.UTF_8)).jsonObject
        val binStart = 20 + jsonLength + 8
        val accessors = gltf.getValue("accessors").jsonArray
        val views = gltf.getValue("bufferViews").jsonArray
        val nodes = gltf.getValue("nodes").jsonArray

        fun JsonObject.int(key: String, default: Int = 0): Int = this[key]?.jsonPrimitive?.int ?: default

        // Un accesor como lista de Float (los enteros normalizados, a 0..1).
        fun floats(index: Int): FloatArray {
            val acc = accessors[index].jsonObject
            val view = views[acc.int("bufferView")].jsonObject
            val count = acc.int("count")
            val comps = when (acc["type"]!!.jsonPrimitive.content) { "SCALAR" -> 1; "VEC2" -> 2; "VEC3" -> 3; "VEC4" -> 4; "MAT4" -> 16; else -> error("tipo") }
            val type = acc.int("componentType")
            val size = when (type) { 5126, 5125 -> 4; 5123 -> 2; 5121 -> 1; else -> error("componente $type") }
            val stride = view.int("byteStride", comps * size)
            val start = binStart + view.int("byteOffset") + acc.int("byteOffset")
            val normalized = acc["normalized"]?.jsonPrimitive?.content == "true"
            val out = FloatArray(count * comps)
            for (i in 0 until count) {
                for (k in 0 until comps) {
                    val at = start + i * stride + k * size
                    out[i * comps + k] = when (type) {
                        5126 -> buf.getFloat(at)
                        5125 -> buf.getInt(at).toFloat()
                        5123 -> (buf.getShort(at).toInt() and 0xFFFF).let { if (normalized) it / 65535f else it.toFloat() }
                        else -> (buf.get(at).toInt() and 0xFF).let { if (normalized) it / 255f else it.toFloat() }
                    }
                }
            }
            return out
        }

        fun image(texture: JsonObject?): ByteArray? {
            val tex = texture?.int("index", -1)?.takeIf { it >= 0 } ?: return null
            val source = gltf.getValue("textures").jsonArray[tex].jsonObject.int("source")
            val view = views[gltf.getValue("images").jsonArray[source].jsonObject.int("bufferView")].jsonObject
            val start = binStart + view.int("byteOffset")
            return bytes.copyOfRange(start, start + view.int("byteLength"))
        }

        // Matriz de un nodo en el mundo (glTF: por columnas, como Three.js).
        val parentOf = IntArray(nodes.size) { -1 }
        for ((i, node) in nodes.withIndex()) {
            node.jsonObject["children"]?.jsonArray?.forEach { parentOf[it.jsonPrimitive.int] = i }
        }
        fun local(i: Int): DoubleArray {
            val node = nodes[i].jsonObject
            node["matrix"]?.jsonArray?.let { m -> return DoubleArray(16) { m[it].jsonPrimitive.double } }
            fun vec(key: String, default: DoubleArray) = node[key]?.jsonArray?.let { a -> DoubleArray(a.size) { a[it].jsonPrimitive.double } } ?: default
            val t = vec("translation", doubleArrayOf(0.0, 0.0, 0.0))
            val r = vec("rotation", doubleArrayOf(0.0, 0.0, 0.0, 1.0))
            val s = vec("scale", doubleArrayOf(1.0, 1.0, 1.0))
            val (x, y, z, w) = listOf(r[0], r[1], r[2], r[3])
            return doubleArrayOf(
                (1 - 2 * (y * y + z * z)) * s[0], (2 * (x * y + w * z)) * s[0], (2 * (x * z - w * y)) * s[0], 0.0,
                (2 * (x * y - w * z)) * s[1], (1 - 2 * (x * x + z * z)) * s[1], (2 * (y * z + w * x)) * s[1], 0.0,
                (2 * (x * z + w * y)) * s[2], (2 * (y * z - w * x)) * s[2], (1 - 2 * (x * x + y * y)) * s[2], 0.0,
                t[0], t[1], t[2], 1.0,
            )
        }
        fun multiply(a: DoubleArray, b: DoubleArray) = DoubleArray(16) { idx ->
            val col = idx / 4
            val row = idx % 4
            (0 until 4).sumOf { k -> a[k * 4 + row] * b[col * 4 + k] }
        }
        fun world(i: Int): DoubleArray = if (parentOf[i] < 0) local(i) else multiply(world(parentOf[i]), local(i))

        val meshNode = nodes.indexOfFirst { it.jsonObject.containsKey("mesh") && it.jsonObject.containsKey("skin") }
        require(meshNode >= 0) { "falta la malla con esqueleto" }
        val node = nodes[meshNode].jsonObject
        val primitive = gltf.getValue("meshes").jsonArray[node.int("mesh")].jsonObject.getValue("primitives").jsonArray[0].jsonObject
        val attributes = primitive.getValue("attributes").jsonObject
        val positions = floats(attributes.getValue("POSITION").jsonPrimitive.int)
        val normals = floats(attributes.getValue("NORMAL").jsonPrimitive.int)
        // glTF cuenta la v desde arriba de la imagen; el motor, como Three.js
        // con sus texturas de siempre, desde abajo.
        val uvs = floats(attributes.getValue("TEXCOORD_0").jsonPrimitive.int)
        for (i in 1 until uvs.size step 2) uvs[i] = 1f - uvs[i]
        val joints = floats(attributes.getValue("JOINTS_0").jsonPrimitive.int)
        val weights = floats(attributes.getValue("WEIGHTS_0").jsonPrimitive.int)
        val indices = floats(primitive.int("indices")).let { f -> IntArray(f.size) { f[it].toInt() } }

        // La malla, en la postura del atado (la matriz de su nodo).
        val m = world(meshNode)
        for (i in 0 until positions.size / 3) {
            val x = positions[i * 3].toDouble()
            val y = positions[i * 3 + 1].toDouble()
            val z = positions[i * 3 + 2].toDouble()
            positions[i * 3] = (m[0] * x + m[4] * y + m[8] * z + m[12]).toFloat()
            positions[i * 3 + 1] = (m[1] * x + m[5] * y + m[9] * z + m[13]).toFloat()
            positions[i * 3 + 2] = (m[2] * x + m[6] * y + m[10] * z + m[14]).toFloat()
            val nx = normals[i * 3].toDouble()
            val ny = normals[i * 3 + 1].toDouble()
            val nz = normals[i * 3 + 2].toDouble()
            val ox = m[0] * nx + m[4] * ny + m[8] * nz
            val oy = m[1] * nx + m[5] * ny + m[9] * nz
            val oz = m[2] * nx + m[6] * ny + m[10] * nz
            val len = Math.sqrt(ox * ox + oy * oy + oz * oz).coerceAtLeast(1e-9)
            normals[i * 3] = (ox / len).toFloat()
            normals[i * 3 + 1] = (oy / len).toFloat()
            normals[i * 3 + 2] = (oz / len).toFloat()
        }

        // Huesos del archivo a huesos del juego, por su nombre.
        val skin = gltf.getValue("skins").jsonArray[node.int("skin")].jsonObject
        val remap = skin.getValue("joints").jsonArray.map { j ->
            val name = nodes[j.jsonPrimitive.int].jsonObject["name"]?.jsonPrimitive?.content
            ORDER.indexOf(name).coerceAtLeast(0).toFloat()
        }
        val skinIndex = FloatArray(joints.size) { remap[joints[it].toInt()] }

        val material = primitive["material"]?.jsonPrimitive?.int?.let { gltf.getValue("materials").jsonArray[it].jsonObject }
        val colorImage = image(material?.get("pbrMetallicRoughness")?.jsonObject?.get("baseColorTexture")?.jsonObject)
        val glowImage = image(material?.get("emissiveTexture")?.jsonObject)

        val extras = nodes.firstNotNullOfOrNull { it.jsonObject["extras"]?.jsonObject?.get("nidaria") }
            ?.let { Json.parseToJsonElement(it.jsonPrimitive.content).jsonObject }
            ?: error("faltan los datos de atado (extras.nidaria)")

        return GlbModel(positions, normals, uvs, indices, skinIndex, weights, colorImage, glowImage, extras)
    }

    // Ancho y alto de una imagen JPEG o PNG, sin descodificarla.
    fun imageSize(bytes: ByteArray): Pair<Int, Int> {
        if (bytes.size > 24 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte()) {
            val b = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            return b.getInt(16) to b.getInt(20)
        }
        var i = 2
        while (i + 9 < bytes.size) {
            if (bytes[i] != 0xFF.toByte()) { i++; continue }
            val marker = bytes[i + 1].toInt() and 0xFF
            val length = ((bytes[i + 2].toInt() and 0xFF) shl 8) or (bytes[i + 3].toInt() and 0xFF)
            // SOF0 a SOF15 (menos DHT, JPG y DAC): ahí van el alto y el ancho.
            if (marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                val h = ((bytes[i + 5].toInt() and 0xFF) shl 8) or (bytes[i + 6].toInt() and 0xFF)
                val w = ((bytes[i + 7].toInt() and 0xFF) shl 8) or (bytes[i + 8].toInt() and 0xFF)
                return w to h
            }
            i += 2 + length
        }
        error("no sé leer el tamaño de esta imagen")
    }
}

// Atajos para leer los extras.
internal fun JsonArray.v3(): com.nidaria.app.sculpt.V3 =
    com.nidaria.app.sculpt.V3(this[0].jsonPrimitive.double, this[1].jsonPrimitive.double, this[2].jsonPrimitive.double)
