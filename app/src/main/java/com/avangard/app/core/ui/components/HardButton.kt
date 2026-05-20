package com.avangard.app.core.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.avangard.app.ui.theme.IsaColors

enum class HardButtonVariant { Default, Primary, Danger }

/**
 * Rectangular HMI-style button. No ripple, no rounding, no shadow.
 * Colour follows variant; when disabled the colour swaps to [IsaColors.Mute].
 */
@Composable
fun HardButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: HardButtonVariant = HardButtonVariant.Default,
) {
    val activeColor: Color = when (variant) {
        HardButtonVariant.Default -> IsaColors.LiveMetal
        HardButtonVariant.Primary -> IsaColors.Approve
        HardButtonVariant.Danger -> IsaColors.Signal
    }
    val color = if (enabled) activeColor else IsaColors.Mute
    val interactionSource = remember { MutableInteractionSource() }
    Text(
        text = label,
        color = color,
        style = MaterialTheme.typography.labelLarge,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                role = Role.Button
                if (!enabled) disabled()
            }
            .border(width = if (enabled) 2.dp else 1.dp, color = color)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 14.dp),
    )
}
