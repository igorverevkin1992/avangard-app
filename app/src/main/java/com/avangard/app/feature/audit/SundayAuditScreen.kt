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
import com.avangard.app.core.domain.model.BottleneckFollowup
import com.avangard.app.core.domain.model.EvasionKind
import com.avangard.app.core.domain.model.Habit
import com.avangard.app.core.domain.usecase.SundayAuditView
import com.avangard.app.core.ui.components.HardButton
import com.avangard.app.core.ui.components.HardButtonVariant
import com.avangard.app.core.ui.components.PulpitPanel
import com.avangard.app.ui.theme.IsaColors

@Composable
fun SundayAuditScreen(
    onOpenHistory: () -> Unit,
    onOpenPulpit: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SundayAuditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    // Effects pipeline kept idle — the completion state derives from
    // DailySession.bottleneckForNextWeek via the ViewModel's combine, so
    // the UI flips to the sealed layout the moment setBottleneck commits.
    SundayAuditContent(
        state = state,
        onPickBottleneck = viewModel::onPickBottleneck,
        onPickFollowup = viewModel::onPickFollowup,
        onSubmit = viewModel::submit,
        onOpenHistory = onOpenHistory,
        onOpenPulpit = onOpenPulpit,
        modifier = modifier,
    )
}

@Composable
internal fun SundayAuditContent(
    state: SundayAuditState,
    onPickBottleneck: (Bottleneck) -> Unit,
    onPickFollowup: (BottleneckFollowup) -> Unit = {},
    onSubmit: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenPulpit: () -> Unit,
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

        if (view != null && state.evasionsThisWeek.values.sum() > 0) {
            EvasionsRow(evasions = state.evasionsThisWeek)
        }

        state.priorBottleneck?.let { prior ->
            PriorBottleneckPanel(
                prior = prior,
                answer = state.priorBottleneckFollowup,
                onPick = onPickFollowup,
            )
        }

        if (state.isCompleted) {
            CompletionPanel(fixated = state.fixatedBottleneck!!)
        } else {
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
        }

        HardButton(
            label = stringResource(R.string.audit_open_history),
            onClick = onOpenHistory,
        )

        // Sunday is reflection day by design, but the operator may still
        // want to record a focus session on the cycle's last day. The
        // pulpit stays one tap away — the start destination just defaults
        // to the audit on Sundays via AccessPolicy.
        HardButton(
            label = stringResource(R.string.audit_open_pulpit),
            onClick = onOpenPulpit,
        )
    }
}

@Composable
private fun MetricsTable(view: SundayAuditView) {
    val prev = view.previous?.takeIf { it.populated }
    PulpitPanel(label = stringResource(R.string.audit_metrics_label)) {
        MetricRow(
            label = stringResource(R.string.audit_metric_core_hours),
            value = formatHours(view.coreHoursMillis),
            delta = prev?.let { formatDeltaHours(view.coreHoursMillis - it.coreHoursMillis) },
        )
        MetricRow(
            label = stringResource(R.string.audit_metric_days_approved),
            value = view.daysApproved.toString(),
            delta = prev?.let { formatDelta(view.daysApproved - it.daysApproved) },
        )
        MetricRow(
            label = stringResource(R.string.audit_metric_defects),
            value = view.defectCount.toString(),
            delta = prev?.let { formatDeltaInverse(view.defectCount - it.defectCount) },
        )
        MetricRow(
            label = stringResource(R.string.audit_metric_wastes),
            value = view.wasteCount.toString(),
            delta = prev?.let { formatDeltaInverse(view.wasteCount - it.wasteCount) },
        )
        MetricRow(
            label = stringResource(R.string.audit_metric_mvd_days),
            value = view.mvdDays.toString(),
            delta = prev?.let { formatDelta(view.mvdDays - it.mvdDays) },
        )
        listOf(Habit.Spanish, Habit.Sport, Habit.Watching, Habit.Reading).forEach { habit ->
            val b = view.infraBreakdown[habit] ?: return@forEach
            MetricRow(
                label = "${habit.code}·${habit.shortLabel}",
                value = "${b.standard}/${b.mvd}/${b.notDone}",
                delta = null,
            )
        }
        val currentVirtues = view.virtueSums.rationality + view.virtueSums.independence +
            view.virtueSums.honesty + view.virtueSums.justice
        MetricRow(
            label = stringResource(R.string.audit_metric_virtues),
            value = "R:${view.virtueSums.rationality} I:${view.virtueSums.independence} " +
                "H:${view.virtueSums.honesty} J:${view.virtueSums.justice}",
            delta = prev?.let { formatDelta(currentVirtues - it.virtueSum) },
        )
    }
}

