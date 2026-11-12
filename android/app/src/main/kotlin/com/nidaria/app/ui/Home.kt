// Pantalla principal: monedas y tienda arriba, la criatura en el centro
// (la dibuja el motor 3D) y, abajo, sus necesidades y las acciones.

package com.nidaria.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.nidaria.app.Game
import com.nidaria.app.Sheet
import com.nidaria.app.rules.CRITICAL
import com.nidaria.app.rules.Creature
import com.nidaria.app.rules.NEED_NAMES
import com.nidaria.app.rules.STAGE_INFO
import com.nidaria.app.rules.moodOf
import com.nidaria.app.rules.nextStageAt
import com.nidaria.app.rules.stageDuration
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private class NeedLook(val id: String, val icon: Ic, val color: Color, val edge: Color)

private val NEED_LOOKS = listOf(
    NeedLook("hunger", Ic.Food, Nd.hunger, Nd.hungerEdge),
    NeedLook("energy", Ic.Moon, Nd.energy, Nd.energyEdge),
    NeedLook("hygiene", Ic.Drop, Nd.hygiene, Nd.hygieneEdge),
    NeedLook("fun", Ic.Ball, Nd.funny, Nd.funnyEdge),
    NeedLook("health", Ic.Heart, Nd.health, Nd.healthEdge),
)

private val MOOD_TEXT = mapOf(
    "feliz" to "feliz", "bien" to "contenta", "regular" to "regular", "mal" to "lo pasa mal", "fatal" to "muy mal",
    "enferma" to "enferma", "dormida" to "dormida", "huevo" to "incubando", "muerta" to "",
)
private const val DAY_MS = 24.0 * 60 * 60 * 1000

// Entrada escalonada: cada pieza sube y aparece un poco después de la anterior.
private fun Modifier.enter(clock: State<Float>, delay: Float, from: Dp, seconds: Float): Modifier = graphicsLayer {
    val p = ((clock.value - delay) / seconds).coerceIn(0f, 1f)
    val k = 1f - (1f - p).pow(4)
    alpha = k
    translationY = (1f - k) * from.toPx()
}

@Composable
fun HomeScreen(game: Game, insets: Insets) {
    val state = game.store.state ?: return
    val live = game.live ?: return
    val room = LocalRoom.current
    val density = LocalDensity.current
    // Leer el reloj hace que los textos con tiempo se repinten cada segundo.
    val tick = game.clock

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            game.tick()
        }
    }

    val enter = remember { Animatable(if (game.homeArriving) 0f else 2f) }
    LaunchedEffect(Unit) { if (enter.value < 2f) enter.animateTo(2f, tween(2000, easing = LinearEasing)) }
    val enterClock = enter.asState()

    val now = remember(tick, state) { game.store.now() }
    val scale = state.timeScale
    val egg = live.stage == "huevo"
    val mood = moodOf(live)
    val income = remember(tick, state) { game.store.income() }

    var plateBottom by remember { mutableFloatStateOf(0f) }
    var dockTop by remember { mutableFloatStateOf(0f) }
    val sheetTop = game.sheetTop
    // Con una hoja abierta la criatura sube al hueco que queda encima. Si
    // la hoja es tan alta que no deja hueco, el encuadre no se toca.
    LaunchedEffect(plateBottom, dockTop, sheetTop) {
        if (dockTop <= 0f) return@LaunchedEffect
        val fits = sheetTop != null && sheetTop - plateBottom >= 200f
        val bottom = if (fits) min(dockTop, sheetTop!!) else dockTop
        room.setFocus(plateBottom + 6f, bottom - 8f)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 700.dp
        val screenWidth = maxWidth

        // ── Cabecera ──
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = insets.top + 12.dp).enter(enterClock, 0f, (-12).dp, 0.45f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            HudLeft(game, state.user.coins, income, Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Btn({ game.openSheet(Sheet.Shop()) }, tone = Tone.Pink) {
                    Icon(Ic.Bag, 20.dp)
                    BtnText("Tienda")
                }
                if (!egg) {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SideButton(Ic.Hanger, "Armario") { game.openSheet(Sheet.Wardrobe) }
                        SideButton(Ic.Tree, "Criadex") { game.openSheet(Sheet.Criadex) }
                    }
                }
            }
        }

        // ── Placa con el nombre ──
        val sub = if (egg) {
            val left = max(0.0, (nextStageAt(live, scale) ?: now) - now)
            if (left > 0) "Huevo · nace en ${duration(left)}" else "Huevo · a punto de nacer"
        } else {
            val day = floor(((now - live.bornAt) * scale) / DAY_MS).toInt() + 1
            "${STAGE_INFO.getValue(live.stage).name} · día $day · ${MOOD_TEXT[mood] ?: ""}"
        }
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = insets.top + (if (compact) 68.dp else 76.dp))
                .widthIn(max = (screenWidth - 180.dp).coerceAtLeast(120.dp))
                .onGloballyPositioned { plateBottom = with(density) { (it.positionInRoot().y + it.size.height).toDp().value } }
                .enter(enterClock, 0.06f, (-12).dp, 0.45f)
                .pressable({ game.openSheet(Sheet.Profile) }, label = "Ficha de la criatura")
                .softShadow(Elevation.Sm, Nd.rMd)
                .surface(Nd.paper, Nd.rMd)
                .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            T(live.name, Nd.lg, align = TextAlign.Center)
            T(sub, Nd.sm.copy(lineHeight = 1.15.em), color = Nd.inkSoft, align = TextAlign.Center)
        }

        // ── Bocadillo ──
        Bubble(game, plateBottom)

        // ── Muelle ──
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = insets.bottom + 12.dp)
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .onGloballyPositioned { dockTop = with(density) { it.positionInRoot().y.toDp().value } },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (egg) {
                Incubator(live, now, scale, Modifier.enter(enterClock, 0f, 18.dp, 0.5f))
            } else {
                Needs(live, compact, Modifier.enter(enterClock, 0f, 18.dp, 0.5f))
                Acts(game, live, compact, enterClock)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HudLeft(game: Game, coins: Int, income: Int, modifier: Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier
                .softShadow(Elevation.Sm, Nd.rPill)
                .surface(Nd.paper, Nd.rPill)
                .defaultMinSize(minHeight = 48.dp)
                .padding(start = 8.dp, end = 18.dp, top = 6.dp, bottom = 6.dp)
                .semantics(mergeDescendants = true) { contentDescription = "Monedas: ${fmt(coins)}" },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Coin(32.dp)
            SubtleNumber(coins, Nd.lg.copy(lineHeight = 1.em))
        }
        if (income >= 1) {
            val source = remember { MutableInteractionSource() }
            val press = rememberPress(source)
            Row(
                Modifier
                    .graphicsLayer {
                        val s = 1f - 0.04f * press.value
                        scaleX = s
                        scaleY = s
                        translationY = 2.dp.toPx() * press.value
                    }
                    .softShadow(Elevation.Sm, Nd.rPill)
                    .candyPress(Nd.sun, Nd.sunEdge, Nd.rPill, 3f, press)
                    .clickable(interactionSource = source, indication = null, role = Role.Button) { game.collect() }
                    .defaultMinSize(minHeight = 48.dp)
                    .padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 9.dp)
                    .semantics(mergeDescendants = true) { contentDescription = "Recoger ${fmt(income)} monedas" },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Coin(20.dp, Nd.paper)
                T("+", Nd.smBlack)
                T(fmt(income), Nd.smBlack.num)
            }
        }
    }
}

