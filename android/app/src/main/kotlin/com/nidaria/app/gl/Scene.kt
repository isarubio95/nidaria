// Grafo de escena: objetos con posición, giro y escala, mallas,
// materiales, luces y cámara. Sin dependencias de Android, para poder
// probarlo en la JVM.

package com.nidaria.app.gl

import kotlin.math.max
import kotlin.math.min

open class Object3D {
    val position = Vec3()
    val scale = Vec3(1f, 1f, 1f)
    val rotation = Euler()
    val quaternion = Quat()
    val up = Vec3(0f, 1f, 0f)
    val children = ArrayList<Object3D>()
    var parent: Object3D? = null
        private set
    var visible = true
    val matrix = Mat4()
    val matrixWorld = Mat4()

    // Datos sueltos que la escena quiere colgar del objeto.
    var tag: Any? = null

    init {
        rotation.onChange = { quaternion.setFromEuler(rotation, notify = false) }
        quaternion.onChange = { rotation.setFromQuaternion(quaternion, notify = false) }
    }

    fun add(vararg objects: Object3D): Object3D {
        for (o in objects) {
            o.parent?.remove(o)
            o.parent = this
            children.add(o)
        }
        return this
    }

    fun remove(o: Object3D) {
        if (children.remove(o)) o.parent = null
    }

    fun rotateOnAxis(axis: Vec3, angle: Float): Object3D {
        quaternion.multiply(Quat().setFromAxisAngle(axis, angle))
        return this
    }

    fun rotateZ(angle: Float): Object3D = rotateOnAxis(Vec3.Z, angle)

    fun translateX(distance: Float): Object3D {
        position.add(Vec3.X.applyQuat(quaternion).multiplyScalar(distance))
        return this
    }

    // Orienta el eje +Z local hacia un punto (los objetos) o el -Z (la cámara).
    open fun lookAt(x: Float, y: Float, z: Float) {
        quaternion.setFromRotationMatrix(Mat4().lookAt(Vec3(x, y, z), position, up))
    }

    fun traverse(fn: (Object3D) -> Unit) {
        fn(this)
        for (child in children) child.traverse(fn)
    }

    open fun updateMatrixWorld() {
        matrix.compose(position, quaternion, scale)
        val p = parent
        if (p == null) matrixWorld.copy(matrix) else matrixWorld.multiplyMatrices(p.matrixWorld, matrix)
        for (child in children) child.updateMatrixWorld()
    }
}

class Group : Object3D()

fun group(vararg children: Object3D): Group {
    val g = Group()
    if (children.isNotEmpty()) g.add(*children)
    return g
}

enum class MaterialKind { Standard, Basic, Shadow }

class Material(val kind: MaterialKind = MaterialKind.Standard) {
    val color = Color()
    var roughness = 1f
    var metalness = 0f
    var sheen = 0f
    val sheenColor = Color(0f, 0f, 0f)
    var sheenRoughness = 1f
    var clearcoat = 0f
    var clearcoatRoughness = 0f
    var opacity = 1f
    var transparent = false
    var depthWrite = true
    var doubleSide = false
    var visible = true
    var map: Texture? = null
    var uvScaleX = 1f
    var uvScaleY = 1f

    fun clone(): Material {
        val m = Material(kind)
        m.color.copy(color)
        m.roughness = roughness
        m.metalness = metalness
        m.sheen = sheen
        m.sheenColor.copy(sheenColor)
        m.sheenRoughness = sheenRoughness
        m.clearcoat = clearcoat
        m.clearcoatRoughness = clearcoatRoughness
        m.opacity = opacity
        m.transparent = transparent
        m.depthWrite = depthWrite
        m.doubleSide = doubleSide
        m.visible = visible
        m.map = map
        m.uvScaleX = uvScaleX
        m.uvScaleY = uvScaleY
        return m
    }

    companion object {
        fun standard(color: Color, roughness: Float = 1f, build: (Material.() -> Unit)? = null): Material {
            val m = Material(MaterialKind.Standard)
            m.color.copy(color)
            m.roughness = roughness
            build?.invoke(m)
            return m
        }

        fun basic(color: Color = Color(), build: (Material.() -> Unit)? = null): Material {
            val m = Material(MaterialKind.Basic)
            m.color.copy(color)
            build?.invoke(m)
            return m
        }
    }
}

open class Mesh(var geometry: Geometry, var material: Material) : Object3D() {
    var castShadow = false
    var receiveShadow = false

    // Siempre de cara a la cámara (lo que en Three.js es un Sprite).
    var billboard = false
}

// Imagen para un material. Los píxeles se generan bajo demanda en el
// hilo de dibujo; `version` sube cuando hay que volver a subirlos.
class Texture(val width: Int, val height: Int, val repeat: Boolean = false, val mipmaps: Boolean = false) {
    // RGBA sin premultiplicar, fila superior primero.
    var pixels: ByteArray? = null
    var version = 0
    internal var glId = 0
    internal var glVersion = -1
    internal var glContext = -1

    fun update(data: ByteArray) {
        pixels = data
        version++
    }
}

