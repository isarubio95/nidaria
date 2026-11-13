// Lo que se abre encima del juego: el velo, las hojas inferiores, los
// diálogos centrados y los avisos breves.

package com.nidaria.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.foundation.shape.RoundedCornerShape
import com.nidaria.app.DialogState
import com.nidaria.app.Game
import com.nidaria.app.Sheet
import com.nidaria.app.ToastState
import kotlin.math.pow
import kotlin.math.roundToInt

// Impide que un toque atraviese la pieza y llegue a lo que hay detrás.
fun Modifier.absorbTouches(): Modifier = pointerInput(Unit) { detectTapGestures { } }

@Composable
fun Scrim(visible: Boolean, onClick: (() -> Unit)? = null) {
    val alpha by animateFloatAsState(
        if (visible) 1f else 0f, tween(if (visible) Nd.SLOW else Nd.MEDIUM, easing = Nd.ease), label = "velo",
    )
    if (alpha <= 0.001f && !visible) return
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha }
            .background(Nd.scrim)
            .then(if (visible) Modifier.pointerInput(onClick) { detectTapGestures { onClick?.invoke() } } else Modifier),
    )
}

// ── Hojas ─────────────────────────────────────────────────────

// Una sola hoja abierta a la vez: sube blanda desde abajo.
@Composable
fun SheetHost(game: Game, safeBottom: Dp) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        val wide = maxWidth >= 600.dp
        val maxSheet = minOf(maxHeight * 0.86f, 760.dp)
        val screenHeight = constraints.maxHeight
        val shape = if (wide) RoundedCornerShape(Nd.rLg) else RoundedCornerShape(topStart = Nd.rLg, topEnd = Nd.rLg)
        LaunchedEffect(game.sheet == null) { if (game.sheet == null) game.sheetTop = null }
        AnimatedContent(
            targetState = game.sheet,
            transitionSpec = {
                val rise = with(density) { 72.dp.roundToPx() }
                (slideInVertically(tween(Nd.SLOW, easing = Nd.ease)) { rise } + fadeIn(tween(Nd.SLOW, easing = Nd.ease)))
                    .togetherWith(slideOutVertically(tween(Nd.MEDIUM, easing = Nd.ease)) { rise } + fadeOut(tween(Nd.MEDIUM, easing = Nd.ease)))
                    .using(null)
            },
            contentAlignment = Alignment.BottomCenter,
            label = "hoja",
        ) { sheet ->
            if (sheet == null) {
                Box(Modifier.fillMaxWidth().height(0.dp))
            } else {
                val bottomGap = if (wide) 16.dp else 0.dp
                Box(
                    Modifier
                        .padding(bottom = bottomGap)
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .heightIn(max = maxSheet)
                        .onSizeChanged {
                            if (game.sheet == sheet) {
                                game.sheetTop = with(density) { (screenHeight - it.height).toDp().value - bottomGap.value }
                            }
                        }
                        .softShadow(Elevation.Lg, Nd.rLg)
                        .clip(shape)
                        .background(Nd.paper)
                        .absorbTouches()
                        .padding(bottom = if (wide) 0.dp else safeBottom),
                ) {
                    when (sheet) {
                        is Sheet.Shop -> ShopSheet(game, sheet.category)
                        Sheet.Food -> FoodSheet(game)
                        Sheet.Heal -> HealSheet(game)
                        Sheet.Wardrobe -> WardrobeSheet(game)
                        Sheet.Criadex -> CriadexSheet(game)
                        is Sheet.Play -> PlaySheet(game, sheet.tab)
                        Sheet.Profile -> ProfileSheet(game)
                        Sheet.Daily -> DailySheet(game)
                    }
                }
            }
        }
    }
}

