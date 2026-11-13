// Código de recuperación: la caja que lo enseña en el diálogo y la parte
// de la ficha para crear uno nuevo (como public/js/ui/recovery.js).

package com.nidaria.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.nidaria.app.Game
import kotlinx.coroutines.launch

// El código en una caja crema con borde de puntos. Se puede seleccionar.
@Composable
fun RecoveryCode(code: String) {
    SelectionContainer {
        T(
            code,
            Nd.lg.num.copy(letterSpacing = 0.06.em),
            Modifier
                .fillMaxWidth()
                .surface(Nd.cream, Nd.rMd)
                .drawBehind {
                    val width = 2.dp.toPx()
                    val radius = Nd.rMd.toPx()
                    drawRoundRect(
                        Nd.line,
                        topLeft = androidx.compose.ui.geometry.Offset(width / 2f, width / 2f),
                        size = androidx.compose.ui.geometry.Size(size.width - width, size.height - width),
                        cornerRadius = CornerRadius(radius, radius),
                        style = Stroke(width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                    )
                }
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp)
                .semantics { contentDescription = "Código: ${code.toList().joinToString(" ")}" },
            align = TextAlign.Center,
        )
    }
}

// Parte de «Tu cuenta» en la ficha. Crear un código pide la contraseña.
@Composable
fun RecoverySection(game: Game) {
    val has = game.store.state?.user?.hasRecovery == true
    var asking by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    val pass = remember { FieldState() }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    val submit: () -> Unit = submit@{
        if (pass.text.isEmpty()) return@submit pass.fail("Escribe tu contraseña.")
        if (sending) return@submit
        sending = true
        focus.clearFocus()
        scope.launch {
            val error = game.renewRecovery(pass.text)
            sending = false
            if (error != null) {
                pass.fail(error)
            } else {
                pass.text = ""
                asking = false
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!asking) {
            T(
                if (has) "Si olvidas la contraseña, entras con tu nombre y tu código. Si lo has perdido, crea otro: el anterior dejará de valer."
                else "Tu cuenta aún no tiene código. Créalo y guárdalo: si olvidas la contraseña, es la única forma de volver a entrar.",
                Nd.smSoft,
            )
            Btn(if (has) "Crear un código nuevo" else "Crear código", { asking = true }, tone = if (has) null else Tone.Mint, small = true)
        } else {
            T("Para crear un código, escribe la contraseña con la que entras.", Nd.smSoft)
            Field(pass, "Tu contraseña", Modifier.fillMaxWidth(), password = true, imeAction = ImeAction.Done, onDone = submit)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("Crear código", submit, tone = Tone.Mint, small = true, enabled = !sending)
                Btn("Cancelar", {
                    asking = false
                    pass.text = ""
                    pass.error = null
                }, tone = null, small = true)
            }
        }
    }
}
