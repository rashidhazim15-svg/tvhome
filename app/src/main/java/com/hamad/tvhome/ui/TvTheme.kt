@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.hamad.tvhome.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hamad.tvhome.R

/**
 * All measurements in this app are authored in tvOS points (the values in Apple's
 * HIG for tvOS) and converted at runtime. On the tvOS reference canvas the screen is
 * 1920x1080 pt. Android TV boxes vary in resolution and density, so one point maps to
 * `screenHeightDp / 1080` dp. On the project's reference device (1080p, 320 dpi,
 * 540 dp tall) this is exactly 0.5 dp per point, i.e. `dp = pt / 2` as specified.
 */
@Immutable
data class TvMetrics(val dpPerPt: Float) {
    fun dp(points: Float): Dp = (points * dpPerPt).dp
    fun dp(points: Int): Dp = (points * dpPerPt).dp
    fun sp(points: Float): TextUnit = (points * dpPerPt).sp
    fun px(points: Float): Float = points * dpPerPt
}

val LocalTvMetrics: ProvidableCompositionLocal<TvMetrics> = staticCompositionLocalOf {
    TvMetrics(0.5f)
}

val LocalTvType = compositionLocalOf<TvType> { TvType(TvMetrics(0.5f)) }

object TvColor {
    val background = Color(0xFF000000)
    val elevated = Color(0xFF0C0C0E)

    val label = Color.White
    val labelSecondary = Color.White.copy(alpha = 0.78f)
    val labelTertiary = Color.White.copy(alpha = 0.58f)
    val labelQuaternary = Color.White.copy(alpha = 0.40f)

    val systemBlue = Color(0xFF0A84FF)
    val systemGreen = Color(0xFF30D158)
    val systemIndigo = Color(0xFF5E5CE6)
    val systemOrange = Color(0xFFFF9F0A)
    val systemPink = Color(0xFFFF375F)
    val systemPurple = Color(0xFFBF5AF2)
    val systemRed = Color(0xFFFF453A)
    val systemTeal = Color(0xFF40C8E0)
    val systemYellow = Color(0xFFFFD60A)
    val systemGray = Color(0xFF8E8E93)
    val systemGraphite = Color(0xFF636366)
    val systemCyan = Color(0xFF32D7E8)
    val systemMint = Color(0xFF63E6BE)
    val systemBrown = Color(0xFFAC8E68)

    val focusPlatter = Color.White
    val focusText = Color.Black
}

enum class TvTextRole {
    Title1,
    Title2,
    Title3,
    Headline,
    Subtitle1,
    Callout,
    Body,
    Caption1,
    Caption2,
}

@Immutable
class TvType(metrics: TvMetrics) {
    private val m = metrics

    // tvOS built-in text styles: size / leading / weight, with the tvOS tracking
    // table applied at each point size (tracking.txt: 76 = +0.07 pt, 29 = +0.40 pt ...).
    val title1: TextStyle = style(76f, 96f, FontWeight.Medium, 0.07f, m)
    val title2: TextStyle = style(57f, 66f, FontWeight.Medium, 0.29f, m)
    val title3: TextStyle = style(48f, 56f, FontWeight.Medium, 0.35f, m)
    val headline: TextStyle = style(38f, 46f, FontWeight.Medium, 0.37f, m)
    val subtitle1: TextStyle = style(38f, 46f, FontWeight.Normal, 0.37f, m)
    val callout: TextStyle = style(31f, 38f, FontWeight.Medium, 0.39f, m)
    val body: TextStyle = style(29f, 36f, FontWeight.Medium, 0.40f, m)
    val caption1: TextStyle = style(25f, 32f, FontWeight.Medium, 0.15f, m)
    val caption2: TextStyle = style(23f, 30f, FontWeight.Medium, -0.10f, m)

    fun role(role: TvTextRole): TextStyle = when (role) {
        TvTextRole.Title1 -> title1
        TvTextRole.Title2 -> title2
        TvTextRole.Title3 -> title3
        TvTextRole.Headline -> headline
        TvTextRole.Subtitle1 -> subtitle1
        TvTextRole.Callout -> callout
        TvTextRole.Body -> body
        TvTextRole.Caption1 -> caption1
        TvTextRole.Caption2 -> caption2
    }

    /**
     * Arabic needs its own family (IBM Plex Sans Arabic) and no Latin tracking:
     * the tvOS tracking table is tuned for SF Pro and would break Arabic joins.
     */
    fun arabic(base: TextStyle): TextStyle = base.copy(
        fontFamily = TvArabicFontFamily,
        letterSpacing = 0.sp,
    )

    fun arabic(role: TvTextRole): TextStyle = arabic(role(role))

    private fun style(
        sizePt: Float,
        linePt: Float,
        weight: FontWeight,
        trackingPt: Float,
        metrics: TvMetrics,
    ): TextStyle = TextStyle(
        color = TvColor.label,
        fontFamily = TvFontFamily,
        fontWeight = weight,
        fontSize = metrics.sp(sizePt),
        lineHeight = metrics.sp(linePt),
        letterSpacing = (trackingPt * metrics.dpPerPt).sp,
        textMotion = TextMotion.Static,
    )
}

private fun inter(weight: Int): Font = Font(
    resId = R.font.inter_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Inter stands in for SF Pro (tvOS system font); it is not redistributed as SF. */
val TvFontFamily: FontFamily = FontFamily(
    inter(300),
    inter(400),
    inter(500),
    inter(600),
    inter(700),
)

/** Arabic companion family used by the prayer experience. */
val TvArabicFontFamily: FontFamily = FontFamily(
    Font(R.font.ibm_plex_sans_arabic_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_arabic_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_arabic_semibold, FontWeight.SemiBold),
)

@Composable
@ReadOnlyComposable
fun tvType(): TvType = LocalTvType.current

@Composable
fun rememberTvMetrics(dpPerPt: Float): TvMetrics = remember(dpPerPt) { TvMetrics(dpPerPt) }
