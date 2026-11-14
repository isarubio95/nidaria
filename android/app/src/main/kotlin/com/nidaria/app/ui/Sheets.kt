// Las hojas del juego: tienda, comer, curar, armario, Criadex, jugar y
// la ficha de la criatura.

package com.nidaria.app.ui

import com.nidaria.app.scene.breedGenes
import com.nidaria.app.rules.breedsOf
import com.nidaria.app.rules.SPECIES_INFO
import com.nidaria.app.rules.isPet
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.nidaria.app.DialogAction
import com.nidaria.app.DialogSpec
import com.nidaria.app.Game
import com.nidaria.app.Sheet
import com.nidaria.app.data.GameState
import com.nidaria.app.rules.BOND_LEVELS
import com.nidaria.app.rules.BRANCH_INFO
import com.nidaria.app.rules.CATEGORIES
import com.nidaria.app.rules.Creature
import com.nidaria.app.rules.DIFFICULTIES
import com.nidaria.app.rules.FEATURED_MULT
import com.nidaria.app.rules.FAMILIES
import com.nidaria.app.rules.FAMILY_INFO
import com.nidaria.app.rules.breedInfo
import com.nidaria.app.rules.FORM_LIST
import com.nidaria.app.rules.Form
import com.nidaria.app.rules.Item
import com.nidaria.app.rules.MAX_STACK
import com.nidaria.app.rules.MINIGAMES
import com.nidaria.app.rules.PERKS
import com.nidaria.app.rules.SHY_UNTIL
import com.nidaria.app.rules.SLOTS
import com.nidaria.app.rules.SPOTS
import com.nidaria.app.rules.STAGE_INFO
import com.nidaria.app.rules.TOY_COOLDOWN_MS
import com.nidaria.app.rules.TRAITS
import com.nidaria.app.rules.baseReward
import com.nidaria.app.rules.bondLevel
import com.nidaria.app.rules.canPlayGame
import com.nidaria.app.rules.gameLock
import com.nidaria.app.rules.perksFor
import com.nidaria.app.rules.childrenOf
import com.nidaria.app.rules.getForm
import com.nidaria.app.rules.getItem
import com.nidaria.app.rules.hintFor
import com.nidaria.app.rules.itemsOfKind
import com.nidaria.app.rules.secretsOf
import com.nidaria.app.scene.BASE_DECOR
import com.nidaria.app.scene.ThumbSpec
import com.nidaria.app.scene.canonGenes
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.launch

private fun signed(value: Int) = if (value > 0) "+$value" else "−${abs(value)}"

// Efectos de un artículo. `limit` deja solo los primeros, para las
// casillas pequeñas.
@Composable
fun EffectChips(item: Item, limit: Int = 9, center: Boolean = false) {
    val chips = ArrayList<Pair<Ic, String>>()
    when (item.kind) {
        "comida" -> {
            chips.add(Ic.Food to "${signed(item.hunger)} comida")
            if (item.fun_ != 0) chips.add(Ic.Ball to "${signed(item.fun_)} diversión")
            if (item.health != 0) chips.add(Ic.Heart to "${signed(item.health)} salud")
        }
        "cuidado" -> {
            if (item.cures) chips.add(Ic.Heal to "Cura la enfermedad")
            if (item.bath) chips.add(Ic.Drop to "Higiene al máximo")
            if (item.health != 0) chips.add(Ic.Heart to "${signed(item.health)} salud")
            if (item.fun_ != 0) chips.add(Ic.Ball to "${signed(item.fun_)} diversión")
        }
        "juguete" -> {
            chips.add(Ic.Ball to "${signed(item.fun_)} diversión")
            chips.add(Ic.Moon to if (item.energy != 0) "${signed(-item.energy)} energía" else "No cansa")
        }
        "ropa" -> chips.add(Ic.Hanger to SLOTS.first { it.id == item.slot }.name)
        "casa" -> chips.add(Ic.Home to SPOTS.first { it.id == item.spot }.name)
    }
    Chips(center) { for ((icon, text) in chips.take(limit)) Chip(icon, text, tight = center) }
}

