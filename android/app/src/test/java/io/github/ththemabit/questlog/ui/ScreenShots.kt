package io.github.ththemabit.questlog.ui

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
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

    @Composable
    private fun Host(state: AppState, route: Route? = null, tab: Route = Route.Board, overlay: Overlay? = null) {
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
            QuestlogRoot(state, NoActions, nav, ui)
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
}
