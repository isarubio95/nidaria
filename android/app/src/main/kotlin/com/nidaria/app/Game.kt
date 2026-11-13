// El hilo conductor de la app: pide el estado al servidor, decide qué
// pantalla toca (entrada, huevos, despedida o la principal), encadena
// las animaciones de la escena y guarda lo que la interfaz enseña
// encima: hojas, diálogos, avisos, el bocadillo y el minijuego.

package com.nidaria.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nidaria.app.data.Api
import com.nidaria.app.data.ApiError
import com.nidaria.app.data.Daily
import com.nidaria.app.data.Mission
import com.nidaria.app.data.GameEvent
import com.nidaria.app.data.GameState
import com.nidaria.app.data.Payload
import com.nidaria.app.data.Store
import com.nidaria.app.data.Weather
import com.nidaria.app.data.WeatherNow
import com.nidaria.app.notify.Notifier
import com.nidaria.app.rules.BOND_LEVELS
import com.nidaria.app.rules.BRANCH_INFO
import com.nidaria.app.rules.Creature
import com.nidaria.app.rules.FAMILY_INFO
import com.nidaria.app.rules.Item
import com.nidaria.app.rules.MiniGame
import com.nidaria.app.rules.SECRET_INFO
import com.nidaria.app.rules.TRICKS
import com.nidaria.app.rules.TRICK_CHANCE
import com.nidaria.app.rules.TRICK_GAP_MS
import com.nidaria.app.rules.TRICK_LINES
import com.nidaria.app.rules.bondLevel
import com.nidaria.app.rules.getForm
import com.nidaria.app.rules.getGame
import com.nidaria.app.rules.moodOf
import com.nidaria.app.rules.nextStageAt
import com.nidaria.app.rules.stageDuration
import com.nidaria.app.scene.RoomView
import com.nidaria.app.scene.ThumbSpec
import com.nidaria.app.ui.Tone
import com.nidaria.app.ui.bondLine
import com.nidaria.app.ui.fmt
import com.nidaria.app.ui.idleLine
import com.nidaria.app.ui.line
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class Screen { Boot, Auth, Eggs, Farewell, Home, Offline, Broken }

sealed class Sheet {
    data class Shop(val category: String = "comida") : Sheet()
    data object Food : Sheet()
    data object Heal : Sheet()
    data object Wardrobe : Sheet()
    data object Criadex : Sheet()
    data class Play(val tab: String = "juegos") : Sheet()
    data object Profile : Sheet()
    data object Daily : Sheet()
}

class DialogAction(val label: String, val value: Any?, val tone: Tone? = null)

class DialogSpec(
    val title: String,
    val text: String? = null,
    val art: ThumbSpec? = null,
    val extra: (@Composable () -> Unit)? = null,
    val actions: List<DialogAction> = listOf(DialogAction("Vale", true, Tone.Pink)),
)

class DialogState(val spec: DialogSpec) {
    internal val result = CompletableDeferred<Any?>()
    var open by mutableStateOf(false)
    internal var closing = false
}

class ToastState(val id: Long, val message: String, val error: Boolean) {
    var open by mutableStateOf(false)
}

// Una partida de minijuego en pantalla.
class PlaySession(val game: MiniGame, val difficulty: String, val playId: String, val avatar: ThumbSpec) {
    var open by mutableStateOf(false)
    internal var closed = false
}

class Game(context: Context, val room: RoomView, private val scope: CoroutineScope) {
    private val app = context.applicationContext
    val api = Api(context)
    val store = Store()
    private val weatherSource = Weather(context)

    // Pide un permiso de Android y espera la respuesta. Lo pone la actividad.
    var askPermission: (suspend (String) -> Boolean)? = null

    // Avisos en este teléfono: activados por el jugador y permitidos por Android.
    var alertsOn by mutableStateOf(Notifier.enabled(app))
        private set
    var alertsAllowed by mutableStateOf(Notifier.allowed(app))
        private set

