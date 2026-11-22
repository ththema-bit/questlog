package io.github.ththemabit.questlog.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import io.github.ththemabit.questlog.ai.AiPort
import io.github.ththemabit.questlog.ai.NoAi
import io.github.ththemabit.questlog.core.Meta
import io.github.ththemabit.questlog.core.Quest
import io.github.ththemabit.questlog.core.QuestDraft
import io.github.ththemabit.questlog.core.Reward
import io.github.ththemabit.questlog.core.Settings
import io.github.ththemabit.questlog.data.AppState
import io.github.ththemabit.questlog.data.Store
import io.github.ththemabit.questlog.ui.components.BottomNav
import io.github.ththemabit.questlog.ui.pixel.LocalPx
import io.github.ththemabit.questlog.ui.pixel.PixelIcon
import io.github.ththemabit.questlog.ui.pixel.H
import io.github.ththemabit.questlog.ui.pixel.T
import io.github.ththemabit.questlog.ui.pixel.dungeonWall
import io.github.ththemabit.questlog.ui.pixel.pixelUnit
import io.github.ththemabit.questlog.ui.pixel.stonePanel
import io.github.ththemabit.questlog.ui.screens.BoardScreen
import io.github.ththemabit.questlog.ui.screens.ChronicleScreen
import io.github.ththemabit.questlog.ui.screens.CreditsScreen
import io.github.ththemabit.questlog.ui.screens.DetailScreen
import io.github.ththemabit.questlog.ui.screens.EditScreen
import io.github.ththemabit.questlog.ui.screens.GloryScreen
import io.github.ththemabit.questlog.ui.screens.LetterScreen
import io.github.ththemabit.questlog.ui.screens.MasterScreen
import io.github.ththemabit.questlog.ui.screens.Onboarding
import io.github.ththemabit.questlog.ui.screens.OptionsScreen
import io.github.ththemabit.questlog.ui.screens.OverlayHost
import io.github.ththemabit.questlog.ui.screens.QuickAddScreen
import io.github.ththemabit.questlog.ui.screens.RecapScreen
import io.github.ththemabit.questlog.ui.screens.RouletteScreen
import io.github.ththemabit.questlog.ui.screens.SessionScreen
import io.github.ththemabit.questlog.ui.screens.TemplatesScreen
import io.github.ththemabit.questlog.ui.screens.TreasuryScreen
import io.github.ththemabit.questlog.ui.theme.Pal
import io.github.ththemabit.questlog.ui.theme.Q
import io.github.ththemabit.questlog.ui.theme.QuestlogTheme
import kotlinx.coroutines.delay
import java.time.LocalDate

class StoreActions(private val store: Store) : Actions {
    override fun complete(questId: String, mini: Boolean, durationSec: Int?, mods: List<String>, at: Long?, backfilled: Boolean, shout: Int?): String =
        store.complete(questId, mini, durationSec, mods, at ?: store.now(), backfilled, shout)
    override fun hit(questId: String, subtaskId: String) = store.hit(questId, subtaskId)
    override fun undo(eventId: String) = store.undo(eventId)
    override fun saveQuest(q: Quest) = store.saveQuest(q)
    override fun archiveQuest(id: String) = store.archiveQuest(id)
    override fun addDrafts(drafts: List<QuestDraft>) = store.addDrafts(drafts)
    override fun addPacks(keys: Set<String>) = store.addTemplatePacks(keys)
    override fun saveReward(r: Reward) = store.saveReward(r)
    override fun deleteReward(id: String) = store.deleteReward(id)
    override fun buy(r: Reward) = store.buy(r)
    override fun placeWager(questId: String) = store.placeWager(questId)
    override fun startHardcore() = store.startHardcoreWeek()
    override fun setSpin(questId: String, blind: Boolean) = store.setPendingSpin(questId, blind)
    override fun startPause(until: LocalDate?) = store.startPause(until)
    override fun endPause() = store.endPause()
    override fun settings(block: (Settings) -> Settings) = store.settings(block)
    override fun meta(block: (Meta) -> Meta) = store.meta(block)
    override fun exportJson() = store.exportJson()
    override fun importJson(text: String): Boolean = runCatching { store.importJson(text) }.isSuccess
    override fun resetAll() = store.resetAll()
    override fun newId() = store.newId()
    override fun now() = store.now()
}

/** Der KI-Spielleiter für die Oberfläche. Ohne KI: [NoAi]. */
val LocalAi = staticCompositionLocalOf<AiPort> { NoAi }

