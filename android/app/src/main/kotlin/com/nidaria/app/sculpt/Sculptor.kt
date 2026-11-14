// Esculpe los encargos en hilos aparte para que el dibujo no se pare. Lo
// ya esculpido se reaprovecha de memoria y, entre aperturas de la app, de
// disco. La geometría se entrega en el acto: si hay que esculpirla, llega
// vacía (`ready` falso) y se rellena sola al terminar.
//
// Sin hilos (en las pruebas, o antes de `start`) se esculpe en el acto.

package com.nidaria.app.sculpt

import android.util.Log
import com.nidaria.app.gl.Geometry
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

object Sculptor {
    // Súbelo si cambia cómo se calcula la malla (Sdf.kt): las guardadas
    // en disco dejan de valer. Los cambios en los modelos no hacen falta:
    // cambian el encargo y con él la clave.
    private const val MESHER_VERSION = 1
    private const val MEMORY_BYTES = 48L * 1024 * 1024
    private const val DISK_BYTES = 64L * 1024 * 1024

    // Las listas tal como las quiere el motor (los huesos en Float).
    private class Baked(val mesh: SculptMesh?) {
        val skinIndex: FloatArray? = mesh?.skinIndex?.let { ints -> FloatArray(ints.size) { ints[it].toFloat() } }
        val bytes: Long = (mesh?.bytes ?: 0).toLong()
    }

    private val lock = Any()
    private val memory = LinkedHashMap<String, Baked>(64, 0.75f, true)
    private var memoryBytes = 0L
    private val pending = HashMap<String, MutableList<Geometry>>()

    private var pool: ThreadPoolExecutor? = null
    private var disk: File? = null
    private val order = AtomicLong()

    // Lo que pide la habitación pasa antes que las miniaturas.
    private var lowPriority = false

    // En las pruebas: aquí se apunta cada encargo.
    @Volatile var capture: MutableList<Job>? = null

    private class Task(val high: Boolean, val run: () -> Unit) : Runnable, Comparable<Task> {
        val seq = order.incrementAndGet()
        override fun run() = run.invoke()
        override fun compareTo(other: Task): Int =
            if (high != other.high) (if (high) -1 else 1) else seq.compareTo(other.seq)
    }

    // Arranca los hilos y la caché de disco (desde la vista, una vez).
    fun start(cacheDir: File) = synchronized(lock) {
        if (pool != null) return
        val threads = (Runtime.getRuntime().availableProcessors() - 1).coerceIn(1, 3)
        @Suppress("UNCHECKED_CAST")
        pool = ThreadPoolExecutor(threads, threads, 30, TimeUnit.SECONDS, PriorityBlockingQueue<Runnable>() as java.util.concurrent.BlockingQueue<Runnable>) { r ->
            Thread({
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
                r.run()
            }, "escultor").apply { isDaemon = true }
        }.apply { allowCoreThreadTimeOut(true) }
        disk = File(cacheDir, "mallas-v$MESHER_VERSION").apply { mkdirs() }
        File(cacheDir, "mallas-v${MESHER_VERSION - 1}").deleteRecursively()
        pool!!.execute(Task(false) { prune() })
    }

    // Esculpe lo que pida `build` sin prisa (para las miniaturas).
    fun <T> lowPriority(build: () -> T): T {
        val before = lowPriority
        lowPriority = true
        try {
            return build()
        } finally {
            lowPriority = before
        }
    }

    fun geometryFor(job: Job): Geometry {
        capture?.add(job)
        val key = job.key
        synchronized(lock) { memory[key]?.let { return geometryOf(it) } }
        val executor = pool
        if (executor == null) {
            val baked = Baked(job.mesh())
            synchronized(lock) { remember(key, baked) }
            return geometryOf(baked)
        }
        val geometry = Geometry(FloatArray(0), FloatArray(0))
        geometry.ready = false
        synchronized(lock) {
            val waiting = pending[key]
            if (waiting != null) {
                waiting.add(geometry)
                return geometry
            }
            pending[key] = mutableListOf(geometry)
        }
        executor.execute(Task(!lowPriority) {
            val baked = try {
                Baked(load(key) ?: job.mesh().also { save(key, it) })
            } catch (error: Throwable) {
                Log.w(TAG, "No se pudo esculpir", error)
                Baked(null)
            }
            val waiting = synchronized(lock) {
                remember(key, baked)
                pending.remove(key)
            }
            waiting?.forEach { fill(it, baked) }
        })
        return geometry
    }

