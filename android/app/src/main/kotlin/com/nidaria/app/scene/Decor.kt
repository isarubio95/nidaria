// Decoración de la habitación: los papeles pintados y las alfombras. Los
// muebles son modelos como los demás artículos y viven en Props.kt. Es el
// equivalente de public/js/scene/decor.js.
//
// Cada papel es una baldosa de 256 px que se repite cada medio metro, con
// grano de papel. Se pinta con un lienzo mínimo escrito en Kotlin, sin
// android.graphics, para que las pruebas puedan construir las muestras de
// la tienda. Las alfombras son una pieza mullida con el canto redondeado
// y los anillos de color tejidos en su textura.

package com.nidaria.app.scene

import android.graphics.Path
import com.nidaria.app.gl.Color
import com.nidaria.app.gl.Geometry
import com.nidaria.app.gl.Group
import com.nidaria.app.gl.Material
import com.nidaria.app.gl.Mesh
import com.nidaria.app.gl.PI_F
import com.nidaria.app.gl.Shape
import com.nidaria.app.gl.Texture
import com.nidaria.app.gl.extrudeGeometry
import com.nidaria.app.rules.Rng
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// Lo que hay en cada sitio si el jugador no ha puesto nada.
val BASE_DECOR: Map<String, String?> = mapOf("pared" to "papel-tablas", "alfombra" to "alfombra-marina", "rincon" to "planta", "ventana" to null)

