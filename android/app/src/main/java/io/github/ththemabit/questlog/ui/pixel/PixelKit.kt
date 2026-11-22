package io.github.ththemabit.questlog.ui.pixel

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.ththemabit.questlog.ui.theme.Pal
import io.github.ththemabit.questlog.ui.theme.Q
import kotlin.math.max
import kotlin.math.roundToInt

/** Größe eines Kunst-Pixels in echten Bildschirmpixeln (immer ganzzahlig, damit alles scharf bleibt). */
val LocalPx = staticCompositionLocalOf { 4f }

fun pixelUnit(density: Float): Float = max(2f, (density * 1.75f).roundToInt().toFloat())

// ---------------------------------------------------------------- Icons

object IconCache {
    private val cache = HashMap<String, ImageBitmap>()

    fun get(name: String): ImageBitmap = cache.getOrPut(name) {
        val grid = IconArt.grid(name) ?: IconArt.grid("star")!!
        val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        for (y in 0 until 16) {
            val row = grid.getOrNull(y) ?: continue
            for (x in 0 until 16) {
                val c = row.getOrNull(x) ?: '.'
                if (c == '.') continue
                bmp.setPixel(x, y, (IconArt.palette[c] ?: 0xFFFF00FF).toInt())
            }
        }
        bmp.asImageBitmap()
    }
}

/** Pixel-Icon, immer in ganzzahliger Vergrößerung gezeichnet. */
@Composable
fun PixelIcon(name: String, size: Dp = 24.dp, modifier: Modifier = Modifier, alpha: Float = 1f, tint: Color? = null, description: String? = null) {
    val density = LocalDensity.current
    val k = max(1, (with(density) { size.toPx() } / 16f).roundToInt())
    val img = remember(name) { IconCache.get(name) }
    val side = with(density) { (16 * k).toDp() }
    val semantic = if (description != null) Modifier.semantics { contentDescription = description } else Modifier
    Canvas(modifier.size(side).then(semantic)) {
        drawImage(
            img, srcOffset = IntOffset.Zero, srcSize = IntSize(16, 16), dstOffset = IntOffset.Zero, dstSize = IntSize(16 * k, 16 * k),
            alpha = alpha, colorFilter = tint?.let { ColorFilter.tint(it, BlendMode.SrcIn) }, filterQuality = FilterQuality.None,
        )
    }
}

// ---------------------------------------------------------------- Texturen

/** Kachel mit leichtem Rauschen, in Gerätepixeln vorgerendert (u px pro Kunst-Pixel). */
object Textures {
    private val cache = HashMap<String, ImageBitmap>()