    // Clima real: si está activado y lo último que se sabe.
    var weatherOn by mutableStateOf(weatherSource.on)
        private set
    var weather by mutableStateOf<WeatherNow?>(weatherSource.cached())
        private set

    var screen by mutableStateOf(Screen.Boot)
        private set
    var message by mutableStateOf("")
        private set
    var sheet by mutableStateOf<Sheet?>(null)
        private set

    // Borde superior, en dp, de la hoja abierta en su posición final.
    var sheetTop by mutableStateOf<Float?>(null)
    val dialogs = mutableStateListOf<DialogState>()
    val toasts = mutableStateListOf<ToastState>()
    var play by mutableStateOf<PlaySession?>(null)
        private set

    // La criatura adelantada hasta ahora con la simulación local.
    var live by mutableStateOf<Creature?>(null)
        private set

    // Sube una vez por segundo: lo leen los textos que cuentan el tiempo.
    var clock by mutableIntStateOf(0)
        private set
    var bubbleText by mutableStateOf("")
        private set
    var bubbleOpen by mutableStateOf(false)
        private set
    var selectedEgg by mutableIntStateOf(0)
        private set

    // La pantalla principal entra animada solo si se llega desde otra.
    var homeArriving = false
        private set
    var busy by mutableStateOf(false)
        private set

    private var screenName = ""
    private var animating = false
    private val queue = Mutex()
    private val seenFarewell = HashSet<String>()
    private var resumed = true
    private var syncing = false
    private var lastSync = 0L
    private var lastPet = 0L
    private var petLine = 0L
    private var lastTrick = 0L
    private var eggTaps = 0
    private var eggJob: Job? = null
    private var bubbleJob: Job? = null
    private var chatterAt = 0L
    private var toastId = 0L

    init {
        room.onPet = { if (screen == Screen.Home) pet() }
        room.onTap = { if (screen == Screen.Home) tap() }
        room.onEggTap = { if (screen == Screen.Home) eggTap() }
        room.onEggPick = { index -> if (screen == Screen.Eggs) selectEgg(index) }
        room.onError = {
            message = "Tu teléfono no puede dibujar la escena en 3D (hace falta OpenGL ES 3.0)."
            screen = Screen.Broken
        }
    }

    private fun clockNow() = System.currentTimeMillis()

    // ── Llamadas al servidor ──

    // Ejecuta una llamada, aplica su resultado y enseña el error si lo
    // hay. Devuelve el payload o null si falló.
    suspend fun send(silent: Boolean = false, call: suspend Api.() -> Payload): Payload? = try {
        val payload = api.call()
        if (payload.state != null) apply(payload)
        payload
    } catch (error: ApiError) {
        if (error.status == 401) {
            store.clear()
            route(null, Payload())
        } else if (!silent) {
            toast(error.message ?: "Algo ha fallado. Inténtalo de nuevo.", error = true)
        }
        null
    }

    fun apply(payload: Payload) {
        val state = payload.state ?: return
        val before = store.state?.daily
        store.apply(state)
        Notifier.remember(app, state, store.skew)
        live = store.live()?.creature
        route(state, payload)
        announceMissions(before, state.daily)
        linkDevice(state)
    }

    // Avisa con un mensaje breve cuando una acción cumple una misión.
    private fun announceMissions(before: Daily?, after: Daily?) {
        if (before == null || after == null || before.day != after.day) return
        val wasDone = before.missions.filter { it.done }.map { it.id }.toSet()
        for (mission in after.missions) {
            if (mission.done && !mission.claimed && mission.id !in wasDone) toast("¡Misión cumplida! ${mission.text}.", holdMs = 3400)
        }
    }

    // Una vez por visita, tras entrar: la zona horaria del teléfono, que
    // decide cuándo cambian las misiones.
    private var linked = false
    private fun linkDevice(state: GameState) {
        if (linked) return
        linked = true
        val zone = Api.localTimeZone()
        if (state.user.timeZone != zone) scope.launch { send(silent = true) { setTimeZone(zone) } }
    }

