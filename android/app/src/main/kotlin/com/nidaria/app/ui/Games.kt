// Pantalla de minijuego y los seis juegos. Los de lienzo (fruta,
// charcos, burbujas, escondite) se dibujan en un Canvas con medidas en
// dp; parejas y eco están hechos con piezas de la interfaz.

package com.nidaria.app.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.core.content.res.ResourcesCompat
import com.nidaria.app.Game
import com.nidaria.app.PlaySession
import com.nidaria.app.R
import com.nidaria.app.rules.DIFFICULTIES
import com.nidaria.app.rules.FEATURED_MULT
import com.nidaria.app.rules.Level
import com.nidaria.app.rules.PERKS
import com.nidaria.app.rules.TRAITS
import com.nidaria.app.rules.baseReward
import com.nidaria.app.rules.perksFor
import com.nidaria.app.scene.ThumbSpec
import com.nidaria.app.scene.Thumbs
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class Hud(val score: Int, val target: Int, val time: Int? = null, val lives: Int? = null)

private val FRUIT = listOf("manzana", "zanahoria", "pan", "galleta", "helado", "pescado")
private val FACES = listOf("manzana", "helado", "tarta", "pescado", "pelota", "peonza", "peluche", "cometa", "gorro", "corona", "zanahoria", "galleta")

private fun rand(a: Float, b: Float) = a + Random.nextFloat() * (b - a)

// ── Pantalla ──────────────────────────────────────────────────

@Composable
fun GameScreen(game: Game, session: PlaySession, insets: Insets) {
    val room = LocalRoom.current
    val shown by animateFloatAsState(if (session.open) 1f else 0f, tween(Nd.FAST, easing = Nd.ease), label = "juego")
    LaunchedEffect(session) { session.open = true }
    val mini = session.game
    // Con las ventajas del carácter de la criatura ya aplicadas.
    val level = session.level
    var hud by remember { mutableStateOf(Hud(0, level.target)) }
    var started by remember { mutableStateOf(false) }
    var wanted by remember { mutableStateOf(false) }

    // El juego no empieza hasta tener dibujadas las miniaturas que usa.
    val needed = remember {
        listOf(session.avatar) + when (mini.id) {
            "fruta" -> FRUIT.map { ThumbSpec.Item(it) }
            "parejas" -> FACES.map { ThumbSpec.Item(it) }
            else -> emptyList()
        }
    }
    LaunchedEffect(Unit) { needed.forEach { room.requestThumb(it) } }
    val ready = needed.all { it.key in Thumbs.images }
    LaunchedEffect(ready, wanted) { if (ready && wanted) started = true }

    val avatar = Thumbs.images[session.avatar.key]
    val running = started && session.open
    val onHud: (Hud) -> Unit = { hud = it }
    val onEnd: (Int) -> Unit = { score -> game.finishGame(session, score) }

    Column(Modifier.fillMaxSize().graphicsLayer { alpha = shown }.background(Nd.cream).absorbTouches()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = insets.top + 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBtn(Ic.Back, "Salir del juego", { game.leaveGame(session) })
            T(mini.name, Nd.strong, Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat(Ic.Target, "${hud.score} / ${hud.target}", "Puntos y objetivo")
                hud.time?.let { Stat(Ic.Clock, "$it s", "Segundos") }
                hud.lives?.let { Stat(Ic.Heart, "$it", "Vidas") }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            when (mini.id) {
                "fruta" -> FrutaGame(level, avatar, running, onHud, onEnd)
                "parejas" -> ParejasGame(level, running, insets.bottom, onHud, onEnd)
                "charcos" -> CharcosGame(level, avatar, running, onHud, onEnd)
                "eco" -> EcoGame(level, running, onHud, onEnd)
                "burbujas" -> BurbujasGame(level, running, onHud, onEnd)
                "escondite" -> EsconditeGame(level, avatar, running, onHud, onEnd)
            }
            if (!started) {
                Box(
                    Modifier.fillMaxSize().background(Nd.cream.copy(alpha = 0.82f)).absorbTouches().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        Modifier
                            .widthIn(max = 380.dp)
                            .fillMaxWidth()
                            .softShadow(Elevation.Lg, Nd.rLg)
                            .surface(Nd.paper, Nd.rLg)
                            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        T(mini.name, Nd.xl, align = TextAlign.Center)
                        T(mini.blurb, Nd.lead, Modifier.widthIn(max = 300.dp), align = TextAlign.Center)
                        val prize = baseReward(mini, session.difficulty, if (session.featured) FEATURED_MULT else 1.0)
                        T(
                            "Dificultad ${DIFFICULTIES.getValue(session.difficulty).name.lowercase()}: llega a ${level.target} para ganar ${fmt(prize)} monedas.",
                            Nd.lead, Modifier.widthIn(max = 300.dp), align = TextAlign.Center,
                        )
                        if (session.featured) {
                            T("Hoy es el juego del día: paga un 50 % más.", Nd.sm, Modifier.widthIn(max = 300.dp), align = TextAlign.Center)
                        }
                        for (trait in perksFor(mini.id, session.traits)) {
                            val text = PERKS.getValue(trait).text.replaceFirstChar { it.lowercase() }
                            T(
                                "Por ser ${TRAITS.getValue(trait).name.lowercase()}: $text",
                                Nd.sm, Modifier.widthIn(max = 300.dp), align = TextAlign.Center,
                            )
                        }
                        Btn(
                            if (wanted) "Cargando…" else "Empezar",
                            { if (ready) started = true else wanted = true },
                            Modifier.fillMaxWidth().padding(top = 8.dp), tone = Tone.Pink, enabled = !wanted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(icon: Ic, text: String, label: String) {
    Row(
        Modifier
            .softShadow(Elevation.Sm, Nd.rPill)
            .surface(Nd.paper, Nd.rPill)
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$label: $text" },
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, 16.dp)
        T(text, Nd.smBlack.num.copy(lineHeight = 1.em), maxLines = 1)
    }
}

// ── Utilidades de los juegos de lienzo ────────────────────────

// Llama a `step` en cada fotograma con el tiempo en segundos. El valor
// devuelto cambia cada vez, para que el lienzo se vuelva a pintar.
@Composable
private fun rememberFrames(step: (dt: Float, t: Float) -> Unit): State<Int> {
    val frame = remember { mutableIntStateOf(0) }
    val current by rememberUpdatedState(step)
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else min(0.05f, (now - last) / 1e9f)
                last = now
                current(dt, now / 1e9f)
                frame.intValue++
            }
        }
    }
    return frame
}

// Textos que suben y se desvanecen ("+1").
private class Pops {
    private class Pop(val x: Float, var y: Float, val text: String, val color: Color, var life: Float)

    private val list = ArrayList<Pop>()

    fun add(x: Float, y: Float, text: String, color: Color = Nd.ink) {
        list.add(Pop(x, y, text, color, 0.7f))
    }

    fun update(dt: Float) {
        val each = list.iterator()
        while (each.hasNext()) {
            val pop = each.next()
            pop.life -= dt
            pop.y -= 46f * dt
            if (pop.life <= 0f) each.remove()
        }
    }

    fun draw(scope: DrawScope, paint: Paint) = scope.drawIntoCanvas { canvas ->
        for (pop in list) {
            paint.color = pop.color.copy(alpha = min(1f, pop.life / 0.3f)).toArgb()
            canvas.nativeCanvas.drawText(pop.text, pop.x, pop.y, paint)
        }
    }
}

@Composable
private fun rememberPopPaint(): Paint {
    val context = LocalContext.current
    return remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = ResourcesCompat.getFont(context, R.font.zen_maru_gothic_black) ?: Typeface.DEFAULT_BOLD
            textSize = 20f
            textAlign = Paint.Align.CENTER
        }
    }
}

