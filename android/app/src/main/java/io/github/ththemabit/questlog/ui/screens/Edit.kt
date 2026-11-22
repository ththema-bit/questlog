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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.ththemabit.questlog.core.Categories
import io.github.ththemabit.questlog.core.HeuristicQuestSmith
import io.github.ththemabit.questlog.core.Quest
import io.github.ththemabit.questlog.core.Subtask
import io.github.ththemabit.questlog.core.Templates
import io.github.ththemabit.questlog.data.AppState
import io.github.ththemabit.questlog.ui.Actions
import io.github.ththemabit.questlog.ui.Fmt
import io.github.ththemabit.questlog.ui.LocalFx
import io.github.ththemabit.questlog.ui.Nav
import io.github.ththemabit.questlog.ui.Overlay
import io.github.ththemabit.questlog.ui.Route
import io.github.ththemabit.questlog.ui.UiController
import io.github.ththemabit.questlog.ui.components.ChipRow
import io.github.ththemabit.questlog.ui.components.ParchSection
import io.github.ththemabit.questlog.ui.components.PixelField
import io.github.ththemabit.questlog.ui.components.RatingRow
import io.github.ththemabit.questlog.ui.components.ScreenTitle
import io.github.ththemabit.questlog.ui.components.Segmented
import io.github.ththemabit.questlog.ui.components.Stepper
import io.github.ththemabit.questlog.ui.components.Toggle
import io.github.ththemabit.questlog.ui.pixel.Btn
import io.github.ththemabit.questlog.ui.pixel.Frames
import io.github.ththemabit.questlog.ui.pixel.IconTap
import io.github.ththemabit.questlog.ui.pixel.PixelButton
import io.github.ththemabit.questlog.ui.pixel.PixelIcon
import io.github.ththemabit.questlog.ui.pixel.T
import io.github.ththemabit.questlog.ui.pixel.Tag
import io.github.ththemabit.questlog.ui.pixel.parchmentCard
import io.github.ththemabit.questlog.ui.theme.Pal
import io.github.ththemabit.questlog.ui.theme.Q
import java.time.LocalDate

private val importanceCaptions = listOf("egal", "eher unwichtig", "normal", "wichtig", "sehr wichtig")
private val dreadCaptions = listOf("macht Spaß", "geht so", "neutral", "lästig", "Horror")
private val effortPresets = listOf(2, 5, 10, 15, 20, 30, 45, 60, 90, 120)
private val rhythmPresets = listOf("täglich" to (1 to 1), "alle 2–3 Tage" to (2 to 3), "wöchentlich" to (5 to 7), "alle 2 Wochen" to (10 to 14), "monatlich" to (25 to 31))

