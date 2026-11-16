// La habitación horneada, la misma de la web (public/rooms, hecha con
// art/room/build_room.py; ver baked-house.js): la pared, el suelo y los
// muebles fijos vienen de Blender con la luz ya calculada, en tres canales
// (sol, cielo y lámpara) que aquí se tiñen y se mezclan según la hora, el
// tiempo y si la criatura duerme. El papel pintado se cambia encima y lo
// que se mueve deja su sombra en dos planos que solo reciben sombra.
//
// Si los archivos no están o no se pueden leer, se monta la habitación de
// siempre (House.kt) con la misma interfaz.

package com.nidaria.app.scene

import android.util.Log
import com.nidaria.app.gl.BakedLight
import com.nidaria.app.gl.Color
import com.nidaria.app.gl.Geometry
import com.nidaria.app.gl.Group
import com.nidaria.app.gl.Material
import com.nidaria.app.gl.MaterialKind
import com.nidaria.app.gl.Mesh
import com.nidaria.app.gl.PI_F
import com.nidaria.app.gl.Texture
import com.nidaria.app.gl.planeGeometry
import com.nidaria.app.gl.shapeGeometry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.max

// Lo que Room necesita de la habitación, sea horneada o la de siempre.
class HouseView(
    val group: Group,
    val setWallpaper: (Texture) -> Unit,
    // night: 0 de día, 1 con la luz apagada; phase: la hora del cielo
    // ("day", "dawn", "dusk", "night"); cover: < 1 con el cielo tapado.
    val setLighting: (night: Float, phase: String, cover: Float) -> Unit,
)

private val SUN_TINT = mapOf(
    "day" to Color.hex("#fff0d8"), "dawn" to Color.hex("#ffc796"), "dusk" to Color.hex("#ffae78"), "night" to Color.hex("#9fb0ff"),
)
private val SKY_DAY = Color.hex("#f2f6ff")
private val SKY_GREY = Color.hex("#e4e8ee")
private val SKY_NIGHT = Color.hex("#5d6fb8")
private val LAMP_TINT = Color.hex("#ffcf9e")
private const val SKY_LEVEL = 0.95f
private const val SUN_LEVEL = 2.6f
private const val LAMP_LEVEL = 0.7f
private val SUN_SHARE = mapOf("day" to 1f, "dawn" to 0.55f, "dusk" to 0.6f, "night" to 0f)
private val SKY_SHARE = mapOf("day" to 1f, "dawn" to 0.75f, "dusk" to 0.7f, "night" to 0.22f)

fun buildBakedHouse(sky: Texture?): HouseView = try {
    bakedHouse(sky)
} catch (e: Exception) {
    Log.w("Nidaria", "Habitación horneada no disponible; se usa la de siempre", e)
    classicHouse(sky)
}

// Mapa de luz: datos, no color (sin sRGB), con la fila de arriba primero.
private fun lightTexture(bytes: ByteArray): Texture {
    val (w, h) = Glb.imageSize(bytes)
    return Texture(w, h, mipmaps = true, srgb = false) { decodeImage(bytes, w, h) }
}

internal fun colorTexture(bytes: ByteArray, repeat: Boolean = false): Texture {
    val (w, h) = Glb.imageSize(bytes)
    return Texture(w, h, repeat = repeat, mipmaps = true) { decodeImage(bytes, w, h) }
}

internal fun decodeImage(bytes: ByteArray, w: Int, h: Int): ByteArray {
    val options = android.graphics.BitmapFactory.Options().apply { inPremultiplied = false }
    val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    val argb = IntArray(w * h)
    bitmap.getPixels(argb, 0, w, 0, 0, w, h)
    bitmap.recycle()
    val out = ByteArray(w * h * 4)
    for (i in argb.indices) {
        val c = argb[i]
        out[i * 4] = (c shr 16).toByte()
        out[i * 4 + 1] = (c shr 8).toByte()
        out[i * 4 + 2] = c.toByte()
        out[i * 4 + 3] = (c ushr 24).toByte()
    }
    return out
}

// Cortinas de lino crudo con un ribete teja (las de la web).
private fun linenTexture(): Texture = painted(128, 512, mipmaps = true) { canvas, paint ->
    val w = 128f
    val h = 512f
    canvas.drawColor(css("#f1e9dc"))
    var x = 0f
    var i = 0
    while (x < w) {
        paint.color = rgba(120, 90, 60, 0.03f + 0.03f * ((i * 7) % 5) / 5f)
        canvas.drawRect(x, 0f, x + 1f, h, paint)
        x += 2f
        i += 2
    }
    paint.color = rgba(120, 90, 60, 0.03f)
    var y = 0f
    while (y < h) {
        canvas.drawRect(0f, y, w, y + 1f, paint)
        y += 2f
    }
    paint.color = css("#b8532f")
    canvas.drawRect(0f, h - 44f, w, h - 20f, paint)
    paint.color = css("#8f3d22")
    canvas.drawRect(0f, h - 20f, w, h - 16f, paint)
}

