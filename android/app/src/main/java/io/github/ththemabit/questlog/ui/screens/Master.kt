package io.github.ththemabit.questlog.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.ththemabit.questlog.ai.AiPhase
import io.github.ththemabit.questlog.ai.AiPort
import io.github.ththemabit.questlog.ai.AiStatus
import io.github.ththemabit.questlog.ai.ChatTurn
import io.github.ththemabit.questlog.ai.LiveReply
import io.github.ththemabit.questlog.ai.Proposal
import io.github.ththemabit.questlog.core.AiConfig
import io.github.ththemabit.questlog.core.Categories
import io.github.ththemabit.questlog.data.AppState
import io.github.ththemabit.questlog.ui.Fmt
import io.github.ththemabit.questlog.ui.LocalAi
import io.github.ththemabit.questlog.ui.LocalFx
import io.github.ththemabit.questlog.ui.Nav
import io.github.ththemabit.questlog.ui.Route
import io.github.ththemabit.questlog.ui.UiController
import io.github.ththemabit.questlog.ui.components.PixelField
import io.github.ththemabit.questlog.ui.components.ScreenTitle
import io.github.ththemabit.questlog.ui.pixel.Btn
import io.github.ththemabit.questlog.ui.pixel.Frame
import io.github.ththemabit.questlog.ui.pixel.Frames
import io.github.ththemabit.questlog.ui.pixel.H
import io.github.ththemabit.questlog.ui.pixel.IconTap
import io.github.ththemabit.questlog.ui.pixel.LocalPx
import io.github.ththemabit.questlog.ui.pixel.PixelButton
import io.github.ththemabit.questlog.ui.pixel.PixelIcon
import io.github.ththemabit.questlog.ui.pixel.ScrollPanel
import io.github.ththemabit.questlog.ui.pixel.T
import io.github.ththemabit.questlog.ui.pixel.Tag
import io.github.ththemabit.questlog.ui.pixel.bevel
import io.github.ththemabit.questlog.ui.pixel.parchmentCard
import io.github.ththemabit.questlog.ui.pixel.stonePanel
import io.github.ththemabit.questlog.ui.theme.Pal
import io.github.ththemabit.questlog.ui.theme.Q
import kotlinx.coroutines.delay
import java.time.LocalDate

private val ideas = listOf(
    "Plane meine Woche: Was steht an, was fehlt noch?",
    "Ich will regelmäßiger Sport machen.",
    "Mach mir einen Putzplan für die Wohnung.",
    "Welche Quest soll ich jetzt angehen?",
    "Mein größter Boss ist zu groß. Teil ihn auf.",
)

private val waiting = listOf(
    "Der Spielleiter befragt die Kristallkugel …",
    "Der Spielleiter spitzt die Feder …",
    "Der Spielleiter blättert in alten Schriftrollen …",
    "Der Spielleiter würfelt heimlich hinter dem Schirm …",
)

private val inset = Frame(Pal.parch0, Pal.parch3, Pal.parch1, edge = Pal.inkSoft)
private val insetOn = Frame(Pal.parch1, Pal.gold0, Pal.gold2, edge = Pal.wood0)

/** Gespräch mit dem KI-Spielleiter: erzählen, was ansteht, Vorschläge abhaken, übernehmen. */
@Composable
fun MasterScreen(route: Route.Master, state: AppState, nav: Nav, ui: UiController) {
    val ai = LocalAi.current
    val fx = LocalFx.current
    val cfg = state.save.settings.ai
    val chat by ai.chat.collectAsState()
    val live by ai.live.collectAsState()
    val status by ai.status.collectAsState()
    var input by remember { mutableStateOf(if (route.send) "" else route.prefill) }
    LaunchedEffect(Unit) {
        if (route.send && route.prefill.isNotBlank() && cfg.ready) {
            ai.send(route.prefill)
            nav.replace(Route.Master())
        }
    }
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Spielleiter", onBack = { nav.back() }) {
            if (chat.isNotEmpty() && live == null) IconTap("trash", "Gespräch leeren", { ai.clearChat(); fx.undo() }, size = 26.dp)
        }
        if (!cfg.ready) {
            Sleeping(cfg, nav)
            return@Column
        }
        StatusLine(status, cfg)
        val list = rememberLazyListState()
        val showIdeas = chat.isEmpty() && live == null
        val last = (if (showIdeas) 1 else 0) + chat.size + (if (live != null) 1 else 0)
        LaunchedEffect(chat.size, live != null) { list.animateScrollToItem(last) }
        LaunchedEffect((live?.text?.length ?: 0) / 60, live?.listing) { if (live != null) list.scrollToItem(last, 100_000) }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = list,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "intro") {
                MasterBubble(seed = 1) {
                    T("Erzähl mir, was ansteht. Ich mache Quests daraus, ändere bestehende oder räume alte weg. Übernommen wird nur, was du abhakst.", Q.type.body, color = Pal.ink)
                }
            }
            if (showIdeas) item(key = "ideas") { Ideas { ai.send(it); fx.tap() } }
            itemsIndexed(chat, key = { i, _ -> "turn-$i" }) { i, t ->
                if (t.fromUser) UserBubble(t.text) else Reply(t, i, ai, ui)
            }
            live?.let { l -> item(key = "live") { LiveBubble(l) } }
        }
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            PixelField(input, { input = it }, "Was steht an?", Modifier.weight(1f), singleLine = false, maxLines = 5)
            if (live != null) IconTap("cross", "Abbrechen", { ai.stop(); fx.undo() })
            else IconTap("play", "Senden", { ai.send(input); input = ""; fx.tap() }, enabled = input.isNotBlank())
        }
    }
}

