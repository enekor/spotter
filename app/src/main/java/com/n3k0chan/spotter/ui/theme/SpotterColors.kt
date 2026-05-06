package com.n3k0chan.spotter.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Tokens semánticos del diseño expuestos vía CompositionLocal.
 * Permite acceder a colores que NO están en MaterialTheme.colorScheme directamente
 * (textMuted, primarySoft, success, etc.) sin fight con el mapeo M3.
 */
data class SpotterColors(
    val bg: Color,
    val surface: Color,
    val surfaceMuted: Color,
    val surfaceVariant: Color,
    val text: Color,
    val textMuted: Color,
    val textFaint: Color,
    val primary: Color,
    val onPrimary: Color,
    val primarySoft: Color,
    val primarySoftText: Color,
    val border: Color,
    val borderStrong: Color,
    val success: Color,
    val danger: Color,
    val chartFill: Color,
    val isDark: Boolean,
)

val LightSpotterColors = SpotterColors(
    bg = LightBg,
    surface = LightSurface,
    surfaceMuted = LightSurfaceMuted,
    surfaceVariant = LightSurfaceVariant,
    text = LightText,
    textMuted = LightTextMuted,
    textFaint = LightTextFaint,
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primarySoft = LightPrimarySoft,
    primarySoftText = LightPrimarySoftText,
    border = LightBorder,
    borderStrong = LightBorderStrong,
    success = LightSuccess,
    danger = LightDanger,
    chartFill = LightChartFill,
    isDark = false,
)

val DarkSpotterColors = SpotterColors(
    bg = DarkBg,
    surface = DarkSurface,
    surfaceMuted = DarkSurfaceMuted,
    surfaceVariant = DarkSurfaceVariant,
    text = DarkText,
    textMuted = DarkTextMuted,
    textFaint = DarkTextFaint,
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primarySoft = DarkPrimarySoft,
    primarySoftText = DarkPrimarySoftText,
    border = DarkBorder,
    borderStrong = DarkBorderStrong,
    success = DarkSuccess,
    danger = DarkDanger,
    chartFill = DarkChartFill,
    isDark = true,
)

val LocalSpotterColors = staticCompositionLocalOf { LightSpotterColors }

/** Acceso ergonómico: `SpotterTheme.colors.primary`. */
object SpotterTheme {
    val colors: SpotterColors
        @Composable
        @ReadOnlyComposable
        get() = LocalSpotterColors.current
}