// ── Tienda ────────────────────────────────────────────────────

// Pestañas por categoría, cuadrícula de artículos y, al elegir uno, una
// barra con sus efectos y el botón de compra.
@Composable
fun ShopSheet(game: Game, initial: String) {
    val state = game.store.state ?: return
    var category by remember { mutableStateOf(initial) }
    var selected by remember { mutableStateOf<String?>(null) }
    var qty by remember { mutableIntStateOf(1) }
    val item = selected?.let { getItem(it) }

    SheetFrame(
        game, "Tienda",
        tabs = {
            ScrollTabs(CATEGORIES.map { it.id to it.name }, category, {
                category = it
                selected = null
            })
        },
        foot = if (item != null) ({ BuyBar(game, state, item, qty) { qty = it } }) else null,
    ) {
        val items = itemsOfKind(category)
        Grid(items.size) { index ->
            val entry = items[index]
            val count = state.owned(entry.id)
            val mine = entry.reusable && count > 0
            ItemTile(
                entry.name, ThumbSpec.Item(entry.id),
                onClick = {
                    selected = entry.id
                    qty = 1
                },
                selected = selected == entry.id,
                count = if (!entry.reusable && count > 0) count else null,
            ) {
                if (mine) {
                    Tag("Ya es tuyo")
                } else {
                    Row(
                        Modifier.padding(top = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Coin(20.dp)
                        T(fmt(entry.price), Nd.smBlack.num)
                    }
                }
            }
        }
    }
}

@Composable
private fun BuyBar(game: Game, state: GameState, item: Item, wanted: Int, onQty: (Int) -> Unit) {
    val scope = rememberCoroutineScope()
    var sending by remember { mutableStateOf(false) }
    val count = state.owned(item.id)
    val mine = item.reusable && count > 0
    val space = MAX_STACK - count
    val qty = max(1, min(wanted, space))
    val cost = item.price * qty
    val short = cost - state.user.coins
    val creature = state.creature

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            T(item.name, Nd.lg)
            T(item.blurb, Nd.smSoft)
            EffectChips(item)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!item.reusable) Stepper(qty, min(space, 20), onQty)
            val wide = Modifier.weight(1f)
            if (mine && item.kind == "ropa") {
                val wearing = creature?.equipped?.slot(item.slot!!) == item.id
                val can = creature != null && creature.alive && creature.stage != "huevo"
                Btn(if (wearing) "Lo lleva puesto" else "Ponérselo", { game.equip(item.slot!!, item, announce = true) }, wide, Tone.Mint, enabled = !wearing && can)
            } else if (mine && item.kind == "casa") {
                val there = state.decor.spot(item.spot!!) == item.id
                Btn(if (there) "Ya está puesto" else "Colocar", { game.decorate(item.spot, item, announce = true) }, wide, Tone.Mint, enabled = !there)
            } else if (mine) {
                Btn("Ya es tuyo", {}, wide, enabled = false)
            } else if (short > 0) {
                Btn("Te faltan ${fmt(short)}", {}, wide, Tone.Pink, enabled = false)
            } else {
                Btn({
                    scope.launch {
                        sending = true
                        game.buy(item, qty).join()
                        sending = false
                    }
                }, wide, Tone.Pink, enabled = !sending) {
                    BtnText("Comprar")
                    Coin(20.dp)
                    T(fmt(cost), Nd.strong.num)
                }
            }
        }
    }
}

// ── Comer y curar ─────────────────────────────────────────────

@Composable
fun OwnedTiles(state: GameState, items: List<Item>, onPick: (Item) -> Unit) {
    Grid(items.size) { index ->
        val item = items[index]
        ItemTile(item.name, ThumbSpec.Item(item.id), { onPick(item) }, count = state.owned(item.id)) { EffectChips(item, 1, center = true) }
    }
}

