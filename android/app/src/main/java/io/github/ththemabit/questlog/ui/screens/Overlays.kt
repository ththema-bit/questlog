package io.github.ththemabit.questlog.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.ththemabit.questlog.core.LootKind
import io.github.ththemabit.questlog.core.Moment
import io.github.ththemabit.questlog.core.Rng
import io.github.ththemabit.questlog.core.StoryContext
import io.github.ththemabit.questlog.data.AppState
import io.github.ththemabit.questlog.ui.Actions
import io.github.ththemabit.questlog.ui.Fmt
import io.github.ththemabit.questlog.ui.LocalFx
import io.github.ththemabit.questlog.ui.Nav
import io.github.ththemabit.questlog.ui.Overlay
import io.github.ththemabit.questlog.ui.Route
import io.github.ththemabit.questlog.ui.UiController
import io.github.ththemabit.questlog.ui.components.CalendarGrid
import io.github.ththemabit.questlog.ui.pixel.Bars
import io.github.ththemabit.questlog.ui.pixel.Btn
import io.github.ththemabit.questlog.ui.pixel.Frames
import io.github.ththemabit.questlog.ui.pixel.LocalPx
import io.github.ththemabit.questlog.ui.pixel.PixelBar
import io.github.ththemabit.questlog.ui.pixel.PixelButton
import io.github.ththemabit.questlog.ui.pixel.PixelIcon
import io.github.ththemabit.questlog.ui.pixel.ScrollPanel
import io.github.ththemabit.questlog.ui.pixel.T
import io.github.ththemabit.questlog.ui.pixel.stonePanel
import io.github.ththemabit.questlog.ui.theme.Pal
import io.github.ththemabit.questlog.ui.theme.Q
import io.github.ththemabit.questlog.ui.veil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max

@Composable
fun OverlayHost(o: Overlay, state: AppState, act: Actions, ui: UiController, nav: Nav) {
    val none = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxSize().veil().clickable(none, indication = null) { if (o !is Overlay.Result && o !is Overlay.Shout) ui.dismiss() }
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        when (o) {
            is Overlay.Result -> ResultOverlay(o, state, act, ui, nav)
            is Overlay.Confirm -> ConfirmOverlay(o, ui)
            is Overlay.DatePick -> DatePickOverlay(o, ui)
            is Overlay.Shout -> ShoutOverlay(o, state, act, ui)
        }
    }
}

