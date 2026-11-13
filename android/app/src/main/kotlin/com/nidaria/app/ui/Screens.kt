// Pantallas de tarjeta: entrada, elección de huevo, despedida y los
// avisos de «sin conexión». La escena 3D se ve en el hueco de arriba.

package com.nidaria.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nidaria.app.DialogAction
import com.nidaria.app.DialogSpec
import com.nidaria.app.Game
import com.nidaria.app.data.Api
import com.nidaria.app.data.ApiError
import com.nidaria.app.rules.FAMILY_INFO
import com.nidaria.app.scene.ThumbSpec
import kotlinx.coroutines.launch

// Márgenes que dejan libres las barras del sistema y el teclado.
class Insets(val top: Dp, val bottom: Dp, val keyboard: Dp)

// Tarjeta anclada abajo. La escena se encuadra en el hueco que deja.
@Composable
fun CardScreen(insets: Insets, content: @Composable ColumnScope.() -> Unit) {
    val room = LocalRoom.current
    val density = LocalDensity.current
    Box(
        Modifier.fillMaxSize().padding(
            start = 16.dp, end = 16.dp, top = insets.top + 16.dp, bottom = maxOf(insets.bottom, insets.keyboard) + 16.dp,
        ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .onGloballyPositioned {
                    val top = with(density) { it.positionInRoot().y.toDp().value }
                    room.setFocus(insets.top.value + 16f, top - 8f, animate = false)
                }
                .softShadow(Elevation.Lg, Nd.rLg)
                .surface(Nd.paper, Nd.rLg)
                .absorbTouches()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

// Dónde está el servidor, con un botón para cambiarlo.
@Composable
fun ServerRow(game: Game) {
    val scope = rememberCoroutineScope()
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            T("Servidor", Nd.xsBlack, color = Nd.inkSoft)
            T(game.api.server.substringAfter("://"), Nd.sm, maxLines = 1)
        }
        Btn("Cambiar", {
            scope.launch {
                val field = FieldState(game.api.server.substringAfter("://"))
                val save = game.dialog(
                    DialogSpec(
                        title = "Servidor",
                        text = "Escribe la dirección del ordenador donde corre Nidaria, por ejemplo 192.168.1.20:3000.",
                        extra = {
                            Field(
                                field, null, Modifier.fillMaxWidth(), placeholder = "192.168.1.20:3000",
                                keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done,
                            )
                        },
                        actions = listOf(DialogAction("Guardar", true, Tone.Pink), DialogAction("Cancelar", null)),
                    ),
                )
                if (save != true) return@launch
                val url = Api.cleanServer(field.text)
                if (url == null) game.toast("Esa dirección no es válida.", error = true) else game.changeServer(url)
            }
        }, tone = null, small = true)
    }
}

// Entrada: entrar con una cuenta o crear una nueva.
@Composable
fun AuthScreen(game: Game, insets: Insets) {
    var mode by remember { mutableStateOf("login") }
    var sending by remember { mutableStateOf(false) }
    val name = remember { FieldState() }
    val pass = remember { FieldState() }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    val submit: () -> Unit = submit@{
        val user = name.text.trim()
        val secret = pass.text
        if (user.length < 3) return@submit name.fail("Escribe un nombre de al menos 3 letras.")
        if (secret.length < 8) return@submit pass.fail("La contraseña debe tener al menos 8 caracteres.")
        if (sending) return@submit
        sending = true
        focus.clearFocus()
        scope.launch {
            try {
                game.signIn(mode == "register", user, secret)
            } catch (error: ApiError) {
                val message = error.message ?: "Algo ha fallado. Inténtalo de nuevo."
                val aboutName = message.contains("nombre", ignoreCase = true) && !message.contains("contraseña", ignoreCase = true)
                if (aboutName) name.fail(message) else pass.fail(message)
            } finally {
                sending = false
            }
        }
    }

    CardScreen(insets) {
        T("Nidaria", Nd.xxl)
        T("Cría una criatura única. Lo que hagas decide en qué se convierte.", Nd.lead)
        ScrollTabs(listOf("login" to "Ya tengo cuenta", "register" to "Crear cuenta"), mode, {
            mode = it
            name.error = null
            pass.error = null
        })
        Field(name, "Nombre", Modifier.fillMaxWidth(), maxLength = 20)
        Field(
            pass, "Contraseña", Modifier.fillMaxWidth(), password = true, imeAction = ImeAction.Done, onDone = submit,
            hint = if (mode == "register") "Al menos 8 caracteres. No hay forma de recuperarla, así que apúntala." else null,
        )
        Btn(if (mode == "login") "Entrar" else "Crear cuenta", submit, Modifier.fillMaxWidth(), tone = Tone.Pink, enabled = !sending)
        ServerRow(game)
    }
}

// Elección de huevo: al empezar y cada vez que una criatura se va.
@Composable
fun EggsScreen(game: Game, insets: Insets) {
    val state = game.store.state ?: return
    val offer = state.eggOffer ?: return
    val previous = state.creature
    val selected = game.selectedEgg.coerceIn(0, offer.size - 1)
    val name = remember { FieldState() }
    var sending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    val submit: () -> Unit = submit@{
        val clean = name.text.trim()
        if (clean.isEmpty()) return@submit name.fail("Ponle un nombre antes de empezar.")
        if (sending) return@submit
        sending = true
        focus.clearFocus()
        scope.launch {
            game.send { pickEgg(selected, clean) }
            sending = false
        }
    }

    CardScreen(insets) {
        T("Elige un huevo", Nd.xl)
        val egg = offer[selected]
        val family = FAMILY_INFO.getValue(egg.family)
        T(
            if (egg.heir && previous != null) "Hereda rasgos de ${previous.name}. ${family.blurb}" else "Familia ${family.name}. ${family.blurb}",
            Nd.lead,
        )
        Grid(offer.size, columns = 3) { index ->
            val look = offer[index]
            ItemTile(
                FAMILY_INFO.getValue(look.family).name, ThumbSpec.Egg(look.copy(index = 0, heir = false)),
                onClick = { game.selectEgg(index) }, selected = index == selected, thumbSize = 92.dp, padBottom = 8.dp,
            ) { if (look.heir) Tag("Heredero") }
        }
        Field(
            name, "¿Cómo se va a llamar?", Modifier.fillMaxWidth(), placeholder = "Mota, Trasto, Nube…", maxLength = 14,
            capitalize = true, imeAction = ImeAction.Done, onDone = submit,
        )
        Btn("Empezar a incubar", submit, Modifier.fillMaxWidth(), tone = Tone.Pink, enabled = !sending)
    }
}

// Despedida: la criatura se ha ido. Explica por qué y da paso al huevo.
@Composable
fun FarewellScreen(game: Game, insets: Insets) {
    val state = game.store.state ?: return
    val creature = state.creature ?: return
    val lived = ((creature.diedAt ?: creature.bornAt) - creature.bornAt) * state.timeScale
    val bond = bondPhrase(creature.bond)
    CardScreen(insets) {
        T("${creature.name} se ha ido", Nd.xl)
        T("${causeText(creature.deathCause)} Vivió ${livedSpan(lived)} y llegó a ser ${creature.formName}.${if (bond.isNotEmpty()) " $bond" else ""}", Nd.lead)
        T("Ha dejado un huevo que hereda parte de sus rasgos. Tus monedas y tus cosas siguen contigo, y podrás recordarla en el Criadex.", Nd.lead)
        Btn("Ver los huevos", { game.continueFromFarewell() }, Modifier.fillMaxWidth(), tone = Tone.Pink)
    }
}

@Composable
fun OfflineScreen(game: Game, insets: Insets) {
    CardScreen(insets) {
        T("No hay conexión", Nd.xl)
        T(game.message, Nd.lead)
        Btn("Reintentar", { game.boot() }, Modifier.fillMaxWidth(), tone = Tone.Pink, enabled = !game.busy)
        ServerRow(game)
    }
}

@Composable
fun BrokenScreen(game: Game, insets: Insets) {
    CardScreen(insets) {
        T("No se puede dibujar la escena", Nd.xl)
        T(game.message, Nd.lead)
    }
}
