package io.github.ththemabit.questlog.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.ththemabit.questlog.core.GameState
import io.github.ththemabit.questlog.core.Meta
import io.github.ththemabit.questlog.core.Quest
import io.github.ththemabit.questlog.core.QuestDraft
import io.github.ththemabit.questlog.core.Reward
import io.github.ththemabit.questlog.core.Settings
import java.time.LocalDate

enum class SessionMode { SPEEDRUN, DREISONG, HANDYWEG, TANZ }

sealed interface Route {
    data object Board : Route
    data object Chronicle : Route
    data object Glory : Route
    data object Treasury : Route
    data object Options : Route
    data class Detail(val questId: String, val preset: List<String> = emptyList()) : Route
    data class Edit(val questId: String?) : Route
    data object QuickAdd : Route
    data class Session(val questId: String, val mode: SessionMode, val mods: List<String> = emptyList()) : Route
    data class Roulette(val blind: Boolean) : Route
    data object Recap : Route
    data class Letter(val id: String) : Route
    data class Credits(val questId: String) : Route
    data object Templates : Route
    /** Gespräch mit dem KI-Spielleiter. [send]: [prefill] gleich abschicken. */
    data class Master(val prefill: String = "", val send: Boolean = false) : Route

    val isTab: Boolean get() = this == Board || this == Chronicle || this == Glory || this == Treasury || this == Options
}

@Stable
class Nav {
    var tab by mutableStateOf<Route>(Route.Board)
    val stack = mutableStateListOf<Route>()
    /** Abschnitt, zu dem ein Tab beim Öffnen springen soll (z. B. „ai“ in den Optionen) */
    var focus by mutableStateOf<String?>(null)
    val current: Route get() = stack.lastOrNull() ?: tab
    val canBack: Boolean get() = stack.isNotEmpty() || tab != Route.Board

    fun go(r: Route) {
        if (r.isTab) {
            stack.clear()
            tab = r
        } else {
            stack.add(r)
        }
    }

    fun replace(r: Route) {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
        go(r)
    }

    fun back(): Boolean = when {
        stack.isNotEmpty() -> {
            stack.removeAt(stack.lastIndex)
            true
        }
        tab != Route.Board -> {
            tab = Route.Board
            true
        }
        else -> false
    }
}

/** Was die Oberfläche am Spielstand ändern darf. Die App nutzt den Store, Tests eine Attrappe. */
interface Actions {
    fun complete(questId: String, mini: Boolean = false, durationSec: Int? = null, mods: List<String> = emptyList(), at: Long? = null, backfilled: Boolean = false, shout: Int? = null): String
    fun hit(questId: String, subtaskId: String): String
    fun undo(eventId: String)
    fun saveQuest(q: Quest)
    fun archiveQuest(id: String)
    fun addDrafts(drafts: List<QuestDraft>)
    fun addPacks(keys: Set<String>)
    fun saveReward(r: Reward)
    fun deleteReward(id: String)
    fun buy(r: Reward): String
    fun placeWager(questId: String)
    fun startHardcore()
    fun setSpin(questId: String, blind: Boolean)
    fun startPause(until: LocalDate?)
    fun endPause()
    fun settings(block: (Settings) -> Settings)
    fun meta(block: (Meta) -> Meta)
    fun exportJson(): String
    fun importJson(text: String): Boolean
    fun resetAll()
    fun newId(): String
    fun now(): Long
}

/** Overlays über dem aktuellen Screen. */
sealed interface Overlay {
    data class Result(val eventId: String, val before: GameState) : Overlay
    data class Confirm(val title: String, val text: String, val yes: String, val onYes: () -> Unit) : Overlay
    data class DatePick(val title: String, val initial: LocalDate, val min: LocalDate?, val max: LocalDate?, val onPick: (LocalDate?) -> Unit, val allowNone: Boolean = false) : Overlay
    data class Shout(val questId: String) : Overlay
}

data class Toast(val text: String, val undoEventId: String? = null, val id: Long = System.nanoTime())

@Stable
class UiController {
    var overlay by mutableStateOf<Overlay?>(null)
    var toast by mutableStateOf<Toast?>(null)

    fun show(o: Overlay) {
        overlay = o
    }

    fun dismiss() {
        overlay = null
    }

    fun toast(text: String, undo: String? = null) {
        toast = Toast(text, undo)
    }
}
