// Amigos (como public/js/ui/friends.js y visit.js): la hoja con los
// regalos, las solicitudes, la lista y los códigos; la hoja para elegir un
// regalo, y la pantalla de visita a la habitación de un amigo.

package com.nidaria.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.nidaria.app.Game
import com.nidaria.app.Sheet
import com.nidaria.app.data.FriendCreature
import com.nidaria.app.data.FriendsInfo
import com.nidaria.app.rules.Creature
import com.nidaria.app.rules.GIFT_KINDS
import com.nidaria.app.rules.LOW
import com.nidaria.app.rules.itemsOfKind
import com.nidaria.app.rules.normalizeFriendCode
import com.nidaria.app.scene.ThumbSpec
import kotlin.math.min
import kotlinx.coroutines.launch

private fun ago(game: Game, at: Double): String {
    val ms = game.store.now() - at
    return if (ms < 60_000) "hace un momento" else "hace ${duration(ms)}"
}

// Quién vive en la habitación de un amigo, según su lista.
private fun tenant(creature: FriendCreature?): String = when {
    creature == null -> "Aún no tiene criatura"
    !creature.alive -> "Esperando a su próxima criatura"
    else -> "${creature.name} · ${if (creature.stage == "huevo") "Huevo" else creature.formName}"
}

// ── Hoja de amigos ────────────────────────────────────────────

@Composable
fun FriendsSheet(game: Game) {
    var info by remember { mutableStateOf<FriendsInfo?>(null) }
    var failed by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var working by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reload) {
        val next = game.loadFriends()
        if (next != null) info = next else if (info == null) failed = true
    }

    // Hace una cosa cada vez y, si sale bien, vuelve a pedir la lista.
    val act: (suspend () -> Boolean) -> Unit = { block ->
        if (!working) {
            working = true
            scope.launch {
                val ok = block()
                working = false
                if (ok) reload++
            }
        }
    }

    SheetFrame(game, "Amigos") {
        val data = info
        when {
            failed -> T("No se han podido cargar tus amigos. Cierra y vuelve a intentarlo.", Nd.lead)
            data == null -> T("Buscando a tus amigos…", Nd.lead)
            else -> FriendsBody(game, data, working, act)
        }
    }
}

@Composable
private fun ColumnScope.FriendsBody(game: Game, data: FriendsInfo, working: Boolean, act: (suspend () -> Boolean) -> Unit) {
    var first = true
    val title: @Composable (String) -> Unit = { text ->
        SectionTitle(text, first)
        first = false
    }
    val list: @Composable (@Composable () -> Unit) -> Unit = { rows ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { rows() }
    }

    if (data.gifts.isNotEmpty()) {
        title("Regalos para ti")
        list {
            for (gift in data.gifts) {
                PersonRow({ Icon(Ic.Gift, 26.dp) }, "Un regalo de ${gift.from}", listOf(ago(game, gift.at)), avatar = Nd.sunWash) {
                    Btn("Abrir", { act { game.openGift(gift) } }, tone = Tone.Sun, small = true, enabled = !working)
                }
            }
        }
    }

    if (data.incoming.isNotEmpty()) {
        title("Quieren ser tus amigos")
        list {
            for (request in data.incoming) {
                PersonRow({ Icon(Ic.Friends, 26.dp) }, request.name, listOf("Te lo pidió ${ago(game, request.at)}")) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Btn("Aceptar", { act { game.acceptFriend(request) } }, tone = Tone.Mint, small = true, enabled = !working)
                        Btn("No", { act { game.dropRequest(request) } }, tone = null, small = true, enabled = !working)
                    }
                }
            }
        }
    }

    title(if (data.friends.isEmpty()) "Tus amigos" else "Tus amigos · ${data.friends.size} de ${data.max}")
    if (data.friends.isEmpty()) {
        T(
            "Todavía no tienes amigos aquí. Pásale tu código a alguien o escribe abajo el suyo. Entre amigos os podéis visitar y dejaros un regalo cada día.",
            Nd.lead,
        )
    } else {
        list {
            for (friend in data.friends) {
                val creature = friend.creature?.takeIf { it.alive }
                val lines = listOfNotNull(
                    tenant(friend.creature),
                    friend.visitedAt?.let { "Vino a verte ${ago(game, it)}" },
                    if (friend.giftedToday) "Hoy ya le has dejado un regalo" else null,
                )
                PersonRow(
                    { if (creature != null) Thumb(ThumbSpec.creature(creature.formId, creature.genes), Modifier.size(56.dp)) else Icon(Ic.Home, 26.dp) },
                    friend.name,
                    lines,
                    onClick = { if (!working) act { game.startVisit(friend.id).join(); false } },
                    label = "Visitar a ${friend.name}",
                ) {
                    T("Visitar", Nd.smBlack, Modifier.surface(Nd.paper, Nd.rPill).padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 7.dp))
                }
            }
        }
    }

    title("Añadir a alguien")
    AddFriend(game, working, act)

    title("Tu código")
    val code = game.store.state?.social?.code ?: ""
    RecoveryCode(code)
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Btn("Compartir", { game.shareFriendCode(code) }, Modifier.weight(1f), tone = null)
        Btn("Copiar", { game.copyFriendCode(code) }, Modifier.weight(1f), tone = null)
    }
    TextLink("Cambiar mi código", { act { game.renewFriendCode() } }, Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp))

    if (data.outgoing.isNotEmpty()) {
        title("Esperando respuesta")
        list {
            for (request in data.outgoing) {
                PersonRow({ Icon(Ic.Clock, 26.dp) }, request.name, listOf("Se lo pediste ${ago(game, request.at)}")) {
                    Btn("Cancelar", { act { game.dropRequest(request, "Has cancelado la solicitud a ${request.name}.") } }, tone = null, small = true, enabled = !working)
                }
            }
        }
    }
}

