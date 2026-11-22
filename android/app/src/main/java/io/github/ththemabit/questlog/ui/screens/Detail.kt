package io.github.ththemabit.questlog.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.ththemabit.questlog.ai.GameMaster
import io.github.ththemabit.questlog.core.Categories
import io.github.ththemabit.questlog.core.Done
import io.github.ththemabit.questlog.core.Hit
import io.github.ththemabit.questlog.core.Modifier as Mod
import io.github.ththemabit.questlog.core.Modifiers
import io.github.ththemabit.questlog.core.Rules
import io.github.ththemabit.questlog.core.WindowStatus
import io.github.ththemabit.questlog.data.AppState
import io.github.ththemabit.questlog.ui.Actions
import io.github.ththemabit.questlog.ui.Fmt
import io.github.ththemabit.questlog.ui.LocalAi
import io.github.ththemabit.questlog.ui.LocalFx
import io.github.ththemabit.questlog.ui.Nav
import io.github.ththemabit.questlog.ui.Overlay
import io.github.ththemabit.questlog.ui.Route
import io.github.ththemabit.questlog.ui.SessionMode
import io.github.ththemabit.questlog.ui.UiController
import io.github.ththemabit.questlog.ui.components.ScreenTitle
import io.github.ththemabit.questlog.ui.components.WindowBar
import io.github.ththemabit.questlog.ui.pixel.Btn
import io.github.ththemabit.questlog.ui.pixel.Frames
import io.github.ththemabit.questlog.ui.pixel.PixelButton
import io.github.ththemabit.questlog.ui.pixel.PixelIcon
import io.github.ththemabit.questlog.ui.pixel.ScrollPanel
import io.github.ththemabit.questlog.ui.pixel.SectionTitle
import io.github.ththemabit.questlog.ui.pixel.H
import io.github.ththemabit.questlog.ui.pixel.T
import io.github.ththemabit.questlog.ui.pixel.Tag
import io.github.ththemabit.questlog.ui.pixel.bevel
import io.github.ththemabit.questlog.ui.pixel.parchmentCard
import io.github.ththemabit.questlog.ui.pixel.stonePanel
import io.github.ththemabit.questlog.ui.theme.Pal
import io.github.ththemabit.questlog.ui.theme.Q
import java.time.LocalTime

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(questId: String, state: AppState, act: Actions, nav: Nav, ui: UiController, preset: List<String> = emptyList()) {
    val game = state.game
    val qs = game.quest(questId)
    val fx = LocalFx.current
    if (qs == null) {
        Column(Modifier.fillMaxSize()) {
            ScreenTitle("Quest", onBack = { nav.back() })
            T("Diese Quest gibt es nicht mehr.", Q.type.body, Modifier.padding(16.dp), color = Pal.boneDim)
        }
        return
    }
    val q = qs.quest
    val level = game.player.level
    var mods by remember(questId) { mutableStateOf(preset) }
    val preview = game.preview(questId, mods)
    val done = qs.status == WindowStatus.DONE
    val unlocked: (String) -> Boolean = { key -> (Modifiers.of(key)?.unlockLevel ?: 99) <= level }
    val hour = LocalTime.now().hour
    val wagerFree = game.wager == null || game.wager.resolved
    // KI-Spielleiter: eigene Texte zu genau dieser Quest, falls schon geschrieben
    val ai = LocalAi.current
    val aiCfg = state.save.settings.ai
    val lore = if (aiCfg.ready && aiCfg.questTexts) GameMaster.textsFor(state.save, q) else null
    LaunchedEffect(q.id, aiCfg.ready, aiCfg.questTexts) { if (aiCfg.ready && aiCfg.questTexts) ai.focus(q.id) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            ScreenTitle(if (q.boss) "Boss" else "Quest", onBack = { nav.back() }) {
                if (aiCfg.ready && aiCfg.forge) io.github.ththemabit.questlog.ui.pixel.IconTap("orb", "Mit dem Spielleiter besprechen", {
                    nav.go(Route.Master(prefill = "Zur Quest „${q.title}“: "))
                })
                io.github.ththemabit.questlog.ui.pixel.IconTap("quill", "Bearbeiten", { nav.go(Route.Edit(q.id)) })
            }
        }
        item {
            ScrollPanel(seed = q.id.hashCode()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PixelIcon(Categories.of(q.category).icon, 22.dp)
                        Spacer(Modifier.width(8.dp))
                        T(Categories.of(q.category).name.uppercase(), Q.type.label, Modifier.weight(1f), color = Pal.inkSoft)
                        if (qs.isBounty) Tag("Kopfgeld ×2", Frames.gold, Pal.ink, icon = "target")
                    }
                    H(q.title, Q.type.title, color = Pal.ink)
                    lore?.intro?.let { intro ->
                        Row(verticalAlignment = Alignment.Top) {
                            PixelIcon("orb", 16.dp, Modifier.padding(top = 2.dp))
                            Spacer(Modifier.width(8.dp))
                            T(intro, Q.type.body, color = Pal.wood0)
                        }
                    }
                    T("${Fmt.rhythm(q.minDays, q.maxDays)} · ${Fmt.effort(q.effortMin)} · Wichtigkeit ${q.importance} · Überwindung ${q.dread}", Q.type.small, color = Pal.inkSoft)
                    T(Fmt.window(qs), Q.type.bodyBold, color = when (qs.status) {
                        WindowStatus.OVERDUE -> Pal.blood1
                        WindowStatus.EARLY -> Pal.mana2
                        WindowStatus.DONE -> Pal.inkFaint
                        else -> Pal.leaf2
                    })
                    if (q.recurring) WindowBar(qs)
                    q.why?.takeIf { it.isNotBlank() }?.let { T("„$it“", Q.type.body, color = Pal.inkSoft) }
                    q.ifThen?.takeIf { it.isNotBlank() }?.let { T("Wenn-dann: $it", Q.type.small, color = Pal.inkSoft) }
                    q.note?.takeIf { it.isNotBlank() }?.let { T(it, Q.type.small, color = Pal.inkSoft) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Info("Zuletzt", qs.lastDone?.let { Fmt.relative(it, game.today) } ?: "noch nie", Modifier.weight(1f))
                        Info("Erledigt", "${qs.doneCount}×", Modifier.weight(1f))
                    }
                    if (qs.bestSec != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Info("Bestzeit", Fmt.duration(qs.bestSec), Modifier.weight(1f))
                        Info("Ø Dauer", qs.avgSec?.let { Fmt.duration(it) } ?: "–", Modifier.weight(1f))
                    }
                }
            }
        }

        if (q.boss) (lore?.bossIntro ?: Story.bossIntro(q)).let { text ->
            item {
                Row(Modifier.fillMaxWidth().stonePanel(Frames.blood).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    PixelIcon("skull", 28.dp)
                    Spacer(Modifier.width(10.dp))
                    T(text, Q.type.body, Modifier.weight(1f), color = Pal.bone)
                }
            }
        }
        if (q.subtasks.isNotEmpty()) {
            item { SectionTitle(if (q.boss) "Treffer landen" else "Teilaufgaben", icon = if (q.boss) "sword" else "check", count = q.subtasks.size) }
            items(q.subtasks, key = { it.id }) { st ->
                val hit = st.id in qs.hitsThisCycle
                Row(
                    Modifier.fillMaxWidth().parchmentCard(st.id.hashCode())
                        .clickable(enabled = !hit && !done, role = Role.Checkbox) {
                            val id = act.hit(q.id, st.id)
                            fx.hit()
                            ui.show(Overlay.Result(id, game))
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PixelIcon(if (hit) "check" else if (q.boss) "sword" else "scroll", 22.dp, alpha = if (hit) 1f else 0.7f)
                    Spacer(Modifier.width(10.dp))
                    T(st.title, Q.type.body, Modifier.weight(1f), color = if (hit) Pal.inkFaint else Pal.ink)
                    if (hit) T("ERLEDIGT", Q.type.label, color = Pal.leaf2)
                }
            }
            if (q.boss && qs.bossRage > 0) item {
                T("Der Boss ist zornig: ${(qs.bossRage * 100).toInt()} % weniger Siegesbeute. Jeder Treffer beruhigt ihn.", Q.type.small, color = Pal.blood0)
            }
        }

        if (!done) {
            val cards = Modifiers.attachable + listOf(Modifiers.JOKER)
            item { SectionTitle("Karten auflegen", icon = "clover") }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    cards.forEach { key ->
                        val m = Modifiers.of(key) ?: return@forEach
                        val available = if (key == Modifiers.JOKER) game.inventory.jokers > 0 else unlocked(key)
                        val on = key in mods
                        ModChip(m, on, available, if (key == Modifiers.JOKER) "${game.inventory.jokers}×" else null) {
                            mods = if (on) mods - key else mods + key
                            fx.tap()
                        }
                    }
                    preset.forEach { key -> Modifiers.of(key)?.let { ModChip(it, true, true, null) {} } }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().stonePanel().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T("JETZT ERLEDIGT", Q.type.label, Modifier.weight(1f), color = Pal.boneDim)
                        T("+${preview?.points ?: qs.preview}", Q.type.numberBig, color = Pal.gold0)
                    }
                    preview?.lines?.forEach { l ->
                        Row {
                            T(l.label, Q.type.small, Modifier.weight(1f), color = Pal.boneDim, maxLines = 1)
                            T(l.value, Q.type.small, color = Pal.bone)
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PixelButton("Erledigt", { completeWithResult(act, ui, fx, game, q.id, mods = mods); nav.back() }, Modifier.fillMaxWidth(), icon = "seal")
                    if (q.mini != null && !game.hardcore) {
                        PixelButton("Mini: ${q.mini} (+${qs.miniPreview})", { completeWithResult(act, ui, fx, game, q.id, mini = true); nav.back() }, Modifier.fillMaxWidth(), kind = Btn.STONE, icon = "feather")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PixelButton("Speedrun", { nav.go(Route.Session(q.id, SessionMode.SPEEDRUN, mods)) }, Modifier.weight(1f), kind = Btn.STONE, icon = "hourglass")
                        if (unlocked(Modifiers.DREISONG)) PixelButton("Drei Songs", { nav.go(Route.Session(q.id, SessionMode.DREISONG, mods)) }, Modifier.weight(1f), kind = Btn.STONE, icon = "note")
                    }
                    if (unlocked(Modifiers.HANDYWEG) || unlocked(Modifiers.TANZ)) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (unlocked(Modifiers.HANDYWEG)) PixelButton("Handy weg", { nav.go(Route.Session(q.id, SessionMode.HANDYWEG, mods)) }, Modifier.weight(1f), kind = Btn.MANA, icon = "moon")
                        if (unlocked(Modifiers.TANZ)) PixelButton("Tanzpause", { nav.go(Route.Session(q.id, SessionMode.TANZ, mods)) }, Modifier.weight(1f), kind = Btn.LEAF, icon = "boot")
                    }
                    if (unlocked(Modifiers.WAGER) && wagerFree && hour < Rules.WAGER_DEADLINE_HOUR) {
                        PixelButton("Doppelt oder nichts (${Fmt.n(Rules.wagerStake(game.player.glut))} Glut)", {
                            ui.show(Overlay.Confirm(
                                "Doppelt oder nichts",
                                "Du setzt ${Fmt.n(Rules.wagerStake(game.player.glut))} Glut darauf, dass du „${q.title}“ heute vor 20 Uhr erledigst. Klappt es, bekommst du das Doppelte zurück.",
                                "Einsatz setzen",
                            ) { act.placeWager(q.id); fx.tap(); ui.toast("Einsatz gesetzt. Bis 20 Uhr!") })
                        }, Modifier.fillMaxWidth(), kind = Btn.EMBER, icon = "coin")
                    }
                    PixelButton("Nachtragen", {
                        ui.show(Overlay.DatePick("Wann erledigt?", game.today, min = game.today.minusDays(60), max = game.today, onPick = { d ->
                            if (d != null) {
                                val now = act.now()
                                val at = if (d == game.today) now else d.atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                                completeWithResult(act, ui, fx, game, q.id, mods = emptyList(), at = at, backfilled = d != game.today)
                            }
                        }))
                    }, Modifier.fillMaxWidth(), kind = Btn.STONE, icon = "calendar")
                }
            }
        } else {
            item { T("Diese einmalige Quest ist erledigt. Über die Chronik unten kannst du einen Eintrag zurücknehmen.", Q.type.small, color = Pal.boneDim) }
        }

        val history = qs.history.mapNotNull { id -> game.results[id] }.sortedByDescending { it.at }.take(15)
        if (history.isNotEmpty()) {
            item { SectionTitle("Chronik", icon = "book", count = qs.history.size) }
            items(history, key = { "h-" + it.eventId }) { r ->
                Row(
                    Modifier.fillMaxWidth().stonePanel(Frames.stoneFlat).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PixelIcon(if (r.mini) "feather" else if (r.hit) "sword" else "check", 18.dp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        T(Fmt.relative(r.at, game.today) + if (r.mini) " · Mini" else if (r.hit) " · Teilaufgabe" else "", Q.type.small, color = Pal.bone)
                        val e = state.save.events.firstOrNull { it.id == r.eventId }
                        val extra = (e as? Done)?.let { d -> listOfNotNull(d.durationSec?.let { Fmt.duration(it) }, if (d.backfilled) "nachgetragen" else null).joinToString(" · ") }
                        if (!extra.isNullOrEmpty()) T(extra, Q.type.label, color = Pal.boneFaint)
                        if (e is Hit) T(q.subtasks.firstOrNull { it.id == e.subtaskId }?.title ?: "", Q.type.label, color = Pal.boneFaint)
                    }
                    T("+${r.points}", Q.type.number.copy(fontSize = Q.type.number.fontSize * 0.75f), color = Pal.gold1)
                    io.github.ththemabit.questlog.ui.pixel.IconTap("trash", "Eintrag löschen", {
                        ui.show(Overlay.Confirm("Eintrag löschen?", "Die ${r.points} Punkte von ${Fmt.relative(r.at, game.today)} werden zurückgenommen. Alles wird neu berechnet.", "Löschen") {
                            act.undo(r.eventId)
                            fx.undo()
                        })
                    }, size = 20.dp)
                }
            }
        }
    }
}

