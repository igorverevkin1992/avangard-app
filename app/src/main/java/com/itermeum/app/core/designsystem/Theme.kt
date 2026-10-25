package com.itermeum.app.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private fun darkScheme(accent: Color) = darkColorScheme(
    primary = accent,
    onPrimary = ImColors.OnSurface,
    secondary = ImColors.OnSurfaceVariant,
    onSecondary = ImColors.Background,
    error = ImColors.Error,
    onError = ImColors.OnSurface,
    background = ImColors.Background,
    onBackground = ImColors.OnSurface,
    surface = ImColors.Surface,
    onSurface = ImColors.OnSurface,
    surfaceVariant = ImColors.SurfaceVariant,
    onSurfaceVariant = ImColors.OnSurfaceVariant,
    outline = ImColors.Outline,
)

/**
 * Dark theme by default. [accent] is the liturgical colour of the season;
 * callers that know the season (phase 3 calendar) pass it, the skeleton
 * falls back to Ordinary-time green.
 */
@Composable
fun IterMeumTheme(accent: Color = ImColors.Ordinary, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkScheme(accent),
        typography = ImTypography,
        content = content,
    )
}
