package com.avangard.app.feature.chronometer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.avangard.app.R
import com.avangard.app.core.domain.model.ChronometerProgress
import com.avangard.app.core.ui.components.HardButton
import com.avangard.app.core.ui.components.PulpitPanel
import com.avangard.app.ui.theme.IsaColors
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ChronometerScreen(
    onReturn: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChronometerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    ChronometerContent(
        progress = state,
        onReturn = onReturn,
        onOpenSettings = onOpenSettings,
        modifier = modifier,
    )
}

@Composable
internal fun ChronometerContent(
    progress: ChronometerProgress,
    onReturn: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(IsaColors.Graphite)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.chronometer_header),
            color = IsaColors.LiveMetal,
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() },
        )

        if (!progress.configured) {
            PulpitPanel(label = stringResource(R.string.chronometer_resource_label)) {
                Text(
                    text = stringResource(R.string.chronometer_empty_state),
                    color = IsaColors.Lattice,
                    style = MaterialTheme.typography.bodyMedium,
                )
                HardButton(
                    label = stringResource(R.string.chronometer_open_settings),
                    onClick = onOpenSettings,
                )
            }
        } else {
            ResourcePanel(progress)
            GridPanel(progress)
            Text(
                text = stringResource(R.string.chronometer_footer_note),
                color = IsaColors.Lattice,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        HardButton(
            label = stringResource(R.string.chronometer_return),
            onClick = onReturn,
        )
    }
}

@Composable
private fun ResourcePanel(progress: ChronometerProgress) {
    PulpitPanel(label = stringResource(R.string.chronometer_resource_label)) {
        val total = (progress.extractedDays + progress.partialDays + progress.burnedDays)
            .coerceAtLeast(1)
        val extractedPct = progress.extractedDays * 100.0 / total
        val partialPct = progress.partialDays * 100.0 / total
        val burnedPct = progress.burnedDays * 100.0 / total

        ResourceRow(
            stringResource(R.string.chronometer_days_lived),
            formatLong(progress.daysLived),
        )
        ResourceRow(
            stringResource(R.string.chronometer_days_budget),
            formatLong(progress.daysBudget),
        )
        ResourceRow(
            stringResource(R.string.chronometer_days_remaining),
            stringResource(
                R.string.chronometer_days_remaining_value,
                formatLong(progress.daysRemaining),
                formatYears(progress.yearsRemaining),
            ),
            valueColor = IsaColors.Signal,
        )
        ResourceRow(
            stringResource(R.string.chronometer_extracted),
            stringResource(
                R.string.chronometer_count_with_pct,
                formatLong(progress.extractedDays),
                formatPct(extractedPct),
            ),
            valueColor = IsaColors.Approve,
        )
        ResourceRow(
            stringResource(R.string.chronometer_partial),
            stringResource(
                R.string.chronometer_count_with_pct,
                formatLong(progress.partialDays),
                formatPct(partialPct),
            ),
        )
        ResourceRow(
            stringResource(R.string.chronometer_burned),
            stringResource(
                R.string.chronometer_count_with_pct,
                formatLong(progress.burnedDays),
                formatPct(burnedPct),
            ),
            valueColor = IsaColors.Mute,
        )
    }
}

@Composable
private fun GridPanel(progress: ChronometerProgress) {
    // Tap a week in the grid to inspect that week's class without losing
    // the at-a-glance overview. Popup closes on outside-tap.
    var pickedWeek by remember { mutableStateOf<Int?>(null) }
    PulpitPanel(label = stringResource(R.string.chronometer_grid_label)) {
        LifeGrid(
            weeks = progress.weeks,
            onWeekTap = { idx -> pickedWeek = idx },
        )
        pickedWeek?.let { idx ->
            WeekDetailRow(
                weekIndex = idx,
                weekClass = progress.weeks.getOrNull(idx),
                onDismiss = { pickedWeek = null },
            )
        }
    }
}

@Composable
private fun WeekDetailRow(
    weekIndex: Int,
    weekClass: com.avangard.app.core.domain.model.WeekClass?,
    onDismiss: () -> Unit,
) {
    val (label, color) = when (weekClass) {
        com.avangard.app.core.domain.model.WeekClass.Extracted -> "РЕАЛИЗОВАНА" to IsaColors.Approve
        com.avangard.app.core.domain.model.WeekClass.Partial -> "ЧАСТИЧНО" to IsaColors.Caution
        com.avangard.app.core.domain.model.WeekClass.Burned -> "УПУЩЕНА" to IsaColors.Mute
        com.avangard.app.core.domain.model.WeekClass.Current -> "ТЕКУЩАЯ" to IsaColors.Signal
        com.avangard.app.core.domain.model.WeekClass.Future, null -> "ВПЕРЕДИ" to IsaColors.Lattice
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = color)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "НЕДЕЛЯ #${weekIndex + 1}",
            color = IsaColors.LiveMetal,
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            text = "✕",
            color = IsaColors.Lattice,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
    // Wire dismiss via a transparent overlay tap — clicking on the row
    // header above the X also clears the picked week.
    Spacer(modifier = Modifier
        .fillMaxWidth()
        .height(0.dp))
    LaunchedEffect(weekIndex) {
        // 10-second auto-dismiss so a stray tap doesn't pin the detail
        // strip permanently. Operator can re-tap to refresh.
        kotlinx.coroutines.delay(10_000)
        onDismiss()
    }
}

@Composable
private fun ResourceRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = IsaColors.LiveMetal,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            color = IsaColors.Lattice,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            color = valueColor,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1.2f),
        )
    }
}

private val russianLocale = Locale("ru", "RU")

private fun formatLong(value: Int): String =
    NumberFormat.getIntegerInstance(russianLocale).format(value)

private fun formatYears(value: Double): String =
    String.format(russianLocale, "%.1f", value)

private fun formatPct(value: Double): String =
    String.format(russianLocale, "%.2f", value)
