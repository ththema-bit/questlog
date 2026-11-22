package io.github.ththemabit.questlog.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.ththemabit.questlog.core.Categories
import io.github.ththemabit.questlog.core.GameState
import io.github.ththemabit.questlog.core.QuestState
import io.github.ththemabit.questlog.core.Rng
import io.github.ththemabit.questlog.core.WindowStatus
import io.github.ththemabit.questlog.ui.Fmt
import io.github.ththemabit.questlog.ui.pixel.Bars
import io.github.ththemabit.questlog.ui.pixel.Frame
import io.github.ththemabit.questlog.ui.pixel.Frames
import io.github.ththemabit.questlog.ui.pixel.IconTap
import io.github.ththemabit.questlog.ui.pixel.LocalPx
import io.github.ththemabit.questlog.ui.pixel.PixelBar
import io.github.ththemabit.questlog.ui.pixel.PixelIcon
import io.github.ththemabit.questlog.ui.pixel.H
import io.github.ththemabit.questlog.ui.pixel.T
import io.github.ththemabit.questlog.ui.pixel.Tag
import io.github.ththemabit.questlog.ui.pixel.bevel
import io.github.ththemabit.questlog.ui.pixel.parchmentCard
import io.github.ththemabit.questlog.ui.pixel.stonePanel
import io.github.ththemabit.questlog.ui.theme.Fonts
import io.github.ththemabit.questlog.ui.theme.Pal
import io.github.ththemabit.questlog.ui.theme.Q
import kotlin.math.max
import kotlin.math.roundToInt

/** Zeitfenster als Pixelbalken: blau zu früh, grün im Fenster, rot schraffiert überfällig, weißer Strich = heute. */
@Composable
fun WindowBar(qs: QuestState, modifier: Modifier = Modifier, onDark: Boolean = false) {
    val q = qs.quest
    if (!q.recurring) return
    val u = LocalPx.current
    val min = q.minDays ?: 0
    val max = q.maxDays
    val domain = max((max ?: (min + 7)) + max(2, (max ?: 7) / 2), qs.daysSince + 1).toFloat()
    Canvas(modifier.fillMaxWidth().height(with(LocalDensity.current) { (5 * u).toDp() })) {
        val w = size.width
        val inner = w - 2 * u
        fun xAt(d: Float) = u + ((inner / u) * (d / domain).coerceIn(0f, 1f)).toInt() * u
        drawRect(if (onDark) Pal.edge else Pal.inkSoft, Offset(u, 0f), Size(w - 2 * u, size.height))
        drawRect(if (onDark) Pal.edge else Pal.inkSoft, Offset(0f, u), Size(w, size.height - 2 * u))
        val top = u
        val h = 3 * u
        val x0 = xAt(0f)
        val xMin = xAt(min.toFloat())
        val xMax = if (max != null) xAt(max.toFloat()) else w - u
        if (xMin > x0) drawRect(Pal.mana2, Offset(x0, top), Size(xMin - x0, h))
        drawRect(Pal.leaf1, Offset(xMin, top), Size(xMax - xMin, h))
        drawRect(Pal.leaf0, Offset(xMin, top), Size(xMax - xMin, u))
        if (max != null && xMax < w - u) {
            drawRect(Pal.blood2, Offset(xMax, top), Size(w - u - xMax, h))
            var x = xMax
            var i = 0
            while (x < w - u) {
                drawRect(Pal.blood1, Offset(x, top + (i % 3) * u), Size(u, u))
                x += u
                i++
            }
        }
        val now = xAt(qs.daysSince.toFloat() + 0.5f)
        drawRect(Pal.bone, Offset(now - u, 0f), Size(u, size.height))
        drawRect(Pal.edge, Offset(now, 0f), Size(u, size.height))
    }
}

private fun statusColor(s: WindowStatus): Color = when (s) {
    WindowStatus.EARLY -> Pal.mana2
    WindowStatus.OPEN -> Pal.leaf2
    WindowStatus.OVERDUE -> Pal.blood1
    WindowStatus.DONE -> Pal.inkFaint
}

