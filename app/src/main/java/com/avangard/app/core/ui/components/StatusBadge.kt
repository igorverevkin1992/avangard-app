package com.avangard.app.core.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.avangard.app.R
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
    // Each badge carries a colour AND a leading glyph so the state is legible
    // for colour-blind users / under monochrome screenshots: ✓ confirmed,
    // ▣ partial, ✕ failure, ⊘ locked, · neutral.
    val (label, color) = when (kind) {
        StatusBadgeKind.Idle -> "·  IDLE" to IsaColors.Lattice
        StatusBadgeKind.Standard -> "✓  СТАНДАРТ" to IsaColors.Approve
        StatusBadgeKind.Mvd -> "▣  МИНИМУМ" to IsaColors.Caution
        StatusBadgeKind.Approved -> "✓  ЗАСЧИТАНО" to IsaColors.Approve
        StatusBadgeKind.Fail -> "✕  ПРОВАЛ" to IsaColors.Signal
        StatusBadgeKind.Locked -> "⊘  ЖДЁТ ЯДРО" to IsaColors.Mute
    }
    val a11y = when (kind) {
        StatusBadgeKind.Idle -> stringResource(R.string.a11y_status_idle)
        StatusBadgeKind.Standard -> stringResource(R.string.a11y_status_standard)
        StatusBadgeKind.Mvd -> stringResource(R.string.a11y_status_mvd)
        StatusBadgeKind.Approved -> stringResource(R.string.a11y_status_approved)
        StatusBadgeKind.Fail -> stringResource(R.string.a11y_status_fail)
        StatusBadgeKind.Locked -> stringResource(R.string.a11y_status_locked)
    }
    BadgeBox(label = label, color = color, a11yDescription = a11y, modifier = modifier)
}

@Composable
private fun BadgeBox(
    label: String,
    color: Color,
    a11yDescription: String,
    modifier: Modifier,
) {
    Text(
        text = label,
        color = color,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier
            .semantics { contentDescription = a11yDescription }
            .border(width = 1.dp, color = color)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