@Composable
private fun AddFriend(game: Game, working: Boolean, act: (suspend () -> Boolean) -> Unit) {
    val field = remember { FieldState() }
    val focus = LocalFocusManager.current
    val submit: () -> Unit = submit@{
        if (normalizeFriendCode(field.text) == null) return@submit field.fail("Un código de amigo tiene 8 letras y números, como K7QM-4XPA.")
        focus.clearFocus()
        act {
            val ok = game.addFriend(field.text)
            if (ok) field.text = ""
            ok
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Field(
                field, null, Modifier.weight(1f), placeholder = "K7QM-4XPA", maxLength = 12,
                imeAction = ImeAction.Done, onDone = submit, allCaps = true,
            )
            Btn("Añadir", submit, tone = Tone.Pink, enabled = !working)
        }
        T("Escribe su código de amigo. Le llegará una solicitud y, cuando la acepte, podréis visitaros.", Nd.smSoft)
    }
}

// Una fila de la hoja: el retrato, el nombre con sus líneas y, a la
// derecha, lo que se puede hacer. Si `onClick`, la fila entera se pulsa.
@Composable
private fun PersonRow(
    portrait: @Composable () -> Unit,
    name: String,
    lines: List<String>,
    avatar: Color = Nd.paper,
    onClick: (() -> Unit)? = null,
    label: String? = null,
    trailing: @Composable RowScope.() -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.pressable(onClick, label = label) else Modifier)
            .surface(Nd.cream, Nd.rMd)
            .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp).surface(avatar, Nd.rPill), contentAlignment = Alignment.Center) { portrait() }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            T(name, Nd.strong.copy(lineHeight = 1.3.em))
            for (line in lines) T(line, Nd.smSoft.copy(lineHeight = 1.3.em))
        }
        trailing()
    }
}

// ── Hoja del regalo ───────────────────────────────────────────

@Composable
fun GiftSheet(game: Game) {
    val state = game.store.state ?: return
    val visit = game.visit ?: return
    val items = GIFT_KINDS.flatMap { itemsOfKind(it) }.filter { state.owned(it.id) > 0 }
    SheetFrame(
        game, "Un regalo para ${visit.name}",
        sub = if (items.isNotEmpty()) "Elige algo tuyo. Le llegará envuelto y lo abrirá cuando entre. Puedes dejarle uno al día." else null,
    ) {
        if (items.isEmpty()) {
            Empty("No tienes comida ni cuidados que regalar. Compra algo en la tienda y vuelve.", "Ir a la tienda") {
                game.openSheet(Sheet.Shop("comida"))
            }
        } else {
            OwnedTiles(state, items) { game.giveGift(it) }
        }
    }
}

