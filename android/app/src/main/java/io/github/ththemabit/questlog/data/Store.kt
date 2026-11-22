package io.github.ththemabit.questlog.data

import android.content.Context
import io.github.ththemabit.questlog.core.Buy
import io.github.ththemabit.questlog.core.Done
import io.github.ththemabit.questlog.core.Engine
import io.github.ththemabit.questlog.core.Event
import io.github.ththemabit.questlog.core.GameState
import io.github.ththemabit.questlog.core.Hit
import io.github.ththemabit.questlog.core.Meta
import io.github.ththemabit.questlog.core.PauseRange
import io.github.ththemabit.questlog.core.PendingSpin
import io.github.ththemabit.questlog.core.Quest
import io.github.ththemabit.questlog.core.QuestDraft
import io.github.ththemabit.questlog.core.Reward
import io.github.ththemabit.questlog.core.Rules
import io.github.ththemabit.questlog.core.SaveCodec
import io.github.ththemabit.questlog.core.SaveData
import io.github.ththemabit.questlog.core.Settings
import io.github.ththemabit.questlog.core.Templates
import io.github.ththemabit.questlog.core.Wager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.random.Random

data class AppState(val save: SaveData, val game: GameState)

/**
 * Hält den Spielstand, speichert ihn (atomar, mit Sicherungskopie) und rechnet bei jeder
 * Änderung und jede halbe Minute den Spielzustand neu aus.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class Store(context: Context, private val zone: ZoneId = ZoneId.systemDefault()) {
    private val dir = context.applicationContext.filesDir
    private val file = File(dir, "questlog.json")
    private val backup = File(dir, "questlog.bak.json")
    private val engine = Engine(zone)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val writeLock = Mutex()

    private val save = MutableStateFlow<SaveData?>(null)
    private val clock = MutableStateFlow(System.currentTimeMillis())
    private val writes = MutableSharedFlow<SaveData>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val state: StateFlow<AppState?> = combine(save.filterNotNull(), clock) { s, now -> AppState(s, engine.compute(s, now)) }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.Eagerly, null)

    init {
        scope.launch(Dispatchers.IO) { save.value = load() }
        scope.launch(Dispatchers.IO) { writes.debounce(150).collect { write(it) } }
        scope.launch {
            while (true) {
                delay(30_000)
                clock.value = System.currentTimeMillis()
            }
        }
    }

    suspend fun awaitState(): AppState = state.filterNotNull().first()

    fun refresh() {
        clock.value = System.currentTimeMillis()
    }

    fun now(): Long = System.currentTimeMillis()

    private fun load(): SaveData {
        for (f in listOf(file, backup)) {
            if (!f.exists()) continue
            runCatching { return SaveCodec.decode(f.readText()) }
        }
        return SaveData(meta = Meta(seed = Random.nextLong()))
    }

    private suspend fun write(data: SaveData) = writeLock.withLock {
        runCatching {
            val tmp = File(dir, "questlog.json.tmp")
            tmp.writeText(SaveCodec.encode(data))
            if (file.exists()) file.copyTo(backup, overwrite = true)
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        }
    }

    /** Sofort schreiben (z. B. aus Benachrichtigungen, bevor der Prozess endet). */
    suspend fun flush() {
        save.value?.let { write(it) }
    }

    private fun change(block: (SaveData) -> SaveData) {
        save.update { it?.let(block) }
        clock.value = System.currentTimeMillis()
        save.value?.let { writes.tryEmit(it) }
    }

    // ---------------------------------------------------------------- Quests

    fun newId(): String = UUID.randomUUID().toString()

    fun saveQuest(q: Quest) = change { s ->
        val exists = s.quests.any { it.id == q.id }
        s.copy(quests = if (exists) s.quests.map { if (it.id == q.id) q else it } else s.quests + q)
    }

    fun archiveQuest(id: String) = change { s -> s.copy(quests = s.quests.map { if (it.id == id) it.copy(archived = true) else it }) }

    fun addDrafts(drafts: List<QuestDraft>) = change { s ->
        val now = now()
        s.copy(quests = s.quests + drafts.map { d -> d.toQuest(newId(), now) { newId() } })
    }

    fun addTemplatePacks(keys: Collection<String>) {
        val existing = save.value?.quests?.filter { !it.archived }?.map { it.title.lowercase() }?.toSet() ?: emptySet()
        val drafts = Templates.packs.filter { it.key in keys }.flatMap { it.quests }.filter { it.title.lowercase() !in existing }
        addDrafts(drafts)
    }

    // ---------------------------------------------------------------- Erledigen

    /** Quest erledigen. Gibt die Ereignis-ID zurück (für Beipackzettel und Rückgängig). */
    fun complete(
        questId: String,
        mini: Boolean = false,
        durationSec: Int? = null,
        mods: List<String> = emptyList(),
        at: Long = now(),
        backfilled: Boolean = false,
        shout: Int? = null,
    ): String {
        val id = newId()
        change { s ->
            val spin = s.meta.pendingSpin?.takeIf { it.questId == questId && at - it.at in 0..Rules.ROULETTE_WINDOW_MS && !mini }
            val e = Done(
                id = id, at = at, questId = questId, mini = mini, durationSec = durationSec, mods = mods,
                spinAt = spin?.at, chaos = s.settings.chaos.enabledKeys(), shout = shout, backfilled = backfilled,
            )
            s.copy(events = s.events + e, meta = if (spin != null) s.meta.copy(pendingSpin = null) else s.meta)
        }
        return id
    }

    fun hit(questId: String, subtaskId: String): String {
        val id = newId()
        change { s -> s.copy(events = s.events + Hit(id, now(), questId, subtaskId, s.settings.chaos.enabledKeys())) }
        return id
    }

    fun undo(eventId: String) = change { s -> s.copy(events = s.events.filterNot { it.id == eventId }) }

    fun events(): List<Event> = save.value?.events ?: emptyList()

    // ---------------------------------------------------------------- Shop, Wette, Woche

    fun saveReward(r: Reward) = change { s ->
        s.copy(rewards = if (s.rewards.any { it.id == r.id }) s.rewards.map { if (it.id == r.id) r else it } else s.rewards + r)
    }

    fun deleteReward(id: String) = change { s -> s.copy(rewards = s.rewards.filterNot { it.id == id }) }

    fun buy(r: Reward): String {
        val id = newId()
        change { s -> s.copy(events = s.events + Buy(id, now(), r.id, r.name, r.price)) }
        return id
    }

    fun placeWager(questId: String) {
        val now = now()
        val deadline = LocalDate.now(zone).atTime(Rules.WAGER_DEADLINE_HOUR, 0).atZone(zone).toInstant().toEpochMilli()
        if (now >= deadline) return
        change { s -> s.copy(events = s.events + Wager(newId(), now, questId, deadline)) }
    }

    fun startHardcoreWeek() = change { s ->
        val ws = Engine.weekStart(LocalDate.now(zone)).toString()
        if (ws in s.hardcoreWeeks) s else s.copy(hardcoreWeeks = s.hardcoreWeeks + ws)
    }

    fun setPendingSpin(questId: String, blind: Boolean) = change { s -> s.copy(meta = s.meta.copy(pendingSpin = PendingSpin(questId, now(), blind))) }

    // ---------------------------------------------------------------- Pause

    fun startPause(until: LocalDate?) = change { s ->
        val today = LocalDate.now(zone)
        s.copy(pauses = s.pauses.filter { it.to != null } + PauseRange(today.toString(), until?.toString()))
    }

    fun endPause() = change { s ->
        val yesterday = LocalDate.now(zone).minusDays(1)
        s.copy(pauses = s.pauses.mapNotNull { p ->
            val from = LocalDate.parse(p.from)
            val to = p.to?.let(LocalDate::parse)
            when {
                to != null && to.isBefore(LocalDate.now(zone)) -> p
                from.isAfter(yesterday) -> null
                else -> p.copy(to = yesterday.toString())
            }
        })
    }

    // ---------------------------------------------------------------- Einstellungen & Meta

    fun settings(block: (Settings) -> Settings) = change { s -> s.copy(settings = block(s.settings)) }
    fun meta(block: (Meta) -> Meta) = change { s -> s.copy(meta = block(s.meta)) }

    fun cacheStory(key: String, text: String) = change { s -> s.copy(storyCache = s.storyCache + (key to text)) }

    // ---------------------------------------------------------------- Backup

    fun exportJson(): String = SaveCodec.encodePretty(save.value ?: SaveData())

    /** Spielstand aus einem Backup übernehmen. Wirft bei kaputter Datei eine Ausnahme. */
    fun importJson(text: String) {
        val data = SaveCodec.decode(text)
        change { data }
    }

    fun resetAll() = change { SaveData(meta = Meta(seed = Random.nextLong())) }
}