@Composable
fun FoodSheet(game: Game) {
    val state = game.store.state ?: return
    val items = itemsOfKind("comida").filter { state.owned(it.id) > 0 }
    SheetFrame(
        game, "Comer",
        sub = if (items.isNotEmpty()) "Elige qué le das. Cada criatura tiene una comida favorita y otra que no soporta." else null,
        foot = if (items.isNotEmpty()) ({ Btn("Comprar más comida", { game.openSheet(Sheet.Shop("comida")) }, Modifier.fillMaxWidth(), tone = null) }) else null,
    ) {
        if (items.isEmpty()) {
            Empty("No te queda comida. Compra algo y vuelve.", "Ir a la tienda") { game.openSheet(Sheet.Shop("comida")) }
        } else {
            OwnedTiles(state, items) { game.feed(it) }
        }
    }
}

@Composable
fun HealSheet(game: Game) {
    val state = game.store.state ?: return
    val creature = state.creature ?: return
    val items = itemsOfKind("cuidado").filter { !it.bath && state.owned(it.id) > 0 }
    SheetFrame(
        game, "Curar",
        sub = if (creature.sick) "${creature.name} está enferma: pierde salud hasta que tome medicina."
        else "La medicina cura la enfermedad. Las vitaminas suben la salud.",
        foot = if (items.isNotEmpty()) ({ Btn("Comprar más", { game.openSheet(Sheet.Shop("cuidado")) }, Modifier.fillMaxWidth(), tone = null) }) else null,
    ) {
        if (items.isEmpty()) {
            Empty(
                if (creature.sick) "Está enferma y no tienes medicina. Cómprala cuanto antes." else "No tienes medicina ni vitaminas.",
                "Ir a la tienda",
            ) { game.openSheet(Sheet.Shop("cuidado")) }
        } else {
            OwnedTiles(state, items) { game.heal(it) }
        }
    }
}

// ── Armario ───────────────────────────────────────────────────

// Ponerle o quitarle la ropa que ya es suya.
@Composable
fun WardrobeSheet(game: Game) {
    val state = game.store.state ?: return
    val creature = state.creature ?: return
    var slot by remember { mutableStateOf(SLOTS[0].id) }
    SheetFrame(
        game, "Armario",
        tabs = { ScrollTabs(SLOTS.map { it.id to it.name }, slot, { slot = it }) },
        foot = { Btn("Comprar ropa", { game.openSheet(Sheet.Shop("ropa")) }, Modifier.fillMaxWidth(), tone = null) },
    ) {
        val wearing = creature.equipped.slot(slot)
        val owned = itemsOfKind("ropa").filter { it.slot == slot && state.owned(it.id) > 0 }
        if (owned.isEmpty()) {
            Empty("No tienes nada para esta parte. En la tienda hay ropa de sobra.")
        } else {
            Grid(owned.size + 1) { index ->
                if (index == 0) {
                    ItemTile("Nada", null, { game.equip(slot, null) }, selected = wearing == null)
                } else {
                    val item = owned[index - 1]
                    ItemTile(item.name, ThumbSpec.Item(item.id), { game.equip(slot, item) }, selected = wearing == item.id)
                }
            }
        }
    }
}

// ── Casa ──────────────────────────────────────────────────────

// Cambiar el papel pintado, la alfombra y los muebles de la habitación
// con lo que ya es del jugador.
@Composable
fun HouseSheet(game: Game, initial: String) {
    val state = game.store.state ?: return
    var spot by remember { mutableStateOf(initial) }
    SheetFrame(
        game, "Casa",
        tabs = { ScrollTabs(SPOTS.map { it.id to it.name }, spot, { spot = it }) },
        foot = { Btn("Comprar para la casa", { game.openSheet(Sheet.Shop("casa")) }, Modifier.fillMaxWidth(), tone = null) },
    ) {
        val info = SPOTS.first { it.id == spot }
        val current = state.decor.spot(spot)
        val owned = itemsOfKind("casa").filter { it.spot == spot && state.owned(it.id) > 0 }
        Grid(owned.size + 1) { index ->
            if (index == 0) {
                val base = BASE_DECOR[spot]
                ItemTile(info.base ?: "Nada", base?.let { ThumbSpec.Item(it) }, { game.decorate(spot, null) }, selected = current == null)
            } else {
                val item = owned[index - 1]
                ItemTile(item.name, ThumbSpec.Item(item.id), { game.decorate(spot, item) }, selected = current == item.id)
            }
        }
        if (owned.isEmpty()) {
            T("En la tienda, en Casa, hay más cosas para este sitio.", Nd.smSoft, Modifier.fillMaxWidth().padding(top = 16.dp), align = TextAlign.Center)
        }
    }
}

