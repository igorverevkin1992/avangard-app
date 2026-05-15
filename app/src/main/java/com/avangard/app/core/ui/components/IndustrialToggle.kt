package com.avangard.app.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.avangard.app.ui.theme.IsaColors

/**
 * Hardware-style toggle: no ripple, no shadows. Mirrors the state shift of a
 * physical switch — gray when off, signal yellow when engaged.
 */
@Composable
fun IndustrialToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeColor = IsaColors.Approve
    val inactiveColor = IsaColors.Lattice
    val color = if (checked) activeColor else inactiveColor
    val knobOffset by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(durationMillis = 120, easing = LinearEasing),
        label = "knob",
    )
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = { onCheckedChange(!checked) },
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Canvas(modifier = Modifier.size(width = 64.dp, height = 32.dp)) {
            drawRect(color = color, style = Stroke(width = 2f))
            val padding = 4f
            val knobSize = size.height - padding * 2
            val knobX = padding + knobOffset * (size.width - size.height)
            drawRect(
                color = color,
                topLeft = Offset(knobX, padding),
                size = Size(knobSize, knobSize),
            )
        }
        Text(
            text = "$label  ${if (checked) "ON" else "OFF"}",
            color = color,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
