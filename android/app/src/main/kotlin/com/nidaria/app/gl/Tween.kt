// Animaciones por interpolación con la misma semántica que GSAP: curvas
// de aceleración, líneas de tiempo con posiciones relativas ('<', '>'),
// repeticiones de ida y vuelta, y valores iniciales tomados al empezar.

package com.nidaria.app.gl

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.reflect.KMutableProperty0

typealias Ease = (Float) -> Float

object Eases {
    val none: Ease = { it }
    private fun powerOut(n: Int): Ease = { 1f - (1f - it).pow(n) }
    private fun powerIn(n: Int): Ease = { it.pow(n) }
    private fun powerInOut(n: Int): Ease = { if (it < 0.5f) (it * 2f).pow(n) / 2f else 1f - ((1f - it) * 2f).pow(n) / 2f }

    val power1Out = powerOut(2)
    val power1In = powerIn(2)
    val power1InOut = powerInOut(2)
    val power2Out = powerOut(3)
    val power2In = powerIn(3)
    val power2InOut = powerInOut(3)
    val power3Out = powerOut(4)
    val power3In = powerIn(4)
    val sineOut: Ease = { sin(it * PI.toFloat() / 2f) }
    val sineInOut: Ease = { -(cos(PI.toFloat() * it) - 1f) / 2f }

    fun backOut(overshoot: Float = 1.70158f): Ease = { p ->
        val q = p - 1f
        q * q * ((overshoot + 1f) * q + overshoot) + 1f
    }

    fun backIn(overshoot: Float = 1.70158f): Ease = { p -> p * p * ((overshoot + 1f) * p - overshoot) }

    fun elasticOut(amplitude: Float = 1f, period: Float = 0.3f): Ease {
        val p1 = max(1f, amplitude)
        val p2 = period / (if (amplitude < 1f) amplitude else 1f)
        val twoPi = (PI * 2).toFloat()
        val p3 = p2 / twoPi * asin(1f / p1)
        return { p -> if (p >= 1f) 1f else p1 * 2f.pow(-10f * p) * sin((p - p3) * twoPi / p2) + 1f }
    }

    val bounceOut: Ease = { input ->
        var p = input
        val n1 = 7.5625f
        val n2 = 2.75f
        when {
            p < 1f / n2 -> n1 * p * p
            p < 0.7272727272f -> { p -= 1.5f / n2; n1 * p * p + 0.75f }
            p < 0.9090909090f -> { p -= 2.25f / n2; n1 * p * p + 0.9375f }
            else -> { p -= 2.625f / n2; n1 * p * p + 0.984375f }
        }
    }
}

class Channel(val get: () -> Float, val set: (Float) -> Unit, val goal: Float, val relative: Boolean = false) {
    internal var from = 0f
    internal var to = 0f
}

// Qué se anima: un objeto (para poder cancelar por objeto) y sus canales.
class Props(val target: Any, val channels: List<Channel>)

private fun vecChannels(v: Vec3, x: Float?, y: Float?, z: Float?, relative: Boolean): List<Channel> {
    val list = ArrayList<Channel>(3)
    if (x != null) list.add(Channel({ v.x }, { v.x = it }, x, relative))
    if (y != null) list.add(Channel({ v.y }, { v.y = it }, y, relative))
    if (z != null) list.add(Channel({ v.z }, { v.z = it }, z, relative))
    return list
}

fun Vec3.to(x: Float? = null, y: Float? = null, z: Float? = null) = Props(this, vecChannels(this, x, y, z, false))
fun Vec3.by(x: Float? = null, y: Float? = null, z: Float? = null) = Props(this, vecChannels(this, x, y, z, true))
fun Vec3.toAll(s: Float) = to(s, s, s)

fun Euler.to(x: Float? = null, y: Float? = null, z: Float? = null): Props {
    val list = ArrayList<Channel>(3)
    if (x != null) list.add(Channel({ this.x }, { this.x = it }, x))
    if (y != null) list.add(Channel({ this.y }, { this.y = it }, y))
    if (z != null) list.add(Channel({ this.z }, { this.z = it }, z))
    return Props(this, list)
}

fun KMutableProperty0<Float>.to(value: Float, owner: Any = this) = Props(owner, listOf(Channel({ get() }, { set(it) }, value)))

// Posición de una pieza dentro de una línea de tiempo.
class Pos private constructor(internal val kind: Int, internal val value: Float) {
    companion object {
        // Al final de todo lo añadido hasta ahora.
        val End = Pos(0, 0f)

        // En un instante absoluto.
        fun at(time: Float) = Pos(1, time)

        // Junto al inicio de la pieza anterior ('<'), con un desfase.
        fun withPrev(offset: Float = 0f) = Pos(2, offset)

        // Tras el final de la pieza anterior ('>'), con un desfase.
        fun afterPrev(offset: Float = 0f) = Pos(3, offset)
    }
}

abstract class Anim {
    var dead = false
        protected set
    var done = false
        protected set
    abstract val total: Float
    abstract fun render(time: Float)
    open fun kill() {
        dead = true
    }
}