// ── Criadex ───────────────────────────────────────────────────

// El árbol genealógico como colección. Cada familia parte de su cría; de
// ella salen cuatro jóvenes y de cada joven, cuatro adultas. Aparte, las
// formas ocultas de cada familia. En la otra pestaña, los recuerdos de
// las criaturas que ya se fueron.
@Composable
fun CriadexSheet(game: Game) {
    val state = game.store.state ?: return
    var tab by remember { mutableStateOf("formas") }
    val scroll = rememberScrollState()
    LaunchedEffect(tab) { scroll.scrollTo(0) }
    val sub = if (tab == "formas") {
        val known = state.criadex
        val (secret, regular) = FORM_LIST.partition { it.secret != null }
        "Has descubierto ${regular.count { it.id in known }} de ${regular.size} formas y ${secret.count { it.id in known }} de ${secret.size} ocultas. " +
            "La crianza decide por qué rama sigue cada criatura."
    } else if (tab == "razas") {
        "Perros y gatos de todas las razas. Cada huevo trae una."
    } else {
        "Las criaturas que cuidaste y ya se fueron."
    }
    SheetFrame(
        game, "Criadex",
        sub = sub,
        tabs = { Tabs(listOf("formas" to "Formas", "razas" to "Razas", "recuerdos" to "Recuerdos"), tab, { tab = it }) },
        scroll = scroll,
    ) {
        when (tab) {
            "formas" -> FormsTab(game, state)
            "razas" -> BreedsTab(game, state)
            else -> MemoriesTab(game)
        }
    }
}

