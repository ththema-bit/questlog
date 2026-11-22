package io.github.ththemabit.questlog.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.ththemabit.questlog.core.Categories
import io.github.ththemabit.questlog.core.Persona
import io.github.ththemabit.questlog.core.WindowStatus
import io.github.ththemabit.questlog.data.AppState
import io.github.ththemabit.questlog.ui.Fmt
import io.github.ththemabit.questlog.ui.pixel.BarColors
import io.github.ththemabit.questlog.ui.pixel.Bars
import io.github.ththemabit.questlog.ui.pixel.HeadingRaster
import io.github.ththemabit.questlog.ui.pixel.IconCache
import io.github.ththemabit.questlog.ui.pixel.parchment
import io.github.ththemabit.questlog.ui.pixel.parchmentTex
import io.github.ththemabit.questlog.ui.pixel.pixelUnit
import io.github.ththemabit.questlog.ui.pixel.scrollRod
import io.github.ththemabit.questlog.ui.screens.greeting
import io.github.ththemabit.questlog.ui.theme.Pal
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Zeichnet die Widgets komplett als Pixel-Bild: Schriftrolle, Pixelschrift, Icons, Wachssiegel.
 * Widgets können weder eigene Schriften noch eigenes Zeichnen, darum entsteht alles hier, und
 * Glance legt nur unsichtbare Tippflächen darüber. Dieselben Bilder sind die Vorschau in der
 * Widget-Auswahl (siehe WidgetShots-Test).
 */