// Armazón de una hoja: título y cierre, texto de apoyo, pestañas, cuerpo
// con desplazamiento y pie fijo.
@Composable
fun SheetFrame(
    game: Game,
    title: String,
    sub: String? = null,
    tabs: (@Composable () -> Unit)? = null,
    foot: (@Composable () -> Unit)? = null,
    scroll: ScrollState = rememberScrollState(),
    body: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            T(title, Nd.xl, Modifier.weight(1f))
            IconBtn(Ic.X, "Cerrar", { game.dismissSheet() })
        }
        if (sub != null) T(sub, Nd.smSoft, Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp))
        if (tabs != null) Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)) { tabs() }
        Column(
            Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
            content = body,
        )
        if (foot != null) {
            Box(Modifier.fillMaxWidth().height(2.dp).background(Nd.line))
            Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)) { foot() }
        }
    }
}

// ── Diálogos ──────────────────────────────────────────────────

// Diálogo centrado para momentos que piden parar: una evolución, el
// resultado de un minijuego, una despedida.
@Composable
fun DialogHost(game: Game, bottomInset: Dp) {
    for (state in game.dialogs) {
        key(state) {
            Box(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + bottomInset), contentAlignment = Alignment.Center) {
                DialogCard(game, state)
            }
        }
    }
}

@Composable
private fun DialogCard(game: Game, state: DialogState) {
    LaunchedEffect(state) { state.open = true }
    val shown by animateFloatAsState(
        if (state.open) 1f else 0f, tween(if (state.open) Nd.FAST else Nd.QUICK, easing = Nd.ease), label = "diálogo",
    )
    val spec = state.spec
    Column(
        Modifier
            .widthIn(max = 380.dp)
            .fillMaxWidth()
            .graphicsLayer {
                alpha = shown
                val s = 0.96f + 0.04f * shown
                scaleX = s
                scaleY = s
            }
            .softShadow(Elevation.Lg, Nd.rLg, shown)
            .surface(Nd.paper, Nd.rLg)
            .absorbTouches()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (spec.art != null) Thumb(spec.art, Modifier.size(148.dp))
        T(spec.title, Nd.xl, align = TextAlign.Center)
        if (spec.text != null) T(spec.text, Nd.lead, Modifier.widthIn(max = 300.dp), align = TextAlign.Center)
        spec.extra?.invoke()
        Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (action in spec.actions) {
                Btn(action.label, { game.closeDialog(state, action.value) }, Modifier.fillMaxWidth(), tone = action.tone)
            }
        }
    }
}

private val POWER2_OUT = Easing { 1f - (1f - it).pow(3) }

// Monedas ganadas en un minijuego, contando desde cero.
@Composable
fun Reward(coins: Int) {
    val count = remember { Animatable(0f) }
    LaunchedEffect(coins) { count.animateTo(coins.toFloat(), tween(900, easing = POWER2_OUT)) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Coin(40.dp)
        T("+${fmt(count.value.roundToInt())}", Nd.xxl.num)
    }
}

// ── Avisos ────────────────────────────────────────────────────

// Aviso breve que sube, se queda un momento y se va solo.
@Composable
fun ToastHost(game: Game, safeTop: Dp) {
    Box(Modifier.fillMaxSize().padding(top = safeTop + 132.dp, start = 16.dp, end = 16.dp), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (toast in game.toasts) key(toast.id) { ToastPill(toast) }
        }
    }
}

@Composable
private fun ToastPill(toast: ToastState) {
    val shown by animateFloatAsState(
        if (toast.open) 1f else 0f, tween(if (toast.open) Nd.MEDIUM else Nd.FAST, easing = Nd.ease), label = "aviso",
    )
    Box(
        Modifier
            .graphicsLayer {
                alpha = shown
                translationY = (1f - shown) * 16.dp.toPx()
                val s = 0.97f + 0.03f * shown
                scaleX = s
                scaleY = s
            }
            .softShadow(Elevation.Md, Nd.rPill, shown)
            .surface(if (toast.error) Nd.danger else Nd.ink, Nd.rPill)
            .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 12.dp),
    ) { T(toast.message, Nd.sm.copy(lineHeight = 1.25.em), color = Nd.cream, align = TextAlign.Center) }
}
