// Modelos 3D de los artículos de la tienda y del huevo, hechos con
// primitivas. Los mismos modelos sirven para la escena y para las
// miniaturas de la interfaz.

package com.nidaria.app.scene

import com.nidaria.app.gl.Color
import com.nidaria.app.gl.Group
import com.nidaria.app.gl.Material
import com.nidaria.app.gl.Mesh
import com.nidaria.app.gl.Object3D
import com.nidaria.app.gl.PI_F
import com.nidaria.app.gl.Shape
import com.nidaria.app.gl.Vec2
import com.nidaria.app.gl.Vec3
import com.nidaria.app.gl.boxGeometry
import com.nidaria.app.gl.capsuleGeometry
import com.nidaria.app.gl.coneGeometry
import com.nidaria.app.gl.cylinderGeometry
import com.nidaria.app.gl.extrudeGeometry
import com.nidaria.app.gl.group
import com.nidaria.app.gl.latheGeometry
import com.nidaria.app.gl.sphereGeometry
import com.nidaria.app.gl.torusGeometry
import com.nidaria.app.rules.EggLook
import com.nidaria.app.rules.Rng
import kotlin.math.cos
import kotlin.math.sin

// Variaciones de un material: null deja el valor por defecto.
private class Opt(
    val roughness: Float? = null,
    val metalness: Float? = null,
    val opacity: Float? = null,
    val doubleSide: Boolean = false,
) {
    val key: String get() = "$roughness|$metalness|$opacity|$doubleSide"
}

private val NONE = Opt()
private val cache = HashMap<String, Material>()

private fun mat(color: String, opt: Opt = NONE): Material = cache.getOrPut(color + opt.key) {
    Material.standard(Color.hex(color), opt.roughness ?: 0.62f) {
        metalness = opt.metalness ?: 0f
        if (opt.opacity != null) {
            transparent = true
            opacity = opt.opacity
        }
        doubleSide = opt.doubleSide
    }
}

private val SPHERE = sphereGeometry(1f, 28, 20).also { it.shared = true }

private fun ball(color: String, rx: Float, ry: Float = rx, rz: Float = rx, opt: Opt = NONE): Mesh {
    val mesh = Mesh(SPHERE, mat(color, opt))
    mesh.scale.set(rx, ry, rz)
    mesh.castShadow = true
    return mesh
}

private fun cyl(color: String, top: Float, bottom: Float, height: Float, opt: Opt = NONE, segments: Int = 28): Mesh {
    val mesh = Mesh(cylinderGeometry(top, bottom, height, segments), mat(color, opt))
    mesh.castShadow = true
    return mesh
}

private fun cone(color: String, radius: Float, height: Float, opt: Opt = NONE): Mesh {
    val mesh = Mesh(coneGeometry(radius, height, 22), mat(color, opt))
    mesh.castShadow = true
    return mesh
}

private fun box(color: String, x: Float, y: Float, z: Float): Mesh {
    val mesh = Mesh(boxGeometry(x, y, z), mat(color))
    mesh.castShadow = true
    return mesh
}

private fun ring(color: String, radius: Float, tube: Float): Mesh {
    val mesh = Mesh(torusGeometry(radius, tube, 12, 36), mat(color))
    mesh.castShadow = true
    return mesh
}

private fun <T : Object3D> at(mesh: T, x: Float = 0f, y: Float = 0f, z: Float = 0f): T {
    mesh.position.set(x, y, z)
    return mesh
}

// Cuenco: media esfera hueca apoyada en el suelo.
private fun bowl(color: String, radius: Float = 0.5f): Mesh {
    val points = ArrayList<Vec2>()
    for (i in 0..12) {
        val a = (i / 12f) * (PI_F / 2f)
        points.add(Vec2(sin(a) * radius, radius * 0.62f * (1f - cos(a))))
    }
    points.add(Vec2(radius * 0.94f, radius * 0.62f))
    val mesh = Mesh(latheGeometry(points, 30), mat(color, Opt(doubleSide = true)))
    mesh.castShadow = true
    return mesh
}

private object C {
    const val red = "#f2605c"
    const val orange = "#ff9f43"
    const val yellow = "#ffd166"
    const val green = "#6cc47f"
    const val leaf = "#4fae7c"
    const val blue = "#7fb2f0"
    const val navy = "#4d6fb8"
    const val pink = "#ff8fb1"
    const val cream = "#fff3dc"
    const val white = "#fffdf8"
    const val tan = "#e2b279"
    const val brown = "#a8744c"
    const val cocoa = "#6b4632"
    const val gold = "#f2c14e"
    const val mint = "#7ed6a5"
    const val violet = "#a98ee0"
}

