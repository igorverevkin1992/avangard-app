package com.avangard.app.core.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.avangard.app.ui.theme.IsaColors

enum class StatusBadgeKind {
    Idle,        // grey, neutral
    Standard,    // copper-ish (LiveMetal contrast)
    Mvd,         // copper-ish, muted variant
    Approved,    // sparse green
    Fail,        // signal red
    Locked,      // mute grey, hostage-bound
}

@Composable
fun StatusBadge(
    kind: StatusBadgeKind,
    modifier: Modifier = Modifier,
) {
    val (label, color) = when (kind) {
        StatusBadgeKind.Idle -> "IDLE" to IsaColors.Lattice
        StatusBadgeKind.Standard -> "STANDARD" to IsaColors.LiveMetal
        StatusBadgeKind.Mvd -> "MVD" to IsaColors.Lattice
        StatusBadgeKind.Approved -> "APPROVED" to IsaColors.Approve
        StatusBadgeKind.Fail -> "FAIL" to IsaColors.Signal
        StatusBadgeKind.Locked -> "LOCKED" to IsaColors.Mute
    }
    BadgeBox(label = label, color = color, modifier = modifier)
}

@Composable
private fun BadgeBox(label: String, color: Color, modifier: Modifier) {
    Text(
        text = label,
        color = color,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier
            .border(width = 1.dp, color = color)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
