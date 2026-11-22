package io.github.ththemabit.questlog.ai

import io.github.ththemabit.questlog.core.AiConfig
import io.github.ththemabit.questlog.core.GameState
import io.github.ththemabit.questlog.core.Persona
import io.github.ththemabit.questlog.core.Quest
import io.github.ththemabit.questlog.core.SaveData
import io.github.ththemabit.questlog.data.AppState
import io.github.ththemabit.questlog.data.Store
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

/**
 * Der KI-Spielleiter im Hintergrund. Schreibt, solange die App offen ist, nacheinander
 * Tagesbriefing, Questtexte und die Wochensaga vor und legt sie im storyCache ab.
 * Die Oberfläche liest nur den Cache, wartet also nie auf die KI. Ist der Server nicht
 * erreichbar, wird mit wachsendem Abstand neu versucht und solange gelten die Vorlagen.
 */
class AiService(private val store: Store, private val zone: ZoneId = ZoneId.systemDefault()) : AiPort {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _status = MutableStateFlow(AiStatus())
    private val _chat = MutableStateFlow<List<ChatTurn>>(emptyList())
    private val _live = MutableStateFlow<LiveReply?>(null)
    override val status: StateFlow<AiStatus> = _status
    override val chat: StateFlow<List<ChatTurn>> = _chat
    override val live: StateFlow<LiveReply?> = _live

    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val focusIds = MutableStateFlow<List<String>>(emptyList())
    /** In diesem Lauf schon erledigte Aufträge (der Cache kommt minimal später an). */
    private val recent: MutableSet<String> = ConcurrentHashMap.newKeySet()
    /** Aufträge mit unbrauchbarer Antwort: nach zwei Versuchen bis zum nächsten Start ausgelassen. */
    private val failures = ConcurrentHashMap<String, Int>()
    @Volatile private var foreground = false
    @Volatile private var chatting = false
    @Volatile private var background: Job? = null
    private var chatJob: Job? = null

    private enum class Kind { BRIEF, TEXTS, SAGA }
    private data class Task(val key: String, val kind: Kind, val label: String, val questId: String? = null)
    private class Unusable : Exception()

    init {
        // Aufwecken, wenn sich etwas ändert, wofür neue Texte nötig sein könnten
        scope.launch {
            store.state.filterNotNull().map { trigger(it) }.distinctUntilChanged().collect { wake.trySend(Unit) }
        }
        scope.launch { loop() }
    }

    /** Beendet alles (für Tests; in der App lebt der Dienst so lange wie der Prozess). */
    fun close() = scope.cancel()

    /** Läuft nur, solange die App sichtbar ist. */
    fun setForeground(on: Boolean) {
        foreground = on
        if (on) wake.trySend(Unit)
    }

    private fun hour(now: Long) = Instant.ofEpochMilli(now).atZone(zone).hour

    private fun trigger(st: AppState): Int {
        val s = st.save.settings
        val persona = Persona.active(s)
        return listOf(
            s.ai, persona, GameMaster.briefingTag(st.game, persona, hour(st.game.now)), st.game.recap?.weekStart,
            st.save.quests.filter { !it.archived }.map { it.id + GameMaster.signature(it) },
        ).hashCode()
    }

    private suspend fun sleep(ms: Long) {
        withTimeoutOrNull(ms) { wake.receive() }
    }

    private fun client(cfg: AiConfig, readTimeoutMs: Int = 180_000) = AiClient(cfg.baseUrl, cfg.model, cfg.apiKey, readTimeoutMs = readTimeoutMs)

    // ---------------------------------------------------------------- Hintergrund

    private suspend fun loop() {
        var failStreak = 0
        while (currentCoroutineContext().isActive) {
            val st = store.awaitState()
            val save = store.snapshot() ?: st.save
            val cfg = save.settings.ai
            when {
                !cfg.enabled -> {
                    _status.value = AiStatus(AiPhase.OFF, "Aus")
                    sleep(Long.MAX_VALUE)
                    continue
                }
                !cfg.ready -> {
                    _status.value = AiStatus(AiPhase.OFF, if (cfg.baseUrl.isBlank()) "Keine Adresse eingetragen" else "Kein Modell gewählt")
                    sleep(Long.MAX_VALUE)
                    continue
                }
                !foreground || chatting -> {
                    sleep(Long.MAX_VALUE)
                    continue
                }
            }
            val task = next(save, st.game)
            if (task == null) {
                failStreak = 0
                _status.value = idle(save, st.game)
                sleep(15 * 60_000L)
                continue
            }
            _status.value = AiStatus(AiPhase.WORKING, task.label, working = if (task.kind == Kind.TEXTS) GameMaster.textsKey(task.questId!!) else task.key)
            try {
                val d = scope.async { run(task, cfg) }
                background = d
                d.await()
                recent += task.key
                failStreak = 0
            } catch (e: CancellationException) {
                if (!currentCoroutineContext().isActive) throw e
                // vom Gespräch oder einer neuen Einstellung unterbrochen: später nochmal
            } catch (e: Unusable) {
                failures[task.key] = (failures[task.key] ?: 0) + 1
            } catch (e: AiException) {
                failStreak++
                _status.value = AiStatus(AiPhase.OFFLINE, "Nicht erreichbar", error = e.message)
                sleep(min(15 * 60_000L, 20_000L shl min(failStreak - 1, 6)))
            } catch (e: Exception) {
                failures[task.key] = (failures[task.key] ?: 0) + 1
            } finally {
                background = null
            }
        }
    }