private val BUBBLE = Opt(roughness = 0.1f, opacity = 0.6f)
private val GOLD = Opt(roughness = 0.25f, metalness = 0.7f)

private val BUILDERS: Map<String, () -> Object3D> = mapOf(
    "manzana" to {
        group(
            at(ball(C.red, 0.46f, 0.42f, 0.46f), 0f, 0.42f, 0f),
            at(cyl(C.cocoa, 0.025f, 0.03f, 0.2f), 0.02f, 0.9f, 0f),
            at(ball(C.leaf, 0.14f, 0.03f, 0.08f), 0.15f, 0.94f, 0f),
        )
    },
    "zanahoria" to {
        val body = cone(C.orange, 0.2f, 0.86f)
        body.rotation.z = PI_F
        val g = group(at(body, 0f, 0.43f, 0f))
        for ((x, tilt) in listOf(-0.09f to 0.35f, 0f to 0f, 0.09f to -0.35f)) {
            val leaf = at(ball(C.leaf, 0.05f, 0.18f, 0.04f), x, 0.98f, 0f)
            leaf.rotation.z = tilt
            g.add(leaf)
        }
        g.rotation.z = -0.35f
        group(g)
    },
    "pan" to {
        val g = group(at(ball(C.tan, 0.56f, 0.3f, 0.34f), 0f, 0.3f, 0f))
        for (x in floatArrayOf(-0.24f, 0f, 0.24f)) {
            val cut = at(ball(C.cream, 0.035f, 0.03f, 0.2f), x, 0.585f, 0f)
            cut.rotation.y = 0.45f
            g.add(cut)
        }
        g
    },
    "arroz" to {
        group(
            bowl(C.blue, 0.5f),
            at(ball(C.white, 0.42f, 0.24f, 0.42f), 0f, 0.32f, 0f),
            at(ball(C.cocoa, 0.07f, 0.03f, 0.07f), 0f, 0.555f, 0f),
        )
    },
    "sopa" to {
        group(
            bowl(C.cream, 0.52f),
            at(cyl(C.orange, 0.46f, 0.46f, 0.04f), 0f, 0.29f, 0f),
            at(ball(C.leaf, 0.06f, 0.02f, 0.04f), -0.15f, 0.32f, 0.08f),
            at(ball(C.leaf, 0.05f, 0.02f, 0.04f), 0.14f, 0.32f, -0.1f),
            at(ball(C.white, 0.07f, 0.02f, 0.07f), 0.04f, 0.32f, 0.18f),
        )
    },
    "pescado" to {
        val tail = cone(C.navy, 0.2f, 0.3f)
        tail.rotation.z = -PI_F / 2f
        tail.scale.z = 0.3f
        group(
            at(ball(C.blue, 0.5f, 0.26f, 0.16f), 0f, 0.3f, 0f),
            at(tail, -0.56f, 0.3f, 0f),
            at(ball(C.white, 0.06f, 0.06f, 0.03f), 0.28f, 0.36f, 0.14f),
            at(ball(C.cocoa, 0.03f, 0.03f, 0.02f), 0.29f, 0.36f, 0.17f),
            at(ball(C.navy, 0.1f, 0.05f, 0.03f), 0f, 0.2f, 0.15f),
        )
    },
    "galleta" to {
        val g = group(at(cyl(C.tan, 0.46f, 0.46f, 0.12f), 0f, 0.06f, 0f))
        for ((x, z) in listOf(0.15f to 0.1f, -0.2f to 0.05f, 0.02f to -0.22f, -0.05f to 0.26f, 0.26f to -0.14f)) {
            g.add(at(ball(C.cocoa, 0.06f, 0.035f, 0.06f), x, 0.13f, z))
        }
        g.rotation.x = 0.7f
        group(at(g, 0f, 0.36f, 0f))
    },
    "helado" to {
        val wafer = cone(C.tan, 0.24f, 0.6f)
        wafer.rotation.z = PI_F
        group(
            at(wafer, 0f, 0.3f, 0f),
            at(ball(C.pink, 0.29f), 0f, 0.72f, 0f),
            at(ball(C.cream, 0.22f), 0f, 1.06f, 0f),
            at(ball(C.red, 0.07f), 0f, 1.3f, 0f),
        )
    },
    "tarta" to {
        fun slice(color: String, height: Float, y: Float): Mesh {
            val mesh = Mesh(cylinderGeometry(0.8f, 0.8f, height, 20, 1, false, -0.42f, 0.84f), mat(color))
            mesh.castShadow = true
            return at(mesh, 0f, y, -0.45f)
        }
        group(
            slice(C.cream, 0.22f, 0.11f), slice(C.pink, 0.08f, 0.26f), slice(C.cream, 0.2f, 0.4f), slice(C.white, 0.07f, 0.535f),
            at(ball(C.red, 0.1f, 0.11f, 0.1f), 0f, 0.66f, 0.12f),
        )
    },
    "festin" to {
        group(
            at(cyl(C.white, 0.72f, 0.6f, 0.06f), 0f, 0.03f, 0f),
            at(ball(C.tan, 0.28f, 0.16f, 0.2f), -0.24f, 0.2f, 0.08f),
            at(ball(C.red, 0.2f, 0.19f, 0.2f), 0.26f, 0.24f, 0.1f),
            at(ball(C.green, 0.15f), 0.06f, 0.2f, -0.28f),
            at(ball(C.orange, 0.13f), -0.14f, 0.18f, -0.3f),
            at(ball(C.yellow, 0.1f, 0.08f, 0.1f), 0.02f, 0.14f, 0.34f),
        )
    },

    "medicina" to {
        group(
            at(cyl(C.white, 0.3f, 0.3f, 0.62f), 0f, 0.31f, 0f),
            at(cyl(C.red, 0.2f, 0.2f, 0.2f), 0f, 0.72f, 0f),
            at(box(C.red, 0.26f, 0.08f, 0.02f), 0f, 0.32f, 0.3f),
            at(box(C.red, 0.08f, 0.26f, 0.02f), 0f, 0.32f, 0.3f),
        )
    },
    "vitaminas" to {
        fun half(color: String, y: Float): Mesh {
            val mesh = Mesh(capsuleGeometry(0.2f, 0.3f, 8, 20), mat(color, Opt(roughness = 0.3f)))
            mesh.castShadow = true
            return at(mesh, 0f, y, 0f)
        }
        val g = group(half(C.yellow, 0.18f), half(C.white, -0.18f))
        g.rotation.z = -0.7f
        group(at(g, 0f, 0.42f, 0f))
    },
    "jabon" to {
        group(
            at(ball(C.mint, 0.46f, 0.2f, 0.3f), 0f, 0.2f, 0f),
            at(ball(C.white, 0.16f, 0.16f, 0.16f, BUBBLE), 0.24f, 0.56f, 0.05f),
            at(ball(C.white, 0.1f, 0.1f, 0.1f, BUBBLE), -0.14f, 0.5f, 0.1f),
            at(ball(C.white, 0.07f, 0.07f, 0.07f, BUBBLE), 0.04f, 0.78f, 0f),
        )
    },

    "pelota" to {
        val band = cyl(C.white, 0.455f, 0.455f, 0.2f)
        val star = ball(C.yellow, 0.14f, 0.14f, 0.05f)
        group(at(ball(C.pink, 0.45f), 0f, 0.45f, 0f), at(band, 0f, 0.45f, 0f), at(star, 0f, 0.45f, 0.44f))
    },
    "peonza" to {
        val lower = cone(C.violet, 0.42f, 0.5f)
        lower.rotation.z = PI_F
        val g = group(
            at(lower, 0f, 0.25f, 0f),
            at(cyl(C.yellow, 0.42f, 0.42f, 0.1f), 0f, 0.55f, 0f),
            at(cone(C.pink, 0.4f, 0.22f), 0f, 0.71f, 0f),
            at(cyl(C.cocoa, 0.045f, 0.045f, 0.3f), 0f, 0.92f, 0f),
        )
        g.rotation.z = 0.22f
        group(g)
    },
    "peluche" to {
        group(
            at(ball(C.brown, 0.3f, 0.3f, 0.26f), 0f, 0.3f, 0f),
            at(ball(C.brown, 0.27f, 0.25f, 0.25f), 0f, 0.72f, 0f),
            at(ball(C.brown, 0.1f, 0.1f, 0.06f), -0.2f, 0.94f, 0f),
            at(ball(C.brown, 0.1f, 0.1f, 0.06f), 0.2f, 0.94f, 0f),
            at(ball(C.cream, 0.12f, 0.09f, 0.08f), 0f, 0.66f, 0.2f),
            at(ball(C.cocoa, 0.035f), -0.1f, 0.78f, 0.22f),
            at(ball(C.cocoa, 0.035f), 0.1f, 0.78f, 0.22f),
            at(ball(C.cocoa, 0.035f, 0.028f, 0.03f), 0f, 0.7f, 0.28f),
            at(ball(C.brown, 0.1f, 0.14f, 0.1f), -0.32f, 0.36f, 0.06f),
            at(ball(C.brown, 0.1f, 0.14f, 0.1f), 0.32f, 0.36f, 0.06f),
            at(ball(C.brown, 0.12f, 0.1f, 0.14f), -0.16f, 0.07f, 0.14f),
            at(ball(C.brown, 0.12f, 0.1f, 0.14f), 0.16f, 0.07f, 0.14f),
        )
    },
    "cometa" to {
        val shape = Shape().moveTo(0f, 0.6f).lineTo(0.36f, 0.12f).lineTo(0f, -0.5f).lineTo(-0.36f, 0.12f).closePath()
        val sail = Mesh(extrudeGeometry(shape, 0.03f), mat(C.blue))
        sail.castShadow = true
        val g = group(sail, at(box(C.white, 0.72f, 0.03f, 0.02f), 0f, 0.12f, 0.04f), at(box(C.white, 0.03f, 1.1f, 0.02f), 0f, 0.05f, 0.04f))
        for (i in 0 until 3) {
            val bow = at(ball(listOf(C.pink, C.yellow, C.mint)[i], 0.08f, 0.04f, 0.03f), sin(i * 1.3f) * 0.1f, -0.62f - i * 0.16f, 0f)
            bow.rotation.z = i - 1f
            g.add(bow)
        }
        g.rotation.z = 0.3f
        group(at(g, 0f, 0.95f, 0f))
    },
)