@Composable
private fun Sleeping(cfg: AiConfig, nav: Nav) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScrollPanel(seed = 21) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PixelIcon("orb", 48.dp)
                H("Der Spielleiter schläft", Q.type.heading, color = Pal.ink)
                T(
                    if (!cfg.enabled) "Verbinde Questlog mit deiner KI-Box (Ollama, LM Studio, llama.cpp) oder einem Cloud-Dienst. Dann baut der Spielleiter Quests aus dem, was du erzählst, und schreibt Texte, die genau zu deinen Aufgaben passen."
                    else if (cfg.baseUrl.isBlank()) "Es fehlt noch die Adresse deines KI-Servers."
                    else "Es fehlt noch das Modell. Unter Optionen „Verbindung testen“ zeigt alle Modelle des Servers.",
                    Q.type.body, color = Pal.inkSoft,
                )
                T("Ohne KI funktioniert alles wie gewohnt.", Q.type.small, color = Pal.inkFaint)
            }
        }
        PixelButton("Einrichten", { nav.focus = "ai"; nav.go(Route.Options) }, Modifier.fillMaxWidth(), icon = "gear")
    }
}

@Composable
fun StatusLine(status: AiStatus, cfg: AiConfig, modifier: Modifier = Modifier) {
    val color = when (status.phase) {
        AiPhase.OFF -> Pal.stone3
        AiPhase.IDLE -> Pal.leaf0
        AiPhase.WORKING -> Pal.gold0
        AiPhase.OFFLINE -> Pal.blood0
    }
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(Pal.edge).padding(1.dp).background(color))
        Spacer(Modifier.width(8.dp))
        val text = when (status.phase) {
            AiPhase.OFFLINE -> "Nicht erreichbar" + (status.error?.let { ": $it" } ?: "")
            else -> listOf(cfg.model, status.text).filter { it.isNotBlank() }.joinToString(" · ")
        }
        T(text, Q.type.label, color = if (status.phase == AiPhase.OFFLINE) Pal.blood0 else Pal.boneDim, maxLines = 2)
    }
}

@Composable
private fun MasterBubble(seed: Int, active: Boolean = false, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(end = 24.dp).parchmentCard(seed).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (active) {
                val t = rememberInfiniteTransition(label = "orb")
                val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse), label = "glow")
                PixelIcon("orb", 18.dp, Modifier.alpha(a))
            } else PixelIcon("orb", 18.dp)
            Spacer(Modifier.width(6.dp))
            T("SPIELLEITER", Q.type.label, color = Pal.inkSoft)
        }
        content()
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth().padding(start = 40.dp), horizontalArrangement = Arrangement.End) {
        Box(Modifier.widthIn(min = 48.dp).stonePanel(Frames.stoneFlat).padding(horizontal = 12.dp, vertical = 10.dp)) {
            T(text, Q.type.body, color = Pal.bone)
        }
    }
}

