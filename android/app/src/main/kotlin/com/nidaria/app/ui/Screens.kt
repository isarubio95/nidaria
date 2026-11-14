// Pantallas de tarjeta: entrada, elección de huevo, despedida y los
// avisos de «sin conexión». La escena 3D se ve en el hueco de arriba.

package com.nidaria.app.ui

import com.nidaria.app.scene.canonGenes
import com.nidaria.app.scene.breedGenes
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
import com.nidaria.app.rules.SPECIES_INFO
import com.nidaria.app.rules.speciesOf
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

// Entrada: entrar con una cuenta, crear una nueva o recuperarla con el
// código de recuperación si se ha olvidado la contraseña.
private const val LEAD = "Cría una criatura única. Lo que hagas decide en qué se convierte."
private const val LEAD_RECOVER = "Escribe tu nombre, el código que te dimos al crear la cuenta y una contraseña nueva."
private const val CODE_SHAPE = "El código tiene 16 letras y números, como ABCD-EFGH-JKMN-PQRS."

@Composable
fun AuthScreen(game: Game, insets: Insets) {
    var mode by remember { mutableStateOf("login") }
    var sending by remember { mutableStateOf(false) }
    val name = remember { FieldState() }
    val code = remember { FieldState() }
    val pass = remember { FieldState() }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val recover = mode == "recover"

    fun setMode(next: String) {
        mode = next
        name.error = null
        code.error = null
        pass.error = null
        if (next == "recover") code.text = ""
    }

    val submit: () -> Unit = submit@{
        val user = name.text.trim()
        val secret = pass.text
        if (user.length < 3) return@submit name.fail("Escribe un nombre de al menos 3 letras.")
        if (recover && code.text.replace(Regex("[\\s-]"), "").length != 16) return@submit code.fail(CODE_SHAPE)
        if (secret.length < 8) return@submit pass.fail("La contraseña debe tener al menos 8 caracteres.")
        if (sending) return@submit
        sending = true
        focus.clearFocus()
        scope.launch {
            try {
                game.signIn(mode, user, secret, code.text)
            } catch (error: ApiError) {
                val message = error.message ?: "Algo ha fallado. Inténtalo de nuevo."
                when {
                    message.contains("código", ignoreCase = true) -> code.fail(message)
                    message.contains("nombre", ignoreCase = true) && !message.contains("contraseña", ignoreCase = true) -> name.fail(message)
                    else -> pass.fail(message)
                }
            } finally {
                sending = false
            }
        }
    }

    CardScreen(insets) {
        if (recover) T("Recuperar la cuenta", Nd.xl) else T("Nidaria", Nd.xxl)
        T(if (recover) LEAD_RECOVER else LEAD, Nd.lead)
        if (!recover) {
            ScrollTabs(listOf("login" to "Ya tengo cuenta", "register" to "Crear cuenta"), mode, { setMode(it) })
        }
        Field(name, "Nombre", Modifier.fillMaxWidth(), maxLength = 20)
        if (recover) {
            Field(
                code, "Código de recuperación", Modifier.fillMaxWidth(), placeholder = "ABCD-EFGH-JKMN-PQRS",
                maxLength = 24, allCaps = true, keyboardType = KeyboardType.Ascii,
            )
        }
        Field(
            pass, if (recover) "Contraseña nueva" else "Contraseña", Modifier.fillMaxWidth(), password = true,
            imeAction = ImeAction.Done, onDone = submit,
            hint = when (mode) {
                "register" -> "Al menos 8 caracteres. Al crear la cuenta te daremos un código para recuperarla si la olvidas."
                "recover" -> "Al menos 8 caracteres. Al entrar te daremos un código nuevo."
                else -> null
            },
        )
        val label = when (mode) {
            "register" -> "Crear cuenta"
            "recover" -> "Cambiar contraseña y entrar"
            else -> "Entrar"
        }
        Btn(label, submit, Modifier.fillMaxWidth(), tone = Tone.Pink, enabled = !sending)
        if (mode == "login") {
            TextLink("¿Has olvidado la contraseña?", { setMode("recover") }, Modifier.align(Alignment.CenterHorizontally))
        } else if (recover) {
            TextLink("Volver a entrar con la contraseña", { setMode("login") }, Modifier.align(Alignment.CenterHorizontally))
        }
        ServerRow(game)
    }
}

// Empezar una criatura: se elige especie (perro, gato o monstruo) y se
// lleva su huevo, con la raza o la familia ya decidida y escondida.
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
            game.send { pickEgg(offer[selected].species, clean) }
            sending = false
        }
    }

    CardScreen(insets) {
        T(if (previous != null) "¿Qué vas a criar ahora?" else "¿Qué quieres criar?", Nd.xl)
        val egg = offer[selected]
        val info = SPECIES_INFO.getValue(egg.species)
        T(
            if (egg.heir && previous != null) "Es el heredero de ${previous.name}: lleva parte de sus rasgos. ${info.blurb}" else info.blurb,
            Nd.lead,
        )
        // Cada especie con un adulto de muestra; el huevo elegido sale en la habitación.
        Grid(offer.size, columns = 3) { index ->
            val look = offer[index]
            ItemTile(
                SPECIES_INFO.getValue(look.species).plural, speciesSample(look.species),
                onClick = { game.selectEgg(index) }, selected = index == selected, thumbSize = 100.dp, padBottom = 8.dp,
            ) { if (look.heir) Tag("Heredero") }
        }
        Field(
            name, "¿Cómo se va a llamar?", Modifier.fillMaxWidth(), placeholder = "Rayo, Luna, Chispa…", maxLength = 14,
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
        val species = SPECIES_INFO.getValue(speciesOf(creature.genes.family)).name.lowercase()
        T("Ha dejado un huevo que hereda parte de sus rasgos: es el de $species. Tus monedas y tus cosas siguen contigo, y podrás recordarla en el Criadex.", Nd.lead)
        Btn("Empezar otra", { game.continueFromFarewell() }, Modifier.fillMaxWidth(), tone = Tone.Pink)
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

// El adulto que sale en cada tarjeta de especie (como SAMPLE en eggs.js).
private fun speciesSample(species: String): ThumbSpec = when (species) {
    "perro" -> ThumbSpec.creature("adulto-perro-jugueton-tranquilo", breedGenes("perro", "husky", 0))
    "gato" -> ThumbSpec.creature("adulto-gato-jugueton-tranquilo", breedGenes("gato", "siames", 0))
    else -> ThumbSpec.creature("adulto-pelusa-jugueton-jugueton", canonGenes("pelusa"))
}