    fun boot() {
        scope.launch {
            busy = true
            try {
                apply(api.state())
                refreshWeather()
            } catch (error: ApiError) {
                if (error.status == 401) {
                    route(null, Payload())
                } else {
                    room.showDemo()
                    message = error.message ?: ""
                    screenName = "offline"
                    screen = Screen.Offline
                }
            } finally {
                busy = false
            }
        }
    }

    // Cambia de servidor: la sesión del anterior ya no vale.
    fun changeServer(url: String) {
        api.server = url
        store.clear()
        live = null
        screenName = ""
        screen = Screen.Boot
        boot()
    }

    suspend fun signIn(register: Boolean, name: String, password: String) {
        val payload = if (register) api.register(name, password) else api.login(name, password)
        apply(payload)
    }

    // ── Qué pantalla toca ──

    private fun show(name: String, next: Screen) {
        screenName = name
        screen = next
    }

    private fun enqueue(block: suspend () -> Unit) {
        scope.launch { queue.withLock { block() } }
    }

    private suspend fun announce(creature: Creature, first: GameEvent) {
        val form = getForm(creature.formId)
        val art = ThumbSpec.creature(creature.formId, creature.genes)
        if (first.from == "huevo" && form.stage == "cria") {
            dialog(
                DialogSpec(
                    title = "¡${creature.name} ha nacido!",
                    text = "Es ${form.name}, de la familia ${FAMILY_INFO.getValue(form.family!!).name}. Según cómo la cuides, crecerá por una rama u otra.",
                    art = art,
                    actions = listOf(DialogAction("Conocerla", true, Tone.Pink)),
                ),
            )
            return
        }
        if (form.secret != null) {
            dialog(
                DialogSpec(
                    title = "¡Una forma oculta!",
                    text = "${creature.name} ahora es ${form.name}. ${SECRET_INFO.getValue(form.secret).reason}",
                    art = art,
                    actions = listOf(DialogAction("Verla", true, Tone.Pink)),
                ),
            )
            return
        }
        val branch = form.branch?.let { BRANCH_INFO.getValue(it).name.lowercase() } ?: ""
        dialog(
            DialogSpec(
                title = "¡${creature.name} ha evolucionado!",
                text = "Ahora es ${form.name}. La has criado $branch y por eso ha seguido esa rama.",
                art = art,
                actions = listOf(DialogAction("Verla", true, Tone.Pink)),
            ),
        )
    }

    private fun route(state: GameState?, payload: Payload) {
        val events = payload.events

        if (state == null) {
            linked = false
            dismissSheet()
            live = null
            bubbleOpen = false
            room.setNight(false)
            room.showDemo()
            show("auth", Screen.Auth)
            return
        }

        val creature = state.creature
        if (creature == null || (!creature.alive && creature.id in seenFarewell)) {
            if (screenName != "eggs") {
                dismissSheet()
                bubbleOpen = false
                room.setNight(false)
                state.eggOffer?.let { room.showEggs(it) }
                show("eggs", Screen.Eggs)
                selectEgg(0)
            }
            return
        }

        if (!creature.alive) {
            if (screenName == "farewell" || animating) return
            val wasHome = screenName == "home"
            animating = true
            enqueue {
                closeSheet()
                bubbleOpen = false
                show("farewell", Screen.Boot)
                room.setNight(false)
                if (wasHome && events.any { it.type == "died" }) room.farewell()
                animating = false
                screen = Screen.Farewell
            }
            return
        }

        val fresh = screenName != "home"
        if (fresh) {
            homeArriving = screenName != ""
            lastSync = clockNow()
            chatterAt = clockNow() + 9000
            show("home", Screen.Home)
        }
        paintRoom()

        val evolved = events.filter { it.type == "evolved" }
        if (evolved.isNotEmpty() && !fresh && !animating) {
            animating = true
            enqueue {
                closeSheet()
                if (evolved[0].from == "huevo") room.hatch(creature) else room.evolve(creature)
                animating = false
                room.showCreature(store.state?.creature ?: creature)
                announce(creature, evolved[0])
                say(line("nace"))
                offerAlerts(creature)
            }
        } else if (!animating) {
            room.showCreature(creature)
        }

        for (event in events) {
            when (event.type) {
                "sick" -> toast("${creature.name} se ha puesto enferma. Dale medicina.", error = true, holdMs = 4200)
                "collapsed" -> toast("${creature.name} se ha dormido de puro cansancio.", holdMs = 3600)
                "woke" -> toast("${creature.name} se ha despertado.")
                "bond" -> {
                    val level = BOND_LEVELS.getOrNull(event.level) ?: continue
                    toast(level.reached.replace("{name}", creature.name), holdMs = 4000)
                    val fond = bondLine(creature, state.user.name, event.level)
                    if (fond != null && !animating) say(fond)
                }
            }
        }
    }

