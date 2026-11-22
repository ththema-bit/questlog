package io.github.ththemabit.questlog.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.ththemabit.questlog.core.AchievementState
import io.github.ththemabit.questlog.core.Engine
import io.github.ththemabit.questlog.core.GameState
import io.github.ththemabit.questlog.core.Ranks
import io.github.ththemabit.questlog.data.AppState
import io.github.ththemabit.questlog.ui.Actions
import io.github.ththemabit.questlog.ui.Fmt
import io.github.ththemabit.questlog.ui.Nav
import io.github.ththemabit.questlog.ui.components.ScreenTitle
import io.github.ththemabit.questlog.ui.components.Segmented
import io.github.ththemabit.questlog.ui.pixel.Bars
import io.github.ththemabit.questlog.ui.pixel.Frames
import io.github.ththemabit.questlog.ui.pixel.LocalPx
import io.github.ththemabit.questlog.ui.pixel.PixelBar
import io.github.ththemabit.questlog.ui.pixel.PixelIcon
import io.github.ththemabit.questlog.ui.pixel.SectionTitle
import io.github.ththemabit.questlog.ui.pixel.T
import io.github.ththemabit.questlog.ui.pixel.Tag
import io.github.ththemabit.questlog.ui.pixel.parchmentCard
import io.github.ththemabit.questlog.ui.pixel.stonePanel
import io.github.ththemabit.questlog.ui.theme.Pal
import io.github.ththemabit.questlog.ui.theme.Q
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.IsoFields
import java.util.Locale
import kotlin.math.max

/** Pixel-Säulendiagramm. ghost = Vergleichswerte (dünn, hell). */
@Composable
fun BarChart(values: List<Int>, labels: List<String>, highlight: Int?, modifier: Modifier = Modifier, ghost: List<Int>? = null, description: String) {
    val u = LocalPx.current
    val top = max(1, max(values.maxOrNull() ?: 0, ghost?.maxOrNull() ?: 0))
    Column(modifier.fillMaxWidth().semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val n = values.size
            val slot = size.width / n
            val barW = max(u, ((slot * 0.62f) / u).toInt() * u)
            val base = size.height - u
            drawRect(Pal.stone3, Offset(0f, base), Size(size.width, u))
            for (i in 0 until n) {
                val x = (i * slot + (slot - barW) / 2f).let { (it / u).toInt() * u }
                ghost?.getOrNull(i)?.let { g ->
                    val gh = ((g.toFloat() / top) * (base - 2 * u) / u).toInt() * u
                    if (gh > 0) drawRect(Pal.boneFaint.copy(alpha = 0.45f), Offset(x - u, base - gh), Size(u, gh))
                }
                val h = ((values[i].toFloat() / top) * (base - 2 * u) / u).toInt() * u
                if (h <= 0) continue
                val hi = i == highlight
                val mid = if (hi) Pal.ember1 else Pal.gold1
                val light = if (hi) Pal.ember0 else Pal.gold0
                val dark = if (hi) Pal.ember2 else Pal.gold2
                drawRect(Pal.edge, Offset(x - 0f, base - h - u), Size(barW, h + u))
                drawRect(mid, Offset(x + u, base - h), Size(barW - 2 * u, h))
                drawRect(light, Offset(x + u, base - h), Size(barW - 2 * u, u))
                drawRect(dark, Offset(x + barW - 2 * u, base - h), Size(u, h))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEach { T(it, Q.type.label.copy(fontSize = Q.type.label.fontSize * 0.8f), Modifier.weight(1f), color = Pal.boneFaint, align = TextAlign.Center, maxLines = 1) }
        }
    }
}

