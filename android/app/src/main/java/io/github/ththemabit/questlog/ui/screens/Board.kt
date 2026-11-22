package io.github.ththemabit.questlog.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.ththemabit.questlog.ai.GameMaster
import io.github.ththemabit.questlog.core.GameState
import io.github.ththemabit.questlog.core.Moment
import io.github.ththemabit.questlog.core.Persona
import io.github.ththemabit.questlog.core.QuestState
import io.github.ththemabit.questlog.core.SaveData
import io.github.ththemabit.questlog.core.Settings
import io.github.ththemabit.questlog.core.StoryContext
import io.github.ththemabit.questlog.core.TemplateStoryteller
import io.github.ththemabit.questlog.data.AppState
import io.github.ththemabit.questlog.ui.Actions
import io.github.ththemabit.questlog.ui.Fmt
import io.github.ththemabit.questlog.ui.LocalFx
import io.github.ththemabit.questlog.ui.Nav
import io.github.ththemabit.questlog.ui.Overlay
import io.github.ththemabit.questlog.ui.Route
import io.github.ththemabit.questlog.ui.UiController
import io.github.ththemabit.questlog.ui.components.Banner
import io.github.ththemabit.questlog.ui.components.BossCard
import io.github.ththemabit.questlog.ui.components.EmptyHint
import io.github.ththemabit.questlog.ui.components.QuestCard
import io.github.ththemabit.questlog.ui.components.SeasonCard
import io.github.ththemabit.questlog.ui.components.StatusHeader
import io.github.ththemabit.questlog.ui.pixel.Btn
import io.github.ththemabit.questlog.ui.pixel.Frames
import io.github.ththemabit.questlog.ui.pixel.IconTap
import io.github.ththemabit.questlog.ui.pixel.PixelButton
import io.github.ththemabit.questlog.ui.pixel.PixelIcon
import io.github.ththemabit.questlog.ui.pixel.SectionTitle
import io.github.ththemabit.questlog.ui.pixel.H
import io.github.ththemabit.questlog.ui.pixel.T
import io.github.ththemabit.questlog.ui.pixel.parchmentCard
import io.github.ththemabit.questlog.ui.theme.Pal
import io.github.ththemabit.questlog.ui.theme.Q
import java.time.Instant
import java.time.ZoneId

val Story = TemplateStoryteller()

fun persona(settings: Settings): Persona = Persona.active(settings)

/** Begrüßung: das Briefing des KI-Spielleiters, falls es eins für heute gibt, sonst die Vorlage. */
fun greeting(save: SaveData, game: GameState): Pair<String, Boolean> {
    val ai = save.settings.ai
    if (ai.ready && ai.briefing) GameMaster.briefing(save, game.today)?.let { return it to true }
    return greeting(game, save.settings) to false
}

fun greeting(game: GameState, settings: Settings): String {
    val hour = Instant.ofEpochMilli(game.now).atZone(ZoneId.systemDefault()).hour
    val moment = when {
        game.player.comebackReady -> Moment.COMEBACK
        game.player.streakAtRisk -> Moment.STREAK_RISK
        hour < 11 -> Moment.MORNING
        hour < 18 -> Moment.DAY
        else -> Moment.EVENING
    }
    return Story.line(persona(settings), moment, StoryContext(level = game.player.level), salt = game.today.toEpochDay())
}

/** Abhaken mit Beipackzettel. Wird von Board, Detail und Session genutzt. */
fun completeWithResult(act: Actions, ui: UiController, fx: io.github.ththemabit.questlog.ui.Fx, game: GameState, questId: String,
                       mini: Boolean = false, durationSec: Int? = null, mods: List<String> = emptyList(), at: Long? = null,
                       backfilled: Boolean = false, shout: Int? = null) {
    val id = act.complete(questId, mini, durationSec, mods, at, backfilled, shout)
    if (mini) fx.mini() else fx.done()
    ui.show(Overlay.Result(id, game))
}

