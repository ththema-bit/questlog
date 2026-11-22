package io.github.ththemabit.questlog.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.ththemabit.questlog.BuildConfig
import io.github.ththemabit.questlog.core.Chaos
import io.github.ththemabit.questlog.core.Persona
import io.github.ththemabit.questlog.core.Templates
import io.github.ththemabit.questlog.data.AppState
import io.github.ththemabit.questlog.ui.Actions
import io.github.ththemabit.questlog.ui.LocalFx
import io.github.ththemabit.questlog.ui.Nav
import io.github.ththemabit.questlog.ui.Overlay
import io.github.ththemabit.questlog.ui.Route
import io.github.ththemabit.questlog.ui.UiController
import io.github.ththemabit.questlog.ui.components.ChipRow
import io.github.ththemabit.questlog.ui.components.ParchSection
import io.github.ththemabit.questlog.ui.components.ScreenTitle
import io.github.ththemabit.questlog.ui.components.Stepper
import io.github.ththemabit.questlog.ui.components.Toggle
import io.github.ththemabit.questlog.ui.pixel.Btn
import io.github.ththemabit.questlog.ui.pixel.PixelButton
import io.github.ththemabit.questlog.ui.pixel.PixelIcon
import io.github.ththemabit.questlog.ui.pixel.ScrollPanel
import io.github.ththemabit.questlog.ui.pixel.H
import io.github.ththemabit.questlog.ui.pixel.T
import io.github.ththemabit.questlog.ui.pixel.parchmentCard
import io.github.ththemabit.questlog.ui.theme.Pal
import io.github.ththemabit.questlog.ui.theme.Q
import java.time.LocalDate

/** Position des KI-Abschnitts in der Liste (Titel, Erinnerungen, Pause, KI) */
private const val AI_ITEM = 3

/** Wie rememberLauncherForActivityResult, aber ohne Absturz, wo es keine Activity gibt (Vorschau, Tests). */
@Composable
fun <I, O> rememberLauncherOrNull(contract: ActivityResultContract<I, O>, onResult: (O) -> Unit): ManagedActivityResultLauncher<I, O>? =
    if (LocalActivityResultRegistryOwner.current == null) null else rememberLauncherForActivityResult(contract, onResult)

private data class ChaosItem(val title: String, val text: String, val icon: String, val get: (Chaos) -> Boolean, val set: (Chaos, Boolean) -> Chaos)

private val chaosItems = listOf(
    ChaosItem("Comic-Sans-Fluch", "Liegt eine Quest viel zu lange, läuft die ganze App in Comic Sans, bis sie erledigt ist.", "skull", { it.comicCurse }, { c, v -> c.copy(comicCurse = v) }),
    ChaosItem("Erzähler-Stimmen", "Oma, Drill-Sergeant, Butler oder Tierfilmer sprechen in Begrüßung und Erinnerungen.", "horn", { it.narrators }, { c, v -> c.copy(narrators = v) }),
    ChaosItem("Nemesis Gerald", "Ein erfundener Rivale, der angeblich auch Haushalt macht und immer knapp vor dir liegt.", "crown", { it.gerald }, { c, v -> c.copy(gerald = v) }),
    ChaosItem("Faulheitssteuer", "Der Glut-Verfall kommt wöchentlich als Bescheid vom Finanzamt für Ordnung.", "letter", { it.lazyTax }, { c, v -> c.copy(lazyTax = v) }),
    ChaosItem("Wollmaus-Plage", "Überfällige Putz-Quests locken Staubwesen auf dein Board.", "bunny1", { it.dustBunnies }, { c, v -> c.copy(dustBunnies = v) }),
    ChaosItem("Quest-Hochzeiten", "Quests, die du oft direkt nacheinander machst, heiraten und bekommen einen Ehebonus.", "ring", { it.weddings }, { c, v -> c.copy(weddings = v) }),
    ChaosItem("Heldensaga", "Der Wochenrückblick wird als epische Sage erzählt.", "book", { it.saga }, { c, v -> c.copy(saga = v) }),
    ChaosItem("Abspann", "Nach jedem besiegten Boss laufen Kino-Credits.", "star", { it.credits }, { c, v -> c.copy(credits = v) }),
    ChaosItem("Absurde Feiertage", "Erfundene Feiertage mit Bonus, etwa der Tag der verlorenen Socke.", "calendar", { it.holidays }, { c, v -> c.copy(holidays = v) }),
    ChaosItem("Kampfschrei-Abhaken", "Siegel lange drücken und schreien. Lauter heißt mehr Konfetti. Braucht das Mikrofon.", "horn", { it.battleCry }, { c, v -> c.copy(battleCry = v) }),
)