/** Beipackzettel (K01) mit Beute, Stufenaufstieg, Rekorden und Meilensteinen. */
@Composable
private fun ResultOverlay(o: Overlay.Result, state: AppState, act: Actions, ui: UiController, nav: Nav) {
    val r = state.game.results[o.eventId]
    val fx = LocalFx.current
    if (r == null) {
        PixelIcon("hourglass", 48.dp)
        return
    }
    val before = o.before
    val after = state.game
    val levelUp = after.player.level > before.player.level
    val beforeAch = before.achievements.associate { it.def.id to it.unlockedAt }
    val newAch = after.achievements.filter { it.unlockedAt != null && beforeAch[it.def.id] == null }
    val dayRecord = before.records.bestDay != null && (after.records.bestDay?.second ?: 0) > (before.records.bestDay?.second ?: 0) && after.records.bestDay?.first == after.today
    val weekRecord = before.records.bestWeek != null && (after.records.bestWeek?.second ?: 0) > (before.records.bestWeek?.second ?: 0) && after.records.bestWeek?.first == after.season.weekStart
    val chaos = state.save.settings.chaos
    val shout = (state.save.events.firstOrNull { it.id == r.eventId } as? io.github.ththemabit.questlog.core.Done)?.shout
    LaunchedEffect(o.eventId) {
        when {
            levelUp -> fx.level()
            r.loot != null -> fx.loot()
            dayRecord || weekRecord || newAch.isNotEmpty() -> fx.record()
        }
    }
    val headline = when {
        r.bossDefeated -> "Boss besiegt!"
        r.hit && r.finished -> "Alles erledigt!"
        r.hit -> "Treffer!"
        r.mini -> "Mini-Version"
        r.early -> "Zu früh, aber erledigt"
        else -> "Quest erledigt"
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ScrollPanel(seed = r.eventId.hashCode()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PixelIcon(if (r.bossDefeated) "skull" else if (r.mini) "feather" else if (r.hit) "sword" else "seal", 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            T(headline, Q.type.heading, color = Pal.ink)
                            T(r.questTitle, Q.type.small, color = Pal.inkSoft, maxLines = 2)
                        }
                        T("+${Fmt.n(r.points)}", Q.type.numberBig, color = Pal.gold3)
                    }
                    r.lines.forEach { l ->
                        Row {
                            T(l.label, Q.type.small, Modifier.weight(1f), color = Pal.inkSoft, maxLines = 1)
                            T(l.value, Q.type.small, color = Pal.ink)
                        }
                    }
                    Row(Modifier.padding(top = 4.dp)) {
                        T("Glut ${Fmt.n(r.glutBefore)} → ${Fmt.n(r.glutAfter)}", Q.type.bodyBold, Modifier.weight(1f), color = Pal.ember3)
                        T("${Fmt.n(after.player.xp)} XP", Q.type.bodyBold, color = Pal.inkSoft)
                    }
                    r.wagerWon?.let { T("Wette gewonnen: +${Fmt.n(it)} Glut zurück", Q.type.bodyBold, color = Pal.leaf2) }
                    if (r.newPb) T("Neue Bestzeit!", Q.type.bodyBold, color = Pal.mana2)
                    T(Story.line(persona(state.save.settings), Moment.DONE, StoryContext(), salt = Rng.hash(r.eventId)), Q.type.small, color = Pal.inkSoft)
                }
            }
            r.loot?.let { loot ->
                Row(Modifier.fillMaxWidth().stonePanel(if (loot.rare) Frames.violet else Frames.gold).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    PixelIcon(when (loot.kind) { LootKind.SHIELD -> "shield"; LootKind.COINS -> "coin"; LootKind.GLUT -> "flame"; LootKind.JOKER -> "clover"; LootKind.TROPHY -> "chest" }, 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        T(if (loot.rare) "SELTENE BEUTE" else "BEUTE", Q.type.label, color = if (loot.rare) Pal.bone else Pal.ink)
                        T(loot.name + if (loot.kind == LootKind.COINS || loot.kind == LootKind.GLUT) " +${loot.amount}" else "", Q.type.bodyBold, color = if (loot.rare) Pal.bone else Pal.ink)
                    }
                }
            }
            if (levelUp) Banner2("flame", "Stufe ${after.player.level}!", "Rang: ${after.player.rank.name}. " + Story.line(persona(state.save.settings), Moment.LEVEL_UP, StoryContext(level = after.player.level)), Frames.ember)
            if (dayRecord) Banner2("crown", "Tagesrekord", "${Fmt.n(after.records.bestDay?.second ?: 0)} Punkte heute.", Frames.gold)
            if (weekRecord) Banner2("crown", "Wochenrekord", "${Fmt.n(after.records.bestWeek?.second ?: 0)} Punkte diese Woche.", Frames.gold)
            newAch.forEach { a -> Banner2(a.def.icon, "Meilenstein: ${a.def.title}", a.def.text, Frames.wood) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PixelButton("Rückgängig", {
                    act.undo(r.eventId); fx.undo(); ui.dismiss(); ui.toast("Zurückgenommen.")
                }, Modifier.weight(1f), kind = Btn.STONE, icon = "back")
                PixelButton("Weiter", { ui.dismiss() }, Modifier.weight(1f), icon = "check")
            }
            if (r.bossDefeated && chaos.credits) PixelButton("Abspann ansehen", { ui.dismiss(); nav.go(Route.Credits(r.questId)) }, Modifier.fillMaxWidth(), kind = Btn.VIOLET, icon = "star")
        }
        Confetti(r.eventId, count = 40 + (if (r.bossDefeated) 60 else 0) + (if (levelUp) 40 else 0) + (shout ?: 0))
    }
}

@Composable
private fun Banner2(icon: String, title: String, text: String, frame: io.github.ththemabit.questlog.ui.pixel.Frame) {
    Row(Modifier.fillMaxWidth().stonePanel(frame).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        PixelIcon(icon, 32.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            T(title, Q.type.bodyBold, color = if (frame == Frames.gold || frame == Frames.ember) Pal.ink else Pal.bone)
            T(text, Q.type.small, color = if (frame == Frames.gold || frame == Frames.ember) Pal.wood0 else Pal.boneDim)
        }
    }
}

/** Pixel-Konfetti aus Glut und Gold. */
@Composable
fun Confetti(key: String, count: Int) {
    val u = LocalPx.current
    val t = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { t.animateTo(1f, tween(1800, easing = LinearEasing)) }
    val colors = listOf(Pal.gold0, Pal.gold1, Pal.ember1, Pal.ember2, Pal.leaf0, Pal.mana0, Pal.bone)
    Canvas(Modifier.fillMaxSize()) {
        val p = t.value
        if (p >= 1f) return@Canvas
        val rng = Rng(key)
        val cx = size.width / 2
        val cy = size.height * 0.35f
        repeat(count.coerceAtMost(220)) {
            val angle = rng.nextDouble() * Math.PI * 2
            val speed = (0.35 + rng.nextDouble() * 0.65) * size.width * 0.7
            val vx = Math.cos(angle) * speed
            val vy = Math.sin(angle) * speed - size.height * 0.35
            val x = cx + vx * p
            val y = cy + vy * p + 0.5 * size.height * 1.4 * p * p
            val c = colors[rng.nextInt(colors.size)]
            val s = if (rng.nextInt(3) == 0) 2 * u else u
            drawRect(c.copy(alpha = (1f - p).coerceIn(0f, 1f)), Offset(((x / u).toInt() * u).toFloat(), ((y / u).toInt() * u).toFloat()), Size(s, s))
        }
    }
}