@Composable
private fun Info(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        T(label.uppercase(), Q.type.label, color = Pal.inkFaint)
        T(value, Q.type.bodyBold, color = Pal.ink, maxLines = 1)
    }
}

@Composable
fun ModChip(m: Mod, on: Boolean, available: Boolean, badge: String?, onClick: () -> Unit) {
    val u = io.github.ththemabit.questlog.ui.pixel.LocalPx.current
    val frame = if (on) Frames.gold else if (available) Frames.stoneFlat else Frames.stoneDark
    Row(
        Modifier
            .drawBehind { bevel(u, frame, pressed = on) }
            .clickable(enabled = available, role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PixelIcon(if (available) m.icon else "lock", 18.dp, alpha = if (available) 1f else 0.6f)
        Column {
            T(m.name, Q.type.small, color = if (on) Pal.ink else if (available) Pal.bone else Pal.boneFaint, maxLines = 1)
            T(if (available) "×" + String.format(java.util.Locale.GERMANY, "%.1f", m.factor) + (badge?.let { " · $it" } ?: "")
                else if (m.key == Modifiers.JOKER) "keine im Inventar" else "ab Stufe ${m.unlockLevel}",
                Q.type.label, color = if (on) Pal.inkSoft else Pal.boneFaint)
        }
    }
}