    private fun idle(save: SaveData, game: GameState): AiStatus {
        val cfg = save.settings.ai
        val finished = game.board.done.filter { !it.quest.recurring }.map { it.quest.id }.toSet()
        val active = save.quests.filter { !it.archived && it.id !in finished }
        val with = active.count { GameMaster.textsFor(save, it) != null }
        val text = if (cfg.questTexts && active.isNotEmpty()) "Bereit · eigene Texte für $with von ${active.size} Quests" else "Bereit"
        return AiStatus(AiPhase.IDLE, text)
    }

    private fun fresh(key: String) = key !in recent && (failures[key] ?: 0) < 2

    private fun next(save: SaveData, game: GameState): Task? {
        val cfg = save.settings.ai
        val persona = Persona.active(save.settings)
        val hour = hour(game.now)
        if (cfg.briefing) {
            val tag = GameMaster.briefingTag(game, persona, hour)
            if (GameMaster.briefingTag(save) != tag && fresh("brief|$tag")) return Task("brief|$tag", Kind.BRIEF, "Schreibt das Tagesbriefing …")
        }
        val byId = save.quests.filter { !it.archived }.associateBy { it.id }
        fun textsFor(ids: List<String>): Task? {
            for (id in ids) {
                val q = byId[id] ?: continue
                if (!GameMaster.needsTexts(save, q, persona)) continue
                val key = "quest|${q.id}|${GameMaster.signature(q)}|${persona.key}"
                if (fresh(key)) return Task(key, Kind.TEXTS, "Schreibt Texte für „${q.title}“ …", q.id)
            }
            return null
        }
        if (cfg.questTexts) textsFor(focusIds.value)?.let { return it }
        if (cfg.saga) game.recap?.let { r ->
            val key = GameMaster.sagaKey(r.weekStart)
            if (save.storyCache[key] == null && fresh(key)) return Task(key, Kind.SAGA, "Schreibt die Heldensaga …")
        }
        if (cfg.questTexts) {
            val b = game.board
            // Erledigte einmalige Quests brauchen keine Texte mehr
            val finished = b.done.filter { !it.quest.recurring }.map { it.quest.id }.toSet()
            val order = (b.overdue + b.open + b.bosses + b.early + b.done).map { it.quest.id } + byId.keys
            textsFor(order.distinct().filter { it !in finished })?.let { return it }
        }
        return null
    }

    private suspend fun run(t: Task, cfg: AiConfig) {
        val st = store.awaitState()
        val save = store.snapshot() ?: st.save
        val persona = Persona.active(save.settings)
        val c = client(cfg)
        when (t.kind) {
            Kind.BRIEF -> {
                val hour = hour(st.game.now)
                val raw = c.chat(GameMaster.briefingMessages(st.game, persona, hour), maxTokens = 900, temperature = 0.9)
                val text = GameMaster.cleanText(raw, 360)
                if (text.length < 12) throw Unusable()
                store.cacheStory(GameMaster.BRIEF_KEY, GameMaster.encodeBriefing(Briefing(GameMaster.briefingTag(st.game, persona, hour), st.game.today.toString(), text)))
            }
            Kind.TEXTS -> {
                val q = save.quests.firstOrNull { it.id == t.questId } ?: return
                val raw = c.chat(GameMaster.questTextMessages(q, persona), maxTokens = 1400, temperature = 0.85)
                val parsed = GameMaster.parseQuestTexts(raw, GameMaster.signature(q), persona) ?: throw Unusable()
                // Ein Intro aus dem Gespräch behalten: Das hat die Person schon gelesen
                val old = GameMaster.texts(store.snapshot() ?: save, q.id)
                val merged = if (old != null && old.partial && old.sig == parsed.sig && old.intro != null) parsed.copy(intro = old.intro) else parsed
                store.cacheStory(GameMaster.textsKey(q.id), GameMaster.encode(merged))
            }
            Kind.SAGA -> {
                val recap = st.game.recap ?: return
                val raw = c.chat(GameMaster.sagaMessages(recap, persona, save.settings.chaos.gerald), maxTokens = 1800, temperature = 0.9)
                val text = GameMaster.cleanSaga(raw)
                if (text.split("\n\n").size < 2) throw Unusable()
                store.cacheStory(t.key, text)
            }
        }
    }

    // ---------------------------------------------------------------- Gespräch

