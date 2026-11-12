// Piezas compartidas de la interfaz: botones de caramelo, moneda,
// pestañas con píldora deslizante, número que cambia en sitio, campos,
// barras y casillas de artículo.

package com.nidaria.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nidaria.app.scene.ThumbSpec
import com.nidaria.app.scene.Thumbs
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

// ── Texto ─────────────────────────────────────────────────────

// Como Intl.NumberFormat('es-ES'): el punto de millar aparece a partir
// de cinco cifras.
fun fmt(value: Double): String {
    val n = value.roundToLong()
    val digits = abs(n).toString()
    if (digits.length < 5) return n.toString()
    val grouped = digits.reversed().chunked(3).joinToString(".").reversed()
    return if (n < 0) "-$grouped" else grouped
}

fun fmt(value: Int): String = fmt(value.toDouble())

// "3 h 12 min", "45 s", "2 días"
fun duration(ms: Double): String {
    val s = max(0L, (ms / 1000).roundToLong())
    if (s < 60) return "$s s"
    val m = s / 60
    if (m < 60) return "$m min"
    val hours = m / 60
    if (hours < 24) return if (m % 60 != 0L) "$hours h ${m % 60} min" else "$hours h"
    val days = hours / 24
    return if (days == 1L) "1 día" else "$days días"
}

// ── Pulsación ─────────────────────────────────────────────────

@Composable
fun rememberPress(source: InteractionSource): State<Float> {
    val pressed by source.collectIsPressedAsState()
    return animateFloatAsState(if (pressed) 1f else 0f, tween(Nd.QUICK, easing = Nd.ease), label = "pulsar")
}

// Encoge un poco la pieza mientras se pulsa.
@Composable
fun Modifier.pressable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    label: String? = null,
    disabledAlpha: Float = 0.55f,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val press = rememberPress(source)
    return this
        .graphicsLayer {
            val s = 1f - 0.04f * press.value
            scaleX = s
            scaleY = s
            alpha = if (enabled) 1f else disabledAlpha
        }
        .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
        .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier)
}

// ── Botones ───────────────────────────────────────────────────

enum class Tone(val bg: Color, val edge: Color) {
    Plain(Nd.paper, Nd.line),
    Pink(Nd.pink, Nd.pinkEdge),
    Mint(Nd.mint, Nd.mintEdge),
    Sun(Nd.sun, Nd.sunEdge),
}

// Botón de píldora. `tone = null` es el botón discreto, solo con borde.
@Composable
fun Btn(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: Tone? = Tone.Plain,
    small: Boolean = false,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val press = rememberPress(source)
    val quiet = tone == null
    Row(
        modifier = modifier
            .graphicsLayer {
                val s = 1f - 0.04f * press.value
                scaleX = s
                scaleY = s
                translationY = if (quiet) 0f else 2.dp.toPx() * press.value
                alpha = if (enabled) 1f else 0.5f
            }
            .then(
                if (quiet) {
                    Modifier.surface(Nd.paper, Nd.rPill).innerBorder(if (press.value > 0.5f) Nd.inkFaint else Nd.line, 2.dp, Nd.rPill)
                } else {
                    Modifier
                        .then(if (enabled) Modifier.softShadow(Elevation.Sm, Nd.rPill, 1f - press.value * 0.6f) else Modifier)
                        .candyPress(tone!!.bg, tone.edge, Nd.rPill, 4f, press)
                },
            )
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = if (small) 40.dp else 48.dp)
            .padding(
                start = if (small) 16.dp else if (quiet) 20.dp else 22.dp,
                end = if (small) 16.dp else if (quiet) 20.dp else 22.dp,
                top = if (small) 7.dp else if (quiet) 13.dp else 11.dp,
                bottom = if (small) 11.dp else if (quiet) 13.dp else 15.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun Btn(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: Tone? = Tone.Plain,
    small: Boolean = false,
    enabled: Boolean = true,
) = Btn(onClick, modifier, tone, small, enabled) { BtnText(label, small, quiet = tone == null) }

@Composable
fun BtnText(label: String, small: Boolean = false, quiet: Boolean = false) {
    val base = if (quiet) Nd.bold else Nd.strong
    T(label, base.copy(fontSize = if (small) 14.sp else 16.sp, lineHeight = 1.1.em), align = TextAlign.Center)
}

// Caramelo cuyo canto se aplasta al pulsar.
fun Modifier.candyPress(color: Color, edge: Color, radius: Dp, edgeDp: Float, press: State<Float>): Modifier = drawBehind {
    val corner = minOf(radius.toPx(), size.width / 2f, size.height / 2f)
    val cr = CornerRadius(corner, corner)
    val lift = (edgeDp - (edgeDp - 1f) * press.value).dp.toPx()
    drawRoundRect(edge, cornerRadius = cr)
    val outline = Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, size), cr)) }
    clipPath(outline) {
        drawRoundRect(color, topLeft = Offset(0f, -lift), size = size, cornerRadius = cr)
    }
}