// Las razas descubiertas: cuentan al nacer una criatura de esa raza.
@Composable
private fun BreedsTab(game: Game, state: GameState) {
    val known = state.criadex
    val creature = state.creature
    for ((index, species) in listOf("perro", "gato").withIndex()) {
        val list = breedsOf(species)
        val found = list.count { "raza:${it.id}" in known }
        Column(Modifier.fillMaxWidth().padding(top = if (index == 0) 0.dp else 32.dp)) {
            T(SPECIES_INFO.getValue(species).plural, Nd.lg)
            T("Has descubierto $found de ${list.size} razas. La raza la decide el huevo.", Nd.smSoft, Modifier.padding(bottom = 12.dp))
            for (row in list.chunked(4)) {
                Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (breed in row) {
                        val isKnown = "raza:${breed.id}" in known
                        val mine = creature?.genes?.breed == breed.id
                        val genes = if (mine) creature!!.genes else breedGenes(species, breed.id, 0)
                        val spec = ThumbSpec.creature("adulto-$species-jugueton-tranquilo", genes, !isKnown)
                        Column(
                            Modifier
                                .weight(1f)
                                .pressable({
                                    game.showDialog(
                                        DialogSpec(
                                            title = if (isKnown) breed.name else "Raza sin descubrir",
                                            text = if (isKnown) {
                                                val times = known["raza:${breed.id}"]?.times ?: 1
                                                "La has criado ${if (times == 1) "una vez" else "$times veces"}. Capas: ${breed.coats.joinToString(", ") { it.name.lowercase() }}."
                                            } else {
                                                "Sale de un huevo de ${SPECIES_INFO.getValue(species).name.lowercase()}, si hay suerte."
                                            },
                                            art = spec,
                                            actions = listOf(DialogAction(if (isKnown) "Cerrar" else "Entendido", true, Tone.Pink)),
                                        ),
                                    )
                                }, label = if (isKnown) breed.name else "Raza sin descubrir")
                                .padding(start = 2.dp, end = 2.dp, top = 4.dp, bottom = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Thumb(spec, Modifier.widthIn(max = 72.dp).fillMaxWidth().aspectRatio(1f), alpha = if (isKnown) 1f else 0.5f)
                            T(if (isKnown) breed.name else "¿?", Nd.xs, color = if (isKnown) Nd.ink else Nd.inkSoft, align = TextAlign.Center)
                        }
                    }
                    repeat(4 - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun FormsTab(game: Game, state: GameState) {
    val creature = state.creature
    val known = state.criadex

    // Las formas por las que ha pasado tu criatura se ven con sus colores.
    fun thumbFor(form: Form, silhouette: Boolean): ThumbSpec {
        val genes = if (creature != null && (form.id in creature.history || form.id == "huevo")) creature.genes
        else canonGenes(form.family ?: creature?.genes?.family ?: "pelusa")
        return ThumbSpec.creature(form.id, genes, silhouette)
    }

    fun show(form: Form, isKnown: Boolean) {
        if (!isKnown) {
            val of = form.family?.let { f -> if (isPet(f)) "de ${SPECIES_INFO.getValue(f).name.lowercase()}" else "de la familia ${FAMILY_INFO.getValue(f).name}" }
            game.showDialog(
                DialogSpec(
                    title = if (form.secret != null) "Forma oculta" else "Forma sin descubrir",
                    text = when {
                        form.secret != null -> "Una forma oculta $of. ${hintFor(form.id)}"
                        of != null -> "${STAGE_INFO.getValue(form.stage).name} $of. ${hintFor(form.id)}"
                        else -> "Elige un huevo para descubrirla."
                    },
                    art = thumbFor(form, true),
                    actions = listOf(DialogAction("Entendido", true, Tone.Pink)),
                ),
            )
            return
        }
        val times = known[form.id]?.times ?: 1
        val how = when {
            form.secret != null -> "Forma oculta. ${hintFor(form.id)}"
            form.branch != null -> BRANCH_INFO.getValue(form.branch).let { "Rama ${it.name.lowercase()}. ${it.hint}" }
            form.stage == "huevo" -> "De aquí sale todo."
            else -> ""
        }
        game.showDialog(
            DialogSpec(
                title = form.name,
                text = "$how La has tenido ${if (times == 1) "una vez" else "$times veces"}.".trim(),
                art = thumbFor(form, false),
                actions = listOf(DialogAction("Cerrar", true, Tone.Pink)),
            ),
        )
    }

    @Composable
    fun Tile(form: Form, modifier: Modifier = Modifier, young: Boolean = false) {
        val isKnown = form.id in known
        val current = creature != null && creature.alive && creature.formId == form.id
        val label = when {
            isKnown -> form.name
            form.secret != null -> "Forma oculta sin descubrir"
            else -> "Forma sin descubrir"
        }
        Column(
            modifier
                .fillMaxWidth()
                .pressable({ show(form, isKnown) }, label = label)
                .surface(if (current) Nd.pinkWash else if (young) Nd.cream else Color.Transparent, Nd.rSm)
                .innerBorder(if (current) Nd.pinkEdge else Color.Transparent, 2.dp, Nd.rSm)
                .padding(start = 2.dp, end = 2.dp, top = 4.dp, bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Thumb(thumbFor(form, !isKnown), Modifier.widthIn(max = 72.dp).fillMaxWidth().aspectRatio(1f), alpha = if (isKnown) 1f else 0.5f)
            when {
                isKnown -> T(form.name, Nd.xs, align = TextAlign.Center)
                form.secret != null -> T("?", Nd.strong, color = Nd.inkSoft, align = TextAlign.Center)
                else -> T("¿?", Nd.xs, color = Nd.inkSoft, align = TextAlign.Center)
            }
        }
    }

    @Composable
    fun Family(title: String, blurb: String, first: Boolean, content: @Composable () -> Unit) {
        Column(Modifier.fillMaxWidth().padding(top = if (first) 0.dp else 32.dp)) {
            T(title, Nd.lg)
            T(blurb, Nd.smSoft, Modifier.padding(bottom = 12.dp))
            content()
        }
    }

    // El huevo es la raíz común de las tres familias.
    Family("El origen", "Todas las criaturas salen de un huevo. Su familia viene en el ADN.", true) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Tile(getForm("huevo"), Modifier.width(96.dp)) }
    }
    for (family in FAMILIES) {
        val info = FAMILY_INFO.getValue(family)
        val baby = getForm("cria-$family")
        val pet = isPet(family)
        Family(
            if (pet) SPECIES_INFO.getValue(family).plural else "Monstruos · Familia ${info.name}",
            if (pet) "Las formas por las que pasa según cómo lo críes, sea de la raza que sea." else info.blurb,
            false,
        ) {
            Box(Modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.Center) { Tile(baby, Modifier.width(96.dp)) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (young in childrenOf(baby.id)) {
                    Column(Modifier.weight(1f)) {
                        Box(Modifier.fillMaxWidth().height(3.dp).background(Nd.line))
                        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Tile(young, young = true)
                            for (adult in childrenOf(young.id)) Tile(adult)
                        }
                    }
                }
            }
            // Las ocultas cuelgan aparte, tras una raya discontinua.
            T("Formas ocultas", Nd.smSoft, Modifier.padding(top = 16.dp, bottom = 8.dp))
            Box(Modifier.fillMaxWidth().height(3.dp).dashed(Nd.line))
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (form in secretsOf(family)) Box(Modifier.weight(1f)) { Tile(form) }
            }
        }
    }
}

// Raya discontinua horizontal del alto del elemento.
private fun Modifier.dashed(color: Color): Modifier = drawBehind {
    val dash = 8.dp.toPx()
    val y = size.height / 2
    drawLine(color, Offset(0f, y), Offset(size.width, y), size.height, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash * 0.75f)))
}

// ── Jugar ─────────────────────────────────────────────────────

// Minijuegos (gastan energía de juego y dan monedas) y juguetes.
@Composable
fun PlaySheet(game: Game, initial: String) {
    var tab by remember { mutableStateOf(initial) }
    SheetFrame(
        game, "Jugar",
        tabs = { ScrollTabs(listOf("juegos" to "Minijuegos", "juguetes" to "Juguetes"), tab, { tab = it }) },
    ) {
        if (tab == "juegos") GamesTab(game) else ToysTab(game)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GamesTab(game: Game) {
    val state = game.store.state ?: return
    val live = game.live ?: return
    val clock = game.clock
    val tickets = remember(clock, state) { game.store.tickets() }
    val max = state.user.ticketsMax
    val blocked = canPlayGame(live)
    val levels = remember { DIFFICULTIES.values.map { it.id to it.name } }
    val featured = state.daily?.featured
    // Mientras crece aún puede tomar otra rama.
    val growing = live.stage == "cria" || live.stage == "joven"
    // El juego del día va primero.
    val ordered = remember(featured) { MINIGAMES.sortedByDescending { it.id == featured } }

    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp).surface(Nd.sunWash, Nd.rMd).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            T("Energía de juego: ${tickets.tickets} de $max", Nd.strong.copy(lineHeight = 1.2.em))
            val next = tickets.nextAt
            T(
                if (next != null) "Otra en ${duration(next - game.store.now())}. Cada partida gasta una." else "Está al máximo. Cada partida gasta una.",
                Nd.smSoft,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            for (i in 0 until max) {
                val on = i < tickets.tickets
                Box(
                    Modifier.size(22.dp).then(
                        if (on) Modifier.candy(Nd.sun, Nd.sunEdge, 3.dp, Nd.rPill)
                        else Modifier.surface(Nd.paper, Nd.rPill).innerBorder(Nd.sunEdge, 2.dp, Nd.rPill),
                    ),
                )
            }
        }
    }
    if (blocked != null) T(blocked, Nd.sm, Modifier.padding(bottom = 12.dp), color = Nd.danger)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (mini in ordered) {
            var level by remember(mini.id) { mutableStateOf(game.api.level(mini.id)) }
            val mult = if (mini.id == featured) FEATURED_MULT else 1.0
            val lock = gameLock(mini, live)
            Column(
                Modifier
                    .fillMaxWidth()
                    .surface(Nd.cream, Nd.rMd)
                    .innerBorder(if (mult > 1.0) Nd.sun else Color.Transparent, 3.dp, Nd.rMd)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column {
                    if (mult > 1.0) {
                        Box(Modifier.padding(bottom = 6.dp).surface(Nd.sun, Nd.rPill).padding(start = 9.dp, end = 9.dp, top = 3.dp, bottom = 4.dp)) {
                            T("Juego del día · paga ×1,5", Nd.xsBlack)
                        }
                    }
                    T(mini.name, Nd.lg)
                    T(mini.blurb, Nd.smSoft, Modifier.padding(top = 2.dp))
                    for (trait in perksFor(mini.id, live.genes.traits)) {
                        val text = PERKS.getValue(trait).text.replaceFirstChar { it.lowercase() }
                        BasicText(
                            buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Black)) { append("${TRAITS.getValue(trait).name}: ") }
                                append(text)
                            },
                            Modifier.padding(top = 6.dp),
                            Nd.sm,
                        )
                    }
                    if (lock != null) {
                        T(
                            lock + if (growing) " Si juegas mucho con ella, puede que lo sea al crecer." else "",
                            Nd.sm,
                            Modifier.padding(top = 6.dp).fillMaxWidth().surface(Nd.track, Nd.rSm).padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 7.dp),
                        )
                    }
                }
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Tabs(levels, level, { level = game.api.level(mini.id, it) }, height = 36.dp, padX = 12.dp)
                    Btn(
                        { game.launchGame(mini.id, level) }, Modifier.widthIn(min = 132.dp), Tone.Pink,
                        enabled = tickets.tickets >= 1 && blocked == null && lock == null,
                    ) {
                        BtnText("Jugar")
                        Coin(20.dp)
                        T(fmt(baseReward(mini, level, mult)), Nd.strong.num)
                    }
                }
            }
        }
    }
}