@Composable
private fun ConfirmOverlay(o: Overlay.Confirm, ui: UiController) {
    val none = remember { MutableInteractionSource() }
    Column(Modifier.fillMaxWidth().padding(20.dp).clickable(none, indication = null) {}) {
        ScrollPanel(seed = o.title.hashCode()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                T(o.title, Q.type.heading, color = Pal.ink)
                T(o.text, Q.type.body, color = Pal.inkSoft)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PixelButton("Abbrechen", { ui.dismiss() }, Modifier.weight(1f), kind = Btn.STONE)
                    PixelButton(o.yes, { ui.dismiss(); o.onYes() }, Modifier.weight(1f), kind = Btn.GOLD)
                }
            }
        }
    }
}

@Composable
private fun DatePickOverlay(o: Overlay.DatePick, ui: UiController) {
    val none = remember { MutableInteractionSource() }
    Column(Modifier.fillMaxWidth().padding(16.dp).clickable(none, indication = null) {}) {
        ScrollPanel(seed = 31) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                T(o.title, Q.type.heading, color = Pal.ink)
                CalendarGrid(o.initial, null, o.min, o.max) { d -> ui.dismiss(); o.onPick(d) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PixelButton("Abbrechen", { ui.dismiss() }, Modifier.weight(1f), kind = Btn.STONE)
                    if (o.allowNone) PixelButton("Ohne Datum", { ui.dismiss(); o.onPick(null) }, Modifier.weight(1f), kind = Btn.STONE)
                }
            }
        }
    }
}

/** C10: Kampfschrei. Misst 2,5 Sekunden die Lautstärke. */
@SuppressLint("MissingPermission")
@Composable
private fun ShoutOverlay(o: Overlay.Shout, state: AppState, act: Actions, ui: UiController) {
    val context = LocalContext.current
    val fx = LocalFx.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    var asked by remember { mutableStateOf(false) }
    var level by remember { mutableFloatStateOf(0f) }
    var peak by remember { mutableFloatStateOf(0f) }
    var done by remember { mutableStateOf(false) }
    val quiet = LocalTime.now().hour.let { it >= state.save.settings.quietFrom || it < state.save.settings.quietTo }
    val launcher = rememberLauncherOrNull(ActivityResultContracts.RequestPermission()) { ok -> granted = ok; asked = true }
    val title = state.game.quest(o.questId)?.quest?.title ?: ""

    fun finish(shout: Int?) {
        ui.dismiss()
        completeWithResult(act, ui, fx, state.game, o.questId, shout = shout)
    }

    LaunchedEffect(granted) {
        if (!granted) return@LaunchedEffect
        val value = withContext(Dispatchers.IO) {
            val rate = 16000
            val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val rec = runCatching { AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(min, 4096)) }.getOrNull()
                ?: return@withContext 0f
            val buf = ShortArray(1024)
            var best = 0f
            runCatching {
                rec.startRecording()
                val end = System.currentTimeMillis() + 2500
                while (System.currentTimeMillis() < end) {
                    val n = rec.read(buf, 0, buf.size)
                    var m = 0
                    for (i in 0 until n) m = max(m, abs(buf[i].toInt()))
                    val db = if (m <= 0) -90.0 else 20 * log10(m / 32767.0)
                    val lv = ((db + 60) / 60 * 100).toFloat().coerceIn(0f, 100f)
                    level = lv
                    if (lv > best) best = lv
                    peak = best
                }
            }
            runCatching { rec.stop() }
            rec.release()
            best
        }
        done = true
        finish(value.toInt())
    }

    val none = remember { MutableInteractionSource() }
    Column(Modifier.fillMaxWidth().padding(20.dp).clickable(none, indication = null) {}) {
        ScrollPanel(seed = 77) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                PixelIcon("horn", 48.dp)
                T(if (quiet) "Flüstermodus" else "Kampfschrei!", Q.type.heading, color = Pal.ink)
                T(if (quiet) "Es ist Ruhezeit. Ein entschlossenes Flüstern für „$title“ genügt." else "Schrei für „$title“! Lauter heißt mehr Konfetti.", Q.type.body, color = Pal.inkSoft, align = TextAlign.Center)
                when {
                    granted -> {
                        PixelBar(level / 100f, Bars.ember, artHeight = 6, marker = peak / 100f)
                        T(if (done) "Spitze: ${peak.toInt()}" else "Höre zu …", Q.type.bodyBold, color = Pal.ink)
                    }
                    launcher != null && !asked -> PixelButton("Mikrofon erlauben", { launcher.launch(Manifest.permission.RECORD_AUDIO) }, Modifier.fillMaxWidth(), icon = "horn")
                    else -> {
                        T("Ohne Mikrofon geht es auch: einfach leise erledigen.", Q.type.small, color = Pal.inkSoft)
                        PixelButton("Leise erledigen", { finish(null) }, Modifier.fillMaxWidth(), icon = "seal")
                    }
                }
                PixelButton("Abbrechen", { ui.dismiss() }, Modifier.fillMaxWidth(), kind = Btn.STONE)
            }
        }
    }
}
