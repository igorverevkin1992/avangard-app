package com.avangard.app.feature.audit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.avangard.app.R
import com.avangard.app.core.domain.model.Bottleneck
import com.avangard.app.core.domain.model.Habit
import com.avangard.app.core.domain.usecase.SundayAuditView
import com.avangard.app.core.ui.components.HardButton
import com.avangard.app.core.ui.components.HardButtonVariant
import com.avangard.app.core.ui.components.PulpitPanel
import com.avangard.app.ui.theme.IsaColors

@Composable
fun SundayAuditScreen(
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SundayAuditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.effects.collect { /* Submitted — UI stays put, user can then tap history */ }
    }
    SundayAuditContent(
        state = state,
        onPickBottleneck = viewModel::onPickBottleneck,
        onSubmit = viewModel::submit,
        onOpenHistory = onOpenHistory,
        modifier = modifier,
    )
}

@Composable
internal fun SundayAuditContent(
    state: SundayAuditState,
    onPickBottleneck: (Bottleneck) -> Unit,
    onSubmit: () -> Unit,
    onOpenHistory: () -> Unit,
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
            text = stringResource(R.string.audit_header),
            color = IsaColors.LiveMetal,
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() },
        )

        val view = state.view
        when {
            view == null -> Text(
                text = stringResource(R.string.audit_loading),
                color = IsaColors.Lattice,
                style = MaterialTheme.typography.bodyMedium,
            )
            view.isEmpty() -> Text(
                text = stringResource(R.string.audit_empty_state),
                color = IsaColors.Lattice,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 1.dp, color = IsaColors.Steel)
                    .padding(16.dp),
            )
            else -> MetricsTable(view = view)
        }

        PulpitPanel(label = stringResource(R.string.audit_bottleneck_label)) {
            Bottleneck.entries.forEach { b ->
                BottleneckRow(
                    bottleneck = b,
                    selected = state.selectedBottleneck == b,
                    onClick = { onPickBottleneck(b) },
                )
            }
        }

        HardButton(
            label = stringResource(R.string.audit_submit),
            onClick = onSubmit,
            enabled = state.canSubmit,
            variant = HardButtonVariant.Primary,
        )

        HardButton(
            label = stringResource(R.string.audit_open_history),
            onClick = onOpenHistory,
        )
    }
}

@Composable
private fun MetricsTable(view: SundayAuditView) {
    PulpitPanel(label = stringResource(R.string.audit_metrics_label)) {
        MetricRow(
            stringResource(R.string.audit_metric_core_hours),
            formatHours(view.coreHoursMillis),
        )
        MetricRow(stringResource(R.string.audit_metric_days_approved), view.daysApproved.toString())
        MetricRow(stringResource(R.string.audit_metric_defects), view.defectCount.toString())
        MetricRow(stringResource(R.string.audit_metric_wastes), view.wasteCount.toString())
        MetricRow(stringResource(R.string.audit_metric_mvd_days), view.mvdDays.toString())
        listOf(Habit.Spanish, Habit.Sport, Habit.Watching, Habit.Reading).forEach { habit ->
            val b = view.infraBreakdown[habit] ?: return@forEach
            MetricRow(
                label = "${habit.code}·${habit.shortLabel}",
                value = "${b.standard}/${b.mvd}/${b.notDone}",
            )
        }
        MetricRow(
            stringResource(R.string.audit_metric_virtues),
            "R:${view.virtueSums.rationality} I:${view.virtueSums.independence} " +
                "H:${view.virtueSums.honesty} J:${view.virtueSums.justice}",
        )
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, color = IsaColors.LiveMetal, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, color = IsaColors.Approve, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun BottleneckRow(
    bottleneck: Bottleneck,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val color = if (selected) IsaColors.Approve else IsaColors.Lattice
    val interactionSource = remember(bottleneck) { MutableInteractionSource() }
    Text(
        text = bottleneck.displayName,
        color = color,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .fillMaxWidth()
            .border(width = if (selected) 2.dp else 1.dp, color = color)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 10.dp),
    )
}

private fun formatHours(millis: Long): String {
    val totalSeconds = millis / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    return "%02d:%02d".format(java.util.Locale.US, h, m)
}