// Dibuja una imagen en un rectángulo dado en las unidades del lienzo.
private fun DrawScope.image(img: ImageBitmap, x: Float, y: Float, w: Float, h: Float, filter: ColorFilter? = null) {
    withTransform({
        translate(x, y)
        scale(w / img.width, h / img.height, Offset.Zero)
    }) { drawImage(img, colorFilter = filter) }
}

private fun DrawScope.oval(color: Color, cx: Float, cy: Float, rx: Float, ry: Float) {
    drawOval(color, Offset(cx - rx, cy - ry), Size(rx * 2f, ry * 2f))
}

// Lienzo que mide en dp, como los píxeles CSS de la versión web.
@Composable
private fun DpCanvas(modifier: Modifier, onSize: (Float, Float) -> Unit, draw: DrawScope.(w: Float, h: Float) -> Unit) {
    val density = LocalDensity.current.density
    Canvas(modifier.fillMaxSize().onSizeChanged { onSize(it.width / density, it.height / density) }) {
        scale(density, density, Offset.Zero) { draw(size.width / density, size.height / density) }
    }
}

private val SHADE = Color(91, 58, 41, 41)

// ── Lluvia de fruta ───────────────────────────────────────────

// Mueve a la criatura para recoger la fruta buena.
@Composable
private fun FrutaGame(level: Level, avatar: ImageBitmap?, running: Boolean, hud: (Hud) -> Unit, onEnd: (Int) -> Unit) {
    class Drop(val x: Float, var y: Float, val bad: Boolean, val index: Int, val spin: Float, var angle: Float = 0f)

    val model = remember {
        object {
            var score = 0
            var lives = level.int("lives")
            var time = level.num("seconds").toFloat()
            var playerX = 0.5f
            var targetX = 0.5f
            var spawnIn = 0.4f
            var shake = 0f
            var ended = false
            var w = 1f
            var h = 1f
            val drops = ArrayList<Drop>()
            val pops = Pops()
        }
    }
    val paint = rememberPopPaint()
    // Versión podrida: la misma silueta, teñida de verde gris.
    val rotten = remember { ColorFilter.tint(Color(96, 104, 72, 209), BlendMode.SrcAtop) }
    val density = LocalDensity.current.density

    fun paintHud() = hud(Hud(model.score, level.target, ceil(model.time).toInt(), model.lives))
    LaunchedEffect(Unit) { paintHud() }
    // Con la ventaja de las glotonas atrapa la fruta desde más lejos.
    val reach = level.numOr("basket", 1.0).toFloat()

    fun finish() {
        if (model.ended) return
        model.ended = true
        onEnd(model.score)
    }

    val frame = rememberFrames { dt, _ ->
        val m = model
        val active = running && !m.ended
        val ground = m.h - 46f
        val size = min(92f, m.w * 0.24f)
        val speed = level.num("fall").toFloat() * max(1f, m.h / 640f)
        if (active) {
            m.time -= dt
            m.spawnIn -= dt
            if (m.spawnIn <= 0f) {
                m.spawnIn = (level.num("every").toFloat() / 1000f) * rand(0.75f, 1.25f)
                val bad = Random.nextFloat() < level.num("rotten")
                m.drops.add(Drop(rand(0.08f, 0.92f), -40f, bad, Random.nextInt(FRUIT.size), rand(-1.5f, 1.5f)))
            }
            if (m.time <= 0f) {
                m.time = 0f
                finish()
            }
            paintHud()
        }
        m.playerX += (m.targetX - m.playerX) * min(1f, dt * 14f)
        m.shake = max(0f, m.shake - dt)
        val px = m.playerX * m.w + (if (m.shake > 0f) sin(m.shake * 60f) * 5f else 0f)
        val each = m.drops.iterator()
        while (each.hasNext()) {
            val drop = each.next()
            if (active) {
                drop.y += speed * dt
                drop.angle += drop.spin * dt
            }
            val dx = drop.x * m.w
            if (active && drop.y > ground - size * 0.8f && drop.y < ground - size * 0.2f && abs(dx - px) < size * 0.5f * reach) {
                each.remove()
                if (drop.bad) {
                    m.lives -= 1
                    m.shake = 0.35f
                    m.pops.add(dx, drop.y - 10f, "Puaj", Nd.healthEdge)
                    if (m.lives <= 0) finish()
                } else {
                    m.score += 1
                    m.pops.add(dx, drop.y - 10f, "+1")
                }
                paintHud()
            } else if (drop.y > m.h + 40f) {
                each.remove()
            }
        }
        m.pops.update(dt)
    }

    DpCanvas(
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                model.targetX = (down.position.x / density / model.w).coerceIn(0.06f, 0.94f)
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: break
                    if (!change.pressed) break
                    model.targetX = (change.position.x / density / model.w).coerceIn(0.06f, 0.94f)
                    change.consume()
                }
            }
        },
        onSize = { w, h ->
            model.w = w
            model.h = h
        },
    ) { w, h ->
        frame.value
        val ground = h - 46f
        val size = min(92f, w * 0.24f)
        drawRect(Nd.sky, Offset.Zero, Size(w, h))
        drawRect(Nd.cream, Offset(0f, ground), Size(w, h - ground))
        oval(Nd.pink, w / 2f, ground + 20f, w * 0.46f, 15f)
        val px = model.playerX * w + (if (model.shake > 0f) sin(model.shake * 60f) * 5f else 0f)
        for (drop in model.drops) {
            val img = Thumbs.images["item:${FRUIT[drop.index]}"] ?: continue
            withTransform({
                translate(drop.x * w, drop.y)
                rotate(drop.angle * 180f / PI.toFloat(), Offset.Zero)
            }) { image(img, -26f, -26f, 52f, 52f, if (drop.bad) rotten else null) }
        }
        oval(SHADE, px, ground + 6f, size * 0.36f * reach, 8f)
        if (avatar != null) image(avatar, px - size / 2f, ground - size * 0.92f, size, size)
        model.pops.draw(this, paint)
    }
}

