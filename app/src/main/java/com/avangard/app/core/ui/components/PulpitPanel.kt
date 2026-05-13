package com.avangard.app.core.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.avangard.app.ui.theme.IsaColors

/**
 * Industrial panel frame — sharp 1dp border, no shadow, no rounding.
 * The optional [label] strip sits flush against the top border in [IsaColors.Lattice].
 * Trailing slot of the label row hosts a right-aligned status, if any.
 */
@Composable
fun PulpitPanel(
    modifier: Modifier = Modifier,
    label: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    borderColor: Color = IsaColors.Steel,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = borderColor),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        if (label != null || trailing != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (label != null) {
                    Text(
                        text = label,
                        color = IsaColors.Lattice,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                trailing?.invoke()
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}