/** Quest-Karte: Pergament, Wischen nach rechts oder Siegel antippen zum Erledigen. */
@Composable
fun QuestCard(
    qs: QuestState,
    game: GameState,
    onOpen: () -> Unit,
    onComplete: () -> Unit,
    onLongComplete: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val q = qs.quest
    val density = LocalDensity.current
    val threshold = with(density) { 110.dp.toPx() }
    var drag by remember(q.id) { mutableFloatStateOf(0f) }
    val shown by animateFloatAsState(drag, label = "drag")
    val early = qs.status == WindowStatus.EARLY
    val seed = Rng.hash(q.id).toInt()
    Box(modifier.fillMaxWidth()) {
        if (shown > 4f) {
            Row(
                Modifier.matchParentSize().padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PixelIcon("check", 28.dp, alpha = (shown / threshold).coerceIn(0.2f, 1f))
                Spacer(Modifier.width(6.dp))
                T(if (shown >= threshold) "LOSLASSEN" else "ERLEDIGEN", Q.type.label, color = Pal.leaf0)
            }
        }
        Row(
            Modifier
                .offset { IntOffset(shown.roundToInt(), 0) }
                .fillMaxWidth()
                .alpha(if (early) 0.82f else 1f)
                .parchmentCard(seed, dust = qs.dust, tint = if (qs.status == WindowStatus.OVERDUE) Pal.blood0.copy(alpha = 0.10f) else null)
                .pointerInput(q.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (drag >= threshold) onComplete()
                            drag = 0f
                        },
                        onDragCancel = { drag = 0f },
                        onHorizontalDrag = { _, dx -> drag = (drag + dx).coerceIn(0f, threshold * 1.25f) },
                    )
                }
                .semantics(mergeDescendants = true) {}
                .clickable(role = Role.Button, onClickLabel = "Details", onClick = onOpen)
                .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                PixelIcon(Categories.of(q.category).icon, 28.dp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val titleStyle = if (qs.cursed) Q.type.quest.copy(fontFamily = Fonts.comic) else Q.type.quest
                    T(q.title, titleStyle, Modifier.weight(1f, fill = false), color = Pal.ink, maxLines = 2)
                    if (qs.partnerId != null && game.marriages.isNotEmpty()) {
                        Spacer(Modifier.width(4.dp))
                        PixelIcon("ring", 16.dp)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    T(Fmt.window(qs), Q.type.small, color = statusColor(qs.status), maxLines = 1)
                    T("·", Q.type.small, color = Pal.inkFaint)
                    T(Fmt.effort(q.effortMin), Q.type.small, color = Pal.inkSoft, maxLines = 1)
                }
                if (qs.isBounty || qs.cursed) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (qs.isBounty) Tag("Kopfgeld ×2", Frames.gold, Pal.ink, icon = "target")
                        if (qs.cursed) Tag("Verflucht", Frames.violet, Pal.bone, icon = "skull")
                    }
                }
                if (q.recurring) WindowBar(qs, Modifier.padding(top = 3.dp, end = 6.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SealButton(onClick = onComplete, onLong = onLongComplete, label = "${q.title} erledigen")
                T("+${qs.preview}", Q.type.number.copy(fontSize = Q.type.number.fontSize * 0.75f), color = Pal.inkSoft)
            }
        }
    }
}

@Composable
fun SealButton(onClick: () -> Unit, onLong: (() -> Unit)?, label: String) {
    Box(
        Modifier.size(52.dp)
            .semantics { contentDescription = label }
            .pointerInput(onLong) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { (onLong ?: onClick)() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        PixelIcon("seal", 40.dp)
    }
}

/** Boss-Karte (W01): Lebensbalken aus Teilaufgaben, Zorn senkt die Beute. */
@Composable
fun BossCard(qs: QuestState, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val q = qs.quest
    val next = q.subtasks.firstOrNull { it.id !in qs.hitsThisCycle }
    Column(
        modifier.fillMaxWidth().stonePanel(Frame(Pal.stone1, Pal.blood1, Pal.night0, rivet = Pal.blood0))
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelIcon("skull", 32.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                T("BOSS", Q.type.label, color = Pal.blood0)
                H(q.title, Q.type.heading, color = Pal.bone, maxLines = 2)
            }
            T("${qs.bossHpLeft}/${qs.bossHpMax}", Q.type.number, color = Pal.blood0)
        }
        PixelBar(if (qs.bossHpMax == 0) 0f else qs.bossHpLeft.toFloat() / qs.bossHpMax, Bars.blood, artHeight = 5)
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(next?.let { "Nächster Treffer: ${it.title}" } ?: "Bereit für den letzten Schlag", Q.type.small, Modifier.weight(1f), color = Pal.boneDim, maxLines = 2)
            if (qs.bossRage > 0) Tag("Zorn −${(qs.bossRage * 100).roundToInt()} % Beute", Frames.blood, Pal.bone)
        }
        if (qs.status == WindowStatus.OVERDUE || qs.closesIn != null) {
            T(Fmt.window(qs), Q.type.small, color = if (qs.status == WindowStatus.OVERDUE) Pal.blood0 else Pal.boneDim)
        }
    }
}