// ── Ropa ──────────────────────────────────────────────────────
// Cada prenda recibe los anclajes de la criatura y devuelve el objeto y
// a qué parte se cuelga: 'head' (gira con la cabeza) o 'body'.

class Wear(val parent: String, val obj: Object3D)

private val UP = Vec3(0f, 1f, 0f)
private val FRONT = Vec3(0f, 0f, 1f)

private fun onHead(anchors: Anchors, yaw: Float, pitch: Float, obj: Object3D, sink: Float = 0f): Object3D {
    val (point, normal) = onSurface(anchors.face.host, yaw, pitch)
    obj.position.copy(point).addScaledVector(normal, -sink)
    obj.quaternion.setFromUnitVectors(UP, normal)
    return obj
}

private fun halfSphere(color: String, radius: Float, squash: Float = 1f): Mesh {
    val mesh = Mesh(sphereGeometry(radius, 30, 16, 0f, PI_F * 2f, 0f, PI_F / 2f), mat(color, Opt(doubleSide = true)))
    mesh.scale.y = squash
    mesh.castShadow = true
    return mesh
}

private fun neckRing(anchors: Anchors, color: String, tube: Float, lift: Float = 0f): Mesh {
    val mesh = ring(color, 1f, tube)
    mesh.rotation.x = PI_F / 2f
    mesh.scale.set(anchors.neck.rx, anchors.neck.rz, 1f)
    mesh.position.y = anchors.neck.y + lift
    return mesh
}

