package io.github.ththemabit.questlog.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.ththemabit.questlog.ui.pixel.Btn
import io.github.ththemabit.questlog.ui.pixel.Frame
import io.github.ththemabit.questlog.ui.pixel.Frames
import io.github.ththemabit.questlog.ui.pixel.LocalPx
import io.github.ththemabit.questlog.ui.pixel.PixelButton
import io.github.ththemabit.questlog.ui.pixel.PixelIcon
import io.github.ththemabit.questlog.ui.pixel.T
import io.github.ththemabit.questlog.ui.pixel.bevel
import io.github.ththemabit.questlog.ui.pixel.parchmentCard
import io.github.ththemabit.questlog.ui.theme.Pal
import io.github.ththemabit.questlog.ui.theme.Q
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val inset = Frame(Pal.parch0, Pal.parch3, Pal.parch1, edge = Pal.inkSoft)

/** Eingabefeld: helles Pergament mit eingelassenem Rand, Tinte als Schrift. */
@Composable
fun PixelField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    label: String? = null,
) {
    val u = LocalPx.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (label != null) T(label.uppercase(), Q.type.label, color = Pal.inkSoft)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = singleLine,
            minLines = minLines,
            textStyle = Q.type.body.copy(color = Pal.ink),
            cursorBrush = SolidColor(Pal.ink),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label ?: placeholder },
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth().drawBehind { bevel(u, inset, pressed = true) }.padding(horizontal = 12.dp, vertical = 11.dp)) {
                    if (value.isEmpty()) T(placeholder, Q.type.body, color = Pal.inkFaint)
                    inner()
                }
            },
        )
    }
}

/** Hebel-Schalter mit Beschriftung. */
@Composable
fun Toggle(checked: Boolean, onChange: (Boolean) -> Unit, title: String, text: String? = null, modifier: Modifier = Modifier, icon: String? = null, dark: Boolean = false) {
    val u = LocalPx.current
    Row(
        modifier.fillMaxWidth()
            .semantics(mergeDescendants = true) { stateDescription = if (checked) "an" else "aus" }
            .clickable(role = Role.Switch) { onChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            PixelIcon(icon, 22.dp)
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            T(title, Q.type.bodyBold, color = if (dark) Pal.bone else Pal.ink)
            if (text != null) T(text, Q.type.small, color = if (dark) Pal.boneDim else Pal.inkSoft)
        }
        Spacer(Modifier.width(10.dp))
        Canvas(Modifier.size(width = 46.dp, height = 26.dp)) {
            val w = size.width
            val h = size.height
            drawRect(Pal.edge, Offset(u, 0f), Size(w - 2 * u, h))
            drawRect(Pal.edge, Offset(0f, u), Size(w, h - 2 * u))
            drawRect(if (checked) Pal.leaf1 else Pal.stone0, Offset(u, u), Size(w - 2 * u, h - 2 * u))
            drawRect(if (checked) Pal.leaf0 else Pal.stone2, Offset(u, u), Size(w - 2 * u, u))
            val knobW = h - 2 * u
            val x = if (checked) w - u - knobW else u
            drawRect(Pal.edge, Offset(x, u), Size(knobW, knobW))
            drawRect(Pal.gold1, Offset(x + u, 2 * u), Size(knobW - 2 * u, knobW - 2 * u))
            drawRect(Pal.gold0, Offset(x + u, 2 * u), Size(knobW - 2 * u, u))
            drawRect(Pal.gold2, Offset(x + u, knobW - u), Size(knobW - 2 * u, u))
        }
    }
}

/** − Wert + */
@Composable
fun Stepper(value: Int, onChange: (Int) -> Unit, range: IntRange, label: String, format: (Int) -> String = { it.toString() }, step: Int = 1, dark: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        T(label, Q.type.bodyBold, Modifier.weight(1f), color = if (dark) Pal.bone else Pal.ink)
        PixelButton("−", { onChange((value - step).coerceIn(range)) }, kind = Btn.STONE, small = true, enabled = value > range.first)
        T(format(value), Q.type.number.copy(fontSize = Q.type.number.fontSize * 0.85f), Modifier.width(96.dp), color = if (dark) Pal.gold0 else Pal.ink, align = TextAlign.Center, maxLines = 1)
        PixelButton("+", { onChange((value + step).coerceIn(range)) }, kind = Btn.STONE, small = true, enabled = value < range.last)
    }
}