/** Kopf mit Stufe, XP, Glut, Serie und Münzen. Kompakt, damit die Quests früh im Bild sind. */
@Composable
fun StatusHeader(game: GameState, modifier: Modifier = Modifier) {
    val p = game.player
    Column(
        modifier.fillMaxWidth().stonePanel().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RankBadge(p.level, Modifier.size(46.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    H("Stufe ${p.level}", Q.type.heading, color = Pal.gold0)
                    Spacer(Modifier.width(8.dp))
                    T(p.rank.name.uppercase(), Q.type.label, Modifier.padding(bottom = 4.dp).weight(1f), color = Pal.boneDim, maxLines = 1)
                    T("${Fmt.n(p.xp)} XP", Q.type.small, Modifier.padding(bottom = 2.dp), color = Pal.boneDim)
                }
                PixelBar(p.levelProgress, Bars.gold, artHeight = 3)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MiniStat("flame", Fmt.n(p.glut), "GLUT", Pal.ember1, Modifier.weight(1f))
            MiniStat("shield", p.streak.toString(), if (p.streakAtRisk) "SERIE WACKELT" else "SERIE", if (p.streakAtRisk) Pal.blood0 else Pal.mana0, Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) { repeat(3) { i -> PixelIcon("shield", 10.dp, alpha = if (i < p.shields) 1f else 0.25f) } }
            }
            MiniStat("coin", Fmt.n(p.coins), "MÜNZEN", Pal.gold1, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MiniStat(icon: String, value: String, label: String, color: Color, modifier: Modifier, extra: (@Composable () -> Unit)? = null) {
    val u = LocalPx.current
    Row(
        modifier.drawBehind { bevel(u, Frames.stoneDark) }.padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PixelIcon(icon, 20.dp)
        Spacer(Modifier.width(6.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T(value, Q.type.number.copy(fontSize = Q.type.number.fontSize * 0.82f), color = color, maxLines = 1)
                if (extra != null) {
                    Spacer(Modifier.width(4.dp))
                    extra()
                }
            }
            T(label, Q.type.label.copy(fontSize = Q.type.label.fontSize * 0.8f), color = Pal.boneFaint, maxLines = 1)
        }
    }
}

/** Rangabzeichen: Flamme in einem Rahmen, Farbe je nach Rang. */
@Composable
fun RankBadge(level: Int, modifier: Modifier = Modifier.size(56.dp)) {
    val u = LocalPx.current
    val frame = when {
        level >= 30 -> Frames.violet
        level >= 15 -> Frames.gold
        level >= 6 -> Frames.ember
        else -> Frames.stoneFlat
    }
    Box(modifier.drawBehind { bevel(u, frame) }, contentAlignment = Alignment.Center) {
        PixelIcon(if (level >= 20) "sun" else "flame", 28.dp)
    }
}

/** Saison-Leiste (K10) mit Geist der Vorwoche (W03) und optional Gerald (C03). */
@Composable
fun SeasonCard(game: GameState, geraldOn: Boolean, onRecap: (() -> Unit)?, modifier: Modifier = Modifier) {
    val s = game.season
    val kw = s.weekStart.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)
    Column(
        modifier.fillMaxWidth().stonePanel(Frames.wood).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelIcon("flag", 16.dp)
            Spacer(Modifier.width(6.dp))
            T("KW $kw", Q.type.label, color = Pal.parch1)
            Spacer(Modifier.width(8.dp))
            T(Fmt.n(s.xp), Q.type.number.copy(fontSize = Q.type.number.fontSize * 0.85f), color = Pal.gold0)
            T(" / ${Fmt.n(s.goal)}", Q.type.small, color = Pal.parch2)
            Spacer(Modifier.weight(1f))
            when {
                s.hardcore -> Tag("Hardcore", Frames.blood, Pal.bone, icon = "skull")
                s.xp >= s.goal -> Tag("Ziel", Frames.leaf, Pal.ink, icon = "check")
                else -> T(if (s.daysLeft == 0) "LETZTER TAG" else "NOCH ${Fmt.tage(s.daysLeft).uppercase()}", Q.type.label, color = Pal.parch2)
            }
        }
        PixelBar(s.progress, Bars.leaf, artHeight = 3, ghost = if (s.goal > 0) (s.ghostXp.toFloat() / s.goal).coerceIn(0f, 1f) else null)
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelIcon("ghost", 14.dp)
            Spacer(Modifier.width(6.dp))
            val d = s.ghostDelta
            T(
                when {
                    s.ghostXp == 0 && s.lastWeekXp == 0 -> "Noch kein Geist: Die Vorwoche war leer."
                    d >= 0 -> "${Fmt.n(d)} vor deinem Geist der Vorwoche"
                    else -> "${Fmt.n(-d)} hinter deinem Geist der Vorwoche"
                },
                Q.type.small, Modifier.weight(1f), color = if (d >= 0) Pal.leaf0 else Pal.parch2, maxLines = 1,
            )
            if (onRecap != null) T("RÜCKBLICK ›", Q.type.label, Modifier.clickable(role = Role.Button, onClick = onRecap).padding(start = 8.dp, top = 6.dp, bottom = 6.dp), color = Pal.gold0)
        }
        if (geraldOn) {
            Row(verticalAlignment = Alignment.Top) {
                PixelIcon("crown", 14.dp)
                Spacer(Modifier.width(6.dp))
                T("Gerald ${Fmt.n(game.gerald.weekXp)}: ${game.gerald.message}", Q.type.small, color = Pal.parch2, maxLines = 2)
            }
        }
    }
}