@Composable
private fun ToysTab(game: Game) {
    val state = game.store.state ?: return
    val creature = state.creature ?: return
    val clock = game.clock
    val toys = itemsOfKind("juguete").filter { state.owned(it.id) > 0 }
    if (toys.isEmpty()) {
        Empty("Todavía no tienes juguetes. Jugar con uno divierte sin gastar energía de juego.", "Ir a la tienda") {
            game.openSheet(Sheet.Shop("juguete"))
        }
        return
    }
    val wait = TOY_COOLDOWN_MS / state.timeScale
    val now = remember(clock, state) { game.store.now() }
    Grid(toys.size) { index ->
        val item = toys[index]
        val left = (creature.cooldowns[item.id] ?: 0.0) + wait - now
        ItemTile(item.name, ThumbSpec.Item(item.id), { game.playToy(item) }, enabled = left <= 0) {
            if (left > 0) Tag("Listo en ${duration(left)}") else EffectChips(item, 1, center = true)
        }
    }
}

// ── Ficha ─────────────────────────────────────────────────────

private const val DAY = 24.0 * 60 * 60 * 1000

@Composable
private fun Fact(label: String, value: String, note: String? = null, code: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().surface(Nd.cream, Nd.rMd).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        T(label, Nd.sm.copy(lineHeight = 1.3.em), Modifier.width(112.dp).alignByBaseline(), color = Nd.inkSoft)
        Column(Modifier.weight(1f).alignByBaseline()) {
            T(value, if (code) Nd.strong.num.copy(lineHeight = 1.3.em, letterSpacing = 0.06.em) else Nd.strong.copy(lineHeight = 1.3.em))
            if (note != null) T(note, Nd.smSoft)
        }
    }
}

