package io.github.ththemabit.questlog.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.ththemabit.questlog.core.Letter
import io.github.ththemabit.questlog.data.AppState
import io.github.ththemabit.questlog.ui.Actions
import io.github.ththemabit.questlog.ui.Fmt
import io.github.ththemabit.questlog.ui.Nav
import io.github.ththemabit.questlog.ui.components.ScreenTitle
import io.github.ththemabit.questlog.ui.pixel.Btn
import io.github.ththemabit.questlog.ui.pixel.Frames
import io.github.ththemabit.questlog.ui.pixel.PixelButton
import io.github.ththemabit.questlog.ui.pixel.PixelIcon
import io.github.ththemabit.questlog.ui.pixel.ScrollPanel
import io.github.ththemabit.questlog.ui.pixel.T
import io.github.ththemabit.questlog.ui.pixel.Tag
import io.github.ththemabit.questlog.ui.pixel.stonePanel
import io.github.ththemabit.questlog.ui.theme.Pal
import io.github.ththemabit.questlog.ui.theme.Q
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields

/** Wochenrückblick (K10) mit Heldensaga (C07). Endet mit dem besten Moment (Peak-End). */
@Composable
fun RecapScreen(state: AppState, act: Actions, nav: Nav) {
    val recap = state.game.recap
    if (recap == null) {
        LaunchedEffect(Unit) { nav.back() }
        return
    }
    val chaos = state.save.settings.chaos
    LaunchedEffect(recap.weekStart) { act.meta { it.copy(seenRecapWeek = recap.weekStart.toString()) } }
    val kw = recap.weekStart.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenTitle("Saison KW $kw", onBack = { nav.back() })
        ScrollPanel(seed = kw) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        T("PUNKTE DER WOCHE", Q.type.label, color = Pal.inkSoft)
                        T(Fmt.n(recap.xp), Q.type.numberBig, color = Pal.gold3)
                        T("Ziel ${Fmt.n(recap.goal)}", Q.type.small, color = Pal.inkSoft)
                    }
                    PixelIcon(if (recap.reached) "seal" else "hourglass", 48.dp)
                }
                RecapLine("Aktive Tage", "${recap.activeDays} von 7")
                RecapLine("Quests erledigt", Fmt.n(recap.doneCount))
                RecapLine("Glut", "${Fmt.n(recap.glutStart)} → ${Fmt.n(recap.glutEnd)}")
                RecapLine("Vorwoche", Fmt.n(recap.prevXp) + " (" + Fmt.signed(recap.xp - recap.prevXp) + ")")
                recap.bestDay?.let { RecapLine("Bester Tag", it.first.format(Fmt.weekday) + ", " + Fmt.n(it.second)) }
                recap.topQuest?.let { RecapLine("Meiste Punkte", "${it.first} (${Fmt.n(it.second)})") }
                if (chaos.gerald) RecapLine("Gerald", Fmt.n(recap.geraldXp) + if (recap.xp > recap.geraldXp) " (geschlagen)" else "")
                if (chaos.lazyTax && recap.tax > 0) RecapLine("Faulheitssteuer", "−${Fmt.n(recap.tax)} Glut")
                if (recap.newRecord) Tag("Neuer Wochenrekord", Frames.gold, Pal.ink, icon = "crown")
            }
        }
        if (chaos.saga) {
            val key = "saga-${recap.weekStart}"
            val paragraphs = state.save.storyCache[key]?.split("\n\n") ?: Story.saga(recap, chaos.gerald)
            ScrollPanel(seed = kw + 1) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    T("Die Saga der ${kw}. Woche", Q.type.heading, color = Pal.ink)
                    paragraphs.forEach { T(it, Q.type.body, color = Pal.ink) }
                }
            }
        }
        recap.bestMoment?.let { m ->
            Column(Modifier.fillMaxWidth().stonePanel(Frames.gold).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                T("DER HELLSTE MOMENT", Q.type.label, color = Pal.ink)
                T(m.questTitle, Q.type.heading, color = Pal.ink)
                T("+${Fmt.n(m.points)} · " + Fmt.day(m.at).format(Fmt.weekday), Q.type.bodyBold, color = Pal.wood0)
            }
        }
        PixelButton("Neue Saison beginnen", { nav.back() }, Modifier.fillMaxWidth(), icon = "flag")
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun RecapLine(label: String, value: String) {
    Row {
        T(label, Q.type.body, Modifier.weight(1f), color = Pal.inkSoft)
        T(value, Q.type.bodyBold, color = Pal.ink, align = TextAlign.End)
    }
}