    fun continueFromFarewell() {
        val state = store.state ?: return
        state.creature?.let { seenFarewell.add(it.id) }
        route(state, Payload())
    }

    fun selectEgg(index: Int) {
        selectedEgg = index
        room.selectEgg(index)
    }

    // ── Pantalla principal ──

    // Lleva a la escena lo que depende de la criatura en este instante.
    private fun paintRoom() {
        val state = store.state ?: return
        val creature = live ?: return
        if (screen != Screen.Home) return
        if (creature.stage == "huevo") {
            val total = stageDuration("huevo", state.timeScale) ?: 1.0
            val left = max(0.0, (nextStageAt(creature, state.timeScale) ?: 0.0) - store.now())
            room.setEggProgress((1.0 - left / total).toFloat())
        }
        room.setNight(creature.asleep)
        room.setMood(moodOf(creature))
    }

    // Una vez por segundo mientras la pantalla principal está a la vista.
    fun tick() {
        clock++
        val result = store.live() ?: return
        live = result.creature
        paintRoom()
        val now = clockNow()
        if (result.events.isNotEmpty() || now - lastSync > 45000) sync()
        val creature = result.creature
        val quiet = sheet == null && dialogs.isEmpty() && play == null && resumed
        if (now > chatterAt && quiet && creature.alive && !creature.asleep && creature.stage != "huevo") {
            say(idleLine(creature, moodOf(creature), weather, store.state?.user?.name))
        }
    }

    private fun sync() {
        if (syncing) return
        syncing = true
        lastSync = clockNow()
        scope.launch {
            send(silent = true) { state() }
            syncing = false
        }
    }

    fun onResume() {
        resumed = true
        // El permiso de avisos se puede quitar o dar desde los ajustes del teléfono.
        alertsAllowed = Notifier.allowed(app)
        if (screen == Screen.Home) sync()
        refreshWeather()
    }

    fun onPause() {
        resumed = false
    }

    fun say(text: String, seconds: Float = 2.6f) {
        if (screen != Screen.Home || !room.hasActor) return
        bubbleJob?.cancel()
        bubbleText = text
        bubbleOpen = true
        room.say(min(1.4f, seconds))
        chatterAt = clockNow() + 14000 + (Random.nextFloat() * 12000).toLong()
        bubbleJob = scope.launch {
            delay((seconds * 1000).toLong())
            bubbleOpen = false
        }
    }

    private fun pet() {
        val creature = live ?: return
        if (creature.asleep || !creature.alive) return
        val now = clockNow()
        if (now - lastPet < 1500) return
        lastPet = now
        scope.launch { send(silent = true) { pet() } }
        if (now - petLine > 6000) {
            petLine = now
            say(line("caricia"), 1.6f)
        }
    }

    private fun tap() {
        val creature = live ?: return
        if (!creature.alive) return
        when {
            creature.asleep -> say("Zzz…", 1.4f)
            creature.stage != "huevo" && trick(creature) -> Unit
            else -> say(idleLine(creature, moodOf(creature), weather, store.state?.user?.name))
        }
    }