@Composable
private fun SideButton(icon: Ic, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .width(60.dp)
            .pressable(onClick)
            .softShadow(Elevation.Sm, Nd.rMd)
            .surface(Nd.paper, Nd.rMd)
            .padding(top = 8.dp, bottom = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(icon, 24.dp)
        T(label, Nd.xs.copy(lineHeight = 1.1.em), maxLines = 1)
    }
}

// Lo que dice la criatura. Sigue a su cabeza mientras está abierto.
@Composable
private fun Bubble(game: Game, plateBottom: Float) {
    val room = LocalRoom.current
    val open = game.bubbleOpen
    val shown by animateFloatAsState(if (open) 1f else 0f, tween(Nd.FAST, easing = Nd.ease), label = "bocadillo")
    var head by remember { mutableStateOf(Offset(room.headX, room.headY)) }
    LaunchedEffect(open) {
        if (!open) return@LaunchedEffect
        while (true) withFrameNanos { head = Offset(room.headX, room.headY) }
    }
    if (shown <= 0.001f && !open) return
    Box(
        Modifier
            .layout { measurable, constraints ->
                val limit = min(240.dp.roundToPx(), (constraints.maxWidth * 0.7f).roundToInt())
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0, maxWidth = limit))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    val half = placeable.width / 2f + 12.dp.toPx()
                    val x = (head.x.dp.toPx()).coerceIn(half, max(half, constraints.maxWidth - half))
                    val y = max(head.y.dp.toPx(), plateBottom.dp.toPx() + placeable.height + 8.dp.toPx())
                    placeable.place((x - placeable.width / 2f).roundToInt(), (y - placeable.height).roundToInt())
                }
            }
            .graphicsLayer {
                alpha = shown
                val s = 0.97f + 0.03f * shown
                scaleX = s
                scaleY = s
                translationY = (1f - shown) * 6.dp.toPx()
            }
            .softShadow(Elevation.Sm, Nd.rMd, shown)
            .drawBehind {
                // El pico: un cuadrado girado que asoma por abajo.
                val side = 14.dp.toPx()
                val center = Offset(size.width / 2f, size.height - 1.dp.toPx())
                rotate(45f, center) { drawRect(Nd.paper, Offset(center.x - side / 2f, center.y - side / 2f), Size(side, side)) }
            }
            .surface(Nd.paper, Nd.rMd)
            .padding(start = 15.dp, end = 15.dp, top = 9.dp, bottom = 10.dp),
    ) { T(game.bubbleText, Nd.sm, align = TextAlign.Center) }
}