@Composable
fun OptionsScreen(state: AppState, act: Actions, nav: Nav, ui: UiController) {
    val s = state.save.settings
    val context = LocalContext.current
    val fx = LocalFx.current
    var granted by remember {
        mutableStateOf(Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherOrNull(ActivityResultContracts.RequestPermission()) { granted = it }
    val export = rememberLauncherOrNull(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(act.exportJson().toByteArray()) }
            ui.toast("Sicherung gespeichert.")
        }.onFailure { ui.toast("Speichern hat nicht geklappt.") }
    }
    val import = rememberLauncherOrNull(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
            if (text == null) ui.toast("Datei ließ sich nicht lesen.") else ui.show(Overlay.Confirm(
                "Sicherung einspielen?", "Der aktuelle Spielstand wird durch die Sicherung ersetzt.", "Einspielen",
            ) { ui.toast(if (act.importJson(text)) "Sicherung eingespielt." else "Das ist keine gültige Questlog-Sicherung.") })
        }
    }
    val list = rememberLazyListState()
    LaunchedEffect(nav.focus) {
        if (nav.focus == "ai") {
            list.scrollToItem(AI_ITEM)
            nav.focus = null
        }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        state = list,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenTitle("Optionen", onBack = null) }
        item {
            ParchSection("Erinnerungen", icon = "bell") {
                Toggle(s.remindersOn, { v -> act.settings { it.copy(remindersOn = v) } }, "Erinnerungen", "Wenn ein Fenster aufgeht und kurz bevor es zugeht.")
                if (!granted && permission != null) PixelButton("Benachrichtigungen erlauben", { permission.launch(Manifest.permission.POST_NOTIFICATIONS) }, Modifier.fillMaxWidth(), kind = Btn.STONE, icon = "bell")
                Stepper(s.digestHour, { v -> act.settings { it.copy(digestHour = v) } }, -1..12, "Morgen-Übersicht", { if (it < 0) "aus" else "$it Uhr" })
                Stepper(s.quietFrom, { v -> act.settings { it.copy(quietFrom = v) } }, 18..23, "Ruhe ab", { "$it Uhr" })
                Stepper(s.quietTo, { v -> act.settings { it.copy(quietTo = v) } }, 5..11, "Ruhe bis", { "$it Uhr" })
                Stepper(s.maxPerDay, { v -> act.settings { it.copy(maxPerDay = v) } }, 1..8, "Höchstens pro Tag", { it.toString() })
                Toggle(s.notifyOpen, { v -> act.settings { it.copy(notifyOpen = v) } }, "Fenster geht auf")
                Toggle(s.notifyClosing, { v -> act.settings { it.copy(notifyClosing = v) } }, "Fenster schließt morgen")
            }
        }
        item {
            ParchSection("Pause", icon = "pause") {
                if (state.game.paused) {
                    T("Pause läuft. Verfall, Serien und Fenster sind eingefroren.", Q.type.small, color = Pal.inkSoft)
                    PixelButton("Pause beenden", { act.endPause(); fx.tap() }, Modifier.fillMaxWidth(), kind = Btn.MANA, icon = "play")
                } else {
                    T("Urlaub, krank, Prüfungsphase: Während einer Pause kostet Nichtstun nichts.", Q.type.small, color = Pal.inkSoft)
                    PixelButton("Pause starten", {
                        ui.show(Overlay.DatePick("Pause bis wann?", state.game.today.plusDays(7), min = state.game.today, max = state.game.today.plusYears(1), allowNone = true, onPick = { d: LocalDate? ->
                            act.startPause(d); ui.toast(if (d == null) "Pause läuft, bis du sie beendest." else "Pause bis ${d.format(io.github.ththemabit.questlog.ui.Fmt.dayMonth)}.")
                        }))
                    }, Modifier.fillMaxWidth(), kind = Btn.MANA, icon = "pause")
                }
            }
        }
        item(key = "ai") { AiSection(state, act, nav, ui) }
        item {
            ParchSection("Chaos-Kammer", icon = "skull") {
                T("Alles hier ist Spaß obendrauf und einzeln schaltbar.", Q.type.small, color = Pal.inkSoft)
                chaosItems.forEach { c ->
                    Toggle(c.get(s.chaos), { v -> act.settings { st -> st.copy(chaos = c.set(st.chaos, v)) } }, c.title, c.text, icon = c.icon)
                }
                if (s.chaos.narrators) {
                    T("ERZÄHLER", Q.type.label, color = Pal.inkSoft)
                    ChipRow(Persona.entries.toList(), { it.key == s.narrator }, { it.title }) { p -> act.settings { it.copy(narrator = p.key) } }
                }
            }
        }
        item {
            ParchSection("Klang & Haptik", icon = "note") {
                Toggle(s.sound, { v -> act.settings { it.copy(sound = v) } }, "8-Bit-Klänge")
                Toggle(s.haptics, { v -> act.settings { it.copy(haptics = v) } }, "Vibration")
            }
        }
        item {
            ParchSection("Quests", icon = "scroll") {
                PixelButton("Vorlagen-Pakete", { nav.go(Route.Templates) }, Modifier.fillMaxWidth(), kind = Btn.STONE, icon = "scroll")
                PixelButton("Schnelleingabe", { nav.go(Route.QuickAdd) }, Modifier.fillMaxWidth(), kind = Btn.STONE, icon = "quill")
            }
        }
        item {
            ParchSection("Sicherung", icon = "chest") {
                T("Alles liegt nur auf diesem Handy. Eine Sicherung ist eine Datei, die du aufs nächste Handy mitnehmen kannst.", Q.type.small, color = Pal.inkSoft)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PixelButton("Exportieren", { export?.launch("questlog-${LocalDate.now()}.json") }, Modifier.weight(1f), kind = Btn.STONE, icon = "letter", enabled = export != null)
                    PixelButton("Einspielen", { import?.launch(arrayOf("application/json", "text/plain", "*/*")) }, Modifier.weight(1f), kind = Btn.STONE, icon = "chest", enabled = import != null)
                }
            }
        }
        item {
            ParchSection("Über Questlog", icon = "quill") {
                T("Version ${BuildConfig.VERSION_NAME}", Q.type.bodyBold, color = Pal.ink)
                T("Offline, ohne Konto, ohne Werbung, ohne Tracking. Der KI-Spielleiter ist optional und spricht nur mit dem Server, den du einträgst.", Q.type.small, color = Pal.inkSoft)
                T("Schriften: Cinzel Decorative, Jersey, Pixelify Sans, Silkscreen, Comic Neue (SIL Open Font License). Pixelgrafiken und Klänge: eigene.", Q.type.small, color = Pal.inkSoft)
            }
        }
        item {
            PixelButton("Alles zurücksetzen", {
                ui.show(Overlay.Confirm("Wirklich alles löschen?", "Alle Quests, Punkte, Rekorde und Einstellungen sind danach weg. Mach vorher eine Sicherung.", "Alles löschen") {
                    act.resetAll()
                    nav.go(Route.Board)
                })
            }, Modifier.fillMaxWidth(), kind = Btn.BLOOD, icon = "trash")
        }
    }
}

