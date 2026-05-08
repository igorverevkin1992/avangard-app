package com.avangard.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MachineColorScheme = darkColorScheme(
    primary = MachineColors.IndicationYellow,
    onPrimary = MachineColors.Background,
    secondary = MachineColors.OutlineGray,
    onSecondary = MachineColors.Background,
    error = MachineColors.SignalRed,
    onError = MachineColors.TextPrimary,
    background = MachineColors.Background,
    onBackground = MachineColors.TextPrimary,
    surface = MachineColors.Background,
    onSurface = MachineColors.TextPrimary,
    outline = MachineColors.OutlineGray,
)

@Composable
fun MachineTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MachineColorScheme,
        typography = MachineTypography,
        content = content,
    )
}
