// Materiales compartidos de la escena, como materials.js en la web. El de
// arcilla es el de las criaturas y los objetos esculpidos: la luz envuelve
// la forma (la penumbra es suave y algo cálida, como en un juguete de
// vinilo blando) y la oclusión horneada en la malla oscurece los pliegues.

package com.nidaria.app.scene

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import com.nidaria.app.gl.Color
import com.nidaria.app.gl.Geometry
import com.nidaria.app.gl.Material
import com.nidaria.app.gl.Mesh
import com.nidaria.app.gl.PI_F
import com.nidaria.app.gl.Texture
import com.nidaria.app.gl.planeGeometry

private class Finish(
    val roughness: Float,
    val metalness: Float = 0f,
    val sheen: Float = 0f,
    val sheenRoughness: Float = 1f,
    val sheenTint: Float = 0f,
    val clearcoat: Float = 0f,
    val clearcoatRoughness: Float = 0f,
)

private val FINISH = mapOf(
    // Tela de peluche: mate con un brillo aterciopelado en los bordes.
    "plush" to Finish(0.86f, sheen = 0.7f, sheenRoughness = 0.55f, sheenTint = 0.35f),
    // Arcilla o vinilo mate.
    "matte" to Finish(0.68f, sheen = 0.25f, sheenRoughness = 0.6f, sheenTint = 0.2f),
    // Vinilo con algo de barniz.
    "gloss" to Finish(0.42f, clearcoat = 0.55f, clearcoatRoughness = 0.32f),
    // Cerámica o caramelo.
    "glaze" to Finish(0.22f, clearcoat = 1f, clearcoatRoughness = 0.12f),
    // Madera, cartón: sin brillo.
    "wood" to Finish(0.62f),
    "fabric" to Finish(0.92f, sheen = 1f, sheenRoughness = 0.5f, sheenTint = 0.45f),
    "metal" to Finish(0.28f, metalness = 0.85f),
)

private val cache = HashMap<String, Material>()

// Material de arcilla. Con `vertexColors` toma el color de la malla
// esculpida; si no, `color`. Se comparte: no se debe tocar.
fun clay(finish: String = "matte", color: String = "#ffffff", vertexColors: Boolean = true, ao: Boolean = true, wrap: Float = 0.32f, sss: String = "#ff9d80", doubleSide: Boolean = false): Material =
    synchronized(cache) {
        cache.getOrPut("clay|$finish|$color|$vertexColors|$ao|$wrap|$sss|$doubleSide") {
            val spec = FINISH[finish] ?: FINISH.getValue("matte")
            Material.standard(Color.hex(color), spec.roughness) {
                metalness = spec.metalness
                this.vertexColors = vertexColors
                this.doubleSide = doubleSide
                if (spec.sheen > 0f) {
                    sheen = spec.sheen
                    sheenRoughness = spec.sheenRoughness
                    sheenColor.set(spec.sheenTint, spec.sheenTint, spec.sheenTint)
                }
                if (spec.clearcoat > 0f) {
                    clearcoat = spec.clearcoat
                    clearcoatRoughness = spec.clearcoatRoughness
                }
                bakedAo = ao
                this.wrap = wrap
                this.sss.copy(Color.hex(sss))
                shared = true
            }
        }
    }

// Material simple sin oclusión ni color por vértice, para piezas hechas
// con geometrías normales (marcos, tablas, cristal…). `extra` ajusta lo
// demás, como los `extra` de la web.
fun plain(color: String, finish: String = "matte", key: String = "", extra: (Material.() -> Unit)? = null): Material = synchronized(cache) {
    val hex = color
    cache.getOrPut("plain|$hex|$finish|$key") {
        val spec = FINISH[finish] ?: FINISH.getValue("matte")
        Material.standard(Color.hex(hex), spec.roughness) {
            metalness = spec.metalness
            if (spec.sheen > 0f) {
                sheen = spec.sheen
                sheenRoughness = spec.sheenRoughness
                sheenColor.copy(Color.hex(hex).lerp(Color(1f, 1f, 1f), 0.5f))
                sheenColor.set(sheenColor.r * spec.sheenTint, sheenColor.g * spec.sheenTint, sheenColor.b * spec.sheenTint)
            }
            if (spec.clearcoat > 0f) {
                clearcoat = spec.clearcoat
                clearcoatRoughness = spec.clearcoatRoughness
            }
            wrap = 0.3f
            sss.copy(Color.hex("#ffd0b8"))
            extra?.invoke(this)
            shared = true
        }
    }
}

// Material físico suelto (cristal, burbujas, agua): como plain pero sin
// la luz de arcilla, para los que la web hace con MeshPhysicalMaterial.
fun physical(color: String, roughness: Float, build: Material.() -> Unit = {}): Material =
    Material.standard(Color.hex(color), roughness, build)

// ── Texturas pintadas ─────────────────────────────────────────
// Se pintan con un lienzo de Android la primera vez que se suben a la
// tarjeta gráfica (ver Texture.source), así que construir un modelo no
// pinta nada y se puede probar sin Android.

fun painted(width: Int, height: Int, repeat: Boolean = false, mipmaps: Boolean = false, srgb: Boolean = true, draw: (Canvas, Paint) -> Unit): Texture =
    Texture(width, height, repeat, mipmaps, srgb) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        draw(Canvas(bitmap), Paint(Paint.ANTI_ALIAS_FLAG))
        val argb = IntArray(width * height)
        bitmap.getPixels(argb, 0, width, 0, 0, width, height)
        bitmap.recycle()
        val out = ByteArray(width * height * 4)
        for (i in argb.indices) {
            val c = argb[i]
            out[i * 4] = (c shr 16).toByte()
            out[i * 4 + 1] = (c shr 8).toByte()
            out[i * 4 + 2] = c.toByte()
            out[i * 4 + 3] = (c ushr 24).toByte()
        }
        out
    }

// Color de CSS con transparencia para el lienzo de Android.
internal fun rgba(r: Int, g: Int, b: Int, a: Float): Int = android.graphics.Color.argb((a * 255f + 0.5f).toInt().coerceIn(0, 255), r, g, b)
internal fun css(hex: String): Int = android.graphics.Color.parseColor(hex)

// Sombra de contacto: una mancha suave para debajo de los objetos.
private val contactTexture: Texture by lazy {
    painted(128, 128) { canvas, paint ->
        paint.shader = RadialGradient(
            64f, 64f, 64f,
            intArrayOf(rgba(70, 40, 25, 0.55f), rgba(70, 40, 25, 0.28f), rgba(70, 40, 25, 0f)),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, 128f, 128f, paint)
    }
}

fun contactShadow(width: Float, depth: Float, opacity: Float = 1f): Mesh {
    val material = Material.basic {
        map = contactTexture
        transparent = true
        depthWrite = false
        this.opacity = opacity
        toneMapped = false
    }
    val mesh = Mesh(planeGeometry(width, depth).rotateX(-PI_F / 2f), material)
    mesh.position.y = 0.004f
    mesh.tag = "contact"
    return mesh
}

// Una geometría compartida (no se libera con las mallas que la usan).
internal fun Geometry.keep(): Geometry {
    shared = true
    return this
}
