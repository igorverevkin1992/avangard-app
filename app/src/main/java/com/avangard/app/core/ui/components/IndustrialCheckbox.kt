package com.avangard.app.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.avangard.app.R
import com.avangard.app.ui.theme.IsaColors

@Composable
fun IndustrialCheckbox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = if (checked) IsaColors.Approve else IsaColors.Lattice
    val interactionSource = remember { MutableInteractionSource() }
    val stateText = stringResource(
        if (checked) R.string.a11y_state_checked else R.string.a11y_state_unchecked
    )
    Row(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                role = Role.Checkbox
                stateDescription = stateText
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onCheckedChange(!checked) },
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Canvas(modifier = Modifier.size(20.dp)) {
            drawRect(color = color, style = Stroke(width = 2f))
            if (checked) {
                drawLine(
                    color = color,
                    start = Offset(4f, size.height / 2f),
                    end = Offset(size.width / 2.2f, size.height - 4f),
                    strokeWidth = 2f,
                )
                drawLine(
                    color = color,
                    start = Offset(size.width / 2.2f, size.height - 4f),
                    end = Offset(size.width - 3f, 4f),
                    strokeWidth = 2f,
                )
            }
        }
        Text(text = label, color = color, style = MaterialTheme.typography.bodyLarge)
    }
}
