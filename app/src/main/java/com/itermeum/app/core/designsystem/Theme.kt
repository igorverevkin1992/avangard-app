package com.itermeum.app.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/** Text on the seasonal accent: dark on the pale Festive/Rose colours, parchment otherwise (≥4.5:1). */
fun onAccent(accent: Color): Color = if (accent.luminance() > 0.35f) ImColors.Background else ImColors.OnSurface

private fun darkScheme(accent: Color) = darkColorScheme(
    primary = accent,
    onPrimary = onAccent(accent),
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
    // Every remaining role is set from the tokens: no Material baseline purple
    // leaks into chips, progress tracks, menus or snackbars.
    secondaryContainer = ImColors.Stone,
    onSecondaryContainer = ImColors.OnSurface,
    tertiaryContainer = ImColors.Stone,
    onTertiaryContainer = ImColors.Gold,
    errorContainer = ImColors.Stone,
    onErrorContainer = ImColors.Error,
    surfaceTint = Color.Transparent,
    inverseSurface = ImColors.OnSurface,
    inverseOnSurface = ImColors.Background,
    inversePrimary = ImColors.Gold,
    scrim = Color.Black,
    surfaceBright = ImColors.SurfaceVariant,
    surfaceDim = ImColors.Background,
    surfaceContainerLowest = ImColors.Background,
    surfaceContainerLow = ImColors.Surface,
    surfaceContainer = ImColors.Surface,
    surfaceContainerHigh = ImColors.SurfaceVariant,
    surfaceContainerHighest = ImColors.Stone,
)

/** Light option: parchment page, ink text; the same gold rules and liturgical accent. */
private fun lightScheme(accent: Color) = lightColorScheme(
    primary = accent,
    onPrimary = onAccent(accent),
    primaryContainer = Color(0xFFE4DCCB),
    onPrimaryContainer = ImColors.LightOnSurface,
    secondary = Color(0xFF5E574C),
    onSecondary = ImColors.LightBackground,
    tertiary = Color(0xFF8A6A22),
    onTertiary = ImColors.LightBackground,
    error = ImColors.Error,
    onError = ImColors.LightBackground,
    background = ImColors.LightBackground,
    onBackground = ImColors.LightOnSurface,
    surface = Color(0xFFF8F4EC),
    onSurface = ImColors.LightOnSurface,
    surfaceVariant = Color(0xFFE9E2D4),
    onSurfaceVariant = Color(0xFF5E574C),
    outline = Color(0xFFB9AE9A),
    outlineVariant = Color(0xFFC9A95E),
    secondaryContainer = Color(0xFFE4DCCB),
    onSecondaryContainer = ImColors.LightOnSurface,
    tertiaryContainer = Color(0xFFE4DCCB),
    onTertiaryContainer = Color(0xFF8A6A22),
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color(0xFFFFFBF3),
    surfaceContainerLow = Color(0xFFF8F4EC),
    surfaceContainer = Color(0xFFF8F4EC),
    surfaceContainerHigh = Color(0xFFE9E2D4),
    surfaceContainerHighest = Color(0xFFE4DCCB),
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
fun IterMeumTheme(accent: Color = ImColors.Ordinary, light: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (light) lightScheme(accent) else darkScheme(accent),
        typography = ImTypography,
        shapes = ImShapes,
        content = content,
    )
}