const val TILE = 256
const val TILE_METERS = 0.5f
// Los dibujos se pensaron en una baldosa de 96: se escalan con esto.
private const val U = TILE / 96f

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
    private inline fun cover(x0: Float, y0: Float, x1: Float, y1: Float, c: FloatArray, alpha: Float, inside: (Float, Float) -> Boolean) {
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

    fun rect(x: Float, y: Float, w: Float, h: Float, hex: String, alpha: Float = 1f) = rect(x, y, w, h, rgb(hex), alpha)

    fun rect(x: Float, y: Float, w: Float, h: Float, c: FloatArray, alpha: Float) =
        cover(x, y, x + w, y + h, c, alpha) { u, v -> u >= x && u < x + w && v >= y && v < y + h }

    fun circle(cx: Float, cy: Float, r: Float, hex: String) {
        val c = rgb(hex)
        cover(cx - r, cy - r, cx + r, cy + r, c, 1f) { x, y -> (x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r }
    }

    fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, angle: Float, hex: String) {
        val cs = cos(angle)
        val sn = sin(angle)
        val r = max(rx, ry)
        cover(cx - r, cy - r, cx + r, cy + r, rgb(hex), 1f) { x, y ->
            val dx = (x - cx) * cs + (y - cy) * sn
            val dy = -(x - cx) * sn + (y - cy) * cs
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
        cover(x0, y0, x1, y1, rgb(hex), 1f) { x, y ->
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

// Los dibujos usan las medidas de la baldosa de 96 y se escalan con U.
private fun Tile.dots(points: List<Pair<Float, Float>>, r: Float, hex: String) {
    for ((x, y) in points) circle(x * U, y * U, r * U, hex)
}

private fun Tile.box(x: Float, y: Float, w: Float, h: Float, hex: String, alpha: Float = 1f) = rect(x * U, y * U, w * U, h * U, hex, alpha)

private fun Tile.star(x: Float, y: Float, outer: Float, inner: Float, hex: String) {
    val pts = FloatArray(20)
    for (i in 0 until 10) {
        val r = (if (i % 2 == 1) inner else outer) * U
        val a = -PI_F / 2f + i * PI_F / 5f
        pts[i * 2] = x * U + cos(a) * r
        pts[i * 2 + 1] = y * U + sin(a) * r
    }
    polygon(pts, hex)
}

private fun Tile.daisy(x: Float, y: Float, petal: String, center: String) {
    for (i in 0 until 5) {
        val a = (i / 5f) * PI_F * 2f - PI_F / 2f
        ellipse((x + cos(a) * 6f) * U, (y + sin(a) * 6f) * U, 5.5f * U, 3.6f * U, a, petal)
    }
    dots(listOf(x to y), 3.4f, center)
}

// Grano de papel: motas claras y oscuras casi invisibles.
private val LIGHT = floatArrayOf(1f, 1f, 1f)
private val DARK = floatArrayOf(90f / 255f, 60f / 255f, 40f / 255f)
private fun Tile.grain(seed: Long, strength: Float = 1f) {
    val rng = Rng(seed)
    for (i in 0 until 2600) {
        val x = rng.range(0.0, size.toDouble()).toFloat()
        val y = rng.range(0.0, size.toDouble()).toFloat()
        val light = rng.chance(0.5)
        val s = rng.range(0.6, 1.8).toFloat()
        if (light) rect(x, y, s, s, LIGHT, 0.09f * strength) else rect(x, y, s, s, DARK, 0.035f * strength)
    }
}

// Pinta una baldosa de 256 × 256, igual que en la web. Todas se repiten sin costuras.
private val WALLPAPERS: Map<String, Tile.() -> Unit> = mapOf(
    // La de serie: tablas pintadas de azul pizarra, con sus juntas.
    "papel-tablas" to {
        fill("#9fbccd")
        box(0f, 0f, 24f, 96f, "#93b1c3")
        box(48f, 0f, 24f, 96f, "#93b1c3")
        for (x in listOf(23f, 47f, 71f, 95f)) box(x, 0f, 1.2f, 96f, "#7f9db0")
        grain(17)
    },
    "papel-lunares" to {
        fill("#bfe3ff")
        dots(listOf(48f to 48f, 0f to 0f, 96f to 0f, 0f to 96f, 96f to 96f), 7f, "#d4edff")
        dots(listOf(46.5f to 46.5f, -1.5f to -1.5f, 94.5f to -1.5f, -1.5f to 94.5f, 94.5f to 94.5f), 3.2f, "#e3f3ff")
        grain(11)
    },
    "papel-rayas" to {
        fill("#e6f7ec")
        box(0f, 0f, 24f, 96f, "#c8ecd6")
        box(48f, 0f, 24f, 96f, "#c8ecd6")
        box(34f, 0f, 4f, 96f, "#fffdf8")
        box(82f, 0f, 4f, 96f, "#fffdf8")
        grain(12)
    },
    "papel-vichy" to {
        fill("#f6f1ea")
        for ((x, y, w, h) in listOf(listOf(0f, 0f, 96f, 24f), listOf(0f, 48f, 96f, 24f), listOf(0f, 0f, 24f, 96f), listOf(48f, 0f, 24f, 96f))) {
            box(x, y, w, h, "#c65a37", 0.26f)
        }
        grain(13)
    },
    "papel-flores" to {
        fill("#fff6e6")
        daisy(24f, 24f, "#ffffff", "#ffd166")
        daisy(72f, 72f, "#ffc7d8", "#ffd166")
        dots(listOf(72f to 22f, 24f to 70f), 3f, "#b8e6c8")
        grain(14)
    },
    "papel-olas" to {
        fill("#e1f1ff")
        // Escamas de olas: cada fila tapa la parte baja de la de arriba.
        for (row in -2..6) {
            val y = row * 24f
            val shift = (((row % 2) + 2) % 2) * 48f
            for (k in -1..1) {
                for ((r, hex) in listOf(48f to "#b7dcfa", 40f to "#e1f1ff", 30f to "#cbe6fc", 22f to "#e1f1ff")) dots(listOf((shift + k * 96f) to y), r, hex)
            }
        }
        grain(15)
    },
    "papel-estrellas" to {
        fill("#ddd3f6")
        star(24f, 28f, 9f, 4f, "#ffd166")
        star(72f, 76f, 9f, 4f, "#ffd166")
        dots(listOf(70f to 24f, 22f to 72f, 48f to 50f), 2.6f, "#f3efff")
        grain(16)
    },
)

fun isWallpaper(id: String) = id in WALLPAPERS

private val wallpapers = HashMap<String, Texture>()

// Textura de un papel. La repetición la pone el material (uvScale).
fun wallpaperTexture(id: String): Texture = synchronized(wallpapers) {
    wallpapers.getOrPut(id) {
        val tile = Tile(TILE)
        (WALLPAPERS[id] ?: WALLPAPERS.getValue("papel-tablas")).invoke(tile)
        Texture(TILE, TILE, repeat = true, mipmaps = true).also { it.update(tile.bytes()) }
    }
}

// ── Alfombras ──

// `edge` da el radio del contorno (1 = círculo) según el ángulo.
private fun edge(kind: String, a: Double): Double = when (kind) {
    "flor" -> 0.84 + 0.16 * sqrt(abs(cos(5 * a)))
    "nube" -> (0.83 + 0.17 * sqrt(abs(cos(3.5 * a)))) * (1 + 0.05 * sin(a))
    else -> 1.0
}

private class Rug(val edge: String, val layers: List<Pair<Double, String>>)

// `layers` van de fuera adentro; el número es el tamaño de cada capa.
private val RUGS = mapOf(
    "alfombra-marina" to Rug("disc", listOf(1.0 to "#2f4b72", 0.9 to "#c65a37", 0.84 to "#2f4b72", 0.62 to "#3b5c86")),
    "alfombra-rosa" to Rug("disc", listOf(1.0 to "#ff8fb1", 0.88 to "#ffb3c9", 0.78 to "#ff94b5")),
    "alfombra-menta" to Rug("disc", listOf(1.0 to "#7ed6a5", 0.88 to "#aee8c5", 0.78 to "#84d9aa")),
    "alfombra-flor" to Rug("flor", listOf(1.0 to "#ffd56b", 0.82 to "#fff1c4", 0.66 to "#ffc44d")),
    "alfombra-arcoiris" to Rug(
        "disc",
        listOf(1.0 to "#ff9aa8", 0.86 to "#ffc78f", 0.72 to "#fff0a3", 0.58 to "#a8e6b8", 0.44 to "#a9cdf5"),
    ),
    "alfombra-nube" to Rug("nube", listOf(1.0 to "#b5d8fb", 0.84 to "#eef7ff", 0.7 to "#c9e4fd")),
)

fun isRug(id: String) = id in RUGS

// Margen del canto mullido por fuera del contorno (en radios).
private const val BEVEL = 0.05
private const val REACH = 1 + BEVEL

private fun outlineShape(kind: String): Shape {
    val shape = Shape()
    val steps = 128
    for (i in 0 until steps) {
        val a = (i / steps.toDouble()) * PI * 2
        val r = edge(kind, a)
        val x = (cos(a) * r).toFloat()
        val y = (sin(a) * r).toFloat()
        if (i == 0) shape.moveTo(x, y) else shape.lineTo(x, y)
    }
    shape.closePath()
    return shape
}

private val rugTextures = HashMap<String, Texture>()

// Textura de la cara de arriba: los anillos con una costura entre ellos y
// motas de fibra.
private fun rugTexture(id: String, spec: Rug): Texture = synchronized(rugTextures) {
    rugTextures.getOrPut(id) {
        val size = 512
        painted(size, size, mipmaps = true) { canvas, paint ->
            val toPx = size / 2.0 / REACH
            canvas.drawColor(css(spec.layers[0].second))
            fun path(scale: Double): Path {
                val p = Path()
                for (i in 0..160) {
                    val a = (i / 160.0) * PI * 2
                    val r = edge(spec.edge, a) * scale * toPx
                    // y se invierte: la cara de arriba se ve desde +y tras girarla.
                    val x = (size / 2 + cos(a) * r).toFloat()
                    val y = (size / 2 - sin(a) * r).toFloat()
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                p.close()
                return p
            }
            spec.layers.forEachIndexed { index, (scale, hex) ->
                paint.style = android.graphics.Paint.Style.FILL
                paint.color = css(hex)
                canvas.drawPath(path(scale), paint)
                if (index > 0) {
                    paint.style = android.graphics.Paint.Style.STROKE
                    paint.strokeWidth = 3f
                    paint.color = rgba(120, 70, 60, 0.16f)
                    canvas.drawPath(path(scale), paint)
                    paint.strokeWidth = 1.5f
                    paint.color = rgba(255, 255, 255, 0.3f)
                    canvas.drawPath(path(scale - 0.012), paint)
                }
            }
            paint.style = android.graphics.Paint.Style.FILL
            val rng = Rng((id.length * 977 + 13).toLong())
            for (i in 0 until 9000) {
                val light = rng.chance(0.5)
                paint.color = if (light) rgba(255, 255, 255, 0.1f) else rgba(90, 50, 40, 0.07f)
                val x = rng.range(0.0, size.toDouble()).toFloat()
                val y = rng.range(0.0, size.toDouble()).toFloat()
                canvas.drawRect(x, y, x + rng.range(1.0, 2.5).toFloat(), y + rng.range(1.0, 2.5).toFloat(), paint)
            }
        }
    }
}

// Relieve de fibra compartido por todas las alfombras (datos, no color).
private val fiber: Texture by lazy {
    painted(256, 256, repeat = true, mipmaps = true, srgb = false) { canvas, paint ->
        canvas.drawColor(css("#808080"))
        val rng = Rng(4242)
        for (i in 0 until 7000) {
            val v = rng.range(70.0, 200.0).roundToInt()
            paint.color = android.graphics.Color.rgb(v, v, v)
            val x = rng.range(0.0, 256.0).toFloat()
            val y = rng.range(0.0, 256.0).toFloat()
            canvas.drawCircle(x, y, rng.range(0.6, 1.6).toFloat(), paint)
        }
    }
}

private val rugGeometries = HashMap<String, Geometry>()

// Pieza mullida: el contorno extruido con un canto redondo grueso. La
// cara de arriba lleva la textura de anillos.
private fun rugGeometry(kind: String): Geometry = synchronized(rugGeometries) {
    rugGeometries.getOrPut(kind) {
        val geometry = extrudeGeometry(outlineShape(kind), 0.004f, bevelEnabled = true, bevelThickness = 0.011f, bevelSize = BEVEL.toFloat(), bevelSegments = 5, curveSegments = 4)
        val pos = geometry.positions
        geometry.uvs = FloatArray(pos.size / 3 * 2) { i ->
            if (i % 2 == 0) (pos[i / 2 * 3] / (2 * REACH) + 0.5).toFloat() else (pos[i / 2 * 3 + 1] / (2 * REACH) + 0.5).toFloat()
        }
        // El grosor (z de la extrusión) pasa a ser la altura.
        geometry.rotateX(-PI_F / 2f).translate(0f, 0.011f, 0f)
        geometry.shared = true
        geometry
    }
}

class BuiltRug(val obj: Group, val material: Material)

// La alfombra entera, lista para poner en el suelo.
fun buildRug(id: String): BuiltRug {
    val key = if (RUGS.containsKey(id)) id else "alfombra-marina"
    val spec = RUGS.getValue(key)
    val material = Material.standard(Color(1f, 1f, 1f), 0.96f) {
        map = rugTexture(key, spec)
        sheen = 1f
        sheenRoughness = 0.42f
        sheenColor.set(0.42f, 0.42f, 0.42f)
        bumpMap = fiber
        bumpScale = 0.9f
        bumpUvScaleX = 6f
        bumpUvScaleY = 6f
    }
    val mesh = Mesh(rugGeometry(spec.edge), material)
    mesh.scale.set(0.84f, 1f, 0.52f)
    mesh.receiveShadow = true
    val group = Group()
    group.add(mesh)
    return BuiltRug(group, material)
}
