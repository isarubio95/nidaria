// Decoración de la habitación: los papeles pintados y las alfombras. Los
// muebles son modelos como los demás artículos y viven en Props.kt. Es el
// equivalente de public/js/scene/decor.js.
//
// Cada papel es una baldosa de 96 px que se repite cada medio metro. Se
// pinta con un lienzo mínimo escrito en Kotlin, sin android.graphics, para
// que las pruebas puedan construir las muestras de la tienda.

package com.nidaria.app.scene

import com.nidaria.app.gl.Color
import com.nidaria.app.gl.Geometry
import com.nidaria.app.gl.Group
import com.nidaria.app.gl.Material
import com.nidaria.app.gl.Mesh
import com.nidaria.app.gl.PI_F
import com.nidaria.app.gl.Shape
import com.nidaria.app.gl.Texture
import com.nidaria.app.gl.shapeGeometry
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// Lo que hay en cada sitio si el jugador no ha puesto nada.
val BASE_DECOR: Map<String, String?> = mapOf("pared" to "papel-lunares", "alfombra" to "alfombra-rosa", "rincon" to "planta", "ventana" to null)

const val TILE = 96
const val TILE_METERS = 0.5f

// Lienzo RGB con bordes suavizados: cada píxel mira 4 × 4 puntos.
internal class Tile(val size: Int) {
    private val px = FloatArray(size * size * 3)

    private fun rgb(hex: String): FloatArray {
        val v = hex.removePrefix("#").toLong(16)
        return floatArrayOf(((v shr 16) and 255) / 255f, ((v shr 8) and 255) / 255f, (v and 255) / 255f)
    }

    fun fill(hex: String) {
        val c = rgb(hex)
        for (i in 0 until size * size) {
            px[i * 3] = c[0]
            px[i * 3 + 1] = c[1]
            px[i * 3 + 2] = c[2]
        }
    }

    // Pinta lo que cumple `inside` dentro de la caja dada, con su alfa.
    private inline fun cover(x0: Float, y0: Float, x1: Float, y1: Float, hex: String, alpha: Float, inside: (Float, Float) -> Boolean) {
        val c = rgb(hex)
        val left = max(0, floor(x0).toInt())
        val top = max(0, floor(y0).toInt())
        val right = min(size - 1, ceil(x1).toInt())
        val bottom = min(size - 1, ceil(y1).toInt())
        for (y in top..bottom) {
            for (x in left..right) {
                var hits = 0
                for (sy in 0 until 4) for (sx in 0 until 4) if (inside(x + (sx + 0.5f) / 4f, y + (sy + 0.5f) / 4f)) hits++
                if (hits == 0) continue
                val a = hits / 16f * alpha
                val i = (y * size + x) * 3
                for (k in 0 until 3) px[i + k] = px[i + k] * (1f - a) + c[k] * a
            }
        }
    }

    fun rect(x: Float, y: Float, w: Float, h: Float, hex: String, alpha: Float = 1f) =
        cover(x, y, x + w, y + h, hex, alpha) { u, v -> u >= x && u < x + w && v >= y && v < y + h }

    fun circle(cx: Float, cy: Float, r: Float, hex: String) =
        cover(cx - r, cy - r, cx + r, cy + r, hex, 1f) { x, y -> (x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r }

    fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, angle: Float, hex: String) {
        val c = cos(angle)
        val s = sin(angle)
        val r = max(rx, ry)
        cover(cx - r, cy - r, cx + r, cy + r, hex, 1f) { x, y ->
            val dx = (x - cx) * c + (y - cy) * s
            val dy = -(x - cx) * s + (y - cy) * c
            (dx * dx) / (rx * rx) + (dy * dy) / (ry * ry) <= 1f
        }
    }

