// Raíz de la interfaz: la pantalla que toque y, encima y por este orden,
// el velo, la hoja, el minijuego, los diálogos y los avisos. La escena
// 3D va debajo de todo, en su propia vista.

package com.nidaria.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Density
import com.nidaria.app.Game
import com.nidaria.app.Screen

private fun Modifier.hiddenWhen(covered: Boolean): Modifier = if (covered) clearAndSetSemantics { } else this

// Con el zoom de la pantalla (Zoom.kt) cada dp mide menos: todo se encoge
// a la vez y la interfaz gana sitio, como al alejar la vista.
@Composable
fun App(game: Game, zoom: Zoom) {
    val base = LocalDensity.current
    val level = zoom.level
    val zoomed = remember(base, level) { Density(base.density * level, base.fontScale) }
    CompositionLocalProvider(LocalDensity provides zoomed) { Layers(game) }
}

@Composable
private fun Layers(game: Game) {
    val density = LocalDensity.current
    val bars = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    val insets = with(density) {
        Insets(bars.getTop(density).toDp(), bars.getBottom(density).toDp(), WindowInsets.ime.getBottom(density).toDp())
    }
    BackHandler(enabled = game.dialogs.isNotEmpty() || game.play != null || game.sheet != null || game.visit != null) { game.back() }

    CompositionLocalProvider(LocalRoom provides game.room) {
        Box(Modifier.fillMaxSize()) {
            // Lo que queda tapado no se puede tocar, y tampoco debe poder
            // recorrerse con el lector de pantalla.
            val hasDialog = game.dialogs.isNotEmpty()
            val covered = game.sheet != null || game.play != null || hasDialog
            Box(Modifier.fillMaxSize().hiddenWhen(covered)) {
                when (game.screen) {
                    Screen.Home -> HomeScreen(game, insets)
                    Screen.Visit -> VisitScreen(game, insets)
                    Screen.Auth -> AuthScreen(game, insets)
                    Screen.Eggs -> EggsScreen(game, insets)
                    Screen.Farewell -> FarewellScreen(game, insets)
                    Screen.Offline -> OfflineScreen(game, insets)
                    Screen.Broken -> BrokenScreen(game, insets)
                    Screen.Boot -> Unit
                }
            }
            Scrim(game.sheet != null && !hasDialog) { game.dismissSheet() }
            Box(Modifier.fillMaxSize().hiddenWhen(hasDialog || game.play != null)) { SheetHost(game, insets.bottom) }
            game.play?.let { session -> Box(Modifier.fillMaxSize().hiddenWhen(hasDialog)) { GameScreen(game, session, insets) } }
            // Con un diálogo abierto el velo sube y tapa también la hoja.
            Scrim(game.dialogs.isNotEmpty())
            DialogHost(game, insets.keyboard)
            ToastHost(game, insets.top)
        }
    }
}