private fun glasses(a: Anchors, dark: Boolean): Wear {
    val s = a.hat.r / 0.5f
    val g = group()
    val centers = ArrayList<Vec3>()
    for (side in intArrayOf(-1, 1)) {
        val (point, normal) = onSurface(a.face.host, side * a.face.gap, a.face.pitch)
        val lens = group(ring(if (dark) C.cocoa else C.navy, 0.105f * s, 0.014f * s))
        if (dark) lens.add(ball(C.cocoa, 0.1f * s, 0.1f * s, 0.012f * s, Opt(roughness = 0.15f)))
        lens.position.copy(point).addScaledVector(normal, 0.05f * s)
        lens.quaternion.setFromUnitVectors(FRONT, normal)
        centers.add(lens.position.clone())
        g.add(lens)
    }
    val bridge = Mesh(capsuleGeometry(0.012f * s, centers[0].distanceTo(centers[1]) - 0.2f * s, 4, 8), mat(if (dark) C.cocoa else C.navy))
    bridge.rotation.z = PI_F / 2f
    bridge.position.copy(centers[0]).lerp(centers[1], 0.5f)
    bridge.position.z += 0.012f * s
    g.add(bridge)
    return Wear("head", g)
}

private val WEAR: Map<String, (Anchors) -> Wear> = mapOf(
    "lazo" to { a ->
        val s = a.hat.r / 0.5f
        val bow = group()
        for (side in intArrayOf(-1, 1)) {
            val loop = cone(C.pink, 0.13f * s, 0.24f * s)
            loop.rotation.z = side * PI_F / 2f
            loop.scale.z = 0.5f
            bow.add(at(loop, side * 0.13f * s, 0f, 0f))
        }
        bow.add(ball(C.red, 0.06f * s))
        Wear("head", onHead(a, 0.55f, 1.0f, group(at(bow, 0f, 0.09f * s, 0f))))
    },
    "gorro" to { a ->
        val s = a.hat.r / 0.5f
        val band = at(ring(C.white, 0.4f * s, 0.065f * s), 0f, 0.01f * s, 0f)
        band.rotation.x = PI_F / 2f
        val hat = group(at(halfSphere(C.red, 0.4f * s, 0.9f), 0f, 0f, 0f), band, at(ball(C.white, 0.1f * s), 0f, 0.42f * s, 0f))
        Wear("head", at(hat, 0f, a.hat.y - 0.24f * s, 0f))
    },
    "gorra" to { a ->
        val s = a.hat.r / 0.5f
        val visor = cyl(C.navy, 0.3f * s, 0.3f * s, 0.035f * s)
        visor.scale.z = 1.1f
        val hat = group(
            halfSphere(C.blue, 0.4f * s, 0.82f),
            at(visor, 0f, 0.02f * s, 0.3f * s),
            at(ball(C.navy, 0.045f * s), 0f, 0.33f * s, 0f),
        )
        hat.rotation.x = -0.08f
        Wear("head", at(hat, 0f, a.hat.y - 0.2f * s, 0f))
    },
    "paja" to { a ->
        val s = a.hat.r / 0.5f
        val hat = group(
            at(cyl(C.yellow, 0.66f * s, 0.7f * s, 0.035f * s), 0f, 0f, 0f),
            at(cyl(C.yellow, 0.28f * s, 0.34f * s, 0.24f * s), 0f, 0.13f * s, 0f),
            at(cyl(C.red, 0.345f * s, 0.35f * s, 0.07f * s), 0f, 0.055f * s, 0f),
        )
        hat.rotation.x = -0.1f
        Wear("head", at(hat, 0f, a.hat.y - 0.08f * s, 0f))
    },
    "flor" to { a ->
        val s = a.hat.r / 0.5f
        val flower = group(ball(C.yellow, 0.06f * s, 0.045f * s, 0.06f * s))
        for (i in 0 until 6) {
            val angle = (i / 6f) * PI_F * 2f
            val petal = at(ball(C.white, 0.07f * s, 0.025f * s, 0.05f * s), cos(angle) * 0.1f * s, -0.01f * s, sin(angle) * 0.1f * s)
            petal.rotation.y = -angle
            flower.add(petal)
        }
        Wear("head", onHead(a, -0.6f, 0.98f, group(at(flower, 0f, 0.03f * s, 0f))))
    },
    "corona" to { a ->
        val s = a.hat.r / 0.5f
        val crown = group(at(cyl(C.gold, 0.25f * s, 0.22f * s, 0.14f * s, GOLD), 0f, 0.07f * s, 0f))
        for (i in 0 until 6) {
            val angle = (i / 6f) * PI_F * 2f
            crown.add(at(cone(C.gold, 0.06f * s, 0.14f * s, GOLD), cos(angle) * 0.22f * s, 0.2f * s, sin(angle) * 0.22f * s))
            crown.add(
                at(
                    ball(if (i % 2 == 1) C.red else C.blue, 0.03f * s),
                    cos(angle + 0.52f) * 0.245f * s, 0.07f * s, sin(angle + 0.52f) * 0.245f * s,
                ),
            )
        }
        Wear("head", at(crown, 0f, a.hat.y - 0.06f * s, 0f))
    },
    "gafas" to { a -> glasses(a, false) },
    "gafasol" to { a -> glasses(a, true) },
    "bigote" to { a ->
        val s = a.hat.r / 0.5f
        val whiskers = group()
        for (side in intArrayOf(-1, 1)) {
            val half = ball(C.cocoa, 0.11f * s, 0.04f * s, 0.035f * s)
            half.rotation.z = side * -0.3f
            whiskers.add(at(half, side * 0.09f * s, 0f, 0f))
        }
        val (point, normal) = onSurface(a.face.host, 0f, a.face.pitch - 0.15f)
        whiskers.position.copy(point).addScaledVector(normal, 0.02f * s)
        whiskers.quaternion.setFromUnitVectors(FRONT, normal)
        Wear("head", whiskers)
    },
    "panuelo" to { a ->
        val flap = cone(C.red, 0.2f, 0.26f)
        flap.rotation.set(PI_F + 0.25f, 0f, 0f)
        flap.scale.z = 0.35f
        Wear("body", group(neckRing(a, C.red, 0.075f), at(flap, 0f, a.neck.y - 0.14f, a.neck.rz + 0.03f)))
    },
    "pajarita" to { a ->
        val bow = group(ball(C.navy, 0.045f))
        for (side in intArrayOf(-1, 1)) {
            val wing = cone(C.blue, 0.1f, 0.17f)
            wing.rotation.z = side * PI_F / 2f
            wing.scale.z = 0.5f
            bow.add(at(wing, side * 0.095f, 0f, 0f))
        }
        Wear("body", group(neckRing(a, C.navy, 0.03f), at(bow, 0f, a.neck.y, a.neck.rz + 0.05f)))
    },
    "bufanda" to { a ->
        val end = group(at(box(C.mint, 0.15f, 0.3f, 0.05f), 0f, -0.15f, 0f), at(box(C.white, 0.15f, 0.05f, 0.055f), 0f, -0.2f, 0f))
        end.rotation.z = 0.12f
        Wear(
            "body",
            group(neckRing(a, C.mint, 0.1f), neckRing(a, C.white, 0.045f, 0.085f), at(end, a.neck.rx * 0.45f, a.neck.y - 0.02f, a.neck.rz + 0.05f)),
        )
    },
    "cascabel" to { a ->
        Wear(
            "body",
            group(neckRing(a, C.red, 0.04f), at(ball(C.gold, 0.08f, 0.08f, 0.08f, GOLD), 0f, a.neck.y - 0.08f, a.neck.rz + 0.04f)),
        )
    },
)