// ── Salta charcos ─────────────────────────────────────────────

// La criatura corre sola; toca para saltar cada charco.
@Composable
private fun CharcosGame(level: Level, avatar: ImageBitmap?, running: Boolean, hud: (Hud) -> Unit, onEnd: (Int) -> Unit) {
    class Puddle(var x: Float, val width: Float, var passed: Boolean = false)

    val model = remember {
        object {
            var score = 0
            var y = 0f
            var vy = 0f
            var nextIn = 1.2f
            var ended = false
            var splash = 0f
            var scroll = 0f
            var w = 1f
            var h = 1f
            val puddles = ArrayList<Puddle>()
            val pops = Pops()

            // Ventajas: las fuertes aguantan un charco y las traviesas saltan más.
            var shields = level.numOr("shield", 0.0).toInt()
        }
    }
    val paint = rememberPopPaint()
    val scope = rememberCoroutineScope()
    val runningNow by rememberUpdatedState(running)
    val lift = 620f * level.numOr("jump", 1.0).toFloat()
    val guarded = level.numOr("shield", 0.0) > 0.0

    fun paintHud() = hud(Hud(model.score, level.target, lives = if (guarded) model.shields + 1 else null))
    LaunchedEffect(Unit) { paintHud() }

    fun finish() {
        if (model.ended) return
        model.ended = true
        scope.launch {
            delay(500)
            onEnd(model.score)
        }
    }

    val frame = rememberFrames { dt, _ ->
        val m = model
        val active = running && !m.ended
        val ground = (m.h * 0.8f).roundToInt().toFloat()
        val size = min(96f, m.w * 0.24f)
        val px = m.w * 0.24f
        val speed = (level.num("speed").toFloat() + m.score * level.num("grow").toFloat()) * min(1.8f, max(1f, m.w / 420f))
        if (active) {
            m.scroll += speed * dt
            m.vy -= 1750f * dt
            m.y = max(0f, m.y + m.vy * dt)
            if (m.y == 0f) m.vy = 0f
            m.nextIn -= dt
            if (m.nextIn <= 0f) {
                m.nextIn = rand(level.num("gapMin").toFloat(), level.num("gapMax").toFloat())
                m.puddles.add(Puddle(m.w + 60f, rand(54f, 78f) + min(34f, m.score * 1.4f)))
            }
            val each = m.puddles.iterator()
            while (each.hasNext()) {
                val puddle = each.next()
                puddle.x -= speed * dt
                val left = puddle.x - puddle.width / 2f
                val right = puddle.x + puddle.width / 2f
                if (!puddle.passed && m.y < 8f && px + size * 0.16f > left && px - size * 0.16f < right) {
                    m.splash = 0.5f
                    if (m.shields > 0) {
                        // Este charco ya no cuenta ni moja otra vez.
                        m.shields -= 1
                        puddle.passed = true
                        m.pops.add(px, ground - size, "¡Uf!", Nd.energyEdge)
                        paintHud()
                    } else {
                        m.pops.add(px, ground - size, "¡Chof!", Nd.energyEdge)
                        finish()
                    }
                } else if (!puddle.passed && right < px - size * 0.2f) {
                    puddle.passed = true
                    m.score += 1
                    m.pops.add(px, ground - size - m.y - 8f, "+1")
                    paintHud()
                    if (m.score >= level.target) finish()
                }
                if (right < -80f) each.remove()
            }
        }
        m.splash = max(0f, m.splash - dt)
        m.pops.update(dt)
    }

    // Posición que da la vuelta al salirse por la izquierda.
    fun wrap(value: Float, span: Float) = ((value % span) + span) % span

    DpCanvas(
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown().consume()
                if (runningNow && !model.ended && model.y == 0f) model.vy = lift
            }
        },
        onSize = { w, h ->
            model.w = w
            model.h = h
        },
    ) { w, h ->
        frame.value
        val m = model
        val ground = (h * 0.8f).roundToInt().toFloat()
        val size = min(96f, w * 0.24f)
        val px = w * 0.24f
        drawRect(Nd.sky, Offset.Zero, Size(w, h))
        for (i in 0 until 4) {
            val cx = wrap(i * 260f - m.scroll * 0.18f, w + 260f) - 130f
            val cy = 50f + (i % 2) * 46f
            oval(Nd.paper, cx, cy, 44f, 16f)
            oval(Nd.paper, cx + 26f, cy - 10f, 30f, 16f)
        }
        for (i in 0 until 4) {
            val hx = wrap(i * 300f - m.scroll * 0.4f, w + 300f) - 150f
            val ry = 70f + (i % 2) * 26f
            drawArc(Nd.mint, 180f, 180f, false, Offset(hx - 170f, ground + 10f - ry), Size(340f, ry * 2f))
        }
        drawRect(Nd.cream, Offset(0f, ground), Size(w, h - ground))
        for (i in 0 until ceil(w / 90f).toInt() + 2) {
            val sx = wrap(i * 90f - m.scroll, w + 180f) - 90f
            drawRect(Nd.track, Offset(sx, ground + 26f), Size(40f, 6f))
        }
        for (puddle in m.puddles) {
            oval(Nd.energyEdge, puddle.x, ground + 5f, puddle.width / 2f, 9f)
            oval(Nd.energy, puddle.x, ground + 3f, puddle.width / 2f - 4f, 6f)
        }
        oval(SHADE, px, ground + 6f, size * 0.34f * (1f - min(0.5f, m.y / 300f)), 7f)
        if (avatar != null) {
            val stretch = if (m.y > 0f) 1.06f else 1f + sin(m.scroll * 0.06f) * 0.03f
            image(avatar, px - (size / stretch) / 2f, ground - size * 0.92f * stretch - m.y, size / stretch, size * stretch)
        }
        if (m.splash > 0f) {
            for (i in 0 until 6) {
                val a = PI.toFloat() * (0.15f + i * 0.14f)
                val d = (0.5f - m.splash) * 150f
                drawCircle(Nd.energy, 6f * m.splash * 2f, Offset(px + cos(a) * d, ground - sin(a) * d * 0.9f))
            }
        }
        m.pops.draw(this, paint)
    }
}