@Composable
fun IconBtn(icon: Ic, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(44.dp).pressable(onClick, label = label).surface(Nd.track, Nd.rPill),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, 20.dp) }
}

@Composable
fun Coin(size: Dp = 32.dp, color: Color = Nd.sun, modifier: Modifier = Modifier) {
    val edge = if (size < 24.dp) 2.dp else 3.dp
    Box(modifier.size(size).candy(color, Nd.sunEdge, edge, Nd.rPill), contentAlignment = Alignment.Center) {
        Icon(Ic.Star, size * 0.6f, filled = true)
    }
}

// `tight` es la variante para las casillas de artículo: menos relleno y,
// si aun así no cabe, el texto pasa a dos líneas en vez de cortarse.
@Composable
fun Chip(icon: Ic, text: String, tight: Boolean = false) {
    val side = if (tight) 7.dp else 9.dp
    Row(
        Modifier.surface(Nd.track, Nd.rMd).padding(start = side, end = side, top = 3.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, 14.dp)
        T(text, Nd.xsBlack, maxLines = if (tight) 2 else 1, align = if (tight) TextAlign.Center else null)
    }
}

// Deja que una pieza centrada ocupe también el relleno lateral de su caja.
private fun Modifier.bleed(side: Dp): Modifier = layout { measurable, constraints ->
    val extra = (side * 2).roundToPx()
    val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = constraints.maxWidth + extra))
    layout(constraints.maxWidth, placeable.height) { placeable.place((constraints.maxWidth - placeable.width) / 2, 0) }
}

// En las casillas (`center`) las etiquetas van centradas y aprovechan
// todo el ancho de la casilla.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Chips(center: Boolean = false, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(
        modifier.padding(top = 4.dp).then(if (center) Modifier.bleed(5.dp) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(6.dp, if (center) Alignment.CenterHorizontally else Alignment.Start),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

// ── Barra ─────────────────────────────────────────────────────

// Barra de 0 a 100 con su canto. En estado crítico lleva un borde rojo.
@Composable
fun Bar(value: Int, color: Color, edge: Color, modifier: Modifier = Modifier, critical: Boolean = false) {
    val shown by animateFloatAsState(value.coerceIn(0, 100) / 100f, tween(Nd.SLOW, easing = Nd.ease), label = "barra")
    Box(
        modifier.height(14.dp).drawBehind {
            val r = CornerRadius(size.height / 2f, size.height / 2f)
            drawRoundRect(Nd.track, cornerRadius = r)
            val w = max(size.height, size.width * shown)
            val fill = Size(w, size.height)
            val outline = Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, fill), r)) }
            drawRoundRect(edge, size = fill, cornerRadius = r)
            clipPath(outline) { drawRoundRect(color, topLeft = Offset(0f, -4.dp.toPx()), size = fill, cornerRadius = r) }
            if (critical) {
                val s = 2.dp.toPx()
                drawRoundRect(
                    Nd.danger, topLeft = Offset(s / 2f, s / 2f), size = Size(size.width - s, size.height - s),
                    cornerRadius = CornerRadius((size.height - s) / 2f), style = Stroke(s),
                )
            }
        },
    )
}

// ── Pestañas ──────────────────────────────────────────────────