    // Con confianza, al tocarla a veces enseña un truco.
    private fun trick(creature: Creature): Boolean {
        val known = TRICKS[min(3, bondLevel(creature.bond))] ?: return false
        val now = clockNow()
        if (now - lastTrick < TRICK_GAP_MS || Random.nextDouble() >= TRICK_CHANCE) return false
        lastTrick = now
        room.trick(known.random())
        say(TRICK_LINES.random(), 1.8f)
        return true
    }

    // Los toques al huevo se juntan y se mandan de una vez.
    private fun eggTap() {
        eggTaps += 1
        eggJob?.cancel()
        eggJob = scope.launch {
            delay(350)
            val taps = eggTaps
            eggTaps = 0
            send(silent = true) { warmEgg(taps) }
        }
    }

    // ── Cuidados ──

    fun toggleSleep(): Job = scope.launch {
        val asleep = live?.asleep ?: return@launch
        val payload = send { sleep(!asleep) } ?: return@launch
        say(line(payload.text("reaction")))
    }

    fun bathe(): Job = scope.launch {
        val soap = store.state?.owned("jabon") ?: 0
        var itemId: String? = null
        if (soap > 0) {
            val choice = dialog(
                DialogSpec(
                    title = "¿Cómo la bañamos?",
                    text = "El jabón de burbujas la deja limpia del todo y le divierte. Te ${if (soap == 1) "queda 1" else "quedan $soap"}.",
                    actions = listOf(
                        DialogAction("Con jabón de burbujas", "jabon", Tone.Mint),
                        DialogAction("Baño rápido", "rapido", Tone.Sun),
                        DialogAction("Ahora no", null),
                    ),
                ),
            ) ?: return@launch
            if (choice == "jabon") itemId = "jabon"
        }
        send { bathe(itemId) } ?: return@launch
        room.bath()
        say(line("bano"))
    }

    fun collect(): Job = scope.launch {
        val payload = send { collect() } ?: return@launch
        toast("Has recogido ${fmt(payload.int("coins"))} monedas.")
    }

    fun feed(item: Item): Job = scope.launch {
        dismissSheet()
        val payload = send { feed(item.id) } ?: return@launch
        val reaction = payload.text("reaction")
        room.feed(item.id, reaction)
        say(line(reaction))
    }

    fun heal(item: Item): Job = scope.launch {
        dismissSheet()
        send { heal(item.id) } ?: return@launch
        room.heal()
        say(line("cura"))
    }

    fun playToy(item: Item): Job = scope.launch {
        dismissSheet()
        send { toy(item.id) } ?: return@launch
        room.playToy(item.id)
        say(line("juega"))
    }

    fun buy(item: Item, qty: Int): Job = scope.launch {
        send { buy(item.id, qty) } ?: return@launch
        toast(if (qty > 1) "Has comprado $qty de ${item.name}." else "Has comprado: ${item.name}.")
    }

    fun equip(slot: String, item: Item?, announce: Boolean = false): Job = scope.launch {
        val payload = send { equip(slot, item?.id) } ?: return@launch
        if (announce && item != null) toast("${payload.state?.creature?.name} lleva puesto: ${item.name}.")
    }

    fun rename(name: String): Job = scope.launch {
        val payload = send { rename(name) } ?: return@launch
        toast("Ahora se llama ${payload.state?.creature?.name}.")
    }

    // ── Avisos ──