@Composable
fun EditScreen(questId: String?, state: AppState, act: Actions, nav: Nav, ui: UiController) {
    val existing = questId?.let { id -> state.save.quests.firstOrNull { it.id == id } }
    val fx = LocalFx.current
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: "haushalt") }
    var importance by remember { mutableIntStateOf(existing?.importance ?: 3) }
    var dread by remember { mutableIntStateOf(existing?.dread ?: 3) }
    var effort by remember { mutableIntStateOf(existing?.effortMin ?: 15) }
    var recurring by remember { mutableStateOf(existing?.recurring ?: true) }
    var minDays by remember { mutableIntStateOf(existing?.minDays ?: 3) }
    var hasMax by remember { mutableStateOf(existing?.maxDays != null || existing == null) }
    var maxDays by remember { mutableIntStateOf(existing?.maxDays ?: 7) }
    var due by remember { mutableStateOf(existing?.dueDate) }
    var mini by remember { mutableStateOf(existing?.mini ?: "") }
    var why by remember { mutableStateOf(existing?.why ?: "") }
    var ifThen by remember { mutableStateOf(existing?.ifThen ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    val subtasks = remember { mutableStateListOf<Subtask>().apply { addAll(existing?.subtasks ?: emptyList()) } }
    var boss by remember { mutableStateOf(existing?.boss ?: false) }
    var newSub by remember { mutableStateOf("") }

    fun save() {
        if (title.isBlank()) {
            fx.error(); ui.toast("Gib der Quest einen Namen."); return
        }
        if (recurring && minDays == 0 && !hasMax) {
            fx.error(); ui.toast("Wiederkehrend braucht ein Frühestens oder ein Spätestens."); return
        }
        if (boss && subtasks.size < 2) {
            fx.error(); ui.toast("Ein Boss braucht mindestens zwei Teilaufgaben."); return
        }
        val q = Quest(
            id = existing?.id ?: act.newId(),
            title = title.trim(),
            category = category,
            importance = importance,
            effortMin = effort,
            dread = dread,
            minDays = if (recurring && minDays > 0) minDays else if (recurring && hasMax) null else null,
            maxDays = if (recurring && hasMax) maxOf(maxDays, minDays) else null,
            dueDate = if (!recurring) due else null,
            mini = mini.trim().ifEmpty { null },
            why = why.trim().ifEmpty { null },
            ifThen = ifThen.trim().ifEmpty { null },
            note = note.trim().ifEmpty { null },
            subtasks = subtasks.toList(),
            boss = boss && subtasks.size >= 2,
            createdAt = existing?.createdAt ?: act.now(),
        )
        act.saveQuest(q)
        fx.tap()
        ui.toast(if (existing == null) "Quest angelegt." else "Gespeichert.")
        nav.back()
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenTitle(if (existing == null) "Neue Quest" else "Quest bearbeiten", onBack = { nav.back() }) }
        item {
            ParchSection("Was ist zu tun?", icon = "quill") {
                PixelField(title, { title = it }, "z. B. Bad putzen", label = "Titel")
                T("KATEGORIE", Q.type.label, color = Pal.inkSoft)
                ChipRow(Categories.all, { it.key == category }, { it.name }, { it.icon }) { category = it.key }
            }
        }
        item {
            ParchSection("Rhythmus", icon = "hourglass") {
                Segmented(listOf("Wiederkehrend", "Einmalig"), if (recurring) 0 else 1, { recurring = it == 0 })
                if (recurring) {
                    ChipRow(rhythmPresets, { (_, mm) -> minDays == mm.first && hasMax && maxDays == mm.second }, { it.first }) { (_, mm) ->
                        minDays = mm.first; maxDays = mm.second; hasMax = true
                    }
                    Stepper(minDays, { minDays = it; if (maxDays < it) maxDays = it }, 0..365, "Frühestens nach", { if (it == 0) "sofort" else Fmt.tage(it) })
                    Toggle(hasMax, { hasMax = it }, "Spätestens-Grenze", "Danach ist die Quest überfällig und die Glut leidet.")
                    if (hasMax) Stepper(maxDays, { maxDays = it }, maxOf(1, minDays)..730, "Spätestens nach", { Fmt.tage(it) })
                    T("Fenster: ${Fmt.rhythm(minDays.takeIf { it > 0 }, if (hasMax) maxDays else null)}", Q.type.small, color = Pal.inkSoft)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T(due?.let { "Termin: " + LocalDate.parse(it).format(Fmt.dayMonthYear) } ?: "Kein Termin", Q.type.bodyBold, Modifier.weight(1f), color = Pal.ink)
                        PixelButton(if (due == null) "Termin" else "Ändern", {
                            ui.show(Overlay.DatePick("Bis wann?", state.game.today, min = state.game.today, max = null, allowNone = true, onPick = { d -> due = d?.toString() }))
                        }, kind = Btn.STONE, small = true, icon = "calendar")
                    }
                }
            }
        }
        item {
            ParchSection("Gewicht", icon = "star") {
                RatingRow(importance, { importance = it }, "star", "Wichtigkeit", importanceCaptions)
                RatingRow(dread, { dread = it }, "skull", "Überwindung", dreadCaptions)
                T("AUFWAND", Q.type.label, color = Pal.inkSoft)
                ChipRow(effortPresets, { it == effort }, { Fmt.effort(it) }) { effort = it }
                Stepper(effort, { effort = it }, 1..480, "Genau", { Fmt.effort(it) }, step = 5)
            }
        }
        item {
            ParchSection("Kleine Helfer", icon = "feather") {
                PixelField(mini, { mini = it }, "z. B. ein Teller statt der ganzen Spüle", label = "Mini-Version (2 Minuten)")
                PixelField(why, { why = it }, "Damit …", label = "Warum ist das wichtig?", singleLine = false, minLines = 2)
                PixelField(ifThen, { ifThen = it }, "Wenn ich vom Sport komme, dann …", label = "Wenn-dann-Plan")
                PixelField(note, { note = it }, "Notiz", label = "Notiz", singleLine = false, minLines = 2)
            }
        }
        item {
            ParchSection("Teilaufgaben", icon = "sword") {
                if (subtasks.isEmpty()) T("Große Brocken zerlegen: Jede Teilaufgabe bringt Teilpunkte.", Q.type.small, color = Pal.inkSoft)
                subtasks.forEachIndexed { i, st ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T("${i + 1}. ${st.title}", Q.type.body, Modifier.weight(1f), color = Pal.ink)
                        IconTap("cross", "Teilaufgabe entfernen", { subtasks.removeAt(i) }, size = 20.dp)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PixelField(newSub, { newSub = it }, "Neue Teilaufgabe", Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    PixelButton("", {
                        if (newSub.isNotBlank()) {
                            subtasks += Subtask(act.newId(), newSub.trim()); newSub = ""
                        }
                    }, kind = Btn.STONE, icon = "plus", small = true)
                }
                if (subtasks.size >= 2) Toggle(boss, { boss = it }, "Als Boss", "Mit Lebensbalken, Zorn und garantierter Beute.", icon = "skull")
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PixelButton("Speichern", { save() }, Modifier.fillMaxWidth(), icon = "check")
                if (existing != null) PixelButton("Archivieren", {
                    ui.show(Overlay.Confirm("Quest archivieren?", "„${existing.title}“ verschwindet vom Board. Punkte und Chronik bleiben erhalten.", "Archivieren") {
                        act.archiveQuest(existing.id)
                        nav.go(Route.Board)
                        ui.toast("Archiviert.")
                    })
                }, Modifier.fillMaxWidth(), kind = Btn.BLOOD, icon = "trash")
            }
        }
    }
}