/** Kalender-Heatmap der letzten 26 Wochen. */
@Composable
fun Heatmap(game: GameState) {
    val u = LocalPx.current
    val weeks = 26
    val lastWs = Engine.weekStart(game.today)
    val firstWs = lastWs.minusWeeks((weeks - 1).toLong())
    val values = (0 until weeks * 7).map { i -> game.days[firstWs.plusDays(i.toLong())]?.xp ?: 0 }
    val nonZero = values.filter { it > 0 }.sorted()
    fun q(p: Float) = if (nonZero.isEmpty()) 1 else nonZero[((nonZero.size - 1) * p).toInt()]
    val t1 = q(0.25f)
    val t2 = q(0.5f)
    val t3 = q(0.8f)
    Canvas(Modifier.fillMaxWidth().height(110.dp).semantics { contentDescription = "Aktivität der letzten 26 Wochen" }) {
        val cell = ((size.width / weeks) / u).toInt() * u
        val cellH = ((size.height / 7) / u).toInt() * u
        val side = minOf(cell, cellH)
        for (w in 0 until weeks) for (d in 0 until 7) {
            val day = firstWs.plusDays((w * 7 + d).toLong())
            if (day.isAfter(game.today)) continue
            val v = values[w * 7 + d]
            val c: Color = when {
                v <= 0 -> Pal.night2
                v < t1 -> Pal.stone3
                v < t2 -> Pal.gold2
                v < t3 -> Pal.gold1
                else -> Pal.ember1
            }
            drawRect(c, Offset(w * side.toFloat(), d * side.toFloat()), Size(side - u, side - u))
        }
    }
}

@Composable
fun ChronicleScreen(state: AppState, nav: Nav) {
    val game = state.game
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val today = game.today
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenTitle("Chronik", onBack = null) }
        item { Segmented(listOf("Woche", "Monat", "Jahr"), tab, { tab = it }) }
        item {
            Column(Modifier.fillMaxWidth().stonePanel().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (tab) {
                    0 -> {
                        val ws = Engine.weekStart(today)
                        val vals = (0..6).map { game.days[ws.plusDays(it.toLong())]?.xp ?: 0 }
                        val ghost = (0..6).map { game.days[ws.minusWeeks(1).plusDays(it.toLong())]?.xp ?: 0 }
                        val kw = ws.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
                        Header("KW $kw", vals.sum(), "Vorwoche ${Fmt.n(ghost.sum())}")
                        BarChart(vals, listOf("MO", "DI", "MI", "DO", "FR", "SA", "SO"), today.dayOfWeek.value - 1, ghost = ghost, description = "Punkte pro Tag diese Woche")
                        T("Helle Striche: dein Geist der Vorwoche am selben Tag.", Q.type.small, color = Pal.boneFaint)
                    }
                    1 -> {
                        val ym = YearMonth.from(today)
                        val vals = (1..ym.lengthOfMonth()).map { game.days[ym.atDay(it)]?.xp ?: 0 }
                        val labels = (1..ym.lengthOfMonth()).map { if (it == 1 || it % 5 == 0) it.toString() else "" }
                        Header(ym.month.getDisplayName(TextStyle.FULL, Locale.GERMAN) + " " + ym.year, vals.sum(), "Vormonat ${Fmt.n(game.months[ym.minusMonths(1)] ?: 0)}")
                        BarChart(vals, labels, today.dayOfMonth - 1, description = "Punkte pro Tag in diesem Monat")
                    }
                    else -> {
                        val vals = (1..12).map { game.months[YearMonth.of(today.year, it)] ?: 0 }
                        val labels = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")
                        Header("Jahr ${today.year}", vals.sum(), "Vorjahr ${Fmt.n((1..12).sumOf { game.months[YearMonth.of(today.year - 1, it)] ?: 0 })}")
                        BarChart(vals, labels, today.monthValue - 1, description = "Punkte pro Monat in diesem Jahr")
                    }
                }
            }
        }
        item { SectionTitle("Ein halbes Jahr", icon = "calendar") }
        item { Column(Modifier.fillMaxWidth().stonePanel(Frames.stoneDark).padding(14.dp)) { Heatmap(game) } }
        item {
            val doneTotal = game.days.values.sumOf { it.count }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Total("Lebens-XP", Fmt.n(game.player.xp), "spark", Modifier.weight(1f))
                Total("Aktive Tage", Fmt.n(game.player.activeDays), "calendar", Modifier.weight(1f))
                Total("Quests", Fmt.n(doneTotal), "check", Modifier.weight(1f))
            }
        }
        if (game.skills.isNotEmpty()) {
            item { SectionTitle("Fertigkeiten", icon = "star") }
            items(game.skills, key = { it.category.key }) { s ->
                Row(Modifier.fillMaxWidth().parchmentCard(s.category.key.hashCode()).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    PixelIcon(s.category.icon, 26.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row {
                            T(s.category.name, Q.type.bodyBold, Modifier.weight(1f), color = Pal.ink)
                            T("Stufe ${s.level}", Q.type.bodyBold, color = Pal.inkSoft)
                        }
                        PixelBar(s.progress, Bars.leaf, artHeight = 3)
                        T("${Fmt.n(s.xp)} XP", Q.type.label, color = Pal.inkFaint)
                    }
                }
            }
        }
        val top = game.quests.filter { it.doneCount > 0 }.sortedByDescending { it.doneCount }.take(6)
        if (top.isNotEmpty()) {
            item { SectionTitle("Stammgäste", icon = "scroll") }
            items(top, key = { "top-" + it.quest.id }) { qs ->
                Row(Modifier.fillMaxWidth().stonePanel(Frames.stoneFlat).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    T(qs.quest.title, Q.type.body, Modifier.weight(1f), color = Pal.bone, maxLines = 1)
                    qs.bestSec?.let { T("PB ${Fmt.duration(it)}  ", Q.type.label, color = Pal.mana0) }
                    T("${qs.doneCount}×", Q.type.number.copy(fontSize = Q.type.number.fontSize * 0.75f), color = Pal.gold1)
                }
            }
        }
    }
}