@Composable
private fun NeedRow(look: NeedLook, value: Int, compact: Boolean, modifier: Modifier = Modifier) {
    val name = NEED_NAMES.getValue(look.id)
    val critical = value < CRITICAL
    Row(
        modifier.semantics(mergeDescendants = true) {
            contentDescription = if (critical) "$name: $value de 100, crítico" else "$name: $value de 100"
        },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(if (compact) 20.dp else 22.dp)) { Icon(look.icon, 20.dp, look.edge) }
        if (!compact) T(name, Nd.sm.copy(lineHeight = 1.em), Modifier.width(76.dp), color = if (critical) Nd.danger else Nd.ink, maxLines = 1)
        Bar(value, look.color, look.edge, Modifier.weight(1f), critical)
    }
}

@Composable
private fun Needs(live: Creature, compact: Boolean, modifier: Modifier) {
    val card = modifier.fillMaxWidth().softShadow(Elevation.Md, Nd.rLg).surface(Nd.paper, Nd.rLg)
    if (compact) {
        // En pantallas bajas: dos columnas sin nombres y la salud a lo ancho.
        Column(card.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            for (pair in NEED_LOOKS.take(4).chunked(2)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    for (look in pair) NeedRow(look, live.need(look.id).roundToInt(), true, Modifier.weight(1f))
                }
            }
            NeedRow(NEED_LOOKS[4], live.health.roundToInt(), true)
        }
    } else {
        Column(card.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            for (look in NEED_LOOKS) NeedRow(look, live.need(look.id).roundToInt(), false)
        }
    }
}

@Composable
private fun Incubator(live: Creature, now: Double, scale: Double, modifier: Modifier) {
    val left = max(0.0, (nextStageAt(live, scale) ?: now) - now)
    val done = 1.0 - left / (stageDuration("huevo", scale) ?: 1.0)
    Column(
        modifier.fillMaxWidth().softShadow(Elevation.Md, Nd.rLg).surface(Nd.paper, Nd.rLg).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        T("Toca el huevo para darle calor", Nd.strong.copy(lineHeight = 1.2.em))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(22.dp)) { Icon(Ic.Sun, 20.dp, Nd.hungerEdge) }
            T("Calor", Nd.sm.copy(lineHeight = 1.em), Modifier.width(76.dp))
            Bar((done * 100).roundToInt(), Nd.hunger, Nd.hungerEdge, Modifier.weight(1f))
        }
        T(if (left > 0) "Si no haces nada, nace sola en ${duration(left)}." else "Ya casi está.", Nd.smSoft)
    }
}

@Composable
private fun Acts(game: Game, live: Creature, compact: Boolean, enterClock: State<Float>) {
    val asleep = live.asleep
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        val slot = Modifier.weight(1f)
        Act(Ic.Food, "Comer", Nd.hunger, Nd.hungerEdge, !asleep, compact, slot.enter(enterClock, 0.04f, 18.dp, 0.5f)) { game.openSheet(Sheet.Food) }
        Act(
            if (asleep) Ic.Sun else Ic.Moon, if (asleep) "Despertar" else "Dormir", Nd.energy, Nd.energyEdge, true, compact,
            slot.enter(enterClock, 0.08f, 18.dp, 0.5f),
        ) { game.toggleSleep() }
        Act(Ic.Drop, "Baño", Nd.hygiene, Nd.hygieneEdge, !asleep, compact, slot.enter(enterClock, 0.12f, 18.dp, 0.5f)) { game.bathe() }
        Act(Ic.Ball, "Jugar", Nd.funny, Nd.funnyEdge, !asleep, compact, slot.enter(enterClock, 0.16f, 18.dp, 0.5f)) { game.openSheet(Sheet.Play()) }
        Act(Ic.Heal, "Curar", Nd.health, Nd.healthEdge, true, compact, slot.enter(enterClock, 0.2f, 18.dp, 0.5f)) { game.openSheet(Sheet.Heal) }
    }
}

@Composable
private fun Act(icon: Ic, label: String, color: Color, edge: Color, enabled: Boolean, compact: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val press = rememberPress(source)
    Column(
        modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.55f }
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            Modifier
                .size(if (compact) 52.dp else 60.dp)
                .graphicsLayer {
                    val s = 1f - 0.04f * press.value
                    scaleX = s
                    scaleY = s
                    translationY = 3.dp.toPx() * press.value
                }
                .softShadow(Elevation.Sm, Nd.rPill)
                .candyPress(color, edge, Nd.rPill, 5f, press)
                .padding(bottom = 4.dp),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, 28.dp) }
        // La etiqueta puede ser más ancha que su columna («Despertar»).
        Box(
            Modifier
                .wrapContentWidth(unbounded = true)
                .softShadow(Elevation.Sm, Nd.rPill)
                .surface(Nd.paper, Nd.rPill)
                .padding(start = 8.dp, end = 8.dp, top = 3.dp, bottom = 4.dp),
        ) { T(label, Nd.xsBlack.copy(lineHeight = 1.em), maxLines = 1) }
    }
}
