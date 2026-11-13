// Ajustes del teléfono, al final de la ficha: sonido, avisos y clima real.
// Los mismos que en la web; qué avisos se quieren se guarda en la cuenta.

package com.nidaria.app.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.nidaria.app.Game
import com.nidaria.app.data.WEATHER_NAMES

private val ALERT_GROUPS = listOf(
    Triple("care", "Cuando necesite algo", "Hambre, sueño, suciedad, aburrimiento o si enferma."),
    Triple("growth", "Cuando nazca o evolucione", null),
    Triple("streak", "Si la racha está en peligro", "A partir de las 20:00, si no has completado las misiones."),
    Triple("games", "Energía de juego llena", "Después de gastarla casi toda."),
    Triple("quiet", "Silencio de 23:00 a 8:00", "Lo que pase de noche te llega por la mañana."),
)

// Interruptor: apagado es carril; encendido, menta con su canto.
@Composable
fun Toggle(on: Boolean) {
    val x by animateDpAsState(if (on) 24.dp else 4.dp, tween(Nd.FAST, easing = Nd.ease), label = "interruptor")
    Box(
        Modifier.size(52.dp, 32.dp).then(
            if (on) Modifier.candy(Nd.mint, Nd.mintEdge, 3.dp, Nd.rPill)
            else Modifier.surface(Nd.track, Nd.rPill).innerBorder(Nd.line, 2.dp, Nd.rPill),
        ),
    ) {
        Box(
            Modifier.offset(x = x, y = 4.dp).size(24.dp).softShadow(Elevation.Sm, Nd.rPill).surface(Nd.paper, Nd.rPill),
        )
    }
}

// Fila de ajuste: toda ella se toca, y se lee como un interruptor.
@Composable
fun SettingRow(label: String, hint: String?, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .surface(Nd.cream, Nd.rMd)
            .toggleable(value = on, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            T(label, Nd.strong.copy(lineHeight = 1.3.em))
            if (hint != null) T(hint, Nd.smSoft.copy(lineHeight = 1.35.em))
        }
        Toggle(on)
    }
}

@Composable
fun ColumnScope.SettingsSections(game: Game) {
    val state = game.store.state ?: return
    val name = state.creature?.name ?: "tu criatura"

    SectionTitle("Sonido")
    SettingRow("Efectos de sonido", "Al comer, al bañarla, al ganar… Solo en este dispositivo.", game.soundOn) { game.setSound(it) }

    SectionTitle("Avisos")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            !game.alertsOn -> {
                T("Te avisamos cuando $name te necesite, aunque tengas el juego cerrado.", Nd.smSoft)
                Btn("Activar avisos", { game.enableAlerts() }, tone = Tone.Pink)
            }
            !game.alertsAllowed -> {
                T("Las notificaciones de Nidaria están bloqueadas en el teléfono. Permítelas en sus ajustes para recibir los avisos.", Nd.smSoft)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn("Abrir ajustes", { game.openAlertSettings() }, tone = Tone.Pink)
                    Btn("Desactivar", { game.disableAlerts() }, tone = null)
                }
            }
            else -> {
                val prefs = state.push?.prefs
                if (prefs != null) {
                    for ((group, label, hint) in ALERT_GROUPS) {
                        SettingRow(label, hint, prefs.group(group)) { game.setAlertGroup(group, it) }
                    }
                }
                Btn("Desactivar en este teléfono", { game.disableAlerts() }, tone = null, small = true)
            }
        }
    }

    SectionTitle("Clima real")
    val weather = game.weather
    val now = if (game.weatherOn && weather != null) " Ahora: ${WEATHER_NAMES[weather.kind]}, ${weather.temp} °C." else ""
    SettingRow(
        "Ver por la ventana el tiempo que hace",
        "Usa tu ubicación aproximada para preguntar el tiempo a Open-Meteo. No se guarda en Nidaria.$now",
        game.weatherOn,
    ) { if (it) game.enableWeather() else game.disableWeather() }
}