    // Polígono (par-impar), en pares x, y.
    fun polygon(points: FloatArray, hex: String) {
        var x0 = Float.MAX_VALUE
        var y0 = Float.MAX_VALUE
        var x1 = -Float.MAX_VALUE
        var y1 = -Float.MAX_VALUE
        for (i in points.indices step 2) {
            x0 = min(x0, points[i]); x1 = max(x1, points[i])
            y0 = min(y0, points[i + 1]); y1 = max(y1, points[i + 1])
        }
        cover(x0, y0, x1, y1, hex, 1f) { x, y ->
            var inside = false
            var j = points.size - 2
            for (i in points.indices step 2) {
                val xi = points[i]
                val yi = points[i + 1]
                val xj = points[j]
                val yj = points[j + 1]
                if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
                j = i
            }
            inside
        }
    }

    fun bytes(): ByteArray {
        val out = ByteArray(size * size * 4)
        for (i in 0 until size * size) {
            for (k in 0 until 3) out[i * 4 + k] = (px[i * 3 + k] * 255f + 0.5f).toInt().coerceIn(0, 255).toByte()
            out[i * 4 + 3] = 255.toByte()
        }
        return out
    }
}

private fun Tile.dots(points: List<Pair<Float, Float>>, r: Float, hex: String) {
    for ((x, y) in points) circle(x, y, r, hex)
}

private fun Tile.star(x: Float, y: Float, outer: Float, inner: Float, hex: String) {
    val pts = FloatArray(20)
    for (i in 0 until 10) {
        val r = if (i % 2 == 1) inner else outer
        val a = -PI_F / 2f + i * PI_F / 5f
        pts[i * 2] = x + cos(a) * r
        pts[i * 2 + 1] = y + sin(a) * r
    }
    polygon(pts, hex)
}

private fun Tile.daisy(x: Float, y: Float, petal: String, center: String) {
    for (i in 0 until 5) {
        val a = (i / 5f) * PI_F * 2f - PI_F / 2f
        ellipse(x + cos(a) * 6f, y + sin(a) * 6f, 5.5f, 3.6f, a, petal)
    }
    circle(x, y, 3.4f, center)
}

// Pinta una baldosa de 96 × 96, igual que en la web. Todas se repiten sin costuras.
private val WALLPAPERS: Map<String, Tile.() -> Unit> = mapOf(
    "papel-lunares" to {
        fill("#bfe3ff")
        val s = size.toFloat()
        dots(listOf(s / 2 to s / 2, 0f to 0f, s to 0f, 0f to s, s to s), 7f, "#cdeaff")
    },
    "papel-rayas" to {
        fill("#e6f7ec")
        rect(0f, 0f, 24f, 96f, "#c3ead2")
        rect(48f, 0f, 24f, 96f, "#c3ead2")
        rect(34f, 0f, 4f, 96f, "#fffdf8")
        rect(82f, 0f, 4f, 96f, "#fffdf8")
    },
    "papel-vichy" to {
        fill("#fff4f7")
        for ((x, y, w, h) in listOf(listOf(0f, 0f, 96f, 24f), listOf(0f, 48f, 96f, 24f), listOf(0f, 0f, 24f, 96f), listOf(48f, 0f, 24f, 96f))) {
            rect(x, y, w, h, "#ff8fb1", 0.3f)
        }
    },
    "papel-flores" to {
        fill("#fff6e6")
        daisy(24f, 24f, "#ffffff", "#ffd166")
        daisy(72f, 72f, "#ffc7d8", "#ffd166")
        dots(listOf(72f to 22f, 24f to 70f), 3f, "#b8e6c8")
    },
    "papel-olas" to {
        fill("#e1f1ff")
        // Escamas de olas: cada fila tapa la parte baja de la de arriba.
        for (row in -2..6) {
            val y = row * 24f
            val shift = (((row % 2) + 2) % 2) * 48f
            for (k in -1..1) {
                for ((r, hex) in listOf(48f to "#b7dcfa", 40f to "#e1f1ff", 30f to "#cbe6fc", 22f to "#e1f1ff")) circle(shift + k * 96f, y, r, hex)
            }
        }
    },
    "papel-estrellas" to {
        fill("#ddd3f6")
        star(24f, 28f, 9f, 4f, "#ffd166")
        star(72f, 76f, 9f, 4f, "#ffd166")
        dots(listOf(70f to 24f, 22f to 72f, 48f to 50f), 2.6f, "#f3efff")
    },
)

