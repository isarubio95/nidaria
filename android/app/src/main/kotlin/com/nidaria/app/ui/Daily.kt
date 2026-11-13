// Misiones del día: tres encargos con su premio y, arriba, la racha.
// Las mismas que en la web; las reparte y las cuenta el servidor.

package com.nidaria.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.nidaria.app.Game
import com.nidaria.app.data.Daily
import com.nidaria.app.data.Mission
import kotlin.math.max
import kotlin.math.min

private val TIERS = listOf("Fácil", "Media", "Difícil")
private const val STREAK_PIPS = 7

fun days(n: Int) = "$n ${if (n == 1) "día" else "días"}"

private fun streakNote(daily: Daily): String = when {
    daily.doneToday && daily.best > daily.streak -> "Hoy ya está. Tu mejor racha es de ${days(daily.best)}."
    daily.doneToday -> "Hoy ya está. Vuelve mañana para seguirla."
    daily.streak > 0 -> "Recoge las tres de hoy para llegar a ${days(daily.streak + 1)} y ganar ${fmt(daily.bonus)} monedas más."
    else -> "Recoge las tres de hoy para empezar una racha y ganar ${fmt(daily.bonus)} monedas más."
}

@Composable
fun DailySheet(game: Game) {
    val state = game.store.state ?: return
    val daily = state.daily ?: return
    val clock = game.clock
    val now = remember(clock, state) { game.store.now() }

    SheetFrame(game, "Misiones del día", sub = "Cambian en ${duration(daily.resetAt - now)}.") {
        StreakCard(daily)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            daily.missions.forEachIndexed { tier, mission -> MissionRow(game, mission, tier) }
        }
    }
}

@Composable
private fun StreakCard(daily: Daily) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 12.dp).surface(Nd.sunWash, Nd.rMd)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).candy(Nd.sun, Nd.sunEdge, 3.dp, Nd.rPill), contentAlignment = Alignment.Center) {
                Icon(Ic.Flame, 24.dp, filled = true)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                T(if (daily.streak > 0) "Racha de ${days(daily.streak)}" else "Sin racha todavía", Nd.strong.copy(lineHeight = 1.2.em))
                T(streakNote(daily), Nd.smSoft)
            }
        }
        // Una semana de racha: a partir del séptimo día el premio ya no sube.
        val lit = min(daily.streak, STREAK_PIPS)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 0 until STREAK_PIPS) {
                Box(
                    Modifier.weight(1f).height(12.dp).then(
                        if (i < lit) Modifier.candy(Nd.sun, Nd.sunEdge, 3.dp, Nd.rPill)
                        else Modifier.surface(Nd.paper, Nd.rPill).innerBorder(Nd.sunEdge, 2.dp, Nd.rPill),
                    ),
                )
            }
        }
    }
}

@Composable
private fun MissionRow(game: Game, mission: Mission, tier: Int) {
    val back = when {
        mission.claimed -> Modifier.surface(Nd.paper, Nd.rMd).innerBorder(Nd.line, 2.dp, Nd.rMd)
        mission.done -> Modifier.surface(Nd.sunWash, Nd.rMd)
        else -> Modifier.surface(Nd.cream, Nd.rMd)
    }
    Row(
        Modifier.fillMaxWidth().then(back).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            T(TIERS.getOrElse(tier) { "" }, Nd.xsBlack, color = Nd.inkSoft)
            T(mission.text, Nd.strong.copy(lineHeight = 1.25.em), color = if (mission.claimed) Nd.inkSoft else Nd.ink)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Progress(
                    mission.progress, mission.goal,
                    Modifier.weight(1f).semantics { contentDescription = "Progreso: ${mission.progress} de ${mission.goal}" },
                )
                T("${mission.progress} de ${mission.goal}", Nd.sm.num, color = Nd.inkSoft, maxLines = 1)
            }
        }
        when {
            mission.claimed -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Ic.Check, 18.dp, Nd.inkSoft)
                T("Recogida", Nd.smBlack, color = Nd.inkSoft)
            }
            mission.done -> Btn({ game.claim(mission) }, tone = Tone.Sun, small = true) {
                BtnText("Recoger", small = true)
                Coin(20.dp)
                T(fmt(mission.reward), Nd.smBlack.num)
            }
            else -> Row(
                Modifier.semantics(mergeDescendants = true) { contentDescription = "Premio: ${fmt(mission.reward)} monedas" },
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Coin(20.dp)
                T(fmt(mission.reward), Nd.strong.num)
            }
        }
    }
}

// Barra fina color sol: el dinero que está por llegar. Vacía, solo carril.
@Composable
private fun Progress(value: Int, goal: Int, modifier: Modifier) {
    val k = if (goal > 0) value.toFloat() / goal else 0f
    Box(
        modifier.height(10.dp).drawBehind {
            val r = CornerRadius(size.height / 2f)
            drawRoundRect(Nd.track, cornerRadius = r)
            if (k <= 0f) return@drawBehind
            val w = max(size.height, size.width * min(1f, k))
            drawRoundRect(Nd.sunEdge, size = Size(w, size.height), cornerRadius = r)
            drawRoundRect(Nd.sun, size = Size(w, size.height - 3.dp.toPx()), cornerRadius = r)
        },
    )
}
