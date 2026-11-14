// Miniaturas: dibuja un modelo 3D en un búfer aparte y lo devuelve como
// imagen. Se guardan en memoria para no repetir el trabajo.

package com.nidaria.app.scene

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.nidaria.app.gl.Box3
import com.nidaria.app.gl.Color
import com.nidaria.app.gl.DirectionalLight
import com.nidaria.app.gl.HemisphereLight
import com.nidaria.app.gl.Mesh
import com.nidaria.app.gl.Object3D
import com.nidaria.app.gl.PerspectiveCamera
import com.nidaria.app.gl.Renderer
import com.nidaria.app.gl.Scene
import com.nidaria.app.gl.Vec3
import com.nidaria.app.gl.tanDeg
import com.nidaria.app.rules.EggLook
import com.nidaria.app.rules.Genes
import com.nidaria.app.rules.createGenes
import com.nidaria.app.rules.eggLook
import com.nidaria.app.sculpt.Sculptor
import java.nio.ByteBuffer
import kotlin.math.max

// Qué miniatura se pide. `key` la identifica en la caché.
sealed class ThumbSpec {
    abstract val key: String

    data class Item(val itemId: String) : ThumbSpec() {
        override val key get() = "item:$itemId"
    }

    data class Egg(val look: EggLook, val silhouette: Boolean = false) : ThumbSpec() {
        override val key get() = if (silhouette) "egg:silhouette" else "egg:${look.hue}:${look.sat}:${look.light}:${look.hue2}"
    }

    data class Form(val formId: String, val genes: Genes, val silhouette: Boolean = false) : ThumbSpec() {
        // Con el interruptor de modelos, la misma forma sale de modelo o esculpida.
        override val key get() = "form:$formId:${Models.modelFor(formId, genes)?.id ?: "sdf"}:${genes.hashCode()}:$silhouette"
    }

    companion object {
        fun creature(formId: String, genes: Genes, silhouette: Boolean = false): ThumbSpec =
            if (formId == "huevo") Egg(genes.eggLook(), silhouette) else Form(formId, genes, silhouette)
    }
}

private val CANON = mapOf(
    "pelusa" to createGenes(101, "pelusa"),
    "hoja" to createGenes(202, "hoja"),
    "gota" to createGenes(303, "gota"),
    "perro" to createGenes(404, "perro").copy(breed = "golden", coat = 0, eyes = 0),
    "gato" to createGenes(505, "gato").copy(breed = "europeo", coat = 1, eyes = 0),
)

// Genes de muestra de cada familia, para las formas que aún no se han criado.
fun canonGenes(family: String): Genes = CANON[family] ?: CANON.getValue("pelusa")

// Genes de muestra con una raza y capa concretas.
fun breedGenes(family: String, breed: String, coat: Int = 0): Genes = canonGenes(family).copy(breed = breed, coat = coat, eyes = 0)

// Caché de miniaturas. Se lee y se escribe desde el hilo principal.
object Thumbs {
    const val SIZE = 192
    val images = mutableStateMapOf<String, ImageBitmap>()
    internal val pending = HashSet<String>()
}

// Estudio de miniaturas: una escena con la misma luz que la habitación.
// Vive en el hilo de dibujo. Cada miniatura se encarga (el modelo se
// esculpe sin prisa, después de lo de la habitación) y se dibuja cuando
// su malla está lista.
internal class ThumbStudio {
    private class Shot(val spec: ThumbSpec, val obj: Object3D, val viewX: Float, val viewY: Float, val silhouette: Boolean, val dispose: () -> Unit)

    private val scene = Scene()
    private val camera = PerspectiveCamera(24f, 1f, 0.1f, 50f)
    private val shots = ArrayList<Shot>()

    // Cuántas hay encargadas y aún sin dibujar.
    val busy: Int get() = shots.size

    init {
        scene.environmentIntensity = 0.45f
        scene.exposure = 1.05f
        // La misma luz que la habitación: sol cálido arriba a la izquierda,
        // relleno de cielo y un contraluz frío que recorta el borde.
        scene.hemisphere = HemisphereLight(Color.hex("#e8f3ff"), Color.hex("#f2d7b8"), 0.7f)
        val key = DirectionalLight(Color.hex("#fff0dc"), 2.9f)
        key.position.set(-2.4f, 4.5f, 4f)
        val rim = DirectionalLight(Color.hex("#d6e8ff"), 1.1f)
        rim.position.set(3f, 2.4f, -2.6f)
        scene.lights.add(key)
        scene.lights.add(rim)
        camera.updateProjectionMatrix()
    }

    private fun free(obj: Object3D) {
        obj.traverse { if (it is Mesh && !it.geometry.shared) it.geometry.dispose() }
    }

    // Encarga una miniatura; false si no hay modelo que dibujar.
    fun start(spec: ThumbSpec): Boolean {
        val shot = Sculptor.lowPriority {
            when (spec) {
                is ThumbSpec.Item -> buildItem(spec.itemId)?.let { obj -> Shot(spec, obj, 0.45f, 0.42f, false) { free(obj) } }
                is ThumbSpec.Egg -> buildEgg(spec.look).let { obj -> Shot(spec, obj, 0.2f, 0.18f, spec.silhouette) { free(obj) } }
                is ThumbSpec.Form -> buildCreature(spec.formId, spec.genes, if (spec.silhouette) "tiny" else "low").let { built ->
                    Shot(spec, built.obj, 0.28f, 0.16f, spec.silhouette) { built.dispose() }
                }
            }
        } ?: return false
        shots.add(shot)
        return true
    }

    // Dibuja hasta `limit` miniaturas de las que ya están esculpidas.
    fun finish(renderer: Renderer, limit: Int): List<Pair<ThumbSpec, Bitmap?>> {
        val ready = shots.filter { Sculptor.ready(it.obj) }.take(limit)
        shots.removeAll(ready)
        return ready.map { it.spec to render(renderer, it) }
    }

    private fun render(renderer: Renderer, shot: Shot): Bitmap? {
        val obj = shot.obj
        scene.add(obj)
        val box = Box3().setFromObject(obj)
        val center = box.center()
        val size = box.size()
        val radius = max(max(size.x, size.y), size.z * 0.8f) / 2f
        if (!radius.isFinite() || radius <= 0f) {
            scene.remove(obj)
            shot.dispose()
            return null
        }
        val distance = (radius / tanDeg(12f)) * 1.12f
        camera.position.copy(center).add(Vec3(shot.viewX, shot.viewY, 1f).normalize().multiplyScalar(distance))
        camera.lookAt(center.x, center.y, center.z)
        val pixels = renderer.renderToPixels(scene, camera, Thumbs.SIZE)
        scene.remove(obj)
        shot.dispose()
        if (shot.silhouette) {
            // Misma silueta, de un solo color (#979eb0), con su transparencia.
            var i = 0
            while (i < pixels.size) {
                val a = pixels[i + 3].toInt() and 255
                pixels[i] = (0x97 * a / 255).toByte()
                pixels[i + 1] = (0x9e * a / 255).toByte()
                pixels[i + 2] = (0xb0 * a / 255).toByte()
                i += 4
            }
        }
        val bitmap = Bitmap.createBitmap(Thumbs.SIZE, Thumbs.SIZE, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(pixels))
        return bitmap
    }
}