    // Tras la primera eclosión o evolución, una sola vez, se ofrecen los avisos.
    private suspend fun offerAlerts(creature: Creature) {
        val prefs = app.getSharedPreferences("nidaria", Context.MODE_PRIVATE)
        if (alertsOn || prefs.getBoolean("avisos-ofrecidos", false)) return
        prefs.edit().putBoolean("avisos-ofrecidos", true).apply()
        val yes = dialog(
            DialogSpec(
                title = "¿Te aviso si ${creature.name} te necesita?",
                text = "Te avisaremos si tiene hambre, se pone enferma o va a evolucionar, aunque tengas el juego cerrado. De noche, nunca.",
                actions = listOf(DialogAction("Sí, avísame", true, Tone.Pink), DialogAction("Ahora no", false)),
            ),
        )
        if (yes == true) enableAlerts().join()
    }

    fun enableAlerts(): Job = scope.launch {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = askPermission?.invoke(Manifest.permission.POST_NOTIFICATIONS) ?: false
            if (!granted) {
                alertsAllowed = Notifier.allowed(app)
                toast("Sin permiso, el teléfono no puede enseñar los avisos.", error = true, holdMs = 3600)
                return@launch
            }
        }
        Notifier.setEnabled(app, true)
        alertsOn = true
        alertsAllowed = Notifier.allowed(app)
        toast("Listo. Te avisaremos en este teléfono.")
    }

    fun disableAlerts() {
        Notifier.setEnabled(app, false)
        Notifier.clear(app)
        alertsOn = false
        toast("Este teléfono ya no te avisará.")
    }

    // Qué avisos quiere el jugador. Se guarda en la cuenta, como en la web.
    fun setAlertGroup(group: String, on: Boolean): Job = scope.launch {
        send { setAlert(group, on) }
    }

    // Los avisos están bloqueados en Android: se cambian en sus ajustes.
    fun openAlertSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, app.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        app.startActivity(intent)
    }

    // Solo para probar: repasa los avisos dentro de unos segundos.
    fun devCheckAlerts() {
        Notifier.checkSoon(app, 10)
        toast("Sal del juego: en unos segundos se repasan los avisos.", holdMs = 3600)
    }

    // ── Clima ──

    private fun refreshWeather() {
        if (!weatherOn) {
            room.setWeather(null)
            return
        }
        scope.launch {
            weather = weatherSource.refresh() ?: weather
            room.setWeather(weather?.kind)
        }
    }

    fun enableWeather(): Job = scope.launch {
        val granted = askPermission?.invoke(Manifest.permission.ACCESS_COARSE_LOCATION) ?: false
        if (!granted) {
            toast("Sin permiso de ubicación no se puede saber qué tiempo hace.", error = true, holdMs = 3600)
            return@launch
        }
        val now = weatherSource.enable()
        if (!weatherSource.on) {
            toast("No se ha podido saber dónde estás. Prueba de nuevo en un momento.", error = true, holdMs = 3600)
            return@launch
        }
        weatherOn = true
        weather = now
        room.setWeather(now?.kind)
    }

    fun disableWeather() {
        weatherSource.disable()
        weatherOn = false
        weather = null
        room.setWeather(null)
    }

    // Recoge el premio de una misión. Con la tercera llega el de la racha.
    fun claim(mission: Mission): Job = scope.launch {
        val payload = send { claim(mission.id) } ?: return@launch
        val coins = payload.int("coins")
        val bonus = payload.int("bonus")
        val streak = payload.int("streak")
        if (bonus == 0) {
            toast("Has recogido ${fmt(coins)} monedas.")
            return@launch
        }
        dialog(
            DialogSpec(
                title = "¡Misiones del día completas!",
                text = if (streak > 1) "Llevas una racha de ${com.nidaria.app.ui.days(streak)}. Mañana habrá tres más."
                else "Has empezado una racha. Mañana habrá tres más.",
                extra = { com.nidaria.app.ui.Reward(coins + bonus) },
                actions = listOf(DialogAction("¡Bien!", true, Tone.Sun)),
            ),
        )
    }

    fun devSkip(hours: Int): Job = scope.launch {
        closeSheet()
        send { devSkip(hours) }
    }

    fun logout(): Job = scope.launch {
        closeSheet()
        try {
            api.logout()
        } catch (error: ApiError) {
            // Sin conexión también se sale: la sesión se borra del teléfono.
        }
        // Este teléfono deja de avisar de la cuenta que sale.
        Notifier.setEnabled(app, false)
        Notifier.forget(app)
        alertsOn = false
        store.clear()
        route(null, Payload())
    }

    // ── Minijuegos ──

    fun launchGame(gameId: String, difficulty: String): Job = scope.launch {
        closeSheet()
        val game = getGame(gameId) ?: return@launch
        val started = send { startGame(gameId, difficulty) } ?: return@launch
        val playId = started.text("playId") ?: return@launch
        val creature = store.state?.creature ?: return@launch
        bubbleOpen = false
        play = PlaySession(game, difficulty, playId, ThumbSpec.creature(creature.formId, creature.genes))
        room.setPaused(true)
    }

    fun finishGame(session: PlaySession, score: Int, abandoned: Boolean = false): Job = scope.launch {
        if (session.closed) return@launch
        session.closed = true
        val finished = send { finishGame(session.playId, score) }
        session.open = false
        room.setPaused(false)
        launch {
            delay(260)
            if (play === session) play = null
        }
        if (finished == null || abandoned) return@launch
        val won = finished.flag("won")
        val coins = finished.int("coins")
        val target = finished.int("target")
        dialog(
            DialogSpec(
                title = if (won) "¡Has ganado!" else "No ha podido ser",
                text = if (won) "Has hecho $score y el objetivo era $target."
                else "Has hecho $score y hacían falta $target. La energía de juego se ha gastado igual.",
                extra = if (won) ({ com.nidaria.app.ui.Reward(coins) }) else null,
                actions = listOf(DialogAction(if (won) "Recoger" else "Volver", true, if (won) Tone.Sun else Tone.Pink)),
            ),
        )
        room.cheer(won)
        say(line(if (won) "gana" else "pierde"))
    }

    fun leaveGame(session: PlaySession): Job = scope.launch {
        val sure = dialog(
            DialogSpec(
                title = "¿Salir del juego?",
                text = "Perderás esta partida y la energía de juego que ha costado.",
                actions = listOf(DialogAction("Seguir jugando", false, Tone.Pink), DialogAction("Salir", true)),
            ),
        )
        if (sure == true) finishGame(session, 0, abandoned = true)
    }

    // ── Hojas, diálogos y avisos ──

    fun openSheet(next: Sheet) {
        sheet = next
    }

    fun dismissSheet() {
        sheet = null
    }

    // Cierra la hoja y espera a que termine de bajar.
    suspend fun closeSheet() {
        if (sheet == null) return
        sheet = null
        delay(350)
    }

    suspend fun dialog(spec: DialogSpec): Any? {
        val state = DialogState(spec)
        dialogs.add(state)
        return state.result.await()
    }

    fun showDialog(spec: DialogSpec) {
        scope.launch { dialog(spec) }
    }

    fun closeDialog(state: DialogState, value: Any?) {
        if (state.closing) return
        state.closing = true
        state.open = false
        scope.launch {
            delay(150)
            dialogs.remove(state)
            state.result.complete(value)
        }
    }

    fun toast(message: String, error: Boolean = false, holdMs: Long = 2600) {
        val state = ToastState(++toastId, message, error)
        toasts.add(state)
        while (toasts.size > 2) toasts.removeAt(0)
        scope.launch {
            delay(16)
            state.open = true
            delay(holdMs)
            state.open = false
            delay(290)
            toasts.remove(state)
        }
    }

    // El botón «atrás» deshace lo último que se abrió. Devuelve si hizo algo.
    fun back(): Boolean {
        val dialog = dialogs.lastOrNull()
        if (dialog != null) {
            closeDialog(dialog, dialog.spec.actions.last().value)
            return true
        }
        val session = play
        if (session != null) {
            leaveGame(session)
            return true
        }
        if (sheet != null) {
            dismissSheet()
            return true
        }
        return false
    }
}