// Pestañas con una píldora que se desliza hasta la elegida.
@Composable
fun Tabs(
    options: List<Pair<String, String>>,
    active: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp,
    padX: Dp = 16.dp,
) {
    val lefts = remember(options) { mutableStateListOf(*Array(options.size) { 0f }) }
    val widths = remember(options) { mutableStateListOf(*Array(options.size) { 0f }) }
    val x = remember { Animatable(0f) }
    val w = remember { Animatable(0f) }
    var placed by remember { mutableStateOf(false) }
    val index = options.indexOfFirst { it.first == active }.coerceAtLeast(0)
    val targetX = lefts.getOrElse(index) { 0f }
    val targetW = widths.getOrElse(index) { 0f }
    LaunchedEffect(targetX, targetW) {
        if (targetW <= 0f) return@LaunchedEffect
        if (!placed) {
            x.snapTo(targetX)
            w.snapTo(targetW)
            placed = true
        } else {
            coroutineScope {
                launch { x.animateTo(targetX, tween(Nd.FAST, easing = Nd.ease)) }
                launch { w.animateTo(targetW, tween(Nd.FAST, easing = Nd.ease)) }
            }
        }
    }
    Box(modifier.surface(Nd.track, Nd.rPill).padding(3.dp)) {
        Box(
            Modifier
                .offset { IntOffset(x.value.roundToInt(), 0) }
                .width(with(LocalDensity.current) { w.value.toDp() })
                .height(height)
                .softShadow(Elevation.Sm, Nd.rPill)
                .surface(Nd.paper, Nd.rPill),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            options.forEachIndexed { i, (id, label) ->
                val selected = id == active
                val color by animateColorAsState(if (selected) Nd.ink else Nd.inkSoft, tween(Nd.FAST, easing = Nd.ease), label = "pestaña")
                Box(
                    Modifier
                        .onGloballyPositioned {
                            lefts[i] = it.positionInParent().x
                            widths[i] = it.size.width.toFloat()
                        }
                        .height(height)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Tab,
                        ) { if (!selected) onChange(id) }
                        .padding(start = padX, end = padX, bottom = 2.dp),
                    contentAlignment = Alignment.Center,
                ) { T(label, Nd.smBlack, color = color, maxLines = 1) }
            }
        }
    }
}

// Pestañas que pueden desbordar a lo ancho (la tienda en pantallas estrechas).
@Composable
fun ScrollTabs(options: List<Pair<String, String>>, active: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.horizontalScroll(rememberScrollState())) { Tabs(options, active, onChange) }
}

// ── Número que cambia en sitio ────────────────────────────────

// Solo las cifras que cambian se funden; las demás no se mueven.
@Composable
fun SubtleNumber(value: Int, style: TextStyle, modifier: Modifier = Modifier) {
    var previous by remember { mutableIntStateOf(value) }
    val up = value >= previous
    LaunchedEffect(value) { previous = value }
    val text = fmt(value)
    Row(modifier.semantics(mergeDescendants = true) { contentDescription = text }) {
        text.forEachIndexed { i, ch ->
            key(text.length, i) {
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = {
                        val dir = if (up) 1 else -1
                        (slideInVertically(tween(455, easing = Nd.ease)) { (it * 0.14f * dir).roundToInt() } + fadeIn(tween(455, easing = Nd.ease)))
                            .togetherWith(slideOutVertically(tween(455, easing = Nd.ease)) { (-it * 0.14f * dir).roundToInt() } + fadeOut(tween(455, easing = Nd.ease)))
                    },
                    label = "cifra",
                ) { T(it.toString(), style.num) }
            }
        }
    }
}

// ── Campos ────────────────────────────────────────────────────

class FieldState(initial: String = "") {
    var text by mutableStateOf(initial)
    var error by mutableStateOf<String?>(null)
    var shakes by mutableIntStateOf(0)

    fun fail(message: String) {
        error = message
        shakes++
    }
}

@Composable
fun Field(
    state: FieldState,
    label: String?,
    modifier: Modifier = Modifier,
    hint: String? = null,
    placeholder: String? = null,
    password: Boolean = false,
    maxLength: Int = 200,
    imeAction: ImeAction = ImeAction.Next,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalize: Boolean = false,
    onDone: (() -> Unit)? = null,
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val shake = remember { Animatable(0f) }
    LaunchedEffect(state.shakes) {
        if (state.shakes == 0) return@LaunchedEffect
        shake.animateTo(6f, tween(80, easing = Nd.ease))
        shake.animateTo(-6f, tween(80, easing = Nd.ease))
        shake.animateTo(4f, tween(60, easing = Nd.ease))
        shake.animateTo(0f, tween(60, easing = Nd.ease))
    }
    val border by animateColorAsState(
        if (state.error != null) Nd.danger else if (focused) Nd.ink else Nd.line, tween(Nd.QUICK), label = "borde",
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) T(label, Nd.smBlack)
        BasicTextField(
            value = state.text,
            onValueChange = {
                state.text = it.take(maxLength)
                state.error = null
            },
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = shake.value.dp.toPx() }
                .surface(Nd.cream, Nd.rMd)
                .innerBorder(border, 2.dp, Nd.rMd),
            textStyle = Nd.bold,
            singleLine = true,
            cursorBrush = SolidColor(Nd.pinkEdge),
            interactionSource = source,
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                capitalization = if (capitalize) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = if (password) KeyboardType.Password else keyboardType,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }, onGo = { onDone?.invoke() }),
            decorationBox = { inner ->
                Box(
                    Modifier.defaultMinSize(minHeight = 48.dp).padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (state.text.isEmpty() && placeholder != null) T(placeholder, Nd.bold, color = Nd.inkSoft, maxLines = 1)
                    inner()
                }
            },
        )
        if (hint != null) T(hint, Nd.smSoft)
        state.error?.let { T(it, Nd.sm.copy(lineHeight = 1.45.em), color = Nd.danger) }
    }
}

