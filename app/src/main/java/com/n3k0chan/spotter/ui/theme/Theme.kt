package com.n3k0chan.spotter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Spotter theme. Sin dynamic color por decisión de diseño:
 * el design canvas fija paleta naranja/slate consistente para todas las pantallas.
 */

private val LightM3 = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimarySoft,
    onPrimaryContainer = LightPrimarySoftText,
    secondary = LightPrimary,
    onSecondary = LightOnPrimary,
    secondaryContainer = LightPrimarySoft,
    onSecondaryContainer = LightPrimarySoftText,
    tertiary = LightPrimary,
    background = LightBg,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextMuted,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurface,
    surfaceContainerHighest = LightSurface,
    surfaceContainerLow = LightSurfaceMuted,
    surfaceContainerLowest = LightBg,
    outline = LightBorderStrong,
    outlineVariant = LightBorder,
    error = LightDanger,
    onError = LightOnPrimary,
)

private val DarkM3 = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimarySoft,
    onPrimaryContainer = DarkPrimarySoftText,
    secondary = DarkPrimary,
    onSecondary = DarkOnPrimary,
    secondaryContainer = DarkPrimarySoft,
    onSecondaryContainer = DarkPrimarySoftText,
    tertiary = DarkPrimary,
    background = DarkBg,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextMuted,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurface,
    surfaceContainerHighest = DarkSurface,
    surfaceContainerLow = DarkSurfaceMuted,
    surfaceContainerLowest = DarkBg,
    outline = DarkBorderStrong,
    outlineVariant = DarkBorder,
    error = DarkDanger,
    onError = DarkOnPrimary,
)

@Composable
fun SpotterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val m3 = if (darkTheme) DarkM3 else LightM3
    val tokens = if (darkTheme) DarkSpotterColors else LightSpotterColors
    CompositionLocalProvider(LocalSpotterColors provides tokens) {
        MaterialTheme(
            colorScheme = m3,
            typography = Typography,
            content = content,
        )
    }
}
