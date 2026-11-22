package io.github.ththemabit.questlog.ui.screens

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import android.os.SystemClock
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.ththemabit.questlog.core.Categories
import io.github.ththemabit.questlog.core.Modifiers
import io.github.ththemabit.questlog.data.AppState
import io.github.ththemabit.questlog.ui.Actions
import io.github.ththemabit.questlog.ui.Fmt
import io.github.ththemabit.questlog.ui.LocalFx
import io.github.ththemabit.questlog.ui.Nav
import io.github.ththemabit.questlog.ui.Route
import io.github.ththemabit.questlog.ui.SessionMode
import io.github.ththemabit.questlog.ui.UiController
import io.github.ththemabit.questlog.ui.components.ScreenTitle
import io.github.ththemabit.questlog.ui.components.Toggle
import io.github.ththemabit.questlog.ui.pixel.Bars
import io.github.ththemabit.questlog.ui.pixel.Btn
import io.github.ththemabit.questlog.ui.pixel.PixelBar
import io.github.ththemabit.questlog.ui.pixel.PixelButton
import io.github.ththemabit.questlog.ui.pixel.PixelIcon
import io.github.ththemabit.questlog.ui.pixel.ScrollPanel
import io.github.ththemabit.questlog.ui.pixel.H
import io.github.ththemabit.questlog.ui.pixel.T
import io.github.ththemabit.questlog.ui.pixel.parchmentCard
import io.github.ththemabit.questlog.ui.pixel.stonePanel
import io.github.ththemabit.questlog.ui.theme.Pal
import io.github.ththemabit.questlog.ui.theme.Q
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/** Speedrun (W02), Drei-Song-Challenge (M04), Handy weg (M03) und Tanzpause (M10). */
@Composable
fun SessionScreen(route: Route.Session, state: AppState, act: Actions, nav: Nav, ui: UiController) {
    val game = state.game
    val qs = game.quest(route.questId)
    val fx = LocalFx.current
    val context = LocalContext.current
    if (qs == null) {
        LaunchedEffect(Unit) { nav.back() }
        return
    }
    var running by remember { mutableStateOf(false) }
    var startAt by remember { mutableLongStateOf(0L) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var broken by remember { mutableStateOf(false) }
    var dance by remember { mutableIntStateOf(0) } // 0 = noch nicht, 1 = läuft, 2 = bestanden, 3 = nicht bestanden
    var danceProgress by remember { mutableFloatStateOf(0f) }
    val limit = Modifiers.DREISONG_LIMIT_SEC * 1000L

    LaunchedEffect(running) {
        while (running) {
            elapsed = SystemClock.elapsedRealtime() - startAt
            delay(200)
        }
    }
    if (route.mode == SessionMode.HANDYWEG) {
        val owner = LocalLifecycleOwner.current
        DisposableEffect(owner, running) {
            val obs = LifecycleEventObserver { _, e ->
                if (e == Lifecycle.Event.ON_STOP && running) {
                    val pm = context.getSystemService(PowerManager::class.java)
                    if (pm?.isInteractive == true) broken = true
                }
            }
            owner.lifecycle.addObserver(obs)
            onDispose { owner.lifecycle.removeObserver(obs) }
        }
    }

    fun finish(passedDance: Boolean) {
        val sec = (elapsed / 1000).toInt().coerceAtLeast(1)
        val mods = buildList {
            addAll(route.mods)
            when (route.mode) {
                SessionMode.DREISONG -> add(Modifiers.DREISONG)
                SessionMode.HANDYWEG -> if (!broken) add(Modifiers.HANDYWEG)
                SessionMode.TANZ -> if (passedDance) add(Modifiers.TANZ)
                SessionMode.SPEEDRUN -> {}
            }
        }
        completeWithResult(act, ui, fx, game, qs.quest.id, durationSec = sec, mods = mods)
        nav.back()
    }

    if (dance == 1) DanceMeter(context, onProgress = { danceProgress = it }) { ok ->
        dance = if (ok) 2 else 3
    }
    LaunchedEffect(dance) {
        if (dance == 2 || dance == 3) {
            delay(900)
            finish(dance == 2)
        }
    }

    val title = when (route.mode) {
        SessionMode.SPEEDRUN -> "Speedrun"
        SessionMode.DREISONG -> "Drei Songs"
        SessionMode.HANDYWEG -> "Handy weg"
        SessionMode.TANZ -> "Tanzpause"
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenTitle(title, onBack = { nav.back() })
        Row(Modifier.fillMaxWidth().parchmentCard(qs.quest.id.hashCode()).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            PixelIcon(Categories.of(qs.quest.category).icon, 28.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                T(qs.quest.title, Q.type.quest, color = Pal.ink)
                T(qs.bestSec?.let { "Bestzeit ${Fmt.duration(it)}" + (qs.avgSec?.let { a -> " · Ø ${Fmt.duration(a)}" } ?: "") } ?: "Noch keine Bestzeit", Q.type.small, color = Pal.inkSoft)
            }
        }
        val shownMs = if (route.mode == SessionMode.DREISONG) (limit - elapsed).coerceAtLeast(0) else elapsed
        Column(
            Modifier.fillMaxWidth().stonePanel().padding(vertical = 24.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            T(Fmt.duration((shownMs / 1000).toInt()), Q.type.numberHuge, color = if (route.mode == SessionMode.DREISONG && elapsed > limit) Pal.blood0 else Pal.gold0)
            when (route.mode) {
                SessionMode.SPEEDRUN -> T("Stoppuhr starten, Quest erledigen, Fertig drücken. Jede Zeit zählt für den Schnitt, die beste für den Rekord.", Q.type.small, color = Pal.boneDim, align = TextAlign.Center)
                SessionMode.DREISONG -> {
                    PixelBar(1f - (elapsed.toFloat() / limit).coerceIn(0f, 1f), Bars.gold)
                    T(if (elapsed > limit) "Zeit abgelaufen. Erledigen zählt trotzdem, nur ohne ×1,5." else "Drei Songs, 10:30 Minuten. Fertig vorher = ×1,5.", Q.type.small, color = Pal.boneDim, align = TextAlign.Center)
                }
                SessionMode.HANDYWEG -> T(
                    if (broken) "Bonus verloren: Die App wurde bei eingeschaltetem Bildschirm verlassen." else "Bildschirm aus und Handy weglegen. Wer zwischendurch eine andere App öffnet, verliert den Bonus.",
                    Q.type.small, color = if (broken) Pal.blood0 else Pal.boneDim, align = TextAlign.Center,
                )
                SessionMode.TANZ -> T(
                    when (dance) {
                        1 -> "Jetzt tanzen! Der Bewegungssensor zählt mit."
                        2 -> "Bestanden. Sehr elegant."
                        3 -> "Das war eher ein Schunkeln. Kein Bonus, aber die Quest zählt."
                        else -> "Nach dem Erledigen: zehn Sekunden tanzen."
                    }, Q.type.small, color = Pal.boneDim, align = TextAlign.Center,
                )
            }
            if (dance == 1) PixelBar(danceProgress, Bars.leaf)
        }
        if (!running && elapsed == 0L) {
            PixelButton("Start", {
                startAt = SystemClock.elapsedRealtime()
                running = true
                fx.tap()
            }, Modifier.fillMaxWidth(), icon = "play")
        } else if (running) {
            PixelButton(if (route.mode == SessionMode.TANZ) "Erledigt, jetzt tanzen" else "Fertig", {
                running = false
                elapsed = SystemClock.elapsedRealtime() - startAt
                if (route.mode == SessionMode.TANZ) dance = 1 else finish(false)
            }, Modifier.fillMaxWidth(), icon = "seal")
        }
        PixelButton("Abbrechen", { nav.back() }, Modifier.fillMaxWidth(), kind = Btn.STONE, icon = "cross")
    }
}

/** Misst zehn Sekunden lang Bewegung über den Beschleunigungssensor. */
@Composable
private fun DanceMeter(context: Context, onProgress: (Float) -> Unit, onDone: (Boolean) -> Unit) {
    DisposableEffect(Unit) {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var moves = 0
        val need = 120
        val start = SystemClock.elapsedRealtime()
        var finished = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                if (finished) return
                val m = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2])
                if (abs(m - SensorManager.GRAVITY_EARTH) > 2.5f) moves++
                onProgress((moves.toFloat() / need).coerceIn(0f, 1f))
                val over = SystemClock.elapsedRealtime() - start > 10_000
                if (moves >= need || over) {
                    finished = true
                    onDone(moves >= need)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (sensor == null) onDone(false) else sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm?.unregisterListener(listener) }
    }
}