private val MANNEQUIN = Anchors(
    hat = HatAnchor(0.45f, 0.5f),
    face = FaceAnchor(0f, 0.47f, 0.38f, 0.5f, Host(Vec3(), 0.5f, 0.45f, 0.47f), 0.04f),
    neck = NeckAnchor(0f, 0.34f, 0.3f),
)

fun buildWear(itemId: String, anchors: Anchors = MANNEQUIN): Wear? = WEAR[itemId]?.invoke(anchors)

fun buildItem(itemId: String): Object3D? = BUILDERS[itemId]?.invoke() ?: buildWear(itemId)?.obj

fun isWear(itemId: String): Boolean = itemId in WEAR

// Huevo: color de su ADN y motas del color secundario.
fun buildEgg(egg: EggLook): Group {
    val points = ArrayList<Vec2>()
    for (i in 0..28) {
        val t = (i / 28f) * PI_F
        // Más ancho abajo que arriba, como un huevo de verdad.
        val widen = 1f + 0.2f * cos(t)
        points.add(Vec2(sin(t) * 0.36f * widen, 0.47f - cos(t) * 0.47f))
    }
    val shell = Material.standard(Color().setHSL(egg.hue / 360f, (egg.sat - 4) / 100f, (egg.light + 3) / 100f), 0.42f) {
        clearcoat = 0.35f
        clearcoatRoughness = 0.4f
    }
    val body = Mesh(latheGeometry(points, 36), shell)
    body.castShadow = true
    val g = group(body)
    val rng = Rng((egg.hue * 131 + egg.hue2).toLong())
    fun range(a: Float, b: Float) = rng.range(a.toDouble(), b.toDouble()).toFloat()
    val spot = Material.standard(Color().setHSL(egg.hue2 / 360f, (egg.sat + 6) / 100f, (egg.light - 8) / 100f), 0.5f)
    for (i in 0 until 9) {
        val t = range(0.5f, 2.5f)
        val yaw = range(0f, PI_F * 2f)
        val widen = 1f + 0.2f * cos(t)
        val radius = sin(t) * 0.36f * widen
        val size = range(0.035f, 0.07f)
        val mesh = Mesh(SPHERE, spot)
        mesh.scale.set(size, size, size * 0.25f)
        mesh.position.set(sin(yaw) * radius, 0.47f - cos(t) * 0.47f, cos(yaw) * radius)
        mesh.lookAt(mesh.position.x * 3f, mesh.position.y + (if (cos(t) > 0f) -0.2f else 0.2f), mesh.position.z * 3f)
        g.add(mesh)
    }
    return g
}
