package com.avangard.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val IsaColorScheme = darkColorScheme(
    primary = IsaColors.Approve,
    onPrimary = IsaColors.Carbon,
    secondary = IsaColors.LiveMetal,
    onSecondary = IsaColors.Carbon,
    error = IsaColors.Signal,
    onError = IsaColors.LiveMetal,
    background = IsaColors.Graphite,
    onBackground = IsaColors.LiveMetal,
    surface = IsaColors.Graphite,
    onSurface = IsaColors.LiveMetal,
    outline = IsaColors.Steel,
)

@Composable
fun MachineTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = IsaColorScheme,
        typography = MachineTypography,
        content = content,
    )
}