@Composable
fun QuestlogRoot(state: AppState?, act: Actions, nav: Nav = remember { Nav() }, ui: UiController = remember { UiController() }, ai: AiPort = NoAi) {
    QuestlogFrame(state?.save?.settings ?: Settings(), cursed = state?.game?.curse != null, ai = ai) {
        when {
            state == null -> Splash()
            !state.save.meta.onboarded -> Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) { Onboarding(state, act) }
            else -> Main(state, act, nav, ui)
        }
    }
}

/** Pixel-Maß, Klänge, KI, Schriften und Kerkermauer für alles darin (auch für Vorschauen einzelner Teile). */
@Composable
fun QuestlogFrame(settings: Settings, cursed: Boolean = false, ai: AiPort = NoAi, content: @Composable () -> Unit) {
    val u = pixelUnit(LocalDensity.current.density)
    val view = LocalView.current
    val current by rememberUpdatedState(settings)
    val fx = remember(view) { Fx(view, { current.sound }, { current.haptics }) }
    CompositionLocalProvider(LocalPx provides u, LocalFx provides fx, LocalAi provides ai) {
        QuestlogTheme(cursed = cursed) {
            Box(Modifier.fillMaxSize().dungeonWall(u)) { content() }
        }
    }
}

@Composable
private fun Splash() {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
        PixelIcon("flame", 64.dp)
        H("Questlog", Q.type.logo, color = Pal.gold0)
    }
}

@Composable
private fun Main(state: AppState, act: Actions, nav: Nav, ui: UiController) {
    BackHandler(enabled = nav.canBack || ui.overlay != null) {
        if (ui.overlay != null) ui.dismiss() else nav.back()
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
            Box(Modifier.weight(1f)) { ScreenHost(nav.current, state, act, nav, ui) }
            if (nav.stack.isEmpty()) {
                val tabs = listOf(Route.Board, Route.Chronicle, Route.Glory, Route.Treasury, Route.Options)
                BottomNav(tabs.indexOf(nav.tab).coerceAtLeast(0), onSelect = { nav.go(tabs[it]) })
            }
        }
        ui.overlay?.let { OverlayHost(it, state, act, ui, nav) }
        ToastHost(ui, act)
    }
}

@Composable
fun ScreenHost(route: Route, state: AppState, act: Actions, nav: Nav, ui: UiController) {
    when (route) {
        Route.Board -> BoardScreen(state, act, nav, ui)
        Route.Chronicle -> ChronicleScreen(state, nav)
        Route.Glory -> GloryScreen(state, act, nav)
        Route.Treasury -> TreasuryScreen(state, act, nav, ui)
        Route.Options -> OptionsScreen(state, act, nav, ui)
        is Route.Detail -> DetailScreen(route.questId, state, act, nav, ui, route.preset)
        is Route.Edit -> EditScreen(route.questId, state, act, nav, ui)
        Route.QuickAdd -> QuickAddScreen(state, act, nav, ui)
        is Route.Session -> SessionScreen(route, state, act, nav, ui)
        is Route.Roulette -> RouletteScreen(route.blind, state, act, nav)
        Route.Recap -> RecapScreen(state, act, nav)
        is Route.Letter -> LetterScreen(route.id, state, act, nav)
        is Route.Credits -> CreditsScreen(route.questId, state, nav)
        Route.Templates -> TemplatesScreen(state, act, nav, ui)
        is Route.Master -> MasterScreen(route, state, nav, ui)
    }
}

@Composable
private fun ToastHost(ui: UiController, act: Actions) {
    val toast = ui.toast
    LaunchedEffect(toast?.id) {
        if (toast != null) {
            delay(if (toast.undoEventId != null) 5000 else 2600)
            if (ui.toast?.id == toast.id) ui.toast = null
        }
    }
    val fx = LocalFx.current
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(bottom = 84.dp, start = 16.dp, end = 16.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(toast != null, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }) {
            val t = toast ?: return@AnimatedVisibility
            Row(Modifier.fillMaxWidth().stonePanel().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                T(t.text, Q.type.body, Modifier.weight(1f), color = Pal.bone)
                if (t.undoEventId != null) {
                    Spacer(Modifier.width(8.dp))
                    T("RÜCKGÄNGIG", Q.type.label, Modifier.clickable {
                        act.undo(t.undoEventId)
                        fx.undo()
                        ui.toast = null
                    }.padding(8.dp), color = Pal.gold0)
                }
            }
        }
    }
}

/** Dunkler Schleier hinter Overlays. */
fun Modifier.veil(): Modifier = this.background(Pal.night0.copy(alpha = 0.82f))