    // ¿Está todo esculpido en este objeto y sus hijos?
    fun ready(root: com.nidaria.app.gl.Object3D): Boolean {
        var all = true
        root.traverse { node -> if (node is com.nidaria.app.gl.Mesh && !node.geometry.ready) all = false }
        return all
    }

    private fun geometryOf(baked: Baked): Geometry {
        val geometry = Geometry(FloatArray(0), FloatArray(0))
        fill(geometry, baked)
        return geometry
    }

    private fun fill(geometry: Geometry, baked: Baked) {
        val mesh = baked.mesh
        if (mesh == null) {
            geometry.ready = true
            return
        }
        geometry.fill(mesh.position, mesh.normal, mesh.index, mesh.color, mesh.ao, baked.skinIndex, mesh.skinWeight)
    }

    private fun remember(key: String, baked: Baked) {
        if (memory.containsKey(key)) return
        memory[key] = baked
        memoryBytes += baked.bytes
        val it = memory.entries.iterator()
        while (memoryBytes > MEMORY_BYTES && it.hasNext()) {
            val old = it.next()
            if (old.key == key) continue
            memoryBytes -= old.value.bytes
            it.remove()
        }
    }

    // ── Disco ─────────────────────────────────────────────────

    private const val MAGIC = 0x4e494441 // "NIDA"

    private fun save(key: String, mesh: SculptMesh?) {
        val dir = disk ?: return
        if (mesh == null) return
        try {
            val skin = mesh.skinIndex != null
            val buffer = ByteBuffer.allocate(24 + mesh.bytes).order(ByteOrder.LITTLE_ENDIAN)
            buffer.putInt(MAGIC).putInt(mesh.position.size / 3).putInt(mesh.index.size).putInt(if (skin) 1 else 0)
            buffer.putInt(0).putInt(0)
            buffer.asFloatBuffer().put(mesh.position).put(mesh.normal).put(mesh.color).put(mesh.ao)
            buffer.position(buffer.position() + (mesh.position.size + mesh.normal.size + mesh.color.size + mesh.ao.size) * 4)
            buffer.asIntBuffer().put(mesh.index)
            buffer.position(buffer.position() + mesh.index.size * 4)
            if (skin) {
                buffer.asIntBuffer().put(mesh.skinIndex!!)
                buffer.position(buffer.position() + mesh.skinIndex.size * 4)
                buffer.asFloatBuffer().put(mesh.skinWeight!!)
            }
            val tmp = File(dir, "$key.tmp")
            tmp.writeBytes(buffer.array())
            tmp.renameTo(File(dir, "$key.mesh"))
            written.addAndGet(mesh.bytes.toLong())
            if (written.get() > DISK_BYTES / 4) {
                written.set(0)
                prune()
            }
        } catch (error: Exception) {
            Log.w(TAG, "No se pudo guardar una malla", error)
        }
    }

    private val written = AtomicLong()

    private fun load(key: String): SculptMesh? {
        val file = File(disk ?: return null, "$key.mesh")
        if (!file.exists()) return null
        return try {
            val buffer = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
            if (buffer.int != MAGIC) return null
            val count = buffer.int
            val indexCount = buffer.int
            val skin = buffer.int == 1
            buffer.int; buffer.int
            fun floats(n: Int): FloatArray {
                val out = FloatArray(n)
                buffer.asFloatBuffer().get(out)
                buffer.position(buffer.position() + n * 4)
                return out
            }
            fun ints(n: Int): IntArray {
                val out = IntArray(n)
                buffer.asIntBuffer().get(out)
                buffer.position(buffer.position() + n * 4)
                return out
            }
            val mesh = SculptMesh(floats(count * 3), floats(count * 3), floats(count * 3), floats(count), ints(indexCount), if (skin) ints(count * 4) else null, if (skin) floats(count * 4) else null)
            file.setLastModified(System.currentTimeMillis())
            mesh
        } catch (error: Exception) {
            file.delete()
            null
        }
    }

    // Tira las mallas que llevan más tiempo sin usarse si se pasa del tope.
    private fun prune() {
        val files = disk?.listFiles { f -> f.name.endsWith(".mesh") } ?: return
        var total = files.sumOf { it.length() }
        if (total <= DISK_BYTES) return
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= DISK_BYTES * 3 / 4) break
            total -= file.length()
            file.delete()
        }
    }

    private const val TAG = "NidariaSculpt"
}
