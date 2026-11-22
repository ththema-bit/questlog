package io.github.ththemabit.questlog.ui.pixel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.core.content.res.ResourcesCompat
import io.github.ththemabit.questlog.R
import io.github.ththemabit.questlog.ui.theme.Fonts
import io.github.ththemabit.questlog.ui.theme.Pal
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Überschriften wie in alten Rollenspielen: Die Schrift wird ohne Kantenglättung in
 * Kunst-Pixeln gerastert und dann scharf vergrößert, mit einem Pixel Schlagschatten.
 * Die Schrift selbst steht nur in [HeadingRaster.fontRes] und lässt sich dort tauschen.
 */
object HeadingRaster {
    val fontRes = R.font.cinzel_decorative_bold
    private var cached: Typeface? = null

    fun typeface(context: Context): Typeface =
        cached ?: (runCatching { ResourcesCompat.getFont(context, fontRes) }.getOrNull() ?: Typeface.SERIF).also { cached = it }

    fun render(context: Context, text: String, size: Int, color: Int, shadow: Int?, width: Int, maxLines: Int, center: Boolean): ImageBitmap {
        val paint = TextPaint().apply {
            isAntiAlias = false
            isSubpixelText = false
            isFilterBitmap = false
            typeface = typeface(context)
            textSize = size.toFloat()
            this.color = color
        }
        val layoutWidth = max(1, width - 2)
        // Lange Einzelwörter nicht mitten im Wort trennen, sondern die Schrift etwas verkleinern
        val longest = text.split(' ', '\n').maxByOrNull { paint.measureText(it) } ?: text
        var s = size.toFloat()
        val min = size * 0.6f
        while (paint.measureText(longest) > layoutWidth && s > min) {
            s -= 1f
            paint.textSize = s
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, layoutWidth)
            .setAlignment(if (center) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .build()
        var lineWidth = 0f
        for (i in 0 until layout.lineCount) lineWidth = max(lineWidth, layout.getLineWidth(i))
        val w = if (center) width else (ceil(lineWidth).toInt() + 2).coerceAtMost(width)
        val h = layout.height + 1
        val bmp = Bitmap.createBitmap(max(1, w), max(1, h), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        if (shadow != null) {
            paint.color = shadow
            canvas.save()
            canvas.translate(1f, 1f)
            layout.draw(canvas)
            canvas.restore()
            paint.color = color
        }
        layout.draw(canvas)
        return bmp.asImageBitmap()
    }
}

/** Überschrift in Pixel-Fantasy-Schrift. Beim Comic-Sans-Fluch fällt sie auf normalen Text zurück. */
@Composable
fun H(text: String, style: TextStyle, modifier: Modifier = Modifier, color: Color? = null, maxLines: Int = 3, align: TextAlign? = null) {
    if (style.fontFamily == Fonts.comic) {
        T(text, style, modifier, color, maxLines, align)
        return
    }
    val u = LocalPx.current
    val density = LocalDensity.current
    val context = LocalContext.current
    val c = color ?: style.color
    val size = max(8, (with(density) { style.fontSize.toPx() } / u).roundToInt())
    val shadow = if (c.luminance() > 0.4f) Pal.edge else null
    val center = align == TextAlign.Center
    BoxWithConstraints(modifier.semantics { heading(); contentDescription = text }) {
        val maxW = if (constraints.hasBoundedWidth) constraints.maxWidth else 4000
        val artWidth = max(8, (maxW / u).toInt())
        val img = remember(text, size, c, shadow, artWidth, maxLines, center) {
            HeadingRaster.render(context, text, size, c.toArgb(), shadow?.toArgb(), artWidth, maxLines, center)
        }
        val w = (img.width * u).roundToInt()
        val h = (img.height * u).roundToInt()
        androidx.compose.foundation.Canvas(Modifier.size(with(density) { w.toDp() }, with(density) { h.toDp() })) {
            drawImage(img, srcOffset = IntOffset.Zero, srcSize = IntSize(img.width, img.height), dstOffset = IntOffset.Zero, dstSize = IntSize(w, h), filterQuality = FilterQuality.None)
        }
    }
}