@Composable
fun BoardScreen(state: AppState, act: Actions, nav: Nav, ui: UiController) {
    val game = state.game
    val save = state.save
    val chaos = save.settings.chaos
    val fx = LocalFx.current
    var showEarly by rememberSaveable { mutableStateOf(false) }
    var showDone by rememberSaveable { mutableStateOf(false) }
    val complete: (QuestState) -> Unit = { qs -> completeWithResult(act, ui, fx, game, qs.quest.id) }
    val shout: ((QuestState) -> Unit)? = if (chaos.battleCry) { qs -> ui.show(Overlay.Shout(qs.quest.id)) } else null
    val recapOpen = game.recap != null && save.meta.seenRecapWeek != game.recap.weekStart.toString()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "top") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                H("Questlog", Q.type.logo.copy(fontSize = Q.type.logo.fontSize * 0.8f), Modifier.weight(1f).padding(start = 4.dp), color = Pal.gold0, maxLines = 1)
                if (save.settings.ai.enabled && save.settings.ai.forge) IconTap("orb", "Spielleiter", { fx.tap(); nav.go(Route.Master()) })
                IconTap("dice", "Quest-Roulette", { fx.tap(); nav.go(Route.Roulette(blind = false)) })
                IconTap("quill", "Schnelleingabe", { fx.tap(); nav.go(Route.QuickAdd) })
                IconTap("plus", "Neue Quest", { fx.tap(); nav.go(Route.Edit(null)) })
            }
        }
        item(key = "status") { StatusHeader(game) }
        item(key = "greet") {
            val (text, fromAi) = greeting(save, game)
            Greeting(text, persona(save.settings), fromAi)
        }
        item(key = "season") {
            SeasonCard(game, chaos.gerald, if (game.recap != null) ({ nav.go(Route.Recap) }) else null)
        }
        banners(state, act, nav, ui, recapOpen)

        if (game.quests.isEmpty()) {
            item(key = "empty") {
                EmptyHint("scroll", "Das Questlog ist leer", "Leg deine erste Quest an oder starte mit einem Vorlagen-Paket.") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PixelButton("Vorlagen", { nav.go(Route.Templates) }, kind = Btn.STONE, icon = "scroll")
                        PixelButton("Neue Quest", { nav.go(Route.Edit(null)) }, icon = "plus")
                    }
                }
            }
        }
        questSection("overdue", "Überfällig", "skull", game.board.overdue, game, nav, complete, shout)
        questSection("open", "Im Fenster", "scroll", game.board.open, game, nav, complete, shout)
        if (game.board.bosses.isNotEmpty()) {
            item(key = "bossTitle") { SectionTitle("Bosse", icon = "skull", count = game.board.bosses.size, color = Pal.blood0) }
            items(game.board.bosses, key = { "boss-" + it.quest.id }) { qs -> BossCard(qs, onOpen = { nav.go(Route.Detail(qs.quest.id)) }) }
        }
        if (game.board.early.isNotEmpty()) {
            item(key = "earlyTitle") {
                Row(Modifier.clickable(role = Role.Button) { showEarly = !showEarly }, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SectionTitle("Noch zu früh", icon = "hourglass", count = game.board.early.size, color = Pal.mana0) }
                    T(if (showEarly) "ZUKLAPPEN" else "ZEIGEN", Q.type.label, color = Pal.boneDim)
                }
            }
            if (showEarly) items(game.board.early, key = { "early-" + it.quest.id }) { qs ->
                QuestCard(qs, game, onOpen = { nav.go(Route.Detail(qs.quest.id)) }, onComplete = { complete(qs) }, onLongComplete = shout?.let { s -> { s(qs) } })
            }
        }
        if (game.board.done.isNotEmpty()) {
            item(key = "doneTitle") {
                Row(Modifier.clickable(role = Role.Button) { showDone = !showDone }, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SectionTitle("Erledigt", icon = "check", count = game.board.done.size, color = Pal.leaf0) }
                    T(if (showDone) "ZUKLAPPEN" else "ZEIGEN", Q.type.label, color = Pal.boneDim)
                }
            }
            if (showDone) items(game.board.done.take(12), key = { "done-" + it.quest.id }) { qs ->
                Row(
                    Modifier.fillMaxWidth().parchmentCard(qs.quest.id.hashCode()).clickable { nav.go(Route.Detail(qs.quest.id)) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PixelIcon("check", 20.dp)
                    Spacer(Modifier.width(8.dp))
                    T(qs.quest.title, Q.type.body, Modifier.weight(1f), color = Pal.inkSoft, maxLines = 1)
                    qs.lastDone?.let { T(Fmt.relative(it, game.today), Q.type.small, color = Pal.inkFaint) }
                }
            }
        }
        if (chaos.dustBunnies && game.dust > 0) item(key = "dust") { DustBunnies(game.dust) }
    }
}