@Composable
private fun Ideas(onPick: (String) -> Unit) {
    val u = LocalPx.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        T("ZUM BEISPIEL", Q.type.label, Modifier.padding(start = 4.dp), color = Pal.boneFaint)
        ideas.forEach { idea ->
            Row(
                Modifier.fillMaxWidth().drawBehind { bevel(u, Frames.stoneFlat) }.clickable(role = Role.Button) { onPick(idea) }.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PixelIcon("scroll", 18.dp)
                Spacer(Modifier.width(8.dp))
                T(idea, Q.type.small, color = Pal.bone)
            }
        }
    }
}

@Composable
private fun LiveBubble(live: LiveReply) {
    val wait by produceState(0) {
        while (true) {
            delay(2600)
            value += 1
        }
    }
    MasterBubble(seed = 3, active = true) {
        if (live.text.isNotEmpty()) T(live.text, Q.type.body, color = Pal.ink)
        else T(waiting[wait % waiting.size], Q.type.body, color = Pal.inkSoft)
        if (live.listing) T("Schreibt die Quests auf …", Q.type.small, color = Pal.wood0)
    }
}

@Composable
private fun Reply(turn: ChatTurn, index: Int, ai: AiPort, ui: UiController) {
    val fx = LocalFx.current
    var picks by remember(index, turn.proposals) { mutableStateOf(turn.proposals.indices.toSet()) }
    MasterBubble(seed = index + 7) {
        T(turn.text, Q.type.body, color = if (turn.error) Pal.blood1 else Pal.ink)
        turn.proposals.forEachIndexed { i, p ->
            val on = if (turn.applied) i in turn.picked else i in picks
            ProposalRow(p, on, enabled = !turn.applied) {
                picks = if (i in picks) picks - i else picks + i
                fx.tap()
            }
        }
        if (turn.proposals.isNotEmpty()) {
            if (turn.applied) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PixelIcon("check", 18.dp)
                    Spacer(Modifier.width(6.dp))
                    T("Ins Questlog übernommen", Q.type.bodyBold, color = Pal.leaf2)
                }
            } else {
                PixelButton(
                    if (picks.isEmpty()) "Nichts ausgewählt" else "Übernehmen (${picks.size})",
                    {
                        val n = ai.apply(index, picks)
                        fx.done()
                        ui.toast(if (n == 1) "Eine Änderung übernommen." else "$n Änderungen übernommen.")
                    },
                    Modifier.fillMaxWidth(), enabled = picks.isNotEmpty(), icon = "seal",
                )
            }
        }
    }
}

@Composable
private fun ProposalRow(p: Proposal, on: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val u = LocalPx.current
    Row(
        Modifier.fillMaxWidth()
            .drawBehind { bevel(u, if (on) insetOn else inset, pressed = true) }
            .clickable(enabled = enabled, role = Role.Checkbox, onClick = onToggle)
            .padding(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(22.dp).drawBehind { bevel(u, inset, pressed = true) }, contentAlignment = Alignment.Center) {
            if (on) PixelIcon("check", 18.dp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            when (p) {
                is Proposal.New -> {
                    val d = p.draft
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PixelIcon(Categories.of(d.category).icon, 18.dp)
                        Spacer(Modifier.width(6.dp))
                        T(d.title, Q.type.bodyBold, Modifier.weight(1f), color = Pal.ink)
                        if (d.boss) Tag("Boss", Frames.violet, Pal.bone, icon = "skull")
                    }
                    val rhythm = d.dueDate?.let { "bis " + LocalDate.parse(it).format(Fmt.dayMonth) } ?: Fmt.rhythm(d.minDays, d.maxDays)
                    T("$rhythm · ${Fmt.effort(d.effortMin)} · Wichtigkeit ${d.importance} · Überwindung ${d.dread}", Q.type.small, color = Pal.inkSoft)
                    if (d.subtasks.isNotEmpty()) T((if (d.boss) "Treffer: " else "Teilaufgaben: ") + d.subtasks.joinToString(", "), Q.type.small, color = Pal.inkSoft)
                    d.mini?.let { T("Mini: $it", Q.type.small, color = Pal.inkSoft) }
                    p.intro?.let { T("„$it“", Q.type.small, color = Pal.wood0) }
                }
                is Proposal.Change -> {
                    T("ÄNDERN", Q.type.label, color = Pal.mana2)
                    T(p.before.title, Q.type.bodyBold, color = Pal.ink)
                    T(p.summary, Q.type.small, color = Pal.inkSoft)
                }
                is Proposal.Archive -> {
                    T("ARCHIVIEREN", Q.type.label, color = Pal.blood1)
                    T(p.title, Q.type.bodyBold, color = Pal.ink)
                }
            }
        }
    }
}