// ── Artículos ─────────────────────────────────────────────────

@Composable
fun Thumb(spec: ThumbSpec?, modifier: Modifier = Modifier, alpha: Float = 1f) {
    val room = LocalRoom.current
    val image = spec?.let { Thumbs.images[it.key] }
    if (spec != null && image == null) LaunchedEffect(spec.key) { room.requestThumb(spec) }
    Box(modifier) {
        if (image != null) {
            val shown by animateFloatAsState(alpha, tween(Nd.FAST), label = "miniatura")
            Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, alpha = shown)
        }
    }
}

// Cuadrícula de casillas iguales: tantas columnas como quepan, y todas
// las de una fila con el mismo alto.
@Composable
fun Grid(
    count: Int,
    modifier: Modifier = Modifier,
    minCell: Dp = 98.dp,
    gap: Dp = 8.dp,
    columns: Int = 0,
    cell: @Composable (Int) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cols = if (columns > 0) columns else max(1, floor((maxWidth + gap) / (minCell + gap)).toInt())
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            for (row in 0 until (count + cols - 1) / cols) {
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (c in 0 until cols) {
                        val i = row * cols + c
                        Box(Modifier.weight(1f).fillMaxHeight()) { if (i < count) cell(i) }
                    }
                }
            }
        }
    }
}

@Composable
fun ItemTile(
    name: String,
    thumb: ThumbSpec?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    count: Int? = null,
    thumbSize: Dp = 76.dp,
    padBottom: Dp = 12.dp,
    extra: (@Composable () -> Unit)? = null,
) {
    val bg by animateColorAsState(if (selected) Nd.pinkWash else Nd.cream, tween(Nd.QUICK), label = "casilla")
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .pressable(onClick, enabled)
                .surface(bg, Nd.rMd)
                .innerBorder(if (selected) Nd.pinkEdge else Color.Transparent, 3.dp, Nd.rMd)
                .padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = padBottom),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Thumb(thumb, Modifier.size(thumbSize))
            T(name, Nd.sm.copy(lineHeight = 1.15.em, lineBreak = LineBreak.Heading), align = TextAlign.Center)
            extra?.invoke()
        }
        if (count != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .softShadow(Elevation.Sm, Nd.rPill)
                    .surface(Nd.paper, Nd.rPill)
                    .widthIn(min = 26.dp)
                    .padding(start = 7.dp, end = 7.dp, top = 2.dp, bottom = 3.dp)
                    .semantics { contentDescription = "Tienes $count" },
                contentAlignment = Alignment.Center,
            ) { T("×$count", Nd.xsBlack.num) }
        }
    }
}

@Composable
fun Tag(text: String) = T(text, Nd.xsBlack, Modifier.padding(top = 2.dp), color = Nd.inkSoft, align = TextAlign.Center)

@Composable
fun Stepper(value: Int, max: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier.surface(Nd.track, Nd.rPill).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton("−", "Uno menos", value > 1) { onChange(value - 1) }
        T(value.toString(), Nd.strong.num, Modifier.widthIn(min = 34.dp), align = TextAlign.Center)
        StepButton("+", "Uno más", value < max) { onChange(value + 1) }
    }
}

@Composable
private fun StepButton(sign: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(42.dp)
            .pressable(onClick, enabled, label, disabledAlpha = 0.45f)
            .then(if (enabled) Modifier.softShadow(Elevation.Sm, Nd.rPill) else Modifier)
            .surface(Nd.paper, Nd.rPill),
        contentAlignment = Alignment.Center,
    ) { T(sign, Nd.lg.copy(lineHeight = 1.em)) }
}

@Composable
fun Empty(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        T(text, Nd.lead, Modifier.widthIn(max = 280.dp), align = TextAlign.Center)
        if (action != null && onAction != null) Btn(action, onAction, tone = Tone.Pink)
    }
}

@Composable
fun SectionTitle(text: String, first: Boolean = false) = T(text, Nd.lg, Modifier.padding(top = if (first) 0.dp else 24.dp, bottom = 8.dp))