// ── Explota burbujas ──────────────────────────────────────────

private val BUBBLE_COLORS = listOf(
    Nd.pink to Nd.pinkEdge, Nd.mint to Nd.mintEdge, Nd.sun to Nd.sunEdge, Nd.energy to Nd.energyEdge, Nd.health to Nd.healthEdge,
)

// Toca las burbujas de color antes de que se vayan.
@Composable
private fun BurbujasGame(level: Level, running: Boolean, hud: (Hud) -> Unit, onEnd: (Int) -> Unit) {
    class Bubble(val x: Float, val y: Float, val r: Float, val grey: Boolean, val color: Color, val edge: Color, val life: Float, val phase: Float, var age: Float = 0f)
    class Burst(val x: Float, val y: Float, val r: Float, val color: Color, var life: Float)

    val model = remember {
        object {
            var score = 0
            var time = level.num("seconds").toFloat()
            var spawnIn = 0.3f
            var ended = false
            var t = 0f
            var w = 1f
            var h = 1f
            val bubbles = ArrayList<Bubble>()
            val bursts = ArrayList<Burst>()
            val pops = Pops()
        }
    }
    val paint = rememberPopPaint()
    val density = LocalDensity.current.density
    val runningNow by rememberUpdatedState(running)

    fun paintHud() = hud(Hud(model.score, level.target, ceil(model.time).toInt()))
    LaunchedEffect(Unit) { paintHud() }

    fun finish() {
        if (model.ended) return
        model.ended = true
        onEnd(model.score)
    }

    val frame = rememberFrames { dt, t ->
        val m = model
        m.t = t
        val active = running && !m.ended
        if (active) {
            m.time -= dt
            m.spawnIn -= dt
            if (m.spawnIn <= 0f) {
                m.spawnIn = (level.num("every").toFloat() / 1000f) * rand(0.8f, 1.2f)
                val r = rand(30f, 42f)
                for (tries in 0 until 12) {
                    val x = rand(r + 10f, m.w - r - 10f)
                    val y = rand(r + 10f, m.h - r - 24f)
                    if (m.bubbles.any { hypot(it.x - x, it.y - y) <= it.r + r + 6f }) continue
                    val grey = Random.nextFloat() < level.num("grey")
                    val (color, edge) = if (grey) Nd.grey to Nd.greyEdge else BUBBLE_COLORS[Random.nextInt(BUBBLE_COLORS.size)]
                    val life = (level.num("life").toFloat() / 1000f) * (if (grey) 1.2f else 1f)
                    m.bubbles.add(Bubble(x, y, r, grey, color, edge, life, rand(0f, 6f)))
                    break
                }
            }
            if (m.time <= 0f) {
                m.time = 0f
                finish()
            }
            paintHud()
            for (bubble in m.bubbles) bubble.age += dt
        }
        m.bubbles.removeAll { it.age >= it.life }
        for (burst in m.bursts) burst.life -= dt
        m.bursts.removeAll { it.life <= 0f }
        m.pops.update(dt)
    }

    DpCanvas(
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                down.consume()
                if (!runningNow || model.ended) return@awaitEachGesture
                val x = down.position.x / density
                val y = down.position.y / density
                for (i in model.bubbles.indices.reversed()) {
                    val bubble = model.bubbles[i]
                    if (hypot(x - bubble.x, y - bubble.y) > bubble.r + 8f) continue
                    model.bubbles.removeAt(i)
                    model.bursts.add(Burst(bubble.x, bubble.y, bubble.r, bubble.color, 0.25f))
                    if (bubble.grey) {
                        model.score = max(0, model.score - 2)
                        model.pops.add(bubble.x, bubble.y - bubble.r, "−2", Nd.healthEdge)
                    } else {
                        model.score += 1
                        model.pops.add(bubble.x, bubble.y - bubble.r, "+1")
                    }
                    paintHud()
                    break
                }
            }
        },
        onSize = { w, h ->
            model.w = w
            model.h = h
        },
    ) { w, h ->
        frame.value
        drawRect(Nd.sky, Offset.Zero, Size(w, h))
        for (bubble in model.bubbles) {
            val grow = min(1f, bubble.age / 0.14f)
            val fade = min(1f, (bubble.life - bubble.age) / 0.25f).coerceAtLeast(0f)
            val r = bubble.r * (0.6f + 0.4f * grow) * (0.85f + 0.15f * fade)
            val y = bubble.y + sin(model.t * 2f + bubble.phase) * 4f
            drawCircle(bubble.edge, r, Offset(bubble.x, y + 3f), alpha = fade)
            drawCircle(bubble.color, r, Offset(bubble.x, y), alpha = fade)
            val glint = Offset(bubble.x - r * 0.34f, y - r * 0.38f)
            rotate(-0.6f * 180f / PI.toFloat(), glint) {
                drawOval(Color.White, Offset(glint.x - r * 0.2f, glint.y - r * 0.12f), Size(r * 0.4f, r * 0.24f), alpha = 0.7f * fade)
            }
            // Arco que se consume: cuánto le queda.
            drawArc(
                Nd.paper, -90f, 360f * (1f - bubble.age / bubble.life), false,
                Offset(bubble.x - r - 6f, y - r - 6f), Size((r + 6f) * 2f, (r + 6f) * 2f),
                alpha = fade, style = Stroke(4f, cap = StrokeCap.Round),
            )
            if (bubble.grey) {
                val k = r * 0.32f
                drawLine(Nd.paper, Offset(bubble.x - k, y - k), Offset(bubble.x + k, y + k), 5f, StrokeCap.Round, alpha = fade)
                drawLine(Nd.paper, Offset(bubble.x + k, y - k), Offset(bubble.x - k, y + k), 5f, StrokeCap.Round, alpha = fade)
            }
        }
        for (burst in model.bursts) {
            val k = 1f - burst.life / 0.25f
            drawCircle(burst.color, burst.r * (1f + k * 0.6f), Offset(burst.x, burst.y), alpha = (1f - k).coerceIn(0f, 1f), style = Stroke(5f))
        }
        model.pops.draw(this, paint)
    }
}