private fun shadowCatcher(width: Float, height: Float, opacity: Float): Mesh {
    val mesh = Mesh(planeGeometry(width, height), Material(MaterialKind.Shadow).apply {
        color.setHex("#2a1c14")
        this.opacity = opacity
        transparent = true
        depthWrite = false
    })
    mesh.receiveShadow = true
    return mesh
}

private fun bakedHouse(sky: Texture?): HouseView {
    val info = Json.parseToJsonElement(String(Glb.open("room.json"), Charsets.UTF_8)).jsonObject
    fun num(key: String) = info.getValue(key).jsonPrimitive.double.toFloat()
    val maps = info.getValue("maps").jsonObject.mapValues { (_, v) ->
        val o = v.jsonObject
        val max = o.getValue("max").jsonArray.map { it.jsonPrimitive.double.toFloat() }
        lightTexture(Glb.open(o.getValue("file").jsonPrimitive.content)) to max
    }
    val floorInfo = info.getValue("floorTexture").jsonObject
    val floorWood = colorTexture(Glb.open(floorInfo.getValue("file").jsonPrimitive.content), repeat = true)
    val floorMeters = floorInfo.getValue("meters").jsonPrimitive.double.toFloat()

    val light = BakedLight()
    val wainscot = beadboard()
    val curtain = linenTexture()
    val painting = paintingTexture()
    val wallpapered = ArrayList<Material>()
    val group = Group()

    for (part in Glb.parseStatic(Glb.open("room.glb"))) {
        val (lightMap, max) = maps[part.node] ?: continue
        val material = Material(MaterialKind.Baked)
        material.lightMap = lightMap
        for (k in 0 until 3) material.lightMax[k] = max[k]
        material.bakedLight = light
        material.color.set(part.color[0], part.color[1], part.color[2])
        when (part.material) {
            "floor" -> {
                // La tarima de la foto es algo oscura: se aclara y se calienta.
                material.color.setHex("#fff0e0")
                material.color.set(material.color.r * 1.12f, material.color.g * 1.12f, material.color.b * 1.12f)
                material.map = floorWood
                material.planar = 2
                material.uvScaleX = 1f / floorMeters
                material.uvScaleY = 1f / floorMeters
            }
            "wallpaper" -> {
                material.color.set(1f, 1f, 1f)
                material.planar = if (part.node == "shell") 3 else 1
                material.uvScaleX = 1f / TILE_METERS
                material.uvScaleY = 1f / TILE_METERS
                wallpapered += material
            }
            "wainscot" -> {
                material.color.set(1f, 1f, 1f)
                material.map = wainscot
                material.planar = 1
                material.uvScaleX = 1f / 0.12f
                material.uvScaleY = 1f / WAINSCOT
            }
            "curtain" -> {
                material.color.set(1f, 1f, 1f)
                material.map = curtain
            }
            "painting_art" -> {
                material.color.set(1f, 1f, 1f)
                material.map = painting
            }
        }
        val geometry = Geometry(part.positions, part.normals, part.uvs, part.indices)
        geometry.uvs2 = part.uvs2
        group.add(Mesh(geometry, material))
    }
    require(group.children.isNotEmpty()) { "room.glb sin piezas" }

    // Cielo de la ventana, brillo del cristal y haz de sol.
    val r = WINDOW.width / 2f
    val pane = Mesh(shapeGeometry(archShape(WINDOW.width, WINDOW.straight), 24).also { geo ->
        val pos = geo.positions
        val uv = FloatArray(pos.size / 3 * 2)
        for (i in 0 until pos.size / 3) {
            uv[i * 2] = (pos[i * 3] + r) / WINDOW.width
            uv[i * 2 + 1] = pos[i * 3 + 1] / (WINDOW.straight + r)
        }
        geo.uvs = uv
    }, Material.basic(Color(1f, 1f, 1f)) {
        map = sky
        toneMapped = false
    })
    pane.position.set(WINDOW.x, WINDOW.y, WALL_Z - WINDOW.depth - 0.002f)
    val glint = Mesh(planeGeometry(WINDOW.width * 0.9f, WINDOW.straight + WINDOW.width * 0.4f), Material.basic(Color(1f, 1f, 1f)) {
        map = glassGlint()
        transparent = true
        opacity = 0.12f
        depthWrite = false
        toneMapped = false
        additive = true
    })
    glint.position.set(WINDOW.x, WINDOW.y + (WINDOW.straight + r) / 2f, WALL_Z - WINDOW.depth * 0.62f)
    val top = WINDOW.y + WINDOW.straight * 0.95f
    val x = WINDOW.x
    val beamGeo = Geometry(
        floatArrayOf(
            x - 0.34f, top, WALL_Z - 0.02f, x + 0.34f, top, WALL_Z - 0.02f,
            x + 0.5f, 0.02f, WALL_Z + 1.95f, x - 0.22f, 0.02f, WALL_Z + 1.95f,
        ),
        FloatArray(12),
        floatArrayOf(0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f),
        intArrayOf(0, 3, 1, 1, 3, 2),
    ).computeVertexNormals()
    val beamMat = Material.basic(Color.hex("#fff2da")) {
        map = beamTexture()
        transparent = true
        opacity = 0.08f
        depthWrite = false
        doubleSide = true
        toneMapped = false
        additive = true
    }
    val beam = Mesh(beamGeo, beamMat)

    val floorShadow = shadowCatcher(10f, 9.4f, 0.26f)
    floorShadow.geometry.rotateX(-PI_F / 2f)
    floorShadow.position.set(0f, 0.002f, WALL_Z + 4.7f)
    val wallShadow = shadowCatcher(10f, 3.2f, 0.2f)
    wallShadow.position.set(0f, 1.6f, WALL_Z + 0.014f)
    group.add(pane, glint, beam, floorShadow, wallShadow)

    val gainSky = SKY_LEVEL / num("skyAtCreature")
    val gainSun = SUN_LEVEL / num("sunPeak")
    val gainLamp = LAMP_LEVEL / num("lampAtCreature")
    val skyTint = Color()

    return HouseView(
        group,
        setWallpaper = { map -> for (m in wallpapered) m.map = map },
        setLighting = { night, phase, cover ->
            val day = 1f - night
            val grey = cover < 1f
            val sun = day * (SUN_SHARE[phase] ?: 1f) * (if (grey) 0.12f else 1f)
            val tint = SUN_TINT[phase] ?: SUN_TINT.getValue("day")
            light.sun.set(tint.r * gainSun * sun, tint.g * gainSun * sun, tint.b * gainSun * sun)
            val skyAmount = day * (SKY_SHARE[phase] ?: 1f) * (if (grey) 0.85f else 1f) + night * 0.16f
            skyTint.lerpColors(if (grey) SKY_GREY else SKY_DAY, SKY_NIGHT, if (phase == "night") max(night, 0.85f) else night)
            light.sky.set(skyTint.r * gainSky * skyAmount, skyTint.g * gainSky * skyAmount, skyTint.b * gainSky * skyAmount)
            // De noche la lámpara está encendida; si la criatura duerme, más.
            val lamp = max(night, if (phase == "night") 0.75f else 0f)
            light.lamp.set(LAMP_TINT.r * gainLamp * lamp, LAMP_TINT.g * gainLamp * lamp, LAMP_TINT.b * gainLamp * lamp)
            beamMat.opacity = 0.08f * sun
            wallShadow.material.opacity = 0.2f * (0.5f + 0.5f * day)
            floorShadow.material.opacity = 0.26f * (0.6f + 0.4f * day)
        },
    )
}

// La habitación de siempre, con la misma interfaz.
private fun classicHouse(sky: Texture?): HouseView {
    val house = buildHouse(sky)
    val white = Color(1f, 1f, 1f)
    val nightWall = Color.hex("#c6cdf0")
    val nightFloor = Color.hex("#d2c8e2")
    val nightTrim = Color.hex("#d8d8f0")
    return HouseView(
        house.group,
        setWallpaper = { map -> house.wallMat.map = map },
        setLighting = { night, phase, cover ->
            house.wallMat.color.lerpColors(white, nightWall, night)
            house.floorMat.color.lerpColors(white, nightFloor, night)
            house.trimMat.color.lerpColors(white, nightTrim, night)
            val sun = if (phase == "day") 1f else if (phase == "night") 0.3f else 0.7f
            house.patchMat.color.setHex(if (phase == "night") "#b9c8ff" else if (phase == "day") "#fff2d2" else "#ffd9b0")
            house.beamMat.color.copy(house.patchMat.color)
            house.patchMat.opacity = 0.22f * sun * cover * (1f - night * 0.6f)
            house.beamMat.opacity = 0.12f * sun * cover * (1f - night * 0.7f)
        },
    )
}
