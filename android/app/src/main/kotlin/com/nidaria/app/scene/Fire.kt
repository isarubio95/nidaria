// Llama de dibujo animado para las puntas de cola (como fire.js de la web):
// un plano de cara a la cámara pintado por el sombreador de las llamas (tres
// capas de color plano con un ruido que sube y unas chispas) y un halo que
// suma su luz. El origen es el centro de la bola de la base; `size` es la
// altura de la llama entera y `hue` gira sus colores (fuego azul).

package com.nidaria.app.scene

import com.nidaria.app.gl.Color
import com.nidaria.app.gl.Group
import com.nidaria.app.gl.Material
import com.nidaria.app.gl.MaterialKind
import com.nidaria.app.gl.Mesh
import com.nidaria.app.gl.FlameLook
import com.nidaria.app.gl.planeGeometry
import kotlin.math.exp

// Cuánto arde según el humor.
val FLAME_POWER = mapOf("feliz" to 1.15f, "bien" to 1f, "regular" to 0.85f, "mal" to 0.65f, "fatal" to 0.5f, "enferma" to 0.55f, "dormida" to 0.42f)

class Flame(val obj: Group, private val look: FlameLook) {
    private var target = 1f

    fun setPower(value: Float) {
        target = value
    }

    // Se acerca poco a poco a lo que toca arder.
    fun update(dt: Float) {
        look.power += (target - look.power) * (1f - exp(-dt * 3f))
    }
}

// Color '#rrggbb' en sRGB (las llamas se pintan tal cual), con el tono girado.
private fun srgb(hex: String, hue: Double): FloatArray {
    val c = Color.hex(hex)
    if (hue != 0.0) {
        val col = com.nidaria.app.sculpt.Col(c.r.toDouble(), c.g.toDouble(), c.b.toDouble())
        val turned = offsetHue(col, hue)
        c.set(turned.r.toFloat(), turned.g.toFloat(), turned.b.toFloat())
    }
    return floatArrayOf(Color.linearToSrgb(c.r), Color.linearToSrgb(c.g), Color.linearToSrgb(c.b))
}

// `scale`: la de la criatura. El plano mira a la cámara con su propia
// escala (Mesh.billboard no hereda la del padre), así que se la pone aquí.
fun makeFlame(size: Double, seed: Float, hue: Double, scale: Float): Flame {
    val s = size.toFloat()
    val look = FlameLook(
        outer = srgb("#f04a1a", hue), mid = srgb("#ff9a1f", hue), core = srgb("#ffe27a", hue),
        seed = seed, lift = s * 0.3f,
    )
    val obj = Group()

    val flameGeo = planeGeometry(s * 0.8f, s * 1.5f).translate(0f, s * 0.5f, 0f)
    val flameMat = Material(MaterialKind.Flame).apply {
        flame = look
        transparent = true
        depthWrite = false
        doubleSide = true
    }
    val flame = Mesh(flameGeo, flameMat)
    flame.billboard = true
    flame.scale.setScalar(scale)

    val glowGeo = planeGeometry(s * 1.5f, s * 1.5f).translate(0f, s * 0.25f, 0f)
    val glow = srgb("#ff7a26", hue)
    val glowMat = Material(MaterialKind.FlameGlow).apply {
        this.flame = look
        color.set(glow[0], glow[1], glow[2])
        transparent = true
        additive = true
        depthWrite = false
        doubleSide = true
    }
    val halo = Mesh(glowGeo, glowMat)
    halo.billboard = true
    halo.scale.setScalar(scale)
    obj.add(halo, flame)
    return Flame(obj, look)
}