/** Schnelleingabe: Freitext wird per Faustregeln zu Quests (KI-Version später). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickAddScreen(state: AppState, act: Actions, nav: Nav, ui: UiController) {
    var text by remember { mutableStateOf("") }
    val smith = remember { HeuristicQuestSmith() }
    val drafts = remember(text) { smith.draft(text, state.game.today) }
    val fx = LocalFx.current
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenTitle("Schnelleingabe", onBack = { nav.back() }) }
        item {
            ParchSection("Eine Zeile pro Quest", icon = "quill") {
                T("Erkannt werden Rhythmus („alle 3 Tage“, „wöchentlich“), Dauer („30 min“), Wichtigkeit („!“, „wichtig“), Unlust („nervig“), Termine („bis Freitag“) und Teilaufgaben nach einem Doppelpunkt.", Q.type.small, color = Pal.inkSoft)
                PixelField(text, { text = it }, "Bad putzen alle 7 Tage 20 min nervig\nSteuer bis 30.11. !!\nKeller: Regal, Kisten, Fahrrad", singleLine = false, minLines = 5)
            }
        }
        items(drafts) { d ->
            Column(Modifier.fillMaxWidth().parchmentCard(d.title.hashCode()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PixelIcon(Categories.of(d.category).icon, 22.dp)
                    Spacer(Modifier.width(8.dp))
                    T(d.title, Q.type.quest, Modifier.weight(1f), color = Pal.ink)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Tag(Categories.of(d.category).name, Frames.stoneFlat, Pal.bone)
                    Tag(if (d.dueDate != null) "bis " + LocalDate.parse(d.dueDate).format(Fmt.dayMonth) else Fmt.rhythm(d.minDays, d.maxDays), Frames.mana, Pal.ink)
                    Tag(Fmt.effort(d.effortMin), Frames.stoneFlat, Pal.bone)
                    if (d.importance != 3) Tag("Wichtigkeit ${d.importance}", Frames.gold, Pal.ink)
                    if (d.dread != 3) Tag("Überwindung ${d.dread}", Frames.blood, Pal.bone)
                    if (d.boss) Tag("Boss · ${d.subtasks.size} Treffer", Frames.violet, Pal.bone, icon = "skull")
                    else if (d.subtasks.isNotEmpty()) Tag("${d.subtasks.size} Teilaufgaben", Frames.leaf, Pal.ink)
                }
            }
        }
        item {
            PixelButton(
                if (drafts.isEmpty()) "Nichts zu übernehmen" else "${drafts.size} ${if (drafts.size == 1) "Quest" else "Quests"} anlegen",
                {
                    act.addDrafts(drafts)
                    fx.done()
                    ui.toast("${drafts.size} ${if (drafts.size == 1) "Quest" else "Quests"} angelegt.")
                    nav.back()
                },
                Modifier.fillMaxWidth(), enabled = drafts.isNotEmpty(), icon = "plus",
            )
        }
    }
}

@Composable
fun TemplatesScreen(state: AppState, act: Actions, nav: Nav, ui: UiController) {
    val selected = remember { mutableStateListOf<String>() }
    val fx = LocalFx.current
    val existing = state.save.quests.filter { !it.archived }.map { it.title.lowercase() }.toSet()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenTitle("Vorlagen", onBack = { nav.back() }) }
        items(Templates.packs, key = { it.key }) { p ->
            val on = p.key in selected
            val fresh = p.quests.count { it.title.lowercase() !in existing }
            Column(Modifier.fillMaxWidth().parchmentCard(p.key.hashCode()).padding(14.dp)) {
                Toggle(on, { if (it) selected += p.key else selected -= p.key }, p.name, p.text, icon = p.icon)
                T(p.quests.joinToString(" · ") { it.title }, Q.type.small, color = Pal.inkSoft)
                if (fresh < p.quests.size) T("${p.quests.size - fresh} davon hast du schon.", Q.type.label, color = Pal.inkFaint)
            }
        }
        item {
            PixelButton("Hinzufügen", {
                act.addPacks(selected.toSet())
                fx.done()
                ui.toast("Vorlagen hinzugefügt.")
                nav.back()
            }, Modifier.fillMaxWidth(), enabled = selected.isNotEmpty(), icon = "plus")
        }
    }
}