/** Briefe: Steuerbescheid (C04) und Vermählungsanzeige (C06). */
@Composable
fun LetterScreen(id: String, state: AppState, act: Actions, nav: Nav) {
    val game = state.game
    val letter: Letter? = when {
        id.startsWith("steuer-") -> game.taxNotices.firstOrNull { it.id == id }?.let { Story.taxLetter(it) }
        id.startsWith("hochzeit-") -> game.marriages.firstOrNull { "hochzeit-${it.firstId}-${it.secondId}" == id }?.let { m ->
            val title = { qid: String -> state.save.quests.firstOrNull { it.id == qid }?.title ?: "?" }
            Story.weddingLetter(m, title(m.firstId), title(m.secondId), Fmt.day(m.since))
        }
        else -> null
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenTitle("Post", onBack = { nav.back() })
        if (letter == null) {
            T("Dieser Brief ist nicht mehr da.", Q.type.body, color = Pal.boneDim)
        } else {
            ScrollPanel(seed = id.hashCode()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    T(letter.sender.uppercase(), Q.type.label, color = Pal.inkSoft)
                    T(letter.title, Q.type.heading, color = Pal.ink)
                    letter.body.forEach { T(it, Q.type.body, color = Pal.ink) }
                    T(letter.footer, Q.type.small, color = Pal.inkSoft)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { PixelIcon("seal", 48.dp) }
                }
            }
            PixelButton("Abheften", {
                act.meta { it.copy(seenLetters = (it.seenLetters + id).distinct()) }
                nav.back()
            }, Modifier.fillMaxWidth(), icon = "check")
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** C08: Abspann nach einem besiegten Boss. */
@Composable
fun CreditsScreen(questId: String, state: AppState, nav: Nav) {
    val q = state.save.quests.firstOrNull { it.id == questId }
    if (q == null) {
        LaunchedEffect(Unit) { nav.back() }
        return
    }
    val results = state.game.results.values.filter { it.questId == questId }
    val xp = results.sumOf { it.points }
    val first = results.minOfOrNull { it.at } ?: q.createdAt
    val last = results.maxOfOrNull { it.at } ?: q.createdAt
    val days = ChronoUnit.DAYS.between(Fmt.day(first), Fmt.day(last)).toInt() + 1
    val lines = Story.credits(q, xp, days)
    val scroll = rememberScrollState()
    LaunchedEffect(scroll.maxValue) {
        if (scroll.maxValue > 0) scroll.animateScrollTo(scroll.maxValue, tween(durationMillis = 22_000, easing = LinearEasing))
    }
    Box(Modifier.fillMaxSize().background(Pal.night0)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Spacer(Modifier.height(520.dp))
            PixelIcon("skull", 56.dp)
            lines.forEach { (role, name) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (role.isNotEmpty()) T(role.uppercase(), Q.type.label, color = Pal.boneFaint, align = TextAlign.Center)
                    T(name, if (role.isEmpty()) Q.type.title else Q.type.heading, color = if (role.isEmpty()) Pal.gold0 else Pal.bone, align = TextAlign.Center)
                }
            }
            Spacer(Modifier.height(560.dp))
        }
        Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.TopEnd) {
            PixelButton("Überspringen", { nav.back() }, kind = Btn.STONE, small = true)
        }
    }
}

@Composable
fun Spacer8() = Spacer(Modifier.width(8.dp))