/** 1–5 Icons zum Antippen (Wichtigkeit, Überwindung). */
@Composable
fun RatingRow(value: Int, onChange: (Int) -> Unit, icon: String, label: String, captions: List<String>) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(label, Q.type.bodyBold, Modifier.weight(1f), color = Pal.ink)
            T(captions.getOrElse(value - 1) { "" }, Q.type.small, color = Pal.inkSoft)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (i in 1..5) {
                Box(
                    Modifier.size(48.dp)
                        .semantics { contentDescription = "$label $i: ${captions.getOrElse(i - 1) { "" }}" }
                        .clickable(role = Role.RadioButton) { onChange(i) },
                    contentAlignment = Alignment.Center,
                ) { PixelIcon(icon, 30.dp, alpha = if (i <= value) 1f else 0.22f) }
            }
        }
    }
}

/** Auswahl-Chips mit optionalem Icon. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChipRow(options: List<T>, selected: (T) -> Boolean, label: (T) -> String, icon: (T) -> String? = { null }, onSelect: (T) -> Unit) {
    val u = LocalPx.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { o ->
            val sel = selected(o)
            Row(
                Modifier.defaultMinSize(minHeight = 40.dp)
                    .drawBehind { bevel(u, if (sel) Frames.gold else inset, pressed = !sel) }
                    .clickable(role = Role.RadioButton) { onSelect(o) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                icon(o)?.let { PixelIcon(it, 18.dp) }
                T(label(o), Q.type.small, color = Pal.ink, maxLines = 1)
            }
        }
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val u = LocalPx.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEachIndexed { i, s ->
            val sel = i == selected
            Box(
                Modifier.weight(1f).height(44.dp)
                    .drawBehind { bevel(u, if (sel) Frames.wood else Frames.stoneDark, pressed = sel) }
                    .clickable(role = Role.Tab) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { T(s.uppercase(), Q.type.label, color = if (sel) Pal.gold0 else Pal.boneDim, maxLines = 1) }
        }
    }
}

/** Monatskalender zum Auswählen eines Tages. */
@Composable
fun CalendarGrid(initial: LocalDate, selected: LocalDate?, min: LocalDate?, max: LocalDate?, onPick: (LocalDate) -> Unit) {
    var month by remember { mutableStateOf(YearMonth.from(selected ?: initial)) }
    val u = LocalPx.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelButton("‹", { month = month.minusMonths(1) }, kind = Btn.STONE, small = true)
            T(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN)), Q.type.heading, Modifier.weight(1f), color = Pal.ink, align = TextAlign.Center)
            PixelButton("›", { month = month.plusMonths(1) }, kind = Btn.STONE, small = true)
        }
        Row {
            listOf("MO", "DI", "MI", "DO", "FR", "SA", "SO").forEach {
                T(it, Q.type.label, Modifier.weight(1f), color = Pal.inkSoft, align = TextAlign.Center)
            }
        }
        val first = month.atDay(1)
        val lead = first.dayOfWeek.value - DayOfWeek.MONDAY.value
        val days = month.lengthOfMonth()
        val cells = ((lead + days + 6) / 7) * 7
        for (row in 0 until cells / 7) {
            Row {
                for (col in 0 until 7) {
                    val idx = row * 7 + col - lead + 1
                    Box(Modifier.weight(1f).height(44.dp), contentAlignment = Alignment.Center) {
                        if (idx in 1..days) {
                            val d = month.atDay(idx)
                            val allowed = (min == null || !d.isBefore(min)) && (max == null || !d.isAfter(max))
                            val sel = d == selected
                            Box(
                                Modifier.size(40.dp)
                                    .then(if (sel) Modifier.drawBehind { bevel(u, Frames.gold) } else Modifier)
                                    .clickable(enabled = allowed, role = Role.Button) { onPick(d) },
                                contentAlignment = Alignment.Center,
                            ) {
                                T(idx.toString(), Q.type.number.copy(fontSize = Q.type.number.fontSize * 0.7f),
                                    color = when { !allowed -> Pal.parch3; sel -> Pal.ink; d == initial -> Pal.blood1; else -> Pal.ink }, align = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Pergament-Abschnitt mit Überschrift. */
@Composable
fun ParchSection(title: String, icon: String? = null, modifier: Modifier = Modifier, seed: Int = title.hashCode(), content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth().parchmentCard(seed).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                PixelIcon(icon, 20.dp)
                Spacer(Modifier.width(8.dp))
            }
            T(title, Q.type.heading, color = Pal.ink)
        }
        content()
    }
}