/** Erster Start: Begrüßung, Vorlagen, Erlaubnis für Erinnerungen. */
@Composable
fun Onboarding(state: AppState, act: Actions) {
    var step by remember { mutableIntStateOf(0) }
    val selected = remember { mutableStateListOf("haushalt") }
    val fx = LocalFx.current
    val permission = rememberLauncherOrNull(ActivityResultContracts.RequestPermission()) { finish(act, selected) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        PixelIcon("flame", 64.dp)
        H("Questlog", Q.type.logo, color = Pal.gold0)
        when (step) {
            0 -> {
                ScrollPanel(seed = 5) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        H("Willkommen in der Chronik", Q.type.heading, color = Pal.ink)
                        Bullet("hourglass", "Jede Quest hat ein Zeitfenster: zu früh, im Fenster, überfällig.")
                        Bullet("coin", "Punkte nach Aufwand, Wichtigkeit und Überwindung. Das Gehasste zählt mehr.")
                        Bullet("flame", "Die Glut zeigt deine Form. Sie kühlt ab, wenn du nichts tust, erlischt aber nie.")
                        Bullet("shield", "Ein freier Tag bricht keine Serie. Erst der zweite.")
                        Bullet("chest", "Alles bleibt auf deinem Handy. Kein Konto, keine Werbung.")
                    }
                }
                PixelButton("Weiter", { step = 1; fx.tap() }, Modifier.fillMaxWidth(), icon = "play")
            }
            1 -> {
                H("Womit willst du anfangen?", Q.type.heading, color = Pal.gold0, align = TextAlign.Center)
                Templates.packs.forEach { p ->
                    Column(Modifier.fillMaxWidth().parchmentCard(p.key.hashCode()).padding(horizontal = 14.dp, vertical = 6.dp)) {
                        Toggle(p.key in selected, { if (it) selected += p.key else selected -= p.key }, p.name, "${p.quests.size} Quests · ${p.text}", icon = p.icon)
                    }
                }
                T("Alles lässt sich später anpassen, löschen oder ergänzen.", Q.type.small, color = Pal.boneDim, align = TextAlign.Center)
                PixelButton("Weiter", { step = 2; fx.tap() }, Modifier.fillMaxWidth(), icon = "play")
            }
            else -> {
                ScrollPanel(seed = 8) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        H("Erinnerungen", Q.type.heading, color = Pal.ink)
                        T("Questlog meldet sich, wenn ein Fenster aufgeht und kurz bevor es zugeht. Höchstens dreimal am Tag, nie zwischen 22 und 7 Uhr. Das lässt sich in den Optionen ändern.", Q.type.body, color = Pal.inkSoft)
                    }
                }
                val needs = Build.VERSION.SDK_INT >= 33 && permission != null
                PixelButton(if (needs) "Erlauben und loslegen" else "Loslegen", {
                    fx.done()
                    if (needs) permission!!.launch(Manifest.permission.POST_NOTIFICATIONS) else finish(act, selected)
                }, Modifier.fillMaxWidth(), icon = "flame")
                if (needs) PixelButton("Ohne Erinnerungen", { finish(act, selected) }, Modifier.fillMaxWidth(), kind = Btn.STONE)
            }
        }
    }
}

private fun finish(act: Actions, selected: List<String>) {
    if (selected.isNotEmpty()) act.addPacks(selected.toSet())
    act.meta { it.copy(onboarded = true) }
}

@Composable
private fun Bullet(icon: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        PixelIcon(icon, 22.dp)
        Spacer(Modifier.width(10.dp))
        T(text, Q.type.body, color = Pal.ink)
    }
}
