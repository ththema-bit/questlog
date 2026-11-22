package io.github.ththemabit.questlog.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.ththemabit.questlog.core.Modifiers
import io.github.ththemabit.questlog.core.Reward
import io.github.ththemabit.questlog.core.Trophies
import io.github.ththemabit.questlog.data.AppState
import io.github.ththemabit.questlog.ui.Actions
import io.github.ththemabit.questlog.ui.Fmt
import io.github.ththemabit.questlog.ui.LocalFx
import io.github.ththemabit.questlog.ui.Nav
import io.github.ththemabit.questlog.ui.Overlay
import io.github.ththemabit.questlog.ui.UiController
import io.github.ththemabit.questlog.ui.components.ChipRow
import io.github.ththemabit.questlog.ui.components.ParchSection
import io.github.ththemabit.questlog.ui.components.PixelField
import io.github.ththemabit.questlog.ui.components.ScreenTitle
import io.github.ththemabit.questlog.ui.components.Stepper
import io.github.ththemabit.questlog.ui.pixel.Btn
import io.github.ththemabit.questlog.ui.pixel.Frames
import io.github.ththemabit.questlog.ui.pixel.IconTap
import io.github.ththemabit.questlog.ui.pixel.PixelButton
import io.github.ththemabit.questlog.ui.pixel.PixelIcon
import io.github.ththemabit.questlog.ui.pixel.SectionTitle
import io.github.ththemabit.questlog.ui.pixel.T
import io.github.ththemabit.questlog.ui.pixel.Tag
import io.github.ththemabit.questlog.ui.pixel.parchmentCard
import io.github.ththemabit.questlog.ui.pixel.stonePanel
import io.github.ththemabit.questlog.ui.theme.Pal
import io.github.ththemabit.questlog.ui.theme.Q
import java.util.Locale

