package com.n3k0chan.spotter.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Sistema tipográfico del design canvas.
 * Sans = sistema (Roboto). Mono = monospace para datos cuantitativos.
 *
 * Si más adelante quieres Geist real:
 *  - mete los .ttf en res/font/
 *  - sustituye SansFamily/MonoFamily por FontFamily(Font(R.font.geist), ...)
 */
private val SansFamily = FontFamily.Default
private val MonoFamily = FontFamily.Monospace

/** Estilos textuales 1:1 con SP_TYPE del design canvas. */
object SpotterText {
    val display = TextStyle(
        fontFamily = SansFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 56.sp, lineHeight = 1.05.em, letterSpacing = (-1.2).sp,
    )
    val title1 = TextStyle(
        fontFamily = SansFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp, lineHeight = 1.2.em, letterSpacing = (-0.4).sp,
    )
    val title2 = TextStyle(
        fontFamily = SansFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 1.25.em, letterSpacing = (-0.3).sp,
    )
    val title3 = TextStyle(
        fontFamily = SansFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp, lineHeight = 1.3.em, letterSpacing = (-0.2).sp,
    )
    val body = TextStyle(
        fontFamily = SansFamily, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 1.45.em,
    )
    val bodyMd = TextStyle(
        fontFamily = SansFamily, fontWeight = FontWeight.Medium,
        fontSize = 15.sp, lineHeight = 1.45.em,
    )
    val small = TextStyle(
        fontFamily = SansFamily, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 1.4.em,
    )
    val smallMd = TextStyle(
        fontFamily = SansFamily, fontWeight = FontWeight.Medium,
        fontSize = 13.sp, lineHeight = 1.4.em,
    )
    val label = TextStyle(
        fontFamily = SansFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp, lineHeight = 1.2.em, letterSpacing = 0.6.sp,
    )
    val caps = TextStyle(
        fontFamily = SansFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp, lineHeight = 1.2.em, letterSpacing = 1.2.sp,
    )

    // Numeric / data (mono)
    val numXL = TextStyle(
        fontFamily = MonoFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 64.sp, lineHeight = 1.0.em, letterSpacing = (-2).sp,
    )
    val numL = TextStyle(
        fontFamily = MonoFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp, lineHeight = 1.0.em, letterSpacing = (-1).sp,
    )
    val numM = TextStyle(
        fontFamily = MonoFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp, lineHeight = 1.1.em, letterSpacing = (-0.5).sp,
    )
    val numS = TextStyle(
        fontFamily = MonoFamily, fontWeight = FontWeight.Medium,
        fontSize = 15.sp, lineHeight = 1.2.em,
    )
}

/** Mapeo a Material 3 Typography para fallbacks. Pantallas usan SpotterText directamente. */
val Typography = Typography(
    displayLarge = SpotterText.display,
    titleLarge = SpotterText.title1,
    titleMedium = SpotterText.title2,
    titleSmall = SpotterText.title3,
    bodyLarge = SpotterText.body,
    bodyMedium = SpotterText.bodyMd,
    bodySmall = SpotterText.small,
    labelLarge = SpotterText.smallMd,
    labelMedium = SpotterText.label,
    labelSmall = SpotterText.caps,
)