class WidgetRender(
    private val context: Context,
    val density: Float = context.resources.displayMetrics.density,
    fontScale: Float = context.resources.configuration.fontScale,
) {
    /** Kunst-Pixel in Gerätepixeln, wie in der App */
    private val u: Int = pixelUnit(density).toInt()
    private val textScale = density * fontScale.coerceIn(0.85f, 1.3f)
    // Wie in der App: Überschriften gerastert, alles andere in der normalen Systemschrift
    private val textFace = sans(400)
    private val boldFace = sans(600)
    private val labelFace = sans(500)
    private val numberFace = Typeface.create("sans-serif-condensed", Typeface.BOLD)

    private fun sans(weight: Int): Typeface =
        if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.SANS_SERIF, weight, false)
        else Typeface.create(if (weight == 500) "sans-serif-medium" else "sans-serif", if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)

    fun px(v: Dp): Int = (v.value * density).roundToInt()
    fun dpOf(px: Int): Dp = (px / density).dp

    /** Eine Schrift in einer Größe und Farbe. */
    private inner class Ink(face: Typeface, sizeSp: Float, color: Color, spacing: Float = 0f) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = face
            textSize = sizeSp * textScale
            this.color = color.toArgb()
            letterSpacing = spacing
        }
        private val fm = paint.fontMetrics
        val height: Int = ceil(fm.descent - fm.ascent).toInt()
        fun width(t: String): Int = ceil(paint.measureText(t)).toInt()
        fun fit(t: String, max: Int): String = TextUtils.ellipsize(t, paint, max.coerceAtLeast(1).toFloat(), TextUtils.TruncateAt.END).toString()
        fun draw(c: android.graphics.Canvas, t: String, x: Int, top: Int) = c.drawText(t, x.toFloat(), top - fm.ascent, paint)
        fun layout(t: String, width: Int, maxLines: Int): StaticLayout =
            StaticLayout.Builder.obtain(t, 0, t.length, paint, max(1, width))
                .setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END).setIncludePad(false).build()
    }

    private fun draw(w: Int, h: Int, block: DrawScope.(android.graphics.Canvas) -> Unit): Bitmap {
        val img = ImageBitmap(max(1, w), max(1, h))
        val canvas = Canvas(img)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(w.toFloat(), h.toFloat())) { block(canvas.nativeCanvas) }
        return img.asAndroidBitmap()
    }

    /** Stab oben, Pergament, Stab unten. Um die Rolle herum bleibt alles durchsichtig. */
    private fun DrawScope.scrollFrame(seed: Int) {
        val f = u.toFloat()
        scrollRod(f)
        inset(2 * f, 6 * f, 2 * f, 6 * f) { parchment(f, seed, parchmentTex(f)) }
        translate(top = size.height - 7 * f) { scrollRod(f) }
    }

    /** Icon-Vergrößerung für etwa [sizeDp], immer ganzzahlig, damit die Pixel scharf bleiben. */
    private fun k(sizeDp: Float) = max(1, (sizeDp * density / 16f).roundToInt())

    private fun DrawScope.icon(name: String, x: Int, y: Int, k: Int) {
        drawImage(
            IconCache.get(name), srcOffset = IntOffset.Zero, srcSize = IntSize(16, 16),
            dstOffset = IntOffset(x, y), dstSize = IntSize(16 * k, 16 * k), filterQuality = FilterQuality.None,
        )
    }

    /** Überschrift in der gerasterten Fantasy-Schrift der App. */
    private fun heading(text: String, sizeSp: Float, color: Color, maxWidth: Int): ImageBitmap {
        val size = max(8, (sizeSp * textScale / u).roundToInt())
        val shadow = if (color.luminance() > 0.4f) Pal.edge.toArgb() else null
        return HeadingRaster.render(context, text, size, color.toArgb(), shadow, max(8, maxWidth / u), 1, false)
    }

    private fun DrawScope.image(img: ImageBitmap, x: Int, y: Int) {
        drawImage(img, srcOffset = IntOffset.Zero, srcSize = IntSize(img.width, img.height), dstOffset = IntOffset(x, y),
            dstSize = IntSize(img.width * u, img.height * u), filterQuality = FilterQuality.None)
    }

    private fun DrawScope.dots(x0: Int, x1: Int, y: Int, color: Color) {
        var x = x0
        while (x + u <= x1) {
            drawRect(color, Offset(x.toFloat(), y.toFloat()), Size(u.toFloat(), u.toFloat()))
            x += 2 * u
        }
    }

    /** Fortschrittsbalken wie PixelBar in der App. */
    private fun DrawScope.bar(x: Int, y: Int, w: Int, progress: Float, colors: BarColors, ghost: Float?, art: Int = 4): Int {
        val f = u.toFloat()
        val h = (art + 2) * f
        val left = x.toFloat()
        val top = y.toFloat()
        drawRect(Pal.edge, Offset(left + f, top), Size(w - 2 * f, h))
        drawRect(Pal.edge, Offset(left, top + f), Size(w.toFloat(), h - 2 * f))
        val innerW = w - 2 * f
        drawRect(Pal.night0, Offset(left + f, top + f), Size(innerW, art * f))
        val fillW = ((innerW / f) * progress.coerceIn(0f, 1f)).toInt() * f
        if (fillW > 0) {
            drawRect(colors.mid, Offset(left + f, top + f), Size(fillW, art * f))
            drawRect(colors.light, Offset(left + f, top + f), Size(fillW, f))
            drawRect(colors.dark, Offset(left + f, top + art * f), Size(fillW, f))
        }
        ghost?.let { g ->
            val gx = left + f + ((innerW / f) * g.coerceIn(0f, 1f)).toInt() * f
            drawRect(Pal.bone.copy(alpha = 0.7f), Offset(gx - f, top), Size(f, h))
        }
        return h.toInt()
    }

    // ---------------------------------------------------------------- Questrolle

    data class QuestRow(val id: String, val title: String, val label: String)

    /** Fertige Questrolle plus Maße für die Tippflächen (alles in Gerätepixeln). */
    class QuestScroll(
        val bitmap: Bitmap,
        /** von oben bis hier: App öffnen */
        val headerHeight: Int,
        val rowHeight: Int,
        val rows: List<QuestRow>,
        /** Breite der Siegel-Tippfläche am rechten Rand */
        val sealZone: Int,
        val summary: String,
    )

    fun questScroll(state: AppState, w: Int, h: Int): QuestScroll {
        val game = state.game
        val list = game.board.overdue + game.board.open
        val padX = 6 * u
        val top = 9 * u
        val bottom = h - 8 * u
        val title = Ink(boldFace, 15f, Pal.ink)
        val sub = Ink(textFace, 12f, Pal.inkSoft)
        val glutInk = Ink(numberFace, 20f, Pal.ember3)
        val streakInk = Ink(numberFace, 20f, Pal.ink)
        val points = Ink(numberFace, 18f, Pal.gold3)
        val note = Ink(textFace, 13f, Pal.inkSoft)
        val kIcon = k(18f)
        val kSeal = k(30f)
        val kStat = k(16f)
        val iconPx = 16 * kIcon
        val sealPx = 16 * kSeal
        val statPx = 16 * kStat

        val glut = Fmt.n(game.player.glut)
        val streak = game.player.streak.toString()
        val statsW = statPx + u + glutInk.width(glut) + 4 * u + statPx + u + streakInk.width(streak)
        val head = heading("Questlog", 19f, Pal.ink, w - 2 * padX - statsW - 4 * u)
        val headH = maxOf(head.height * u, statPx, glutInk.height)
        val dividerY = top + headH + 2 * u
        val rowsTop = dividerY + 3 * u
        val rowH = max(sealPx, title.height + sub.height + u) + 2 * u
        val avail = bottom - rowsTop
        var n = min(list.size, min(6, max(0, avail / rowH)))
        val moreH = note.height + u
        // Passen nicht alle hin, kommt unten „und n weitere“. Dafür notfalls eine Zeile weniger.
        if (list.size > n && n > 1 && n * rowH + moreH > avail) n -= 1
        val shown = list.take(n)
        val hidden = list.size - n
        val showMore = hidden > 0 && shown.isNotEmpty() && shown.size * rowH + moreH <= avail
        val sealX = w - padX - sealPx

        val bitmap = draw(w, h) { c ->
            scrollFrame(seed = 3)
            // Kopf: Titel links, Glut und Serie rechts
            image(head, padX, top + (headH - head.height * u) / 2)
            var x = w - padX - streakInk.width(streak)
            streakInk.draw(c, streak, x, top + (headH - streakInk.height) / 2)
            x -= u + statPx
            icon("shield", x, top + (headH - statPx) / 2, kStat)
            x -= 4 * u + glutInk.width(glut)
            glutInk.draw(c, glut, x, top + (headH - glutInk.height) / 2)
            x -= u + statPx
            icon("flame", x, top + (headH - statPx) / 2, kStat)
            dots(padX, w - padX, dividerY, Pal.parch3)

            if (list.isEmpty()) {
                val next = game.board.early.minByOrNull { it.opensIn ?: Int.MAX_VALUE }
                val lines = when {
                    game.quests.isEmpty() -> listOf("Noch keine Quests.", "Tippen, um die erste anzulegen.")
                    next != null -> listOf("Alles erledigt. Die Glut brennt.", "Als Nächstes: „${next.quest.title}“ " + Fmt.window(next))
                    else -> listOf("Alles erledigt.", "Die Glut brennt.")
                }
                val blockH = statPx * 2 + u + lines.size * note.height
                var y = rowsTop + max(0, (bottom - rowsTop - blockH) / 2)
                icon("flame", (w - 2 * statPx) / 2, y, kStat * 2)
                y += statPx * 2 + u
                for (line in lines) {
                    val t = note.fit(line, w - 2 * padX)
                    note.draw(c, t, (w - note.width(t)) / 2, y)
                    y += note.height
                }
            }

            shown.forEachIndexed { i, qs ->
                val y0 = rowsTop + i * rowH
                val cy = y0 + rowH / 2
                icon(Categories.of(qs.quest.category).icon, padX, cy - iconPx / 2, kIcon)
                icon("seal", sealX, cy - sealPx / 2, kSeal)
                val pts = "+" + Fmt.n(qs.preview)
                val ptsX = sealX - 2 * u - points.width(pts)
                points.draw(c, pts, ptsX, cy - points.height / 2)
                val textX = padX + iconPx + 2 * u
                val textW = ptsX - 2 * u - textX
                val ty = cy - (title.height + sub.height) / 2
                title.draw(c, title.fit(qs.quest.title, textW), textX, ty)
                val window = Fmt.window(qs) + if (qs.isBounty) " · Kopfgeld ×2" else ""
                val subInk = if (qs.status == WindowStatus.OVERDUE) Ink(textFace, 12f, Pal.blood1) else Ink(textFace, 12f, Pal.leaf2)
                subInk.draw(c, subInk.fit(window, textW), textX, ty + title.height)
                if (i < shown.size - 1) dots(textX, w - padX, y0 + rowH - u, Pal.parch3.copy(alpha = 0.6f))
            }
            if (showMore) {
                val t = if (hidden == 1) "und eine weitere Quest" else "und $hidden weitere Quests"
                note.draw(c, t, padX + iconPx + 2 * u, rowsTop + shown.size * rowH + u)
            }
        }
        val summary = buildString {
            append("Questlog. Glut $glut, Serie $streak Tage. ")
            append(if (list.isEmpty()) "Alles erledigt." else "${list.size} ${if (list.size == 1) "Quest wartet" else "Quests warten"}.")
        }
        return QuestScroll(
            bitmap = bitmap,
            headerHeight = rowsTop,
            rowHeight = rowH,
            rows = shown.map { QuestRow(it.quest.id, it.quest.title, "${it.quest.title}, ${Fmt.window(it)}") },
            sealZone = w - (sealX - 2 * u),
            summary = summary,
        )
    }

    // ---------------------------------------------------------------- Glutrolle

    class GlutScroll(val bitmap: Bitmap, val summary: String)

    fun glutScroll(state: AppState, w: Int, h: Int): GlutScroll {
        val game = state.game
        val p = game.player
        val padX = 6 * u
        val top = 9 * u
        val bottom = h - 8 * u
        val inner = w - 2 * padX
        val glut = Fmt.n(p.glut)
        val streak = p.streak.toString()
        val kStat = k(20f)
        val statPx = 16 * kStat
        var glutInk = Ink(numberFace, 28f, Pal.ember3)
        var streakInk = Ink(numberFace, 22f, Pal.ink)
        if (statPx * 2 + 4 * u + glutInk.width(glut) + streakInk.width(streak) > inner) {
            glutInk = Ink(numberFace, 22f, Pal.ember3)
            streakInk = Ink(numberFace, 18f, Pal.ink)
        }
        val label = Ink(labelFace, 10f, Pal.inkSoft, spacing = 0.06f)
        val text = Ink(textFace, 12f, Pal.ink)
        val s = game.season
        val (line, fromAi) = greeting(state.save, game)
        val persona = Persona.active(state.save.settings)
        val voice = listOfNotNull(if (fromAi) "Spielleiter" else null, if (persona != Persona.CHRONIST) persona.title else null).joinToString(" · ")

        val bitmap = draw(w, h) { c ->
            scrollFrame(seed = 11)
            val rowH = maxOf(statPx, glutInk.height)
            var y = top
            icon("flame", padX, y + (rowH - statPx) / 2, kStat)
            glutInk.draw(c, glut, padX + statPx + u, y + (rowH - glutInk.height) / 2)
            val sx = w - padX - streakInk.width(streak)
            streakInk.draw(c, streak, sx, y + (rowH - streakInk.height) / 2)
            icon("shield", sx - u - statPx, y + (rowH - statPx) / 2, kStat)
            y += rowH + u
            label.draw(c, label.fit("STUFE ${p.level} · ${p.rank.name.uppercase()}", inner), padX, y)
            y += label.height + 2 * u
            if (y + 6 * u + label.height <= bottom) {
                y += bar(padX, y, inner, s.progress, Bars.gold, if (s.goal > 0) s.ghostXp.toFloat() / s.goal else null) + u
                label.draw(c, label.fit("WOCHE ${Fmt.n(s.xp)} / ${Fmt.n(s.goal)}", inner), padX, y)
                y += label.height + 2 * u
            }
            // Ein Wort vom Spielleiter oder Erzähler, wenn Platz ist
            val lines = (bottom - y - (if (voice.isNotEmpty()) label.height else 0)) / text.height
            if (lines >= 2) {
                dots(padX, w - padX, y, Pal.parch3)
                y += 2 * u
                if (voice.isNotEmpty()) {
                    label.draw(c, label.fit(voice.uppercase(), inner), padX, y)
                    y += label.height
                }
                val layout = text.layout(line, inner, min(lines, 6))
                c.save()
                c.translate(padX.toFloat(), y.toFloat())
                layout.draw(c)
                c.restore()
            }
        }
        val summary = "Questlog. Glut $glut, Serie $streak Tage, Stufe ${p.level}. Woche ${Fmt.n(s.xp)} von ${Fmt.n(s.goal)} Punkten. $line"
        return GlutScroll(bitmap, summary)
    }
}