fun isWallpaper(id: String) = id in WALLPAPERS

private val wallpapers = HashMap<String, Texture>()

// Textura de un papel. La repetición la pone el material (uvScale).
fun wallpaperTexture(id: String): Texture = synchronized(wallpapers) {
    wallpapers.getOrPut(id) {
        val tile = Tile(TILE)
        (WALLPAPERS[id] ?: WALLPAPERS.getValue("papel-lunares")).invoke(tile)
        Texture(TILE, TILE, repeat = true, mipmaps = true).also { it.update(tile.bytes()) }
    }
}

// ── Alfombras ──

private fun edge(kind: String, a: Float): Float = when (kind) {
    "flor" -> 0.84f + 0.16f * sqrt(abs(cos(5f * a)))
    "nube" -> (0.83f + 0.17f * sqrt(abs(cos(3.5f * a)))) * (1f + 0.05f * sin(a))
    else -> 1f
}

private class Rug(val edge: String, val layers: List<Pair<Float, String>>)

// `layers` van de fuera adentro; el número es el tamaño de cada capa.
private val RUGS = mapOf(
    "alfombra-rosa" to Rug("disc", listOf(1f to "#ff8fb1", 0.88f to "#ffabc5", 0.78f to "#ff8fb1")),
    "alfombra-menta" to Rug("disc", listOf(1f to "#7ed6a5", 0.88f to "#a8e6c1", 0.78f to "#7ed6a5")),
    "alfombra-flor" to Rug("flor", listOf(1f to "#ffd56b", 0.82f to "#fff1c4", 0.66f to "#ffc44d")),
    "alfombra-arcoiris" to Rug(
        "disc",
        listOf(1f to "#ff9aa8", 0.86f to "#ffc78f", 0.72f to "#fff0a3", 0.58f to "#a8e6b8", 0.44f to "#a9cdf5"),
    ),
    "alfombra-nube" to Rug("nube", listOf(1f to "#b5d8fb", 0.84f to "#eef7ff", 0.7f to "#c9e4fd")),
)

fun isRug(id: String) = id in RUGS

// De noche las alfombras se tiñen como la de siempre (rosa → #8d5884).
val RUG_NIGHT: Color = Color.hex("#8d9dbe")

private val outlines = HashMap<String, Geometry>()

// Contorno plano en el suelo, con radio 1 y la normal hacia arriba.
private fun outline(kind: String): Geometry = synchronized(outlines) {
    outlines.getOrPut(kind) {
        val shape = Shape()
        val steps = 96
        for (i in 0 until steps) {
            val a = (i / steps.toFloat()) * PI_F * 2f
            val r = edge(kind, a)
            if (i == 0) shape.moveTo(cos(a) * r, sin(a) * r) else shape.lineTo(cos(a) * r, sin(a) * r)
        }
        shape.closePath()
        shapeGeometry(shape).rotateX(-PI_F / 2f).also { it.shared = true }
    }
}

// Una capa de alfombra: su material y el color de día, para que la
// habitación la oscurezca de noche.
class RugLayer(val material: Material, val day: Color)

class BuiltRug(val obj: Group, val layers: List<RugLayer>)

fun buildRug(id: String): BuiltRug {
    val spec = RUGS[id] ?: RUGS.getValue("alfombra-rosa")
    val group = Group()
    val layers = ArrayList<RugLayer>()
    spec.layers.forEachIndexed { index, (scale, hex) ->
        val material = Material.basic(Color.hex(hex))
        val mesh = Mesh(outline(spec.edge), material)
        mesh.scale.set(0.84f * scale, 1f, 0.52f * scale - (1f - scale) * 0.1f)
        mesh.position.y = 0.025f + index * 0.002f
        group.add(mesh)
        layers.add(RugLayer(material, material.color.clone()))
    }
    return BuiltRug(group, layers)
}
