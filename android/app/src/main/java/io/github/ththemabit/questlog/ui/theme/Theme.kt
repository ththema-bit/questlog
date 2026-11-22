package io.github.ththemabit.questlog.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.ththemabit.questlog.R

/** Palette: Kerkerstein, Pergament, Gold, Glut. Eigene Farben, keine Verläufe. */
object Pal {
    val night0 = Color(0xFF0D0906)
    val night1 = Color(0xFF15100B)
    val night2 = Color(0xFF1D1610)

    val edge = Color(0xFF080503)
    val stone0 = Color(0xFF221A13)
    val stone1 = Color(0xFF2E241B)
    val stone2 = Color(0xFF3B2F24)
    val stone3 = Color(0xFF54442F)
    val stone4 = Color(0xFF6E5A40)

    val wood0 = Color(0xFF3B2414)
    val wood1 = Color(0xFF5A3820)
    val wood2 = Color(0xFF7A4E2C)
    val wood3 = Color(0xFF9C6A3C)

    val parch0 = Color(0xFFF4E6C1)
    val parch1 = Color(0xFFE6CF98)
    val parch2 = Color(0xFFD1B375)
    val parch3 = Color(0xFFAA884C)
    val burn = Color(0xFF6E4E26)

    val ink = Color(0xFF2A1709)
    val inkSoft = Color(0xFF5B3A1E)
    val inkFaint = Color(0xFF8A6A42)

    val gold0 = Color(0xFFFFE28F)
    val gold1 = Color(0xFFF2B636)
    val gold2 = Color(0xFFB97A1C)
    val gold3 = Color(0xFF6E440D)

    val ember0 = Color(0xFFFFF0A0)
    val ember1 = Color(0xFFFFC23D)
    val ember2 = Color(0xFFFF7A1A)
    val ember3 = Color(0xFFC2380E)

    val blood0 = Color(0xFFE05A3C)
    val blood1 = Color(0xFFA62B1C)
    val blood2 = Color(0xFF5E120C)

    val leaf0 = Color(0xFF9BD068)
    val leaf1 = Color(0xFF5E9A3A)
    val leaf2 = Color(0xFF2F5A22)

    val mana0 = Color(0xFF8EC9F0)
    val mana1 = Color(0xFF4A86C4)
    val mana2 = Color(0xFF23477A)

    val violet0 = Color(0xFFC9A0F0)
    val violet1 = Color(0xFF8A55C8)
    val violet2 = Color(0xFF4A2A78)

    val bone = Color(0xFFEEDFBF)
    val boneDim = Color(0xFFB39C77)
    val boneFaint = Color(0xFF7C6A50)
}

object Fonts {
    val fraktur = FontFamily(Font(R.font.jacquard24))
    val frakturBig = FontFamily(Font(R.font.jacquard12))

    @OptIn(ExperimentalTextApi::class)
    val pixel = FontFamily(
        Font(R.font.pixelify_sans, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
        Font(R.font.pixelify_sans, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
        Font(R.font.pixelify_sans, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
        Font(R.font.pixelify_sans, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    )
    val numbers = FontFamily(Font(R.font.jersey10))
    val label = FontFamily(Font(R.font.silkscreen))
    val comic = FontFamily(Font(R.font.comic_neue_bold))
}

@Immutable
data class QType(
    val logo: TextStyle,
    val title: TextStyle,
    val heading: TextStyle,
    val quest: TextStyle,
    val body: TextStyle,
    val bodyBold: TextStyle,
    val small: TextStyle,
    val label: TextStyle,
    val number: TextStyle,
    val numberBig: TextStyle,
    val numberHuge: TextStyle,
)

private fun style(family: FontFamily, size: TextUnit, line: TextUnit, weight: FontWeight = FontWeight.Normal, spacing: TextUnit = 0.sp) =
    TextStyle(fontFamily = family, fontSize = size, lineHeight = line, fontWeight = weight, letterSpacing = spacing, color = Pal.bone)

fun qType(cursed: Boolean): QType {
    val fr = if (cursed) Fonts.comic else Fonts.fraktur
    val frBig = if (cursed) Fonts.comic else Fonts.frakturBig
    val px = if (cursed) Fonts.comic else Fonts.pixel
    val num = if (cursed) Fonts.comic else Fonts.numbers
    val lab = if (cursed) Fonts.comic else Fonts.label
    return QType(
        logo = style(frBig, 46.sp, 46.sp),
        title = style(fr, 32.sp, 34.sp),
        heading = style(fr, 25.sp, 28.sp),
        quest = style(px, 17.sp, 21.sp, FontWeight.SemiBold),
        body = style(px, 15.sp, 21.sp),
        bodyBold = style(px, 15.sp, 21.sp, FontWeight.SemiBold),
        small = style(px, 13.sp, 17.sp),
        label = style(lab, 10.sp, 13.sp, spacing = 0.06.em),
        number = style(num, 26.sp, 26.sp),
        numberBig = style(num, 40.sp, 40.sp),
        numberHuge = style(num, 72.sp, 72.sp),
    )
}

val LocalQType = staticCompositionLocalOf { qType(false) }

object Q {
    val type: QType
        @Composable get() = LocalQType.current
}

@Composable
fun QuestlogTheme(cursed: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalQType provides qType(cursed), content = content)
}