    fun noise(key: String, u: Float, base: Color, specks: List<Pair<Color, Int>>, tile: Int = 24): ImageBitmap =
        cache.getOrPut("$key-$u") {
            val p = u.roundToInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(tile * p, tile * p, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            canvas.drawColor(base.toArgb())
            val paint = android.graphics.Paint()
            var s = key.hashCode().toLong() * 31 + 17
            fun rnd(): Int {
                s = s * 6364136223846793005L + 1442695040888963407L
                return ((s ushr 33) % 1000).toInt()
            }
            for (y in 0 until tile) for (x in 0 until tile) {
                val r = rnd()
                var acc = 0
                for ((c, permille) in specks) {
                    acc += permille
                    if (r < acc) {
                        paint.color = c.toArgb()
                        canvas.drawRect((x * p).toFloat(), (y * p).toFloat(), ((x + 1) * p).toFloat(), ((y + 1) * p).toFloat(), paint)
                        break
                    }
                }
            }
            bmp.asImageBitmap()
        }

    /** Dunkle Kerkermauer: Ziegel mit Fugen, sehr wenig Kontrast. */
    fun wall(u: Float): ImageBitmap = cache.getOrPut("wall-$u") {
        val p = u.roundToInt().coerceAtLeast(1)
        val w = 32
        val h = 16
        val bmp = Bitmap.createBitmap(w * p, h * p, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        canvas.drawColor(Pal.night1.toArgb())
        val paint = android.graphics.Paint()
        fun px(x: Int, y: Int, c: Color) {
            paint.color = c.toArgb()
            canvas.drawRect((x * p).toFloat(), (y * p).toFloat(), ((x + 1) * p).toFloat(), ((y + 1) * p).toFloat(), paint)
        }
        var s = 99L
        fun rnd(n: Int): Int {
            s = s * 6364136223846793005L + 1442695040888963407L
            return ((s ushr 33) % n).toInt()
        }
        for (y in 0 until h) for (x in 0 until w) {
            val row = y / 8
            val off = if (row % 2 == 0) 0 else 8
            val mortarH = y % 8 == 7
            val mortarV = (x + off) % 16 == 15
            when {
                mortarH || mortarV -> px(x, y, Pal.night0)
                y % 8 == 0 -> px(x, y, Pal.night2)
                rnd(100) < 7 -> px(x, y, if (rnd(2) == 0) Pal.night2 else Pal.night0)
            }
        }
        bmp.asImageBitmap()
    }
}

fun DrawScope.tiled(img: ImageBitmap, topLeft: Offset = Offset.Zero, size: Size = this.size, alpha: Float = 1f) {
    drawRect(ShaderBrush(ImageShader(img, TileMode.Repeated, TileMode.Repeated)), topLeft, size, alpha = alpha)
}

/** Hintergrund: Kerkermauer. */
fun Modifier.dungeonWall(u: Float): Modifier = drawBehind { tiled(Textures.wall(u)) }

// ---------------------------------------------------------------- Rahmen

data class Frame(val fill: Color, val light: Color, val dark: Color, val edge: Color = Pal.edge, val rivet: Color? = null)

object Frames {
    val stone = Frame(Pal.stone1, Pal.stone3, Pal.stone0, rivet = Pal.gold2)
    val stoneFlat = Frame(Pal.stone1, Pal.stone2, Pal.stone0)
    val stoneDark = Frame(Pal.night2, Pal.stone2, Pal.night0)
    val wood = Frame(Pal.wood1, Pal.wood2, Pal.wood0, rivet = Pal.gold1)
    val gold = Frame(Pal.gold1, Pal.gold0, Pal.gold2)
    val blood = Frame(Pal.blood1, Pal.blood0, Pal.blood2)
    val leaf = Frame(Pal.leaf1, Pal.leaf0, Pal.leaf2)
    val mana = Frame(Pal.mana1, Pal.mana0, Pal.mana2)
    val violet = Frame(Pal.violet1, Pal.violet0, Pal.violet2)
    val ember = Frame(Pal.ember2, Pal.ember1, Pal.ember3)
}

/** Kantiger Rahmen mit Fase (Licht oben links), Ecken ausgespart, optional Goldnieten. */
fun DrawScope.bevel(u: Float, f: Frame, pressed: Boolean = false, texture: ImageBitmap? = null) {
    val w = size.width
    val h = size.height
    if (w < 4 * u || h < 4 * u) return
    drawRect(f.fill, Offset(u, u), Size(w - 2 * u, h - 2 * u))
    if (texture != null) tiled(texture, Offset(u, u), Size(w - 2 * u, h - 2 * u))
    val top = if (pressed) f.dark else f.light
    val bottom = if (pressed) f.light else f.dark
    drawRect(top, Offset(u, u), Size(w - 2 * u, u))
    drawRect(top, Offset(u, u), Size(u, h - 2 * u))
    drawRect(bottom, Offset(u, h - 2 * u), Size(w - 2 * u, u))
    drawRect(bottom, Offset(w - 2 * u, u), Size(u, h - 2 * u))
    drawRect(f.edge, Offset(u, 0f), Size(w - 2 * u, u))
    drawRect(f.edge, Offset(u, h - u), Size(w - 2 * u, u))
    drawRect(f.edge, Offset(0f, u), Size(u, h - 2 * u))
    drawRect(f.edge, Offset(w - u, u), Size(u, h - 2 * u))
    f.rivet?.let { r ->
        if (w > 16 * u && h > 12 * u) {
            for ((x, y) in listOf(3 * u to 3 * u, w - 4 * u to 3 * u, 3 * u to h - 4 * u, w - 4 * u to h - 4 * u)) {
                drawRect(r, Offset(x, y), Size(u, u))
            }
        }
    }
}

fun Modifier.framed(f: Frame, u: Float, pressed: Boolean = false, texture: ImageBitmap? = null): Modifier =
    drawBehind { bevel(u, f, pressed, texture) }

/** Steinplatte mit Rauschen. */
@Composable
fun Modifier.stonePanel(frame: Frame = Frames.stone): Modifier {
    val u = LocalPx.current
    val tex = remember(u, frame) {
        Textures.noise("stone-${frame.fill.toArgb()}", u, frame.fill, listOf(frame.light.copy(alpha = 0.35f) to 18, frame.dark to 40))
    }
    return this.drawBehind { bevel(u, frame, texture = tex) }
}

// ---------------------------------------------------------------- Pergament

/**
 * Pergament mit gerissenen Kanten, Flecken und (je nach Staubstufe 0..3) Staub, Spinnweben und Rissen (W09).
 * Die Form hängt vom Seed ab, damit jede Karte etwas anders aussieht, aber stabil bleibt.
 */
fun DrawScope.parchment(u: Float, seed: Int, tex: ImageBitmap?, dust: Int = 0, tint: Color? = null) {
    val w = size.width
    val h = size.height
    if (w < 6 * u || h < 6 * u) return
    val cols = (w / u).toInt()
    val rows = (h / u).toInt()
    var s = seed.toLong() * 2654435761L + 12345
    fun rnd(n: Int): Int {
        s = s * 6364136223846793005L + 1442695040888963407L
        return ((s ushr 33) % n).toInt()
    }
    // Grundfläche
    drawRect(Pal.parch1, Offset(u, u), Size(w - 2 * u, h - 2 * u))
    if (tex != null) tiled(tex, Offset(u, u), Size(w - 2 * u, h - 2 * u))
    tint?.let { drawRect(it, Offset(u, u), Size(w - 2 * u, h - 2 * u)) }
    // Licht und Schatten
    drawRect(Pal.parch0, Offset(2 * u, u), Size(w - 4 * u, u))
    drawRect(Pal.parch0, Offset(u, 2 * u), Size(u, h - 4 * u))
    drawRect(Pal.parch2, Offset(2 * u, h - 2 * u), Size(w - 4 * u, u))
    drawRect(Pal.parch2, Offset(w - 2 * u, 2 * u), Size(u, h - 4 * u))
    // Kanten mit Kerben
    fun edgeH(y: Float, inward: Float) {
        var x = 2
        while (x < cols - 2) {
            if (rnd(17) == 0) {
                drawRect(Pal.parch3, Offset(x * u, y + inward), Size(u, u))
            } else {
                drawRect(Pal.parch3, Offset(x * u, y), Size(u, u))
            }
            x++
        }
    }
    fun edgeV(x: Float, inward: Float) {
        var y = 2
        while (y < rows - 2) {
            if (rnd(13) == 0) {
                drawRect(Pal.parch3, Offset(x + inward, y * u), Size(u, u))
            } else {
                drawRect(Pal.parch3, Offset(x, y * u), Size(u, u))
            }
            y++
        }
    }
    edgeH(0f, u)
    edgeH(h - u, -u)
    edgeV(0f, u)
    edgeV(w - u, -u)
    // abgegriffene Ecken
    for ((cx, cy) in listOf(u to u, w - 2 * u to u, u to h - 2 * u, w - 2 * u to h - 2 * u)) {
        drawRect(Pal.parch3, Offset(cx, cy), Size(u, u))
    }
    // Flecken
    repeat(2) {
        val fx = 4 + rnd(max(1, cols - 10))
        val fy = 3 + rnd(max(1, rows - 7))
        drawRect(Pal.parch2.copy(alpha = 0.55f), Offset(fx * u, fy * u), Size(3 * u, 2 * u))
        drawRect(Pal.parch2.copy(alpha = 0.55f), Offset((fx + 1) * u, (fy + 2) * u), Size(u, u))
    }
    // Brandspuren
    if (rnd(3) == 0) {
        val bx = 3 + rnd(max(1, cols - 6))
        drawRect(Pal.burn, Offset(bx * u, h - u), Size(2 * u, u))
    }
    if (dust >= 1) {
        val count = cols * rows / (if (dust >= 3) 10 else if (dust == 2) 18 else 35)
        repeat(count) {
            val dx = 2 + rnd(max(1, cols - 4))
            val dy = 2 + rnd(max(1, rows - 4))
            drawRect(Pal.stone4.copy(alpha = 0.45f), Offset(dx * u, dy * u), Size(u, u))
        }
    }
    if (dust >= 2) {
        // Spinnweben oben rechts
        val web = Pal.boneDim.copy(alpha = 0.75f)
        for (i in 0 until 9) {
            drawRect(web, Offset(w - (2 + i) * u, (1 + i) * u), Size(u, u))
            if (i % 3 == 0) {
                drawRect(web, Offset(w - (2 + i) * u, u), Size(u, i * u))
                drawRect(web, Offset(w - (2 + i + 1) * u, (1 + i) * u), Size(i * u, u))
            }
        }
    }
    if (dust >= 3) {
        // Riss
        var cx = (cols * 0.3f).toInt()
        var cy = 1
        while (cy < rows - 1) {
            drawRect(Pal.inkFaint, Offset(cx * u, cy * u), Size(u, u))
            cy++
            cx += if (rnd(3) == 0) 1 else if (rnd(3) == 0) -1 else 0
        }
    }
}

@Composable
fun parchmentTexture(): ImageBitmap {
    val u = LocalPx.current
    return remember(u) {
        Textures.noise("parch", u, Pal.parch1, listOf(Pal.parch0 to 26, Pal.parch2 to 22, Pal.parch3.copy(alpha = 0.4f) to 3))
    }
}

@Composable
fun Modifier.parchmentCard(seed: Int, dust: Int = 0, tint: Color? = null): Modifier {
    val u = LocalPx.current
    val tex = parchmentTexture()
    return this.drawBehind { parchment(u, seed, tex, dust, tint) }
}

/** Holzstab einer Schriftrolle (oben/unten). */
@Composable
fun ScrollRod(modifier: Modifier = Modifier) {
    val u = LocalPx.current
    Canvas(modifier.fillMaxWidth().height(with(LocalDensity.current) { (7 * u).toDp() })) {
        val w = size.width
        // Papierrolle
        val rows = listOf(Pal.edge, Pal.parch0, Pal.parch1, Pal.parch2, Pal.parch3, Pal.burn, Pal.edge)
        rows.forEachIndexed { i, c -> drawRect(c, Offset(3 * u, i * u), Size(w - 6 * u, u)) }
        // Knäufe
        for (x in listOf(0f, w - 3 * u)) {
            drawRect(Pal.edge, Offset(x, u), Size(3 * u, 5 * u))
            drawRect(Pal.wood2, Offset(x + u * 0, 2 * u), Size(3 * u, u))
            drawRect(Pal.wood1, Offset(x, 3 * u), Size(3 * u, 2 * u))
            drawRect(Pal.gold1, Offset(x + u, 2 * u), Size(u, u))
        }
    }
}

/** Große Schriftrolle mit Stäben oben und unten. */
@Composable
fun ScrollPanel(modifier: Modifier = Modifier, seed: Int = 7, content: @Composable () -> Unit) {
    val u = LocalPx.current
    val d = LocalDensity.current
    Column(modifier) {
        ScrollRod()
        Box(
            Modifier.fillMaxWidth().padding(horizontal = with(d) { (2 * u).toDp() }).offset(y = with(d) { (-u).toDp() })
                .parchmentCard(seed)
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) { content() }
        Box(Modifier.offset(y = with(d) { (-2 * u).toDp() })) { ScrollRod() }
    }
}

// ---------------------------------------------------------------- Knöpfe

enum class Btn(val frame: Frame, val text: Color) {
    GOLD(Frames.gold, Pal.ink),
    STONE(Frames.stoneFlat, Pal.bone),
    BLOOD(Frames.blood, Pal.bone),
    LEAF(Frames.leaf, Pal.ink),
    MANA(Frames.mana, Pal.ink),
    VIOLET(Frames.violet, Pal.bone),
    EMBER(Frames.ember, Pal.ink),
}

@Composable
fun PixelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: Btn = Btn.GOLD,
    icon: String? = null,
    enabled: Boolean = true,
    small: Boolean = false,
    textStyle: TextStyle? = null,
) {
    val u = LocalPx.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val frame = if (enabled) kind.frame else Frames.stoneDark
    val color = if (enabled) kind.text else Pal.boneFaint
    val shift = if (pressed && enabled) with(LocalDensity.current) { u.toDp() } else 0.dp
    Box(
        modifier
            .defaultMinSize(minHeight = if (small) 40.dp else 48.dp)
            .semantics { contentDescription = text }
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .drawBehind { bevel(u, frame, pressed && enabled) }
            .padding(horizontal = if (small) 10.dp else 14.dp, vertical = if (small) 6.dp else 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.offset(y = shift),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) PixelIcon(icon, if (small) 16.dp else 20.dp, alpha = if (enabled) 1f else 0.5f)
            if (text.isNotEmpty()) {
                BasicText(
                    text.uppercase(),
                    style = (textStyle ?: Q.type.label.copy(fontSize = if (small) Q.type.label.fontSize else Q.type.label.fontSize * 1.15f))
                        .copy(color = color, textAlign = TextAlign.Center),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Runder Icon-Knopf (Wachssiegel, Würfel …) ohne Rahmen. */
@Composable
fun IconTap(icon: String, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 32.dp, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier.size(48.dp).semantics { contentDescription = description }
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PixelIcon(icon, size, Modifier.offset(y = if (pressed) 2.dp else 0.dp), alpha = if (enabled) 1f else 0.4f)
    }
}

// ---------------------------------------------------------------- Balken

data class BarColors(val light: Color, val mid: Color, val dark: Color)

object Bars {
    val gold = BarColors(Pal.gold0, Pal.gold1, Pal.gold2)
    val ember = BarColors(Pal.ember0, Pal.ember1, Pal.ember2)
    val leaf = BarColors(Pal.leaf0, Pal.leaf1, Pal.leaf2)
    val mana = BarColors(Pal.mana0, Pal.mana1, Pal.mana2)
    val blood = BarColors(Pal.blood0, Pal.blood1, Pal.blood2)
    val violet = BarColors(Pal.violet0, Pal.violet1, Pal.violet2)
}

@Composable
fun PixelBar(progress: Float, colors: BarColors, modifier: Modifier = Modifier, artHeight: Int = 4, marker: Float? = null, ghost: Float? = null) {
    val u = LocalPx.current
    val h = with(LocalDensity.current) { ((artHeight + 2) * u).toDp() }
    Canvas(modifier.fillMaxWidth().height(h)) {
        val w = size.width
        val innerW = w - 2 * u
        drawRect(Pal.edge, Offset(u, 0f), Size(w - 2 * u, size.height))
        drawRect(Pal.edge, Offset(0f, u), Size(w, size.height - 2 * u))
        drawRect(Pal.night0, Offset(u, u), Size(innerW, artHeight * u))
        val fillW = ((innerW / u) * progress.coerceIn(0f, 1f)).toInt() * u
        if (fillW > 0) {
            drawRect(colors.mid, Offset(u, u), Size(fillW, artHeight * u))
            drawRect(colors.light, Offset(u, u), Size(fillW, u))
            drawRect(colors.dark, Offset(u, (artHeight) * u), Size(fillW, u))
        }
        ghost?.let { g ->
            val gx = u + ((innerW / u) * g.coerceIn(0f, 1f)).toInt() * u
            drawRect(Pal.bone.copy(alpha = 0.55f), Offset(gx - u, 0f), Size(u, size.height))
        }
        marker?.let { m ->
            val mx = u + ((innerW / u) * m.coerceIn(0f, 1f)).toInt() * u
            drawRect(Pal.bone, Offset(mx - u, 0f), Size(u, size.height))
        }
    }
}

// ---------------------------------------------------------------- Text & Deko

@Composable
fun T(text: String, style: TextStyle, modifier: Modifier = Modifier, color: Color? = null, maxLines: Int = Int.MAX_VALUE, align: TextAlign? = null) {
    BasicText(
        text, modifier,
        style = style.let { s -> if (color != null) s.copy(color = color) else s }.let { s -> if (align != null) s.copy(textAlign = align) else s },
        maxLines = maxLines, overflow = TextOverflow.Ellipsis,
    )
}

/** Abschnitts-Überschrift in Fraktur mit Ornament-Linie aus Goldpunkten. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, icon: String? = null, count: Int? = null, color: Color = Pal.gold0) {
    val u = LocalPx.current
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            PixelIcon(icon, 20.dp)
            Box(Modifier.width(8.dp))
        }
        H(text, Q.type.heading, color = color)
        if (count != null) {
            Box(Modifier.width(8.dp))
            T(count.toString(), Q.type.number.copy(fontSize = Q.type.number.fontSize * 0.8f), color = Pal.boneDim)
        }
        Box(Modifier.width(10.dp))
        Canvas(Modifier.weight(1f).height(with(LocalDensity.current) { (3 * u).toDp() })) {
            var x = 0f
            var i = 0
            while (x < size.width) {
                val c = if (i % 4 == 0) Pal.gold2 else Pal.stone3
                drawRect(c, Offset(x, u), Size(u, u))
                x += 2 * u
                i++
            }
        }
    }
}

/** Kleines Etikett (z. B. „×2 KOPFGELD“). */
@Composable
fun Tag(text: String, frame: Frame, textColor: Color, modifier: Modifier = Modifier, icon: String? = null) {
    val u = LocalPx.current
    Row(
        modifier.drawBehind { bevel(u, frame) }.padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) PixelIcon(icon, 14.dp)
        T(text.uppercase(), Q.type.label, color = textColor, maxLines = 1)
    }
}
