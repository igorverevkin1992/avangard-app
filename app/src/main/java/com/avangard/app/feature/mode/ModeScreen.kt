package com.avangard.app.feature.mode

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.avangard.app.R
import com.avangard.app.core.domain.model.CoreMode
import com.avangard.app.core.domain.model.Habit
import com.avangard.app.core.domain.model.HabitStandard
import com.avangard.app.core.domain.model.HabitStandardDefaults
import com.avangard.app.core.ui.components.HardButton
import com.avangard.app.core.ui.components.HardButtonVariant
import com.avangard.app.core.ui.components.PulpitPanel
import com.avangard.app.ui.theme.IsaColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ModeScreen(
    modifier: Modifier = Modifier,
    viewModel: ModeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    ModeContent(
        dayMode = state.dayMode,
        standards = state.standards,
        onPickDayMode = viewModel::onPickDayMode,
        onStandardChanged = viewModel::onStandardChanged,
        onFillDefaults = viewModel::onFillDefaults,
        modifier = modifier,
    )
}

@Composable
internal fun ModeContent(
    dayMode: CoreMode?,
    standards: Map<String, HabitStandard>,
    onPickDayMode: (CoreMode) -> Unit,
    onStandardChanged: (String, String, String) -> Unit,
    onFillDefaults: () -> Unit,
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
            text = stringResource(R.string.mode_header),
            color = IsaColors.LiveMetal,
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() },
        )

        DayModePanel(dayMode = dayMode, onPickDayMode = onPickDayMode)

        StandardsPanel(
            standards = standards,
            onStandardChanged = onStandardChanged,
            onFillDefaults = onFillDefaults,
        )
    }
}

@Composable
private fun DayModePanel(
    dayMode: CoreMode?,
    onPickDayMode: (CoreMode) -> Unit,
) {
    PulpitPanel(label = stringResource(R.string.mode_day_picker_label)) {
        Text(
            text = stringResource(R.string.mode_day_picker_hint),
            color = IsaColors.Lattice,
            style = MaterialTheme.typography.labelSmall,
        )

        val (label, accent) = when (dayMode) {
            CoreMode.Standard -> stringResource(R.string.mode_day_current_standard) to IsaColors.Approve
            CoreMode.Mvd -> stringResource(R.string.mode_day_current_mvd) to IsaColors.Caution
            null -> stringResource(R.string.mode_day_current_unset) to IsaColors.Signal
        }
        Text(
            text = label,
            color = accent,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = accent)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )

        if (dayMode == null) {
            HardButton(
                label = stringResource(R.string.mode_day_picker_pick_standard),
                onClick = { onPickDayMode(CoreMode.Standard) },
                variant = HardButtonVariant.Primary,
            )
            HardButton(
                label = stringResource(R.string.mode_day_picker_pick_mvd),
                onClick = { onPickDayMode(CoreMode.Mvd) },
                variant = HardButtonVariant.Default,
            )
        } else {
            Text(
                text = stringResource(R.string.mode_day_picker_locked),
                color = IsaColors.Lattice,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun StandardsPanel(
    standards: Map<String, HabitStandard>,
    onStandardChanged: (String, String, String) -> Unit,
    onFillDefaults: () -> Unit,
) {
    PulpitPanel(label = stringResource(R.string.mode_standards_label)) {
        Text(
            text = stringResource(R.string.mode_standards_hint),
            color = IsaColors.Lattice,
            style = MaterialTheme.typography.labelSmall,
        )
        Habit.entries.forEach { habit ->
            val current = standards[habit.code] ?: HabitStandard()
            HabitStandardRow(
                habit = habit,
                current = current,
                onChanged = onStandardChanged,
            )
        }
        HardButton(
            label = stringResource(R.string.mode_standards_reset),
            onClick = onFillDefaults,
        )
    }
}

@Composable
private fun HabitStandardRow(
    habit: Habit,
    current: HabitStandard,
    onChanged: (String, String, String) -> Unit,
) {
    var standardDraft by rememberSaveable(habit.code, current.standard) {
        mutableStateOf(current.standard)
    }
    var mvdDraft by rememberSaveable(habit.code, current.mvd) {
        mutableStateOf(current.mvd)
    }
    val scope = rememberCoroutineScope()
    val isCore = habit == Habit.Generations
    val frameColor = if (isCore) IsaColors.Approve else IsaColors.Steel
    val defaults = HabitStandardDefaults.defaultFor(habit)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = if (isCore) 2.dp else 1.dp, color = frameColor)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${habit.code}·${habit.displayName}",
                color = IsaColors.LiveMetal,
                style = MaterialTheme.typography.labelMedium,
            )
            if (isCore) {
                Text(
                    text = stringResource(R.string.mode_standards_core_note),
                    color = IsaColors.Approve,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        StandardField(
            label = stringResource(R.string.mode_standards_field_standard),
            value = standardDraft,
            placeholder = defaults.standard,
            onValueChange = {
                standardDraft = it
                scope.launch {
                    delay(600)
                    if (standardDraft == it) {
                        onChanged(habit.code, standardDraft, mvdDraft)
                    }
                }
            },
            accent = IsaColors.Approve,
        )
        StandardField(
            label = stringResource(R.string.mode_standards_field_mvd),
            value = mvdDraft,
            placeholder = defaults.mvd,
            onValueChange = {
                mvdDraft = it
                scope.launch {
                    delay(600)
                    if (mvdDraft == it) {
                        onChanged(habit.code, standardDraft, mvdDraft)
                    }
                }
            },
            accent = IsaColors.Caution,
        )
    }
}

@Composable
private fun StandardField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    accent: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            color = accent,
            style = MaterialTheme.typography.labelSmall,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = IsaColors.Steel)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            if (value.isEmpty() && placeholder.isNotBlank()) {
                Text(
                    text = placeholder,
                    color = IsaColors.Lattice,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = IsaColors.LiveMetal),
                keyboardOptions = KeyboardOptions.Default,
                cursorBrush = androidx.compose.ui.graphics.SolidColor(accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