// ── Pantalla de visita ────────────────────────────────────────

private val WANTS = mapOf(
    "hunger" to "Tiene hambre: algo de comer le vendría bien.",
    "energy" to "Está muy cansada. Necesita dormir.",
    "hygiene" to "Necesita un baño. Un jabón de burbujas le vendría bien.",
    "fun" to "Se aburre un poco.",
)

// Qué tal está la criatura del amigo y qué le vendría bien.
private fun visitStatus(creature: Creature?, owner: String): String {
    if (creature == null) return "$owner no tiene ninguna criatura ahora mismo."
    if (creature.stage == "huevo") return "Todavía es un huevo. $owner lo está incubando."
    if (creature.asleep) return "Está durmiendo. Habla bajito."
    if (creature.sick) return "Está enferma. Una medicina le vendría bien."
    val worst = listOf("hunger", "energy", "hygiene", "fun").minBy { creature.need(it) }
    if (creature.need(worst) < LOW) return WANTS.getValue(worst)
    if (creature.mood == "feliz") return "Está feliz. Se nota que la cuidan bien."
    return "Está tranquila. Le hace ilusión que vengas."
}

@Composable
fun VisitScreen(game: Game, insets: Insets) {
    val visit = game.visit ?: return
    val creature = visit.creature
    val room = LocalRoom.current
    val density = LocalDensity.current

    var plateBottom by remember { mutableFloatStateOf(0f) }
    var dockTop by remember { mutableFloatStateOf(0f) }
    val sheetTop = game.sheetTop
    LaunchedEffect(plateBottom, dockTop, sheetTop) {
        if (dockTop <= 0f) return@LaunchedEffect
        val fits = sheetTop != null && sheetTop - plateBottom >= 200f
        val bottom = if (fits) min(dockTop, sheetTop!!) else dockTop
        room.setFocus(plateBottom + 6f, bottom - 8f)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // ── Volver ──
        Row(
            Modifier
                .padding(start = 16.dp, top = insets.top + 12.dp)
                .pressable({ game.leaveVisit() })
                .softShadow(Elevation.Sm, Nd.rPill)
                .surface(Nd.paper, Nd.rPill)
                .defaultMinSize(minHeight = 48.dp)
                .padding(start = 12.dp, end = 18.dp, top = 6.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Ic.Back, 20.dp)
            T("Volver a casa", Nd.strong)
        }

        // ── Placa ──
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = insets.top + (if (maxHeight < 700.dp) 68.dp else 76.dp))
                .widthIn(max = (maxWidth - 32.dp).coerceAtLeast(120.dp))
                .onGloballyPositioned { plateBottom = with(density) { (it.positionInRoot().y + it.size.height).toDp().value } }
                .softShadow(Elevation.Sm, Nd.rMd)
                .surface(Nd.paper, Nd.rMd)
                .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            T(creature?.name ?: "Casa de ${visit.name}", Nd.lg, align = TextAlign.Center)
            val sub = if (creature == null) "No hay nadie en casa"
            else "En casa de ${visit.name} · ${if (creature.stage == "huevo") "Huevo" else creature.formName}"
            T(sub, Nd.sm.copy(lineHeight = 1.15.em), color = Nd.inkSoft, align = TextAlign.Center)
        }

        Bubble(game, plateBottom)

        // ── Muelle ──
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = insets.bottom + 12.dp)
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .onGloballyPositioned { dockTop = with(density) { it.positionInRoot().y.toDp().value } }
                .softShadow(Elevation.Md, Nd.rLg)
                .surface(Nd.paper, Nd.rLg)
                .absorbTouches()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            T(visitStatus(creature, visit.name), Nd.bold, align = TextAlign.Center)
            Btn({ game.openSheet(Sheet.Gift) }, Modifier.fillMaxWidth(), tone = Tone.Pink, enabled = !visit.giftedToday) {
                Icon(Ic.Gift, 20.dp)
                BtnText(if (visit.giftedToday) "Hoy ya le has dejado un regalo" else "Dejar un regalo")
            }
            TextLink("Quitar de amigos", { game.removeVisitedFriend() })
        }
    }
}
