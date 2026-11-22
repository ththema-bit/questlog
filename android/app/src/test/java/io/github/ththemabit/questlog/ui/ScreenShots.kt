package io.github.ththemabit.questlog.ui

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import io.github.ththemabit.questlog.ai.AiPhase
import io.github.ththemabit.questlog.ai.AiPort
import io.github.ththemabit.questlog.ai.AiStatus
import io.github.ththemabit.questlog.ai.Briefing
import io.github.ththemabit.questlog.ai.ChatTurn
import io.github.ththemabit.questlog.ai.GameMaster
import io.github.ththemabit.questlog.ai.LiveReply
import io.github.ththemabit.questlog.ai.NoAi
import io.github.ththemabit.questlog.ai.Probe
import io.github.ththemabit.questlog.ai.QuestTexts
import io.github.ththemabit.questlog.core.AiConfig
import io.github.ththemabit.questlog.core.Chaos
import io.github.ththemabit.questlog.core.Engine
import io.github.ththemabit.questlog.core.Meta
import io.github.ththemabit.questlog.core.Quest
import io.github.ththemabit.questlog.core.QuestDraft
import io.github.ththemabit.questlog.core.Reward
import io.github.ththemabit.questlog.core.SaveData
import io.github.ththemabit.questlog.core.Settings
import io.github.ththemabit.questlog.data.AppState
import io.github.ththemabit.questlog.data.Demo
import io.github.ththemabit.questlog.ui.screens.AiSection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Screenshots ohne Gerät: ./gradlew recordPaparazziDebug, Bilder unter app/src/test/snapshots/images. */
class ScreenShots {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "android:Theme.Material.NoActionBar")

    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 11, 22)
    private val now = today.atTime(18, 30).atZone(zone).toInstant().toEpochMilli()
    private val showcase = Chaos(gerald = true, dustBunnies = true, narrators = true, holidays = true, weddings = true, lazyTax = true, saga = true, credits = true)

    private fun state(save: SaveData = Demo.save(today, zone, showcase)): AppState = AppState(save, Engine(zone).compute(save, now))

    private object NoActions : Actions {
        override fun complete(questId: String, mini: Boolean, durationSec: Int?, mods: List<String>, at: Long?, backfilled: Boolean, shout: Int?) = "x"
        override fun hit(questId: String, subtaskId: String) = "x"
        override fun undo(eventId: String) {}
        override fun saveQuest(q: Quest) {}
        override fun archiveQuest(id: String) {}
        override fun addDrafts(drafts: List<QuestDraft>) {}
        override fun addPacks(keys: Set<String>) {}
        override fun saveReward(r: Reward) {}
        override fun deleteReward(id: String) {}
        override fun buy(r: Reward) = "x"
        override fun placeWager(questId: String) {}
        override fun startHardcore() {}
        override fun setSpin(questId: String, blind: Boolean) {}
        override fun startPause(until: LocalDate?) {}
        override fun endPause() {}
        override fun settings(block: (Settings) -> Settings) {}
        override fun meta(block: (Meta) -> Meta) {}
        override fun exportJson() = "{}"
        override fun importJson(text: String) = true
        override fun resetAll() {}
        override fun newId() = "new"
        override fun now() = 0L
    }

    private class FakeAi(
        chat: List<ChatTurn> = emptyList(),
        live: LiveReply? = null,
        status: AiStatus = AiStatus(AiPhase.IDLE, "Bereit · eigene Texte für 9 von 13 Quests"),
    ) : AiPort {
        override val status: StateFlow<AiStatus> = MutableStateFlow(status)
        override val chat: StateFlow<List<ChatTurn>> = MutableStateFlow(chat)
        override val live: StateFlow<LiveReply?> = MutableStateFlow(live)
        override fun send(text: String) {}
        override fun stop() {}
        override fun clearChat() {}
        override fun apply(turn: Int, picks: Set<Int>) = 0
        override fun focus(questId: String) {}
        override fun rewriteAll() {}
        override suspend fun probe(cfg: AiConfig) = Probe(true, "ok")
    }

    private val box = AiConfig(enabled = true, baseUrl = "http://192.168.178.65:11434/v1", model = "qwen2.5:7b")

    /** Demo-Spielstand mit eingeschaltetem KI-Spielleiter und fertigen Texten. */
    private fun aiSave(): SaveData {
        val base = Demo.save(today, zone, showcase)
        val bad = base.quests.first { it.id == "bad" }
        val steuer = base.quests.first { it.id == "steuer" }
        val texts = mapOf(
            GameMaster.textsKey("bad") to GameMaster.encode(QuestTexts(
                GameMaster.signature(bad), "butler",
                intro = "Im Bad haben sich Kalkdrachen eingenistet. Ein Schwamm, etwas Mut und zwanzig Minuten, mehr verlangt das Königreich nicht.",
                victory = listOf("Die Kalkdrachen sind vertrieben. Das Waschbecken glänzt wie ein Schild."),
                reminder = "Die Kalkdrachen regen sich wieder.",
                mini = "Ein Waschbecken ist auch ein Sieg.",
            )),
            GameMaster.textsKey("steuer") to GameMaster.encode(QuestTexts(
                GameMaster.signature(steuer), "butler",
                intro = "Ein Stapel Belege, so hoch wie ein Turm.",
                bossIntro = "Aus dem Aktenordner steigt der Formularlindwurm. Fünf Treffer, und er rollt sich für ein Jahr zusammen.",
            )),
        )
        val game = Engine(zone).compute(base, now)
        val brief = GameMaster.encodeBriefing(Briefing(
            GameMaster.briefingTag(game, io.github.ththemabit.questlog.core.Persona.BUTLER, 18), today.toString(),
            "Guten Abend. Das Bad wartet seit drei Tagen auf Ihre Aufmerksamkeit, und für Lea fehlt noch ein Geschenk. Der Rest des Reiches ist in bester Ordnung.",
        ))
        return base.copy(settings = base.settings.copy(ai = box), storyCache = texts + (GameMaster.BRIEF_KEY to brief))
    }

    @Composable
    private fun Host(state: AppState, route: Route? = null, tab: Route = Route.Board, overlay: Overlay? = null, ai: AiPort = NoAi) {
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val owner = remember {
            object : OnBackPressedDispatcherOwner {
                override val onBackPressedDispatcher = OnBackPressedDispatcher()
                override val lifecycle: Lifecycle get() = lifecycle
            }
        }
        val nav = remember { Nav().also { n -> n.tab = tab; if (route != null) n.stack.add(route) } }
        val ui = remember { UiController().also { it.overlay = overlay } }
        CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides owner) {
            QuestlogRoot(state, NoActions, nav, ui, ai)
        }
    }

    @Test fun board() = paparazzi.snapshot { Host(state()) }

    @Test fun boardLong() {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 5600))
        paparazzi.snapshot { Host(state()) }
    }

    @Test fun boardCursed() {
        val base = Demo.save(today, zone, Chaos(comicCurse = true))
        val created = today.minusDays(20).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val s = base.copy(quests = base.quests + Quest("keller", "Keller entrümpeln", "haushalt", 3, 30, 5, 2, 3, createdAt = created))
        paparazzi.snapshot { Host(state(s)) }
    }

    @Test fun detail() = paparazzi.snapshot { Host(state(), Route.Detail("bad")) }

    @Test fun boss() = paparazzi.snapshot { Host(state(), Route.Detail("steuer")) }

    @Test fun edit() = paparazzi.snapshot { Host(state(), Route.Edit("bad")) }

    @Test fun quickAdd() = paparazzi.snapshot { Host(state(), Route.QuickAdd) }

    @Test fun result() {
        val s = state()
        val ev = s.game.results.values.filter { !it.mini && !it.hit }.maxByOrNull { it.at }!!
        paparazzi.snapshot { Host(s, overlay = Overlay.Result(ev.eventId, s.game)) }
    }

    @Test fun chronicle() = paparazzi.snapshot { Host(state(), tab = Route.Chronicle) }

    @Test fun glory() = paparazzi.snapshot { Host(state(), tab = Route.Glory) }

    @Test fun treasury() = paparazzi.snapshot { Host(state(), tab = Route.Treasury) }

    @Test fun options() = paparazzi.snapshot { Host(state(), tab = Route.Options) }

    @Test fun recap() = paparazzi.snapshot { Host(state(), Route.Recap) }

    @Test fun letter() {
        // Drei freie Tage in der Vorwoche: dafür kommt ein Steuerbescheid
        val base = Demo.save(today, zone, showcase)
        val lazy = (9L..11L).map { today.minusDays(it) }.toSet()
        val s = state(base.copy(events = base.events.filter { Fmt.day(it.at) !in lazy }))
        val id = s.game.taxNotices.first().id
        paparazzi.snapshot { Host(s, Route.Letter(id)) }
    }

    @Test fun session() = paparazzi.snapshot { Host(state(), Route.Session("spuele", SessionMode.SPEEDRUN)) }

    @Test fun roulette() = paparazzi.snapshot { Host(state(), Route.Roulette(false)) }

    @Test fun onboarding() {
        val s = SaveData()
        paparazzi.snapshot { Host(state(s)) }
    }

    @Test fun empty() {
        val s = SaveData(meta = Meta(onboarded = true))
        paparazzi.snapshot { Host(state(s)) }
    }

    // ---------------------------------------------------------------- KI-Spielleiter

    @Test fun boardAi() = paparazzi.snapshot { Host(state(aiSave())) }

    @Test fun detailAi() = paparazzi.snapshot { Host(state(aiSave()), Route.Detail("bad")) }

    @Test fun bossAi() = paparazzi.snapshot { Host(state(aiSave()), Route.Detail("steuer")) }

    @Test fun master() {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 3000))
        val save = aiSave()
        val raw = """
            Sehr wohl. Ein Umzug verlangt Planung, und das Bad verdient mehr Zuwendung. Ich habe mir erlaubt, Folgendes vorzubereiten.
            ###QUESTS
            [{"title":"Umzugskartons besorgen","category":"sonstiges","importance":4,"effortMin":30,"dread":2,"dueDate":"2026-12-05","mini":"Beim Supermarkt nach Kartons fragen","intro":"Kartons, so weit das Auge reicht. Ein Umzug beginnt mit ihnen."},
             {"title":"Umzug","category":"haushalt","importance":5,"effortMin":480,"dread":5,"subtasks":["Transporter mieten","Bücher packen","Küche packen","Schlüssel abgeben"],"boss":true,"intro":"Der Umzugsdrache erwacht. Er hasst Treppenhäuser."},
             {"id":"q1","minDays":3,"maxDays":6}]
        """.trimIndent()
        val reply = GameMaster.parseForge(raw, save, today)
        val chat = listOf(
            ChatTurn(true, "Ich ziehe Ende Dezember um. Und das Bad soll öfter dran sein."),
            ChatTurn(false, reply.message, reply.proposals),
            ChatTurn(true, "Die Fenster kann ich mir bis dahin sparen."),
        )
        paparazzi.snapshot { Host(state(save), Route.Master(), ai = FakeAi(chat, LiveReply("Sehr wohl. Die Fenster ruhen bis nach dem Umzug, ich lege sie"))) }
    }

    @Test fun masterApplied() {
        val save = aiSave()
        val reply = GameMaster.parseForge("Sehr wohl.\n###QUESTS\n[{\"id\":\"q7\",\"archive\":true},{\"title\":\"Zahnseide\",\"category\":\"selbst\",\"minDays\":1,\"maxDays\":2,\"effortMin\":2}]", save, today)
        val chat = listOf(
            ChatTurn(true, "Fenster weg, dafür Zahnseide."),
            ChatTurn(false, reply.message, reply.proposals, applied = true, picked = setOf(0, 1)),
        )
        paparazzi.snapshot { Host(state(save), Route.Master(), ai = FakeAi(chat)) }
    }

    @Test fun masterSleeping() = paparazzi.snapshot { Host(state(), Route.Master()) }

    @Test fun masterEmpty() = paparazzi.snapshot { Host(state(aiSave()), Route.Master(), ai = FakeAi()) }

    @Test fun optionsAi() {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 3100))
        val s = state(aiSave())
        val probe = Probe(true, "Verbunden. qwen2.5:7b sagt: „Bereit“", listOf("gemma3:4b", "llama3.2:3b", "qwen2.5:7b"))
        paparazzi.snapshot {
            QuestlogFrame(s.save.settings, ai = FakeAi()) {
                Column(Modifier.padding(12.dp)) { AiSection(s, NoActions, Nav(), UiController(), preview = probe) }
            }
        }
    }
}
