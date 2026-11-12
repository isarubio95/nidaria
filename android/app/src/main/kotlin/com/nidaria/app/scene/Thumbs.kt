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
        override val key get() = "form:$formId:${genes.seed}:${genes.hue}:$silhouette"
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
)

// Genes de muestra de cada familia, para las formas que aún no se han criado.
fun canonGenes(family: String): Genes = CANON.getValue(family)

// Caché de miniaturas. Se lee y se escribe desde el hilo principal.
object Thumbs {
    const val SIZE = 192
    val images = mutableStateMapOf<String, ImageBitmap>()
    internal val pending = HashSet<String>()
}

// Estudio de miniaturas: una escena con la misma luz que la habitación.
// Vive en el hilo de dibujo.
internal class ThumbStudio {
    private val scene = Scene()
    private val camera = PerspectiveCamera(24f, 1f, 0.1f, 50f)

    init {
        scene.environmentIntensity = 0.5f
        scene.hemisphere = HemisphereLight(Color.hex("#ffffff"), Color.hex("#ffe6cc"), 1.05f)
        val key = DirectionalLight(Color.hex("#fff4e2"), 2.1f)
        key.position.set(-2.4f, 4.5f, 4f)
        scene.lights.add(key)
        camera.updateProjectionMatrix()
    }

    private fun free(obj: Object3D) {
        obj.traverse { if (it is Mesh && !it.geometry.shared) it.geometry.dispose() }
    }

    fun render(renderer: Renderer, spec: ThumbSpec): Bitmap? {
        val obj: Object3D
        val viewX: Float
        val viewY: Float
        val silhouette: Boolean
        when (spec) {
            is ThumbSpec.Item -> {
                obj = buildItem(spec.itemId) ?: return null
                viewX = 0.45f
                viewY = 0.42f
                silhouette = false
            }
            is ThumbSpec.Egg -> {
                obj = buildEgg(spec.look)
                viewX = 0.2f
                viewY = 0.18f
                silhouette = spec.silhouette
            }
            is ThumbSpec.Form -> {
                obj = buildCreature(spec.formId, spec.genes).obj
                viewX = 0.28f
                viewY = 0.16f
                silhouette = spec.silhouette
            }
        }
        scene.add(obj)
        val box = Box3().setFromObject(obj)
        val center = box.center()
        val size = box.size()
        val radius = max(max(size.x, size.y), size.z * 0.8f) / 2f
        val distance = (radius / tanDeg(12f)) * 1.12f
        camera.position.copy(center).add(Vec3(viewX, viewY, 1f).normalize().multiplyScalar(distance))
        camera.lookAt(center.x, center.y, center.z)
        val pixels = renderer.renderToPixels(scene, camera, Thumbs.SIZE)
        scene.remove(obj)
        free(obj)
        if (silhouette) {
            // Misma silueta, de un solo color (#b89f8c), con su transparencia.
            var i = 0
            while (i < pixels.size) {
                val a = pixels[i + 3].toInt() and 255
                pixels[i] = (0xb8 * a / 255).toByte()
                pixels[i + 1] = (0x9f * a / 255).toByte()
                pixels[i + 2] = (0x8c * a / 255).toByte()
                i += 4
            }
        }
        val bitmap = Bitmap.createBitmap(Thumbs.SIZE, Thumbs.SIZE, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(pixels))
        return bitmap
    }
}
