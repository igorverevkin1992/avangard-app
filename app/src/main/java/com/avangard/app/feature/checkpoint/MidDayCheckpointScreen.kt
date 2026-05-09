package com.avangard.app.feature.checkpoint

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.avangard.app.R
import com.avangard.app.feature.report.morning.ConfirmButton
import com.avangard.app.feature.report.morning.ErrorBanner
import com.avangard.app.ui.theme.MachineColors

@Composable
fun MidDayCheckpointScreen(
    onCompleted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MidDayCheckpointViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { onCompleted() }
    }

    MidDayCheckpointContent(
        state = state,
        onSelectInProgress = viewModel::selectInProgress,
        onSelectBlocked = viewModel::selectBlocked,
        onActionChange = viewModel::onActionChange,
        onSubmit = viewModel::submit,
        modifier = modifier,
    )
}

@Composable
internal fun MidDayCheckpointContent(
    state: MidDayCheckpointState,
    onSelectInProgress: () -> Unit,
    onSelectBlocked: () -> Unit,
    onActionChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MachineColors.Anthracite)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = stringResource(R.string.midday_directive),
            color = MachineColors.Ivory,
            style = MaterialTheme.typography.headlineLarge,
        )

        state.todayArtifact?.takeIf { it.isNotBlank() }?.let { artifact ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 1.dp, color = MachineColors.WarmGray)
                    .padding(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.dashboard_target_label),
                    color = MachineColors.WarmGray,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = artifact,
                    color = MachineColors.Ivory,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } ?: ShiftClosedNotice()

        if (state.isShiftOpen) {
            ChoiceSelector(
                choice = state.choice,
                onSelectInProgress = onSelectInProgress,
                onSelectBlocked = onSelectBlocked,
            )

            if (state.showActionField) {
                UnblockingActionField(
                    value = state.unblockingAction,
                    onChange = onActionChange,
                )
            }
        }

        state.error?.let { ErrorBanner(it) }

        Spacer(Modifier.height(8.dp))

        ConfirmButton(
            label = stringResource(R.string.midday_action_lock_in),
            enabled = state.canSubmit,
            onClick = onSubmit,
        )
    }
}

@Composable
private fun ShiftClosedNotice() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = MachineColors.AtlasRed)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.midday_shift_closed_title),
            color = MachineColors.AtlasRed,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(R.string.midday_shift_closed_body),
            color = MachineColors.Ivory,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ChoiceSelector(
    choice: MiddayChoice,
    onSelectInProgress: () -> Unit,
    onSelectBlocked: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.midday_status_label),
            color = MachineColors.WarmGray,
            style = MaterialTheme.typography.labelLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChoiceOption(
                label = stringResource(R.string.midday_status_in_progress),
                selected = choice == MiddayChoice.InProgress,
                tint = MachineColors.ReardenCopper,
                onClick = onSelectInProgress,
                modifier = Modifier.weight(1f),
            )
            ChoiceOption(
                label = stringResource(R.string.midday_status_blocked),
                selected = choice == MiddayChoice.Blocked,
                tint = MachineColors.AtlasRed,
                onClick = onSelectBlocked,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ChoiceOption(
    label: String,
    selected: Boolean,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = if (selected) tint else MachineColors.WarmGray
    val interactionSource = remember { MutableInteractionSource() }
    Text(
        text = label,
        color = color,
        style = MaterialTheme.typography.labelLarge,
        modifier = modifier
            .border(width = if (selected) 2.dp else 1.dp, color = color)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 16.dp, horizontal = 12.dp),
    )
}

@Composable
private fun UnblockingActionField(value: String, onChange: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = MachineColors.AtlasRed)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.midday_action_label),
            color = MachineColors.AtlasRed,
            style = MaterialTheme.typography.labelLarge,
        )
        BasicTextField(
            value = value,
            onValueChange = onChange,
            cursorBrush = SolidColor(MachineColors.AtlasRed),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MachineColors.Ivory),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Default,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = MachineColors.WarmGray)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        )
    }
}