    override fun send(text: String) {
        val msg = text.trim()
        if (msg.isEmpty() || chatJob?.isActive == true) return
        val cfg = (store.snapshot() ?: return).settings.ai
        if (!cfg.ready) return
        _chat.update { it + ChatTurn(true, msg) }
        chatting = true
        background?.cancel()
        chatJob = scope.launch {
            _live.value = LiveReply("")
            try {
                val st = store.awaitState()
                val save = store.snapshot() ?: st.save
                val persona = Persona.active(save.settings)
                val raw = client(cfg, readTimeoutMs = 240_000).chat(
                    GameMaster.forgeMessages(save, st.game, _chat.value, persona), maxTokens = 2600, temperature = 0.6,
                ) { visible ->
                    val cut = visible.indexOf(GameMaster.MARK)
                    _live.value = if (cut < 0) LiveReply(visible.trimEnd()) else LiveReply(visible.substring(0, cut).trimEnd(), listing = true)
                }
                val reply = GameMaster.parseForge(raw, store.snapshot() ?: save, st.game.today)
                val note = if (reply.unreadable) "\n\n(Die Quest-Liste am Ende war unleserlich. Sag einfach „nochmal“.)" else ""
                _chat.update { it + ChatTurn(false, reply.message + note, reply.proposals) }
            } catch (e: CancellationException) {
                _chat.update { it + ChatTurn(false, "Abgebrochen.", error = true) }
                throw e
            } catch (e: AiException) {
                _chat.update { it + ChatTurn(false, e.message ?: "Unbekannter Fehler.", error = true) }
            } finally {
                _live.value = null
                chatting = false
                wake.trySend(Unit)
            }
        }
    }

    override fun stop() {
        chatJob?.cancel()
    }

    override fun clearChat() {
        chatJob?.cancel()
        _chat.value = emptyList()
    }

    override fun apply(turn: Int, picks: Set<Int>): Int {
        val t = _chat.value.getOrNull(turn) ?: return 0
        if (t.applied || picks.isEmpty()) return 0
        val save = store.snapshot() ?: return 0
        val persona = Persona.active(save.settings)
        val now = store.now()
        val add = ArrayList<Quest>()
        val update = ArrayList<Quest>()
        val archive = HashSet<String>()
        val cache = HashMap<String, String>()
        t.proposals.forEachIndexed { i, p ->
            if (i !in picks) return@forEachIndexed
            when (p) {
                is Proposal.New -> {
                    val q = p.draft.toQuest(store.newId(), now) { store.newId() }
                    add += q
                    p.intro?.let { cache[GameMaster.textsKey(q.id)] = GameMaster.encode(QuestTexts(GameMaster.signature(q), persona.key, intro = it, partial = true)) }
                }
                is Proposal.Change -> save.quests.firstOrNull { it.id == p.questId }?.let { cur -> update += p.after.copy(archived = cur.archived) }
                is Proposal.Archive -> archive += p.questId
            }
        }
        store.applyPlan(add, update, archive, cache)
        _chat.update { list -> list.mapIndexed { i, c -> if (i == turn) c.copy(applied = true, picked = picks) else c } }
        wake.trySend(Unit)
        return add.size + update.size + archive.size
    }

    override fun focus(questId: String) {
        focusIds.update { (listOf(questId) + it).distinct().take(5) }
        wake.trySend(Unit)
    }

    override fun rewriteAll() {
        background?.cancel()
        recent.clear()
        failures.clear()
        store.dropStory { it.startsWith("quest-") || it.startsWith("saga-") || it == GameMaster.BRIEF_KEY }
        wake.trySend(Unit)
    }

    override suspend fun probe(cfg: AiConfig): Probe {
        if (cfg.baseUrl.isBlank()) return Probe(false, "Trag zuerst die Adresse ein.")
        val c = AiClient(cfg.baseUrl, cfg.model, cfg.apiKey, connectTimeoutMs = 5_000, readTimeoutMs = 120_000)
        val models = try {
            c.models()
        } catch (e: AiException) {
            // Manche Server kennen /models nicht. Mit Modellnamen trotzdem den Chat versuchen.
            if (cfg.model.isBlank()) return Probe(false, e.message ?: "Fehler")
            emptyList()
        }
        if (cfg.model.isBlank()) {
            return if (models.isEmpty()) Probe(false, "Verbunden, aber der Server meldet keine Modelle. Trag den Namen von Hand ein.")
            else Probe(true, "Verbunden mit ${c.base}. ${models.size} ${if (models.size == 1) "Modell" else "Modelle"} gefunden. Wähle eins aus.", models)
        }
        return try {
            val reply = c.chat(listOf(ChatMessage("user", "Antworte nur mit dem einen Wort: Bereit")), maxTokens = 800, temperature = 0.0)
            wake.trySend(Unit)
            Probe(true, "Verbunden. ${cfg.model} sagt: „${GameMaster.cleanText(reply, 40)}“", models)
        } catch (e: AiException) {
            Probe(false, e.message ?: "Fehler", models)
        }
    }
}