class HemisphereLight(sky: Color, ground: Color, var intensity: Float) {
    val sky = sky.clone()
    val ground = ground.clone()
}

class DirectionalLight(color: Color, var intensity: Float) {
    val color = color.clone()
    val position = Vec3(0f, 1f, 0f)
    val target = Vec3()
    var castShadow = false
    var shadowLeft = -5f
    var shadowRight = 5f
    var shadowTop = 5f
    var shadowBottom = -5f
    var shadowNear = 0.5f
    var shadowFar = 500f
    var shadowBias = 0f
    var shadowNormalBias = 0f
    var shadowMapSize = 1024
}

class Scene : Object3D() {
    var background: Color? = null
    var hemisphere: HemisphereLight? = null
    val lights = ArrayList<DirectionalLight>()
    var environmentIntensity = 1f
}

class PerspectiveCamera(var fov: Float, var aspect: Float, var near: Float, var far: Float) : Object3D() {
    val projection = Mat4()
    val view = Mat4()
    val viewProjection = Mat4()
    private val inverseViewProjection = Mat4()

    private var offsetOn = false
    private var fullW = 1f
    private var fullH = 1f
    private var offX = 0f
    private var offY = 0f
    private var offW = 1f
    private var offH = 1f

    override fun lookAt(x: Float, y: Float, z: Float) {
        quaternion.setFromRotationMatrix(Mat4().lookAt(position, Vec3(x, y, z), up))
    }

    // Desplaza la ventana de proyección, como camera.setViewOffset.
    fun setViewOffset(fullWidth: Float, fullHeight: Float, x: Float, y: Float, width: Float, height: Float) {
        offsetOn = true
        fullW = fullWidth
        fullH = fullHeight
        offX = x
        offY = y
        offW = width
        offH = height
    }

    fun updateProjectionMatrix() {
        var top = near * tanDeg(fov * 0.5f)
        var height = 2f * top
        var width = aspect * height
        var left = -0.5f * width
        if (offsetOn) {
            left += offX * width / fullW
            top -= offY * height / fullH
            width *= offW / fullW
            height *= offH / fullH
        }
        projection.perspectiveFrustum(left, left + width, top, top - height, near, far)
    }

    fun updateView() {
        updateMatrixWorld()
        view.copy(matrixWorld).invert()
        viewProjection.multiplyMatrices(projection, view)
        inverseViewProjection.copy(viewProjection).invert()
    }

    // Punto del mundo a coordenadas normalizadas de pantalla (-1 a 1).
    fun project(v: Vec3): Vec3 = v.applyMat4(viewProjection)

    fun unproject(v: Vec3): Vec3 = v.applyMat4(inverseViewProjection)
}

// Caja alineada con los ejes de un objeto y sus hijos, como
// Box3.setFromObject: usa las ocho esquinas de la caja de cada geometría.
class Box3 {
    val min = Vec3(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    val max = Vec3(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY)

    fun setFromObject(root: Object3D): Box3 {
        root.updateMatrixWorld()
        val corner = Vec3()
        root.traverse { node ->
            if (node is Mesh) {
                val b = node.geometry.bounds()
                for (i in 0 until 8) {
                    corner.set(
                        if (i and 1 == 0) b[0] else b[3],
                        if (i and 2 == 0) b[1] else b[4],
                        if (i and 4 == 0) b[2] else b[5],
                    ).applyMat4(node.matrixWorld)
                    min.set(min(min.x, corner.x), min(min.y, corner.y), min(min.z, corner.z))
                    max.set(max(max.x, corner.x), max(max.y, corner.y), max(max.z, corner.z))
                }
            }
        }
        return this
    }

    fun center(): Vec3 = Vec3((min.x + max.x) / 2f, (min.y + max.y) / 2f, (min.z + max.z) / 2f)
    fun size(): Vec3 = Vec3(max.x - min.x, max.y - min.y, max.z - min.z)
}

// Rayo contra un elipsoide dado por su matriz (esfera unidad escalada y
// trasladada). Devuelve la distancia o un negativo si no lo toca.
fun rayHitsUnitSphere(origin: Vec3, direction: Vec3, sphereWorld: Mat4): Float {
    val inv = Mat4().copy(sphereWorld).invert()
    val o = origin.clone().applyMat4(inv)
    val e = inv.e
    val d = Vec3(
        e[0] * direction.x + e[4] * direction.y + e[8] * direction.z,
        e[1] * direction.x + e[5] * direction.y + e[9] * direction.z,
        e[2] * direction.x + e[6] * direction.y + e[10] * direction.z,
    )
    val a = d.dot(d)
    val b = 2f * o.dot(d)
    val c = o.dot(o) - 1f
    val disc = b * b - 4f * a * c
    if (disc < 0f || a == 0f) return -1f
    val root = kotlin.math.sqrt(disc)
    val t0 = (-b - root) / (2f * a)
    val t1 = (-b + root) / (2f * a)
    return if (t0 >= 0f) t0 else if (t1 >= 0f) t1 else -1f
}