/** Quest-Roulette (W06) und Blind Date (M05). */
@Composable
fun RouletteScreen(blindStart: Boolean, state: AppState, act: Actions, nav: Nav) {
    val game = state.game
    val fx = LocalFx.current
    val candidates = remember(game.board) { (game.board.overdue + game.board.open).filter { !it.quest.boss } }
    val blindUnlocked = game.player.level >= (Modifiers.of(Modifiers.BLINDDATE)?.unlockLevel ?: 6)
    var blind by remember { mutableStateOf(blindStart && blindUnlocked) }
    var spinning by remember { mutableStateOf(false) }
    var index by remember { mutableIntStateOf(0) }
    var stopped by remember { mutableStateOf(false) }
    var accepted by remember { mutableStateOf(false) }

    LaunchedEffect(spinning) {
        if (!spinning || candidates.isEmpty()) return@LaunchedEffect
        stopped = false
        var d = 45L
        repeat(18 + Random.nextInt(candidates.size * 2 + 1)) {
            index = (index + 1) % candidates.size
            fx.spin()
            delay(d)
            d = (d * 1.11).toLong()
        }
        spinning = false
        stopped = true
        fx.done()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenTitle(if (blind) "Blind Date" else "Quest-Roulette", onBack = { nav.back() })
        if (candidates.isEmpty()) {
            T("Gerade ist nichts im Fenster. Das Roulette dreht sich, sobald eine Quest fällig ist.", Q.type.body, color = Pal.boneDim)
            return@Column
        }
        if (blindUnlocked && !spinning && !stopped) {
            Box(Modifier.fillMaxWidth().stonePanel().padding(horizontal = 14.dp)) {
                Toggle(blind, { blind = it }, "Blind Date", "Die Chronik wählt, du siehst die Quest erst nach dem Annehmen. ×1,5", icon = "dice", dark = true)
            }
        }
        val current = candidates[index % candidates.size]
        val hidden = blind && !accepted
        ScrollPanel(seed = 99) {
            Column(Modifier.fillMaxWidth().height(170.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                PixelIcon(if (hidden) "dice" else Categories.of(current.quest.category).icon, 48.dp)
                Spacer(Modifier.height(8.dp))
                H(if (hidden) "???" else current.quest.title, Q.type.title, color = Pal.ink, align = TextAlign.Center, maxLines = 2)
                if (stopped && !hidden) T(Fmt.window(current), Q.type.small, color = Pal.inkSoft)
            }
        }
        when {
            !stopped -> PixelButton(if (spinning) "Dreht …" else "Drehen", { spinning = true }, Modifier.fillMaxWidth(), enabled = !spinning, icon = "dice")
            blind && !accepted -> {
                T("Eine Quest ist gezogen. Erst annehmen, dann siehst du sie. Kein Zurück.", Q.type.small, color = Pal.boneDim, align = TextAlign.Center)
                PixelButton("Annehmen", { accepted = true; fx.loot() }, Modifier.fillMaxWidth(), kind = Btn.VIOLET, icon = "check")
            }
            blind -> PixelButton("Los geht's (×1,5)", {
                nav.replace(Route.Detail(current.quest.id, preset = listOf(Modifiers.BLINDDATE)))
            }, Modifier.fillMaxWidth(), icon = "play")
            else -> {
                T("Erledigst du sie in der nächsten Stunde, zählt sie ×1,5.", Q.type.small, color = Pal.boneDim, align = TextAlign.Center)
                PixelButton("Annehmen", {
                    act.setSpin(current.quest.id, false)
                    nav.replace(Route.Detail(current.quest.id))
                }, Modifier.fillMaxWidth(), icon = "check")
                PixelButton("Nochmal drehen", { spinning = true }, Modifier.fillMaxWidth(), kind = Btn.STONE, icon = "dice")
            }
        }
    }
}