// Nivel de vínculo y lo que falta para el siguiente.
@Composable
private fun BondFact(creature: Creature) {
    val bond = creature.bond
    val level = bondLevel(bond)
    val next = BOND_LEVELS.getOrNull(level + 1)
    val points = "${floor(bond).toInt()} de 100."
    var note = if (next != null) "$points Con ${next.min.toInt()}: ${next.unlocks.replaceFirstChar { it.lowercase() }}"
    else "$points Cuanto más os queráis, más vivirá."
    if ("timido" in creature.genes.traits && bond < SHY_UNTIL) note += " Es tímida: le cuesta coger confianza."
    Fact("Vínculo", BOND_LEVELS[level].name, note)
}

// Ficha de la criatura, y debajo la cuenta del jugador.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileSheet(game: Game) {
    val state = game.store.state ?: return
    val creature: Creature = state.creature ?: return
    val clock = game.clock
    val now = remember(clock, state) { game.store.now() }
    val days = floor(((now - creature.bornAt) * state.timeScale) / DAY).toInt()
    val name = remember(creature.name) { FieldState(creature.name) }
    val rename = {
        val clean = name.text.trim()
        if (clean.isNotEmpty() && clean != creature.name) game.rename(clean)
    }

    SheetFrame(game, "Ficha") {
        var photographing by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val photo = {
            if (!photographing) {
                photographing = true
                scope.launch {
                    game.shareCard(creature)
                    photographing = false
                }
            }
        }
        Btn(photo, Modifier.fillMaxWidth(), tone = Tone.Pink, enabled = !photographing) {
            Icon(Ic.Camera)
            BtnText("Hacer una foto para compartir")
        }

        SectionTitle("Nombre")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Field(name, null, Modifier.weight(1f).semantics { contentDescription = "Nombre" }, maxLength = 14, capitalize = true, imeAction = ImeAction.Done, onDone = rename)
            Btn("Cambiar", { rename() }, tone = Tone.Mint)
        }

        SectionTitle("Cómo es")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Fact("Forma", creature.formName, "Etapa: ${creature.stageName.lowercase()}")
            val tendency = creature.tendency
            val nextAt = creature.nextStageAt
            if (tendency != null && nextAt != null) {
                val branch = BRANCH_INFO.getValue(tendency)
                Fact("Va camino de", "Ser ${branch.name.lowercase()}", "Evoluciona en ${duration(nextAt - now)}. ${branch.hint}")
            }
            val breed = creature.genes.breedInfo()
            if (breed != null) {
                Fact("Raza", breed.name, "Capa: ${breed.coats.getOrNull(creature.genes.coat ?: 0)?.name?.lowercase() ?: ""}")
            } else {
                val family = FAMILY_INFO.getValue(creature.genes.family)
                Fact("Familia", family.name, family.blurb)
            }
            Fact(
                "Edad", if (days == 0) "Menos de un día" else duration(days * DAY),
                if (creature.generation > 1) "Generación ${creature.generation}" else null,
            )
            BondFact(creature)
            val traits = creature.genes.traits.mapNotNull { TRAITS[it] }
            Fact(
                "Carácter",
                traits.mapIndexed { index, trait -> if (index > 0) trait.name.lowercase() else trait.name }.joinToString(" y "),
                traits.joinToString(" ") { it.blurb },
            )
            Fact(
                "Le encanta", if (creature.known.fav) getItem(creature.genes.favFood)?.name ?: "" else "Por descubrir",
                if (creature.known.fav) null else "Lo sabrás cuando se lo des de comer.",
            )
            Fact("No soporta", if (creature.known.hate) getItem(creature.genes.hateFood)?.name ?: "" else "Por descubrir")
            Fact("ADN", creature.code, "Su código es único.", code = true)
        }

        if (state.devTools) {
            SectionTitle("Pruebas")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((hours, label) in listOf(1 to "1 hora", 6 to "6 horas", 24 to "1 día")) {
                    Btn("Adelantar $label", { game.devSkip(hours) }, tone = Tone.Sun, small = true)
                }
                Btn("Probar avisos", { game.devCheckAlerts() }, tone = Tone.Sun, small = true)
                // Criaturas de modelo (.glb) en vez de esculpidas, donde las hay.
                Btn(if (game.modelsOn) "Modelos nuevos: sí" else "Modelos nuevos: no", { game.devModels(!game.modelsOn) }, tone = Tone.Sun, small = true)
            }
        }

        SettingsSections(game)

        SectionTitle("Tu cuenta")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            T("Has entrado como ${state.user.name}.", Nd.bold, Modifier.weight(1f))
            Btn("Salir", { game.logout() }, tone = null, small = true)
        }

        SectionTitle("Código de recuperación")
        RecoverySection(game)
    }
}