private fun LazyListScope.questSection(
    key: String, title: String, icon: String, list: List<QuestState>, game: GameState, nav: Nav,
    complete: (QuestState) -> Unit, shout: ((QuestState) -> Unit)?,
) {
    if (list.isEmpty()) return
    item(key = "$key-title") {
        SectionTitle(title, icon = icon, count = list.size, color = if (key == "overdue") Pal.blood0 else Pal.gold0)
    }
    items(list, key = { "$key-" + it.quest.id }) { qs ->
        QuestCard(qs, game, onOpen = { nav.go(Route.Detail(qs.quest.id)) }, onComplete = { complete(qs) }, onLongComplete = shout?.let { s -> { s(qs) } })
    }
}

private fun LazyListScope.banners(state: AppState, act: Actions, nav: Nav, ui: UiController, recapOpen: Boolean) {
    val game = state.game
    val save = state.save
    val chaos = save.settings.chaos
    if (recapOpen) item(key = "recap") {
        Banner("scroll", "Die Saison ist vorbei", "Dein Wochenrückblick liegt bereit.", Frames.wood, action = "Lesen") { nav.go(Route.Recap) }
    }
    if (game.paused) item(key = "pause") {
        Banner("pause", "Pause", "Verfall, Serien und Fenster sind eingefroren.", Frames.mana, action = "Beenden") { act.endPause() }
    }
    game.curse?.let { c ->
        item(key = "curse") {
            Banner("skull", "Fluch!", "„${c.questTitle}“ liegt seit ${Fmt.tagen(c.overdueDays)}. Bis sie erledigt ist, herrscht Comic Sans.", Frames.violet,
                action = "Brechen") { nav.go(Route.Detail(c.questId)) }
        }
    }
    game.holiday?.let { h ->
        item(key = "holiday") { Banner("star", h.name, h.text, Frames.gold) }
    }
    game.wager?.let { w ->
        if (!w.resolved) item(key = "wager") {
            val title = game.quest(w.questId)?.quest?.title ?: "?"
            Banner("coin", "Doppelt oder nichts", "${Fmt.n(w.stake)} Glut auf „$title“ bis ${Fmt.time.format(Instant.ofEpochMilli(w.deadline).atZone(ZoneId.systemDefault()))}.", Frames.gold,
                action = "Zur Quest") { nav.go(Route.Detail(w.questId)) }
        }
    }
    if (chaos.lazyTax) {
        game.taxNotices.firstOrNull { it.id !in save.meta.seenLetters }?.let { t ->
            item(key = "tax") { Banner("letter", "Post vom Finanzamt für Ordnung", "Ein amtlicher Bescheid ist eingegangen.", Frames.stoneFlat, action = "Öffnen") { nav.go(Route.Letter(t.id)) } }
        }
    }
    if (chaos.weddings) {
        game.marriages.firstOrNull { "hochzeit-${it.firstId}-${it.secondId}" !in save.meta.seenLetters }?.let { m ->
            item(key = "wedding") {
                Banner("ring", "Eine Hochzeit!", "Zwei deiner Quests haben sich das Jawort gegeben.", Frames.gold, action = "Anzeige") {
                    nav.go(Route.Letter("hochzeit-${m.firstId}-${m.secondId}"))
                }
            }
        }
    }
}

@Composable
fun Greeting(text: String, persona: Persona, fromAi: Boolean = false) {
    val icon = when {
        fromAi -> "orb"
        else -> when (persona) {
            Persona.CHRONIST -> "quill"
            Persona.OMA -> "heart"
            Persona.SERGEANT -> "horn"
            Persona.BUTLER -> "bell"
            Persona.TIERFILMER -> "leaf"
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        PixelIcon(icon, 20.dp)
        Spacer(Modifier.width(10.dp))
        Column {
            val label = listOfNotNull(if (fromAi) "Spielleiter" else null, if (persona != Persona.CHRONIST) persona.title else null).joinToString(" · ")
            if (label.isNotEmpty()) T(label.uppercase(), Q.type.label, color = Pal.boneFaint)
            T(text, Q.type.body, color = Pal.parch1)
        }
    }
}

/** C05: Wollmäuse, die sich bei überfälligen Putz-Quests vermehren. */
@Composable
fun DustBunnies(count: Int) {
    val t = rememberInfiniteTransition(label = "bunnies")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        T("${count} ${if (count == 1) "Wollmaus" else "Wollmäuse"} unterwegs. Jede Putz-Quest vertreibt ein paar.", Q.type.small, color = Pal.boneFaint)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(count) { i ->
                val frame = ((phase * 2 + i * 0.37f) % 2f) < 1f
                PixelIcon(if (frame) "bunny1" else "bunny2", 28.dp, Modifier.offset(y = if (frame) 0.dp else 2.dp))
            }
        }
    }
}
