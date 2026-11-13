// Recuerdos: las criaturas que ya se fueron, con su foto, cuánto vivieron
// y en qué se convirtieron. También pone los textos de la despedida.

package com.nidaria.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.nidaria.app.DialogAction
import com.nidaria.app.DialogSpec
import com.nidaria.app.Game
import com.nidaria.app.data.MemorialEntry
import com.nidaria.app.rules.bondLevel
import com.nidaria.app.scene.ThumbSpec

val CAUSES = mapOf(
    "hambre" to "Pasó hambre demasiado tiempo.",
    "enfermedad" to "Estuvo enferma demasiado tiempo sin medicina.",
    "abandono" to "Le faltaron cuidados demasiado tiempo.",
    "vejez" to "Vivió su vida entera y se fue tranquila.",
)

private val CAUSES_SHORT = mapOf(
    "hambre" to "Se fue por pasar hambre.",
    "enfermedad" to "Se fue enferma.",
    "abandono" to "Se fue por falta de cuidados.",
    "vejez" to "Se fue de vieja, tranquila.",
)

private val BOND_PHRASES = listOf("", "Te tenía cariño.", "Confiaba en ti.", "Erais inseparables.")

private const val DAY_MS = 24.0 * 60 * 60 * 1000

fun causeText(cause: String?): String = CAUSES[cause] ?: CAUSES.getValue("abandono")

fun bondPhrase(bond: Double): String = BOND_PHRASES[bondLevel(bond)]

fun livedSpan(ms: Double): String = if (ms < DAY_MS) "menos de un día" else duration(ms)

private fun open(game: Game, entry: MemorialEntry) {
    val text = listOf(
        causeText(entry.cause),
        "Vivió ${livedSpan(entry.lived)} y llegó a ser ${entry.formName}.",
        bondPhrase(entry.bond),
    ).filter { it.isNotEmpty() }.joinToString(" ")
    game.showDialog(
        DialogSpec(
            title = entry.name,
            text = text,
            art = ThumbSpec.creature(entry.formId, entry.genes),
            actions = listOf(DialogAction("Cerrar", true, Tone.Pink)),
        ),
    )
}

// La lista de recuerdos. Se pide al servidor cada vez que se abre.
@Composable
fun MemoriesTab(game: Game) {
    var entries by remember { mutableStateOf<List<MemorialEntry>?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val list = game.send(silent = true) { memorial() }?.let { runCatching { it.memorial() }.getOrNull() }
        if (list == null) failed = true else entries = list
    }

    val list = entries
    when {
        failed -> T("No se han podido cargar los recuerdos. Cierra y vuelve a intentarlo.", Nd.lead)
        list == null -> T("Buscando recuerdos…", Nd.lead)
        list.isEmpty() -> T("Todavía no se ha ido ninguna criatura. Cuando una termine su vida, la recordarás aquí.", Nd.lead)
        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (entry in list) Memory(entry) { open(game, entry) }
        }
    }
}

@Composable
private fun Memory(entry: MemorialEntry, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onClick, label = "${entry.name}, ${entry.formName}")
            .surface(Nd.cream, Nd.rMd)
            .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumb(ThumbSpec.creature(entry.formId, entry.genes), Modifier.size(64.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            val small = Nd.smSoft.copy(lineHeight = 1.3.em)
            T(entry.name, Nd.lg.copy(fontSize = Nd.strong.fontSize, lineHeight = 1.3.em))
            T(entry.formName, Nd.bold.copy(lineHeight = 1.3.em))
            T("Vivió ${livedSpan(entry.lived)}${if (entry.generation > 1) " · Generación ${entry.generation}" else ""}", small)
            T(CAUSES_SHORT[entry.cause] ?: CAUSES_SHORT.getValue("abandono"), small)
            if (entry.heir != null) T("Su heredera: ${entry.heir}", small)
        }
    }
}
