package com.itermeum.app.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private fun darkScheme(accent: Color) = darkColorScheme(
    primary = accent,
    onPrimary = ImColors.OnSurface,
    primaryContainer = ImColors.Stone,
    onPrimaryContainer = ImColors.OnSurface,
    secondary = ImColors.OnSurfaceVariant,
    onSecondary = ImColors.Background,
    tertiary = ImColors.Gold,
    onTertiary = ImColors.Background,
    error = ImColors.Error,
    onError = ImColors.OnSurface,
    background = ImColors.Background,
    onBackground = ImColors.OnSurface,
    surface = ImColors.Surface,
    onSurface = ImColors.OnSurface,
    surfaceVariant = ImColors.SurfaceVariant,
    onSurfaceVariant = ImColors.OnSurfaceVariant,
    outline = ImColors.Outline,
    outlineVariant = ImColors.GoldDim,
)

/** Cut stone, not pebbles: every Material shape is square. */
private val ImShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp),
)

/**
 * Dark theme by default. [accent] is the liturgical colour of the season;
 * callers that know the season (phase 3 calendar) pass it, the skeleton
 * falls back to Ordinary-time green. Gold lives in `tertiary`.
 */
@Composable
fun IterMeumTheme(accent: Color = ImColors.Ordinary, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkScheme(accent),
        typography = ImTypography,
        shapes = ImShapes,
        content = content,
    )
}