/** Hinweisband (Feiertag, Fluch, Pause, Brief …). */
@Composable
fun Banner(icon: String, title: String, text: String, frame: Frame, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().stonePanel(frame)
            .then(if (onAction != null) Modifier.clickable(role = Role.Button, onClick = onAction) else Modifier)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PixelIcon(icon, 24.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            T(title, Q.type.bodyBold, color = Pal.bone)
            if (text.isNotEmpty()) T(text, Q.type.small, color = Pal.boneDim)
        }
        if (action != null) {
            Spacer(Modifier.width(8.dp))
            T(action.uppercase() + " ›", Q.type.label, color = Pal.gold0)
        }
    }
}

/** Untere Navigation: fünf Steinkacheln. */
@Composable
fun BottomNav(current: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val u = LocalPx.current
    val items = listOf("scroll" to "Quests", "book" to "Chronik", "crown" to "Ruhm", "chest" to "Schatz", "gear" to "Optionen")
    Row(
        modifier.fillMaxWidth().drawBehind { bevel(u, Frames.stoneDark) }.padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEachIndexed { i, (icon, label) ->
            val active = i == current
            Column(
                Modifier.weight(1f).height(58.dp)
                    .then(if (active) Modifier.drawBehind { bevel(u, Frames.wood) } else Modifier)
                    .semantics { contentDescription = label }
                    .clickable(role = Role.Tab) { onSelect(i) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                PixelIcon(icon, 24.dp, alpha = if (active) 1f else 0.6f)
                Spacer(Modifier.height(3.dp))
                T(label.uppercase(), Q.type.label.copy(fontSize = Q.type.label.fontSize * 0.85f), color = if (active) Pal.gold0 else Pal.boneFaint, maxLines = 1, align = TextAlign.Center)
            }
        }
    }
}

/** Kopfzeile eines Unterscreens mit Zurück-Pfeil. */
@Composable
fun ScreenTitle(title: String, onBack: (() -> Unit)?, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconTap("back", "Zurück", onBack, size = 28.dp) else Spacer(Modifier.width(8.dp))
        H(title, Q.type.title, Modifier.weight(1f), color = Pal.gold0, maxLines = 1)
        actions()
    }
}

@Composable
fun EmptyHint(icon: String, title: String, text: String, modifier: Modifier = Modifier, content: @Composable () -> Unit = {}) {
    Column(
        modifier.fillMaxWidth().parchmentCard(11).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PixelIcon(icon, 48.dp)
        H(title, Q.type.heading, color = Pal.ink, align = TextAlign.Center)
        T(text, Q.type.body, color = Pal.inkSoft, align = TextAlign.Center)
        content()
    }
}

@Composable
fun Fill() = Spacer(Modifier.fillMaxSize())