private val rewardIcons = listOf("coin", "star", "book", "mug", "heart", "note", "crown", "chest", "pot", "moon")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TreasuryScreen(state: AppState, act: Actions, nav: Nav, ui: UiController) {
    val game = state.game
    val coins = game.player.coins
    val fx = LocalFx.current
    var editing by remember { mutableStateOf<Reward?>(null) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenTitle("Schatzkammer", onBack = null) }
        item {
            Row(Modifier.fillMaxWidth().stonePanel(Frames.wood).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                PixelIcon("coin", 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    T(Fmt.n(coins), Q.type.numberBig, color = Pal.gold0)
                    T("Jeder Punkt bringt eine Münze, dazu kommt Beute. Ausgeben kostet keine XP.", Q.type.small, color = Pal.parch2)
                }
            }
        }
        item { SectionTitle("Belohnungen", icon = "chest", count = state.save.rewards.size) }
        if (state.save.rewards.isEmpty() && editing == null) item {
            T("Leg dir echte Belohnungen an und bestimme selbst, was sie kosten: Pizza, Kinoabend, ein neues Buch.", Q.type.small, color = Pal.boneDim)
        }
        items(state.save.rewards, key = { it.id }) { r ->
            Row(Modifier.fillMaxWidth().parchmentCard(r.id.hashCode()).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                PixelIcon(r.icon, 28.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    T(r.name, Q.type.quest, color = Pal.ink, maxLines = 2)
                    T("${Fmt.n(r.price)} Münzen", Q.type.small, color = if (coins >= r.price) Pal.leaf2 else Pal.inkFaint)
                }
                IconTap("quill", "${r.name} bearbeiten", { editing = r }, size = 22.dp)
                PixelButton("Kaufen", {
                    ui.show(Overlay.Confirm("${r.name} kaufen?", "Das kostet ${Fmt.n(r.price)} Münzen. Danach hast du noch ${Fmt.n(coins - r.price)}.", "Kaufen") {
                        act.buy(r)
                        fx.loot()
                        ui.toast("Gönn dir: ${r.name}!")
                    })
                }, kind = Btn.GOLD, small = true, enabled = coins >= r.price)
            }
        }
        item {
            val e = editing
            if (e == null) {
                PixelButton("Neue Belohnung", { editing = Reward(act.newId(), "", 500) }, Modifier.fillMaxWidth(), kind = Btn.STONE, icon = "plus")
            } else {
                RewardEditor(e, onSave = { r ->
                    if (r.name.isBlank()) { fx.error(); ui.toast("Die Belohnung braucht einen Namen.") } else {
                        act.saveReward(r); editing = null; fx.tap()
                    }
                }, onDelete = if (state.save.rewards.any { it.id == e.id }) ({ act.deleteReward(e.id); editing = null }) else null, onCancel = { editing = null })
            }
        }
        if (game.purchases.isNotEmpty()) {
            item { SectionTitle("Eingelöst", icon = "check", count = game.purchases.size) }
            items(game.purchases.sortedByDescending { it.at }.take(8), key = { it.id }) { b ->
                Row(Modifier.fillMaxWidth().stonePanel(Frames.stoneFlat).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    T(b.name, Q.type.body, Modifier.weight(1f), color = Pal.bone)
                    T(Fmt.relative(b.at, game.today) + "  ", Q.type.label, color = Pal.boneFaint)
                    T("−${Fmt.n(b.price)}", Q.type.number.copy(fontSize = Q.type.number.fontSize * 0.7f), color = Pal.gold2)
                }
            }
        }
        item { SectionTitle("Inventar", icon = "chest") }
        item {
            Column(Modifier.fillMaxWidth().stonePanel().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PixelIcon("shield", 24.dp)
                    Spacer(Modifier.width(8.dp))
                    T("Schutzschilde", Q.type.body, Modifier.weight(1f), color = Pal.bone)
                    T("${game.inventory.shields} / 3", Q.type.number.copy(fontSize = Q.type.number.fontSize * 0.8f), color = Pal.mana0)
                }
                T("Ein Schild rettet die Serie an einem zweiten freien Tag in Folge. Alle 10 Serientage gibt es einen neuen.", Q.type.small, color = Pal.boneDim)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PixelIcon("clover", 24.dp)
                    Spacer(Modifier.width(8.dp))
                    T("Glückskarten", Q.type.body, Modifier.weight(1f), color = Pal.bone)
                    T("${game.inventory.jokers}", Q.type.number.copy(fontSize = Q.type.number.fontSize * 0.8f), color = Pal.leaf0)
                }
                T("Trophäen ${game.inventory.trophies.map { it.first }.distinct().size} / ${Trophies.all.size}", Q.type.label, color = Pal.boneFaint)
                if (game.inventory.trophies.isEmpty()) T("Noch keine. Beute fällt zufällig nach Quests, Bosse lassen immer etwas fallen.", Q.type.small, color = Pal.boneDim)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    game.inventory.trophies.map { it.first }.distinct().forEach { name ->
                        val rare = Trophies.all.firstOrNull { it.name == name }?.rare == true
                        Tag(name, if (rare) Frames.violet else Frames.gold, if (rare) Pal.bone else Pal.ink, icon = if (rare) "star" else "chest")
                    }
                }
            }
        }
        item { SectionTitle("Modifier-Karten", icon = "clover") }
        items(Modifiers.all.filter { it.key != Modifiers.JOKER }, key = { "mod-" + it.key }) { m ->
            val open = game.player.level >= m.unlockLevel
            Row(
                Modifier.fillMaxWidth().stonePanel(if (open) Frames.stone else Frames.stoneDark).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PixelIcon(if (open) m.icon else "lock", 26.dp, alpha = if (open) 1f else 0.5f)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    T(m.name, Q.type.bodyBold, color = if (open) Pal.bone else Pal.boneFaint)
                    T(m.rule, Q.type.small, color = Pal.boneDim)
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    T("×" + String.format(Locale.GERMANY, "%.1f", m.factor), Q.type.number.copy(fontSize = Q.type.number.fontSize * 0.8f), color = if (open) Pal.gold0 else Pal.boneFaint)
                    if (!open) T("ab Stufe ${m.unlockLevel}", Q.type.label, color = Pal.boneFaint)
                }
            }
        }
        val hcLevel = Modifiers.of(Modifiers.HARDCORE)?.unlockLevel ?: 8
        if (game.player.level >= hcLevel && !game.hardcore) item {
            PixelButton("Hardcore-Woche starten", {
                ui.show(Overlay.Confirm("Hardcore-Woche?", "Bis Sonntag zählt alles doppelt. Dafür gibt es keine Schutzschilde und keine Mini-Versionen.", "Starten") {
                    act.startHardcore(); fx.level(); ui.toast("Hardcore-Woche läuft. Viel Glück.")
                })
            }, Modifier.fillMaxWidth(), kind = Btn.BLOOD, icon = "skull")
        }
    }
}

@Composable
private fun RewardEditor(initial: Reward, onSave: (Reward) -> Unit, onDelete: (() -> Unit)?, onCancel: () -> Unit) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var price by remember(initial.id) { mutableStateOf(initial.price) }
    var icon by remember(initial.id) { mutableStateOf(initial.icon) }
    ParchSection(if (initial.name.isEmpty()) "Neue Belohnung" else "Belohnung bearbeiten", icon = "chest") {
        PixelField(name, { name = it }, "z. B. Pizza bestellen", label = "Name")
        Stepper(price, { price = it }, 50..100_000, "Preis", { Fmt.n(it) }, step = 50)
        ChipRow(rewardIcons, { it == icon }, { "" }, { it }) { icon = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PixelButton("Speichern", { onSave(Reward(initial.id, name.trim(), price, icon)) }, Modifier.weight(1f), icon = "check")
            PixelButton("Abbrechen", onCancel, Modifier.weight(1f), kind = Btn.STONE)
        }
        if (onDelete != null) PixelButton("Löschen", onDelete, Modifier.fillMaxWidth(), kind = Btn.BLOOD, icon = "trash")
    }
}