class Tween(
    val props: Props,
    private val duration: Float,
    private val ease: Ease,
    private val repeat: Int = 0,
    private val yoyo: Boolean = false,
    var onUpdate: (() -> Unit)? = null,
    var onComplete: (() -> Unit)? = null,
) : Anim() {
    private var started = false
    override val total: Float get() = duration * (repeat + 1)

    override fun render(time: Float) {
        if (dead || done) return
        if (!started) {
            started = true
            for (c in props.channels) {
                c.from = c.get()
                c.to = if (c.relative) c.from + c.goal else c.goal
            }
        }
        val p = if (duration <= 0f || time >= total) {
            if (yoyo && repeat % 2 == 1) 0f else 1f
        } else {
            val iteration = floor(time / duration).toInt()
            val within = time / duration - iteration
            if (yoyo && iteration % 2 == 1) 1f - within else within
        }
        val k = if (p <= 0f) 0f else if (p >= 1f) 1f else ease(p)
        for (c in props.channels) c.set(c.from + (c.to - c.from) * k)
        onUpdate?.invoke()
        if (time >= total) {
            done = true
            onComplete?.invoke()
        }
    }
}

class Timeline internal constructor(private val owner: Animator, var onComplete: (() -> Unit)? = null) : Anim() {
    private class Item(val start: Float, val anim: Anim?, val call: (() -> Unit)?) {
        var fired = false
    }

    private val items = ArrayList<Item>()
    private var length = 0f
    private var prevStart = 0f
    private var prevEnd = 0f
    override val total: Float get() = length

    private fun place(pos: Pos, span: Float): Float {
        val start = max(
            0f,
            when (pos.kind) {
                1 -> pos.value
                2 -> prevStart + pos.value
                3 -> prevEnd + pos.value
                else -> length
            },
        )
        prevStart = start
        prevEnd = start + span
        length = max(length, start + span)
        return start
    }

    private fun insert(item: Item) {
        var index = items.size
        while (index > 0 && items[index - 1].start > item.start) index--
        items.add(index, item)
    }

    fun to(props: Props, duration: Float, ease: Ease = Eases.power1Out, at: Pos = Pos.End, repeat: Int = 0, yoyo: Boolean = false): Timeline {
        val tween = Tween(props, duration, ease, repeat, yoyo)
        owner.track(tween)
        insert(Item(place(at, tween.total), tween, null))
        return this
    }

    fun set(props: Props, at: Pos = Pos.End): Timeline = to(props, 0f, Eases.none, at)

    // Pausa: alarga la línea de tiempo sin animar nada.
    fun wait(duration: Float, at: Pos = Pos.End): Timeline {
        place(at, duration)
        return this
    }

    fun call(at: Pos = Pos.End, fn: () -> Unit): Timeline {
        insert(Item(place(at, 0f), null, fn))
        return this
    }

    override fun render(time: Float) {
        if (dead || done) return
        var i = 0
        while (i < items.size) {
            val item = items[i++]
            if (time < item.start) break
            if (item.call != null) {
                if (!item.fired) {
                    item.fired = true
                    item.call.invoke()
                    if (dead) return
                }
            } else {
                item.anim?.render(time - item.start)
            }
        }
        if (time >= length) {
            done = true
            onComplete?.invoke()
        }
    }

    override fun kill() {
        dead = true
        for (item in items) item.anim?.kill()
    }
}

// Reloj de las animaciones. Vive en el hilo de dibujo.
class Animator {
    private class Root(val anim: Anim, val start: Float)

    private val roots = ArrayList<Root>()
    private val tweens = ArrayList<Tween>()
    var time = 0f
        private set

    internal fun track(tween: Tween) {
        tweens.add(tween)
    }

    fun update(deltaSeconds: Float) {
        // Tras una pausa larga se avanza un fotograma normal, como hace GSAP.
        time += if (deltaSeconds > 0.5f) 0.033f else max(0f, deltaSeconds)
        var i = 0
        while (i < roots.size) {
            val root = roots[i]
            if (!root.anim.dead && !root.anim.done && time >= root.start) root.anim.render(time - root.start)
            i++
        }
        roots.removeAll { it.anim.dead || it.anim.done }
        tweens.removeAll { it.dead || it.done }
    }

    fun to(
        props: Props,
        duration: Float,
        ease: Ease = Eases.power1Out,
        delay: Float = 0f,
        overwrite: Boolean = false,
        repeat: Int = 0,
        yoyo: Boolean = false,
        onUpdate: (() -> Unit)? = null,
        onComplete: (() -> Unit)? = null,
    ): Tween {
        if (overwrite) killTweensOf(props.target)
        val tween = Tween(props, duration, ease, repeat, yoyo, onUpdate, onComplete)
        track(tween)
        roots.add(Root(tween, time + delay))
        if (duration <= 0f && delay <= 0f) tween.render(0f)
        return tween
    }

    fun timeline(delay: Float = 0f, onComplete: (() -> Unit)? = null): Timeline {
        val timeline = Timeline(this, onComplete)
        roots.add(Root(timeline, time + delay))
        return timeline
    }

    fun delayedCall(delay: Float, fn: () -> Unit) {
        timeline(delay).call { fn() }
    }

    fun killTweensOf(target: Any) {
        for (tween in tweens) if (tween.props.target === target) tween.kill()
    }

    fun killAll() {
        for (root in roots) root.anim.kill()
        roots.clear()
        tweens.clear()
    }
}
