package com.avangard.app.feature.closing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.avangard.app.R
import com.avangard.app.core.domain.model.Bottleneck
import com.avangard.app.core.domain.model.DefectKind
import com.avangard.app.core.ui.components.HardButton
import com.avangard.app.core.ui.components.HardButtonVariant
import com.avangard.app.core.ui.components.PulpitPanel
import com.avangard.app.ui.theme.IsaColors

@Composable
fun EveningCloseScreen(
    onClosed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EveningCloseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.effects.collect { onClosed() }
    }
    EveningCloseContent(
        state = state,
        onVirtueChange = viewModel::onVirtueChange,
        onDefectKindChange = viewModel::onDefectKindChange,
        onJournalChange = viewModel::onJournalChange,
        onSubmit = viewModel::submit,
        modifier = modifier,
    )
}

@Composable
internal fun EveningCloseContent(
    state: EveningCloseState,
    onVirtueChange: (Virtue, Int) -> Unit,
    onDefectKindChange: (DefectKind?) -> Unit,
    onJournalChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(IsaColors.Graphite)
            .semantics { isTraversalGroup = true }
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.closing_header),
            color = IsaColors.LiveMetal,
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() },
        )

        state.priorBottleneck?.let { prior ->
            PriorBottleneckReminder(prior = prior)
        }

        PulpitPanel(label = stringResource(R.string.closing_indicators_label)) {
            IndicatorRow(
                label = stringResource(R.string.closing_productivity),
                ok = state.productivityOk,
            )
            IndicatorRow(
                label = stringResource(R.string.closing_pride),
                ok = state.prideOk,
            )
            IndicatorRow(
                label = stringResource(R.string.closing_integrity),
                ok = state.integrityOk,
            )
        }

        if (state.needsDefectKind) {
            PulpitPanel(label = stringResource(R.string.closing_defect_label)) {
                Text(
                    text = stringResource(R.string.closing_defect_hint),
                    color = IsaColors.Lattice,
                    style = MaterialTheme.typography.labelMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HardButton(
                        label = stringResource(R.string.closing_defect_kind_defect),
                        onClick = { onDefectKindChange(DefectKind.Defect) },
                        variant = if (state.defectKind == DefectKind.Defect)
                            HardButtonVariant.Primary else HardButtonVariant.Default,
                        modifier = Modifier.weight(1f),
                    )
                    HardButton(
                        label = stringResource(R.string.closing_defect_kind_waste),
                        onClick = { onDefectKindChange(DefectKind.Waste) },
                        variant = if (state.defectKind == DefectKind.Waste)
                            HardButtonVariant.Danger else HardButtonVariant.Default,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        VirtueRow(
            label = stringResource(R.string.virtue_rationality),
            value = state.rationality,
            onChange = { onVirtueChange(Virtue.Rationality, it) },
            suggestUp = Virtue.Rationality in state.suggestedVirtuesUp,
            suggestDown = Virtue.Rationality in state.suggestedVirtuesDown,
        )
        VirtueRow(
            label = stringResource(R.string.virtue_independence),
            value = state.independence,
            onChange = { onVirtueChange(Virtue.Independence, it) },
            suggestUp = Virtue.Independence in state.suggestedVirtuesUp,
            suggestDown = Virtue.Independence in state.suggestedVirtuesDown,
        )
        VirtueRow(
            label = stringResource(R.string.virtue_honesty),
            value = state.honesty,
            onChange = { onVirtueChange(Virtue.Honesty, it) },
            suggestUp = Virtue.Honesty in state.suggestedVirtuesUp,
            suggestDown = Virtue.Honesty in state.suggestedVirtuesDown,
        )
        VirtueRow(
            label = stringResource(R.string.virtue_justice),
            value = state.justice,
            onChange = { onVirtueChange(Virtue.Justice, it) },
            suggestUp = Virtue.Justice in state.suggestedVirtuesUp,
            suggestDown = Virtue.Justice in state.suggestedVirtuesDown,
        )

        JournalPanel(
            draft = state.journalDraft,
            charCount = state.journalCharCount,
            limit = state.journalLimit,
            onChange = onJournalChange,
        )

        HardButton(
            label = stringResource(R.string.closing_submit),
            onClick = onSubmit,
            enabled = state.canSubmit,
            variant = HardButtonVariant.Primary,
        )
    }
}

@Composable
private fun JournalPanel(
    draft: String,
    charCount: Int,
    limit: Int,
    onChange: (String) -> Unit,
) {
    PulpitPanel(label = stringResource(R.string.closing_journal_label)) {
        Text(
            text = stringResource(R.string.closing_journal_hint),
            color = IsaColors.Lattice,
            style = MaterialTheme.typography.labelMedium,
        )
        BasicTextField(
            value = draft,
            onValueChange = onChange,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = IsaColors.LiveMetal),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
            ),
            decorationBox = { inner ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(width = 1.dp, color = IsaColors.Steel)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    inner()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp),
        )
        Text(
            text = stringResource(R.string.closing_journal_counter, charCount, limit),
            color = IsaColors.Lattice,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.End,
        )
    }
}


@Composable
private fun IndicatorRow(label: String, ok: Boolean) {
    val color = if (ok) IsaColors.Approve else IsaColors.Signal
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, color = IsaColors.LiveMetal, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = if (ok) "OK" else "FAIL",
            color = color,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .border(width = 1.dp, color = color)
                .padding(horizontal = 10.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun VirtueRow(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
    suggestUp: Boolean = false,
    suggestDown: Boolean = false,
) {
    PulpitPanel(label = label) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VirtueOption(
                symbol = "−", selected = value == -1, color = IsaColors.Signal,
                suggested = suggestDown,
                onClick = { onChange(-1) },
            )
            VirtueOption(
                symbol = "0", selected = value == 0, color = IsaColors.Lattice,
                suggested = false,
                onClick = { onChange(0) },
            )
            VirtueOption(
                symbol = "+", selected = value == 1, color = IsaColors.Approve,
                suggested = suggestUp,
                onClick = { onChange(1) },
            )
        }
        if (suggestUp || suggestDown) {
            Text(
                text = stringResource(R.string.closing_virtue_suggestion_hint),
                color = IsaColors.Caution,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun RowScope.VirtueOption(
    symbol: String,
    selected: Boolean,
    color: Color,
    suggested: Boolean,
    onClick: () -> Unit,
) {
    val tint = when {
        selected -> color
        suggested -> IsaColors.Caution
        else -> IsaColors.Lattice
    }
    val interactionSource = remember { MutableInteractionSource() }
    Text(
        text = symbol,
        color = tint,
        style = MaterialTheme.typography.headlineLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .weight(1f)
            .semantics(mergeDescendants = true) {
                role = Role.RadioButton
                this.selected = selected
            }
            .border(width = if (selected || suggested) 2.dp else 1.dp, color = tint)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 10.dp),
    )
}

@Composable
private fun PriorBottleneckReminder(prior: Bottleneck) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = IsaColors.Lattice)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.closing_prior_bottleneck_label),
            color = IsaColors.Lattice,
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            text = prior.displayName,
            color = IsaColors.LiveMetal,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
