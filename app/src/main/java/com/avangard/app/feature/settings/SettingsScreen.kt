package com.avangard.app.feature.settings

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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.avangard.app.R
import com.avangard.app.core.ui.components.HardButton
import com.avangard.app.core.ui.components.HardButtonVariant
import com.avangard.app.core.ui.components.PulpitPanel
import com.avangard.app.ui.theme.IsaColors

@Composable
fun SettingsScreen(
    onReturn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    SettingsContent(
        state = state,
        onEveningCloseChanged = viewModel::onEveningCloseChanged,
        onColdStartThresholdChanged = viewModel::onColdStartThresholdChanged,
        onRequestWipe = viewModel::requestWipe,
        onConfirmWipe = viewModel::confirmWipe,
        onCancelWipe = viewModel::cancelWipe,
        onReturn = onReturn,
        modifier = modifier,
    )
}

@Composable
internal fun SettingsContent(
    state: SettingsState,
    onEveningCloseChanged: (Int, Int) -> Unit,
    onColdStartThresholdChanged: (Int) -> Unit,
    onRequestWipe: () -> Unit,
    onConfirmWipe: () -> Unit,
    onCancelWipe: () -> Unit,
    onReturn: () -> Unit,
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
            text = stringResource(R.string.settings_header),
            color = IsaColors.LiveMetal,
            style = MaterialTheme.typography.headlineLarge,
        )

        EveningCloseBlock(
            hour = state.preferences.eveningCloseHour,
            minute = state.preferences.eveningCloseMinute,
            onChanged = onEveningCloseChanged,
        )

        ColdStartBlock(
            currentMinutes = (state.preferences.coldStartThresholdMs / 60 / 1000).toInt(),
            onChanged = onColdStartThresholdChanged,
        )

        PulpitPanel(label = stringResource(R.string.settings_wipe_label)) {
            if (!state.confirmingWipe) {
                HardButton(
                    label = stringResource(R.string.settings_wipe_request),
                    onClick = onRequestWipe,
                    variant = HardButtonVariant.Danger,
                )
            } else {
                Text(
                    text = stringResource(R.string.settings_wipe_confirm_message),
                    color = IsaColors.Signal,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HardButton(
                        label = stringResource(R.string.settings_wipe_confirm),
                        onClick = onConfirmWipe,
                        variant = HardButtonVariant.Danger,
                        modifier = Modifier.weight(1f),
                    )
                    HardButton(
                        label = stringResource(R.string.settings_wipe_cancel),
                        onClick = onCancelWipe,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (state.wipeInProgress) {
                Text(
                    text = stringResource(R.string.settings_wipe_in_progress),
                    color = IsaColors.Approve,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        HardButton(
            label = stringResource(R.string.settings_return),
            onClick = onReturn,
        )
    }
}

@Composable
private fun EveningCloseBlock(
    hour: Int,
    minute: Int,
    onChanged: (Int, Int) -> Unit,
) {
    var hourDraft by rememberSaveable(hour) { mutableIntStateOf(hour) }
    var minuteDraft by rememberSaveable(minute) { mutableIntStateOf(minute) }

    PulpitPanel(label = stringResource(R.string.settings_evening_close_label)) {
        Text(
            text = stringResource(R.string.settings_evening_close_hint),
            color = IsaColors.Lattice,
            style = MaterialTheme.typography.labelMedium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Stepper(
                label = stringResource(R.string.settings_hour_label),
                value = hourDraft,
                range = 0..23,
                onChange = { hourDraft = it; onChanged(hourDraft, minuteDraft) },
                modifier = Modifier.weight(1f),
            )
            Stepper(
                label = stringResource(R.string.settings_minute_label),
                value = minuteDraft,
                range = 0..59,
                step = 5,
                onChange = { minuteDraft = it; onChanged(hourDraft, minuteDraft) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ColdStartBlock(
    currentMinutes: Int,
    onChanged: (Int) -> Unit,
) {
    PulpitPanel(label = stringResource(R.string.settings_cold_start_label)) {
        Text(
            text = stringResource(R.string.settings_cold_start_hint),
            color = IsaColors.Lattice,
            style = MaterialTheme.typography.labelMedium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingsViewModel.COLD_START_OPTIONS_MINUTES.forEach { minutes ->
                val selected = minutes == currentMinutes
                val color = if (selected) IsaColors.Approve else IsaColors.Lattice
                val interactionSource = remember(minutes) { MutableInteractionSource() }
                Text(
                    text = "$minutes МИН",
                    color = color,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .border(width = if (selected) 2.dp else 1.dp, color = color)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { onChanged(minutes) },
                        )
                        .padding(vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun Stepper(
    label: String,
    value: Int,
    range: IntRange,
    step: Int = 1,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            color = IsaColors.Lattice,
            style = MaterialTheme.typography.labelMedium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StepperButton(symbol = "−", onClick = {
                val next = (value - step).coerceAtLeast(range.first)
                if (next != value) onChange(next)
            })
            Text(
                text = "%02d".format(value),
                color = IsaColors.LiveMetal,
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .border(width = 1.dp, color = IsaColors.Steel)
                    .padding(vertical = 6.dp),
            )
            StepperButton(symbol = "+", onClick = {
                val next = (value + step).coerceAtMost(range.last)
                if (next != value) onChange(next)
            })
        }
    }
}

@Composable
private fun StepperButton(symbol: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Text(
        text = symbol,
        color = IsaColors.LiveMetal,
        style = MaterialTheme.typography.headlineLarge,
        modifier = Modifier
            .border(width = 1.dp, color = IsaColors.Steel)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 2.dp),
    )
}