@Composable
private fun Header(title: String, total: Int, compare: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            T(title, Q.type.heading, color = Pal.gold0)
            T(compare, Q.type.small, color = Pal.boneFaint)
        }
        T(Fmt.n(total), Q.type.numberBig, color = Pal.gold1)
    }
}

@Composable
private fun Total(label: String, value: String, icon: String, modifier: Modifier) {
    Column(modifier.stonePanel(Frames.stoneFlat).padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        PixelIcon(icon, 18.dp)
        T(value, Q.type.number, color = Pal.gold0, maxLines = 1)
        T(label.uppercase(), Q.type.label, color = Pal.boneFaint, maxLines = 1)
    }
}

@Composable
fun GloryScreen(state: AppState, act: Actions, nav: Nav) {
    val game = state.game
    val r = game.records
    val seenBefore = remember { state.save.meta.seenAchievements.toSet() }
    val unlockedIds = game.achievements.filter { it.unlockedAt != null }.map { it.def.id }
    LaunchedEffect(unlockedIds.size) {
        if (!seenBefore.containsAll(unlockedIds)) act.meta { it.copy(seenAchievements = unlockedIds) }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenTitle("Ruhmeshalle", onBack = null) }
        item { SectionTitle("Rekorde", icon = "crown") }
        item {
            Column(Modifier.fillMaxWidth().stonePanel().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Record("Bester Tag", r.bestDay?.let { Fmt.n(it.second) + " XP" }, r.bestDay?.first?.format(Fmt.dayMonthYear))
                Record("Beste Woche", r.bestWeek?.let { Fmt.n(it.second) + " XP" }, r.bestWeek?.first?.let { "KW " + it.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) + " · " + it.year })
                Record("Bester Monat", r.bestMonth?.let { Fmt.n(it.second) + " XP" }, r.bestMonth?.first?.let { it.month.getDisplayName(TextStyle.FULL, Locale.GERMAN) + " " + it.year })
                Record("Längste Serie", if (r.longestStreak > 0) Fmt.tage(r.longestStreak) else null, null)
                Record("Höchste Glut", r.highestGlut?.let { Fmt.n(it.second) }, r.highestGlut?.first?.format(Fmt.dayMonthYear))
                Record("Meiste Quests an einem Tag", r.mostQuestsDay?.let { "${it.second}" }, r.mostQuestsDay?.first?.format(Fmt.dayMonthYear))
                r.fastest.take(3).forEach { (_, title, sec) -> Record("Bestzeit: $title", Fmt.duration(sec), null) }
            }
        }
        val unlocked = game.achievements.count { it.unlockedAt != null }
        item { SectionTitle("Meilensteine", icon = "star", count = unlocked) }
        items(game.achievements.chunked(2), key = { it.first().def.id }) { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { a -> AchievementTile(a, isNew = a.unlockedAt != null && a.def.id !in seenBefore, modifier = Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item { SectionTitle("Ränge", icon = "flame") }
        items(Ranks.all, key = { "rank-" + it.minLevel }) { rank ->
            val current = Ranks.forLevel(game.player.level) == rank
            val reached = game.player.level >= rank.minLevel
            Row(
                Modifier.fillMaxWidth().stonePanel(if (current) Frames.wood else Frames.stoneFlat).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PixelIcon(if (reached) "flame" else "lock", 22.dp, alpha = if (reached) 1f else 0.5f)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    T(rank.name, Q.type.heading.copy(fontSize = Q.type.heading.fontSize * 0.85f), color = if (reached) Pal.gold0 else Pal.boneFaint)
                    T(rank.flavor, Q.type.small, color = Pal.boneDim)
                }
                T("ab Stufe ${rank.minLevel}", Q.type.label, color = Pal.boneFaint)
            }
        }
    }
}