@Composable
private fun MetricRow(label: String, value: String, delta: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, color = IsaColors.LiveMetal, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = value,
                color = IsaColors.Approve,
                style = MaterialTheme.typography.labelLarge,
            )
            if (delta != null) {
                Text(
                    text = delta,
                    color = IsaColors.Lattice,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

private fun formatDelta(diff: Int): String = when {
    diff > 0 -> "(↑+$diff)"
    diff < 0 -> "(↓$diff)"
    else -> "(=)"
}

/** Inverse delta for metrics where less is better (defects, wastes): down is good. */
private fun formatDeltaInverse(diff: Int): String = when {
    diff > 0 -> "(↑+$diff)"
    diff < 0 -> "(↓$diff)"
    else -> "(=)"
}

private fun formatDeltaHours(diffMs: Long): String {
    if (diffMs == 0L) return "(=)"
    val sign = if (diffMs > 0) "↑+" else "↓"
    val absMs = if (diffMs > 0) diffMs else -diffMs
    val totalSeconds = absMs / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    return "(${sign}%02d:%02d)".format(java.util.Locale.US, h, m)
}

@Composable
private fun CompletionPanel(fixated: Bottleneck) {
    PulpitPanel(label = stringResource(R.string.audit_completed_label)) {
        Text(
            text = stringResource(R.string.audit_completed_header),
            color = IsaColors.Approve,
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.audit_completed_subtitle),
            color = IsaColors.Lattice,
            style = MaterialTheme.typography.labelMedium,
        )
        // Sealed bottleneck — green outline, no click affordance. The
        // operator can re-read what they fixated but not flip it
        // without waiting for the next Sunday cycle.
        Text(
            text = fixated.displayName,
            color = IsaColors.Approve,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 2.dp, color = IsaColors.Approve)
                .padding(horizontal = 10.dp, vertical = 10.dp),
        )
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

@Composable
private fun PriorBottleneckPanel(
    prior: Bottleneck,
    answer: BottleneckFollowup?,
    onPick: (BottleneckFollowup) -> Unit,
) {
    PulpitPanel(label = stringResource(R.string.audit_prior_label)) {
        Text(
            text = prior.displayName,
            color = IsaColors.LiveMetal,
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = stringResource(R.string.audit_prior_question),
            color = IsaColors.Lattice,
            style = MaterialTheme.typography.labelSmall,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BottleneckFollowup.entries.forEach { option ->
                FollowupChip(
                    option = option,
                    selected = answer == option,
                    onClick = { onPick(option) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun FollowupChip(
    option: BottleneckFollowup,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = when {
        selected && option == BottleneckFollowup.Yes -> IsaColors.Approve
        selected && option == BottleneckFollowup.Partial -> IsaColors.Caution
        selected && option == BottleneckFollowup.No -> IsaColors.Signal
        else -> IsaColors.Lattice
    }
    val interactionSource = remember(option) { MutableInteractionSource() }
    Text(
        text = option.displayName,
        color = color,
        style = MaterialTheme.typography.labelLarge,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = modifier
            .border(width = if (selected) 2.dp else 1.dp, color = color)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

@Composable
private fun EvasionsRow(evasions: Map<EvasionKind, Int>) {
    val total = evasions.values.sum()
    val sub = evasions[EvasionKind.Substitution] ?: 0
    val def = evasions[EvasionKind.Defect] ?: 0
    val cmp = evasions[EvasionKind.Comparison] ?: 0
    PulpitPanel(label = stringResource(R.string.audit_evasions_label)) {
        Text(
            text = stringResource(R.string.audit_evasions_summary, total, sub, def, cmp),
            color = IsaColors.Signal,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

private fun formatHours(millis: Long): String {
    val totalSeconds = millis / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    return "%02d:%02d".format(java.util.Locale.US, h, m)
}