// ── Al escondite ──────────────────────────────────────────────

private fun easeInOut(t: Float) = if (t < 0.5f) 2f * t * t else 1f - (-2f * t + 2f).let { it * it } / 2f
private fun easeOut(t: Float) = 1f - (1f - t).let { it * it * it }
private fun clamp01(t: Float) = t.coerceIn(0f, 1f)

// La criatura se mete bajo un cojín, los cojines se mezclan y hay que
// tocar el suyo. Solo lo juegan las criaturas juguetonas.
@Composable
private fun EsconditeGame(level: Level, avatar: ImageBitmap?, running: Boolean, hud: (Hud) -> Unit, onEnd: (Int) -> Unit) {
    val n = level.int("cushions")
    val maxLives = level.int("lives")

    val model = remember {
        object {
            var score = 0
            var lives = maxLives
            var round = 0
            var ended = false
            // `slot[c]` es dónde está el cojín c (con decimales mientras se mueve).
            val slot = FloatArray(n) { it.toFloat() }
            val arc = FloatArray(n)
            val lift = FloatArray(n)
            var hidden = 0
            var peek = 0f
            var phase = "idle"
            var clock = 0f
            var swaps: List<Pair<Int, Int>> = emptyList()
            var swapIndex = 0
            var swapTime = 0.5f

            // Los dos cojines del cambio en curso: se fijan al empezarlo,
            // porque a mitad de camino ya no se sabe por su sitio cuál es cuál.
            var moving: Pair<Int, Int>? = null
            var picked = -1
            var message = "Mira dónde se esconde"
            var w = 1f
            var h = 1f
            val pops = Pops()
        }
    }
    val paint = rememberPopPaint()
    val messagePaint = rememberPopPaint()
    val scope = rememberCoroutineScope()
    val runningNow by rememberUpdatedState(running)
    val density = LocalDensity.current.density

    fun paintHud() = hud(Hud(model.score, level.target, lives = if (maxLives > 1) model.lives else null))
    LaunchedEffect(Unit) { paintHud() }

    fun finish() {
        if (model.ended) return
        model.ended = true
        scope.launch {
            delay(600)
            onEnd(model.score)
        }
    }

    // Cada ronda se esconde en uno al azar y hay un cambio más, algo más rápido.
    fun startRound() {
        val m = model
        m.hidden = Random.nextInt(n)
        m.lift.fill(0f)
        m.peek = 0f
        m.picked = -1
        val base = level.num("swapMs").toFloat()
        m.swapTime = max(base * 0.6f, base * 0.94f.pow(m.round)) / 1000f
        m.swaps = List(level.int("swaps") + m.round) {
            val a = Random.nextInt(n)
            var b = Random.nextInt(n - 1)
            if (b >= a) b += 1
            a to b
        }
        m.swapIndex = 0
        m.moving = null
        m.phase = "show"
        m.clock = 0f
        m.message = "Mira dónde se esconde"
    }

    fun cushionAt(place: Int) = model.slot.indexOfFirst { it.roundToInt() == place }

    // Medidas que dependen del tamaño del lienzo.
    class Layout(val cw: Float, val ch: Float, val ground: Float, val size: Float, val spacing: Float, val w: Float) {
        fun xOf(place: Float) = w / 2f + (place - (n - 1) / 2f) * spacing
    }
    fun layout(w: Float, h: Float): Layout {
        val spacing = min(150f, (w - 24f) / n)
        val cw = spacing * 0.86f
        return Layout(cw, cw * 0.6f, (h * 0.64f).roundToInt().toFloat(), min(160f, cw * 1.35f), spacing, w)
    }

    fun pick(cushion: Int) {
        val m = model
        if (!runningNow || m.ended || m.phase != "pick" || cushion < 0) return
        m.picked = cushion
        m.phase = "reveal"
        m.clock = 0f
        if (cushion == m.hidden) {
            m.score += 1
            m.message = "¡La has encontrado!"
        } else {
            m.lives -= 1
            m.message = "Ahí no estaba"
        }
        paintHud()
    }

    LaunchedEffect(running) { if (running && model.phase == "idle") startRound() }

    val frame = rememberFrames { dt, _ ->
        val m = model
        if (running && !m.ended) {
            m.clock += dt
            when (m.phase) {
                "show" -> {
                    m.peek = when {
                        m.clock < 0.45f -> easeOut(m.clock / 0.45f)
                        m.clock < 1.1f -> 1f
                        else -> 1f - clamp01((m.clock - 1.1f) / 0.3f)
                    }
                    if (m.clock >= 1.65f) {
                        m.phase = "shuffle"
                        m.clock = 0f
                        m.message = "¡Que no se te escape!"
                    }
                }
                "shuffle" -> {
                    val (a, b) = m.swaps[m.swapIndex]
                    val pair = m.moving ?: (cushionAt(a) to cushionAt(b)).also { m.moving = it }
                    val (ca, cb) = pair
                    val t = clamp01(m.clock / m.swapTime)
                    val k = easeInOut(t)
                    m.slot[ca] = a + (b - a) * k
                    m.slot[cb] = b + (a - b) * k
                    m.arc[ca] = sin(PI.toFloat() * t)
                    m.arc[cb] = -sin(PI.toFloat() * t) * 0.35f
                    if (t >= 1f) {
                        m.slot[ca] = b.toFloat()
                        m.slot[cb] = a.toFloat()
                        m.arc[ca] = 0f
                        m.arc[cb] = 0f
                        m.moving = null
                        m.swapIndex += 1
                        m.clock = 0f
                        if (m.swapIndex >= m.swaps.size) {
                            m.phase = "pick"
                            m.message = "¿Dónde está?"
                        }
                    }
                }
                "reveal" -> {
                    val found = m.picked == m.hidden
                    m.lift[m.picked] = easeOut(clamp01(m.clock / 0.25f))
                    if (!found) m.lift[m.hidden] = easeOut(clamp01((m.clock - 0.45f) / 0.25f))
                    if (found && m.clock - dt < 0.2f && m.clock >= 0.2f) {
                        val l = layout(m.w, m.h)
                        m.pops.add(l.xOf(m.slot[m.hidden]), l.ground - l.ch - l.size - 14f, "+1")
                    }
                    if (m.clock >= if (found) 0.95f else 1.5f) {
                        if (m.score >= level.target || m.lives <= 0) {
                            m.phase = "done"
                            finish()
                        } else {
                            m.round += 1
                            startRound()
                        }
                    }
                }
            }
        }
        m.pops.update(dt)
    }

    DpCanvas(
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                down.consume()
                val x = down.position.x / density
                val y = down.position.y / density
                val l = layout(model.w, model.h)
                if (y < l.ground - l.ch * 2.2f || y > l.ground + l.ch) return@awaitEachGesture
                var best = -1
                var gap = l.cw * 0.62f
                for (c in 0 until n) {
                    val d = abs(x - l.xOf(model.slot[c]))
                    if (d < gap) {
                        gap = d
                        best = c
                    }
                }
                pick(best)
            }
        },
        onSize = { w, h ->
            model.w = w
            model.h = h
        },
    ) { w, h ->
        frame.value
        val m = model
        val l = layout(w, h)
        drawRect(Nd.sky, Offset.Zero, Size(w, h))
        val floor = (h * 0.46f).roundToInt().toFloat()
        drawRect(Nd.cream, Offset(0f, floor), Size(w, h - floor))
        drawRect(Nd.track, Offset(0f, floor), Size(w, 6f))
        oval(Nd.mint, w / 2f, l.ground + 4f, min(w * 0.48f, n * l.cw * 0.72f), 26f)
        if (m.phase != "idle") {
            messagePaint.color = Nd.ink.toArgb()
            drawIntoCanvas { it.nativeCanvas.drawText(m.message, w / 2f, max(36f, floor * 0.45f), messagePaint) }
        }

        // Primero las sombras, luego la criatura (que asoma por detrás de
        // su cojín) y al final los cojines, los que van por delante los últimos.
        for (c in 0 until n) oval(SHADE, l.xOf(m.slot[c]), l.ground + 4f, l.cw * 0.46f * (1f - m.lift[c] * 0.3f), 8f)
        // Escondida, solo asoma por encima de la mitad de su cojín; con el
        // cojín levantado se la ve entera, sentada en el suelo.
        val out = m.lift[m.hidden] > 0.02f
        if (avatar != null && (out || m.peek > 0f)) {
            val x = l.xOf(m.slot[m.hidden])
            val edge = if (out) l.ground + 2f else l.ground - l.ch * 0.5f
            val top = if (out) l.ground - l.size * 0.9f else edge - l.size * 0.92f * m.peek
            clipRect(0f, 0f, w, edge) { image(avatar, x - l.size / 2f, top, l.size, l.size) }
        }
        for (c in (0 until n).sortedByDescending { m.arc[it] }) {
            val x = l.xOf(m.slot[c])
            val top = l.ground - l.ch - m.arc[c] * l.ch * 0.9f - m.lift[c] * l.size
            val corner = CornerRadius(l.ch * 0.46f)
            drawRoundRect(Nd.pinkEdge, Offset(x - l.cw / 2f, top + 5f), Size(l.cw, l.ch), corner)
            drawRoundRect(Nd.pink, Offset(x - l.cw / 2f, top), Size(l.cw, l.ch), corner)
            oval(Nd.paper.copy(alpha = 0.35f), x - l.cw * 0.12f, top + l.ch * 0.3f, l.cw * 0.24f, l.ch * 0.12f)
            drawCircle(Nd.pinkEdge, l.ch * 0.08f, Offset(x, top + l.ch * 0.52f))
        }
        m.pops.draw(this, paint)
    }
}