@Composable
private fun Record(label: String, value: String?, sub: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PixelIcon("crown", 18.dp, alpha = if (value != null) 1f else 0.35f)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            T(label, Q.type.body, color = Pal.bone, maxLines = 1)
            if (sub != null) T(sub, Q.type.label, color = Pal.boneFaint)
        }
        T(value ?: "–", Q.type.number.copy(fontSize = Q.type.number.fontSize * 0.8f), color = if (value != null) Pal.gold0 else Pal.boneFaint)
    }
}

@Composable
private fun AchievementTile(a: AchievementState, isNew: Boolean, modifier: Modifier) {
    val unlocked = a.unlockedAt != null
    val secret = a.def.hidden && !unlocked
    Column(
        modifier.then(if (unlocked) Modifier.parchmentCard(a.def.id.hashCode()) else Modifier.stonePanel(Frames.stoneDark)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelIcon(if (secret) "lock" else a.def.icon, 26.dp, alpha = if (unlocked) 1f else 0.4f)
            Spacer(Modifier.weight(1f))
            if (isNew) Tag("Neu", Frames.ember, Pal.ink)
        }
        T(if (secret) "???" else a.def.title, Q.type.bodyBold, color = if (unlocked) Pal.ink else Pal.boneDim, maxLines = 2)
        T(if (secret) "Geheimer Meilenstein" else a.def.text, Q.type.small, color = if (unlocked) Pal.inkSoft else Pal.boneFaint, maxLines = 3)
        if (unlocked) {
            T(Fmt.day(a.unlockedAt!!).format(Fmt.dayMonthYear), Q.type.label, color = Pal.inkFaint)
        } else if (!secret) {
            val (v, t) = a.progress ?: (0L to 1L)
            PixelBar((v.toFloat() / t).coerceIn(0f, 1f), Bars.gold, artHeight = 2)
            T("${Fmt.n(minOf(v, t))} / ${Fmt.n(t)}", Q.type.label, color = Pal.boneFaint)
        }
    }
}
