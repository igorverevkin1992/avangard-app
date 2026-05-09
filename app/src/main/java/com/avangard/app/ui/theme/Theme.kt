package com.avangard.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MachineColorScheme = darkColorScheme(
    primary = MachineColors.ReardenCopper,
    onPrimary = MachineColors.Anthracite,
    secondary = MachineColors.BlueprintCyan,
    onSecondary = MachineColors.Anthracite,
    error = MachineColors.AtlasRed,
    onError = MachineColors.Ivory,
    background = MachineColors.Anthracite,
    onBackground = MachineColors.Ivory,
    surface = MachineColors.Anthracite,
    onSurface = MachineColors.Ivory,
    outline = MachineColors.WarmGray,
)

@Composable
fun MachineTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MachineColorScheme,
        typography = MachineTypography,
        content = content,
    )
}