// ── Parejas ───────────────────────────────────────────────────

// Destapa dos cartas iguales hasta encontrarlas todas.
@Composable
private fun ParejasGame(level: Level, running: Boolean, safeBottom: Dp, hud: (Hud) -> Unit, onEnd: (Int) -> Unit) {
    val pairs = level.int("pairs")
    val cols = level.int("cols")
    val deck = remember {
        val faces = FACES.shuffled().take(pairs)
        (faces + faces).shuffled()
    }
    val open = remember { mutableStateListOf(*Array(deck.size) { false }) }
    val done = remember { mutableStateListOf(*Array(deck.size) { false }) }
    var found by remember { mutableIntStateOf(0) }
    var time by remember { mutableFloatStateOf(level.num("seconds").toFloat()) }
    var locked by remember { mutableStateOf(false) }
    var ended by remember { mutableStateOf(false) }
    val picked = remember { ArrayList<Int>() }
    val scope = rememberCoroutineScope()

    fun paintHud() = hud(Hud(found, pairs, ceil(time).toInt()))
    LaunchedEffect(Unit) { paintHud() }

    fun finish() {
        if (ended) return
        ended = true
        scope.launch {
            delay(450)
            onEnd(found)
        }
    }

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        // Las curiosas ven todas las cartas un momento antes de empezar.
        val peek = level.numOr("peek", 0.0)
        if (peek > 0.0) {
            locked = true
            for (i in deck.indices) open[i] = true
            delay((peek * 1000).toLong())
            for (i in deck.indices) open[i] = false
            locked = false
        }
        while (!ended) {
            delay(250)
            time -= 0.25f
            if (time <= 0f) {
                time = 0f
                finish()
            }
            paintHud()
        }
    }

    fun tap(index: Int) {
        if (!running || ended || locked || open[index]) return
        open[index] = true
        picked.add(index)
        if (picked.size < 2) return
        val a = picked[0]
        val b = picked[1]
        picked.clear()
        if (deck[a] == deck[b]) {
            done[a] = true
            done[b] = true
            found += 1
            paintHud()
            if (found >= pairs) finish()
            return
        }
        locked = true
        scope.launch {
            delay(750)
            open[a] = false
            open[b] = false
            locked = false
        }
    }

    // Las cartas llenan el hueco sin deformarse ni salirse.
    BoxWithConstraints(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = safeBottom + 16.dp), contentAlignment = Alignment.Center) {
        val gap = 8.dp
        val rows = (deck.size + cols - 1) / cols
        val byHeight = ((maxHeight - 16.dp - gap * (rows - 1)) / rows) * 0.75f
        val byWidth = (maxWidth - gap * (cols - 1)) / cols
        val cardW = floor(max(40f, minOf(byHeight.value, byWidth.value, 120f))).dp
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            for (row in 0 until rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (c in 0 until cols) {
                        val index = row * cols + c
                        if (index < deck.size) PairCard(deck[index], open[index], done[index], cardW) { tap(index) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PairCard(face: String, open: Boolean, done: Boolean, width: Dp, onClick: () -> Unit) {
    val angle by animateFloatAsState(if (open) 180f else 0f, tween(Nd.MEDIUM, easing = Nd.ease), label = "carta")
    val front = angle > 90f
    Box(
        Modifier
            .size(width, width * 4f / 3f)
            .graphicsLayer {
                rotationY = angle
                cameraDistance = 600f * density / 72f
            }
            .softShadow(Elevation.Sm, Nd.rMd)
            .then(if (front) Modifier.surface(if (done) Nd.mintWash else Nd.paper, Nd.rMd) else Modifier.candy(Nd.pink, Nd.pinkEdge, 4.dp, Nd.rMd))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = !done, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (open) "Carta destapada" else "Carta tapada" },
        contentAlignment = Alignment.Center,
    ) {
        if (front) Thumb(ThumbSpec.Item(face), Modifier.size(width * 0.78f).graphicsLayer { rotationY = 180f })
    }
}

// ── Eco ───────────────────────────────────────────────────────

private class Pad(val color: Color, val edge: Color, val name: String)

private val PADS = listOf(
    Pad(Nd.pink, Nd.pinkEdge, "Rosa"), Pad(Nd.mint, Nd.mintEdge, "Verde"),
    Pad(Nd.sun, Nd.sunEdge, "Amarillo"), Pad(Nd.energy, Nd.energyEdge, "Azul"),
)

// Mira la secuencia de luces y repítela. Cada ronda añade una.
@Composable
private fun EcoGame(level: Level, running: Boolean, hud: (Hud) -> Unit, onEnd: (Int) -> Unit) {
    val sequence = remember { ArrayList<Int>() }
    val lit = remember { mutableStateListOf(false, false, false, false) }
    var status by remember { mutableStateOf("Mira bien") }
    var enabled by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val model = remember {
        object {
            var step = 0
            var rounds = 0
            var listening = false
            var ended = false
        }
    }
    val beat = level.num("beat")

    LaunchedEffect(Unit) { hud(Hud(0, level.target)) }

    suspend fun flash(index: Int, ms: Long) {
        lit[index] = true
        delay(ms)
        lit[index] = false
    }

    suspend fun playRound() {
        model.listening = false
        enabled = false
        status = "Mira bien"
        sequence.add(Random.nextInt(4))
        delay(550)
        for (index in sequence) {
            flash(index, (beat * 0.62).toLong())
            delay((beat * 0.38).toLong())
        }
        model.step = 0
        model.listening = true
        enabled = true
        status = "Te toca"
    }

    fun finish() {
        if (model.ended) return
        model.ended = true
        model.listening = false
        enabled = false
        scope.launch {
            delay(600)
            onEnd(model.rounds)
        }
    }

    fun press(index: Int) {
        if (!model.listening) return
        scope.launch { flash(index, 160) }
        if (index != sequence[model.step]) {
            status = "Esa no era"
            finish()
            return
        }
        model.step += 1
        if (model.step < sequence.size) return
        model.rounds += 1
        hud(Hud(model.rounds, level.target))
        if (model.rounds >= level.target) {
            status = "¡Secuencia completa!"
            finish()
            return
        }
        status = "¡Bien!"
        model.listening = false
        enabled = false
        scope.launch {
            delay(500)
            playRound()
        }
    }

    LaunchedEffect(running) { if (running) playRound() }

    BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth * 0.4f, 190.dp)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            T(status, Nd.lg.copy(lineHeight = 1.45.em), Modifier.heightIn(min = 30.dp), align = TextAlign.Center)
            for (row in 0 until 2) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    for (c in 0 until 2) {
                        val index = row * 2 + c
                        EcoPad(PADS[index], lit[index], enabled, side) { press(index) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EcoPad(pad: Pad, lit: Boolean, enabled: Boolean, side: Dp, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val held = pressed && enabled
    val glow by animateFloatAsState(if (lit || held) 1f else 0.55f, tween(80, easing = LinearEasing), label = "luz")
    val size by animateFloatAsState(if (held) 0.96f else if (lit) 1.04f else 1f, tween(Nd.QUICK, easing = Nd.ease), label = "tecla")
    Box(
        Modifier
            .size(side)
            .graphicsLayer {
                alpha = glow
                scaleX = size
                scaleY = size
            }
            .softShadow(Elevation.Sm, Nd.rLg)
            .candy(pad.color, pad.edge, 8.dp, Nd.rLg)
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = pad.name },
    )
}
