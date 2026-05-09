package com.avangard.app.feature.report.morning

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.avangard.app.R
import com.avangard.app.core.domain.model.ReportError
import com.avangard.app.core.ui.components.IndustrialCheckbox
import com.avangard.app.core.ui.components.IndustrialToggle
import com.avangard.app.ui.theme.MachineColors

@Composable
fun MorningReportScreen(
    onCompleted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MorningReportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { onCompleted() }
    }

    MorningReportContent(
        state = state,
        onArtifactChange = viewModel::onArtifactChange,
        onEliminateEmail = viewModel::onEliminateEmail,
        onEliminateMeetings = viewModel::onEliminateMeetings,
        onEliminateMessengers = viewModel::onEliminateMessengers,
        onFocusModeEngaged = viewModel::onFocusModeEngaged,
        onSubmit = viewModel::submit,
        modifier = modifier,
    )
}

@Composable
internal fun MorningReportContent(
    state: MorningReportState,
    onArtifactChange: (String) -> Unit,
    onEliminateEmail: (Boolean) -> Unit,
    onEliminateMeetings: (Boolean) -> Unit,
    onEliminateMessengers: (Boolean) -> Unit,
    onFocusModeEngaged: (Boolean) -> Unit,
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
            text = stringResource(R.string.report_morning_directive),
            color = MachineColors.Ivory,
            style = MaterialTheme.typography.headlineLarge,
        )

        ArtifactField(
            value = state.artifact,
            onValueChange = onArtifactChange,
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.morning_waste_label),
                color = MachineColors.WarmGray,
                style = MaterialTheme.typography.labelLarge,
            )
            IndustrialCheckbox(
                label = stringResource(R.string.morning_waste_email),
                checked = state.eliminateEmail,
                onCheckedChange = onEliminateEmail,
            )
            IndustrialCheckbox(
                label = stringResource(R.string.morning_waste_meetings),
                checked = state.eliminateMeetings,
                onCheckedChange = onEliminateMeetings,
            )
            IndustrialCheckbox(
                label = stringResource(R.string.morning_waste_messengers),
                checked = state.eliminateMessengers,
                onCheckedChange = onEliminateMessengers,
            )
        }

        IndustrialToggle(
            label = stringResource(R.string.toggle_focus_mode),
            checked = state.focusModeEngaged,
            onCheckedChange = onFocusModeEngaged,
        )

        state.error?.let { ErrorBanner(it) }

        Spacer(Modifier.height(8.dp))

        ConfirmButton(
            label = stringResource(R.string.action_confirm),
            enabled = state.canSubmit,
            onClick = onSubmit,
        )
    }
}

@Composable
private fun ArtifactField(value: String, onValueChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.morning_artifact_field),
            color = MachineColors.WarmGray,
            style = MaterialTheme.typography.labelLarge,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            cursorBrush = SolidColor(MachineColors.ReardenCopper),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MachineColors.Ivory),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = MachineColors.WarmGray)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        )
        Text(
            text = stringResource(R.string.morning_artifact_hint),
            color = MachineColors.WarmGray,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
internal fun ErrorBanner(error: ReportError) {
    val resId = when (error) {
        ReportError.TimeSlotMismatch -> R.string.error_time_slot
        ReportError.ArtifactEmpty -> R.string.error_artifact_empty
        ReportError.ArtifactTooLong -> R.string.error_artifact_too_long
        ReportError.ArtifactInvalidShape -> R.string.error_artifact_invalid_shape
        ReportError.AlreadyInitialized -> R.string.error_already_initialized
        ReportError.NotInitialized -> R.string.error_not_initialized
        ReportError.FailureCauseTooShort -> R.string.error_failure_cause_short
        ReportError.CorrectiveActionTooShort -> R.string.error_corrective_action_short
    }
    Text(
        text = stringResource(resId),
        color = MachineColors.AtlasRed,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = MachineColors.AtlasRed)
            .padding(12.dp),
    )
}

@Composable
internal fun ConfirmButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val color = if (enabled) MachineColors.ReardenCopper else MachineColors.WarmGray
    val interactionSource = remember { MutableInteractionSource() }
    Text(
        text = label,
        color = color,
        style = MaterialTheme.typography.labelLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 2.dp, color = color)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 16.dp),
    )
}
