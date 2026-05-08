package com.avangard.app.feature.report.evening

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.avangard.app.R
import com.avangard.app.feature.report.morning.ConfirmButton
import com.avangard.app.feature.report.morning.ErrorBanner
import com.avangard.app.ui.theme.MachineColors

@Composable
fun EveningReportScreen(
    onCompleted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EveningReportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { onCompleted() }
    }

    EveningReportContent(
        state = state,
        onIsCompletedChange = viewModel::onIsCompletedChange,
        onWasteChange = viewModel::onWasteChange,
        onFailureCauseChange = viewModel::onFailureCauseChange,
        onCorrectiveActionChange = viewModel::onCorrectiveActionChange,
        onSubmit = viewModel::submit,
        modifier = modifier,
    )
}

@Composable
internal fun EveningReportContent(
    state: EveningReportState,
    onIsCompletedChange: (Boolean) -> Unit,
    onWasteChange: (Int) -> Unit,
    onFailureCauseChange: (String) -> Unit,
    onCorrectiveActionChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MachineColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = stringResource(R.string.report_evening_directive),
            color = MachineColors.TextPrimary,
            style = MaterialTheme.typography.headlineLarge,
        )

        state.todayArtifact?.let { artifact ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 1.dp, color = MachineColors.OutlineGray)
                    .padding(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.dashboard_target_label),
                    color = MachineColors.OutlineGray,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = artifact,
                    color = MachineColors.TextPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        StatusSelector(
            isCompleted = state.isCompleted,
            onChange = onIsCompletedChange,
        )

        WasteCounter(
            value = state.eliminatedWaste,
            onChange = onWasteChange,
        )

        if (state.showFailureAnalysis) {
            FailureAnalysis(
                cause = state.failureCause,
                action = state.correctiveAction,
                onCauseChange = onFailureCauseChange,
                onActionChange = onCorrectiveActionChange,
            )
        }

        state.error?.let { ErrorBanner(it) }

        Spacer(Modifier.height(8.dp))

        ConfirmButton(
            label = stringResource(R.string.action_close_shift),
            enabled = state.canSubmit,
            onClick = onSubmit,
        )
    }
}

@Composable
private fun StatusSelector(isCompleted: Boolean, onChange: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.evening_status_label),
            color = MachineColors.OutlineGray,
            style = MaterialTheme.typography.labelLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatusOption(
                label = stringResource(R.string.evening_status_completed),
                selected = isCompleted,
                tint = MachineColors.IndicationYellow,
                onClick = { onChange(true) },
                modifier = Modifier.weight(1f),
            )
            StatusOption(
                label = stringResource(R.string.evening_status_failed),
                selected = !isCompleted,
                tint = MachineColors.SignalRed,
                onClick = { onChange(false) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatusOption(
    label: String,
    selected: Boolean,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = if (selected) tint else MachineColors.OutlineGray
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
private fun WasteCounter(value: Int, onChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.evening_waste_label),
            color = MachineColors.OutlineGray,
            style = MaterialTheme.typography.labelLarge,
        )
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StepButton(symbol = "-", onClick = { onChange(value - 1) })
            BasicTextField(
                value = value.toString(),
                onValueChange = { input ->
                    onChange(input.filter(Char::isDigit).toIntOrNull() ?: 0)
                },
                singleLine = true,
                cursorBrush = SolidColor(MachineColors.IndicationYellow),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MachineColors.TextPrimary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier
                    .weight(1f)
                    .border(width = 1.dp, color = MachineColors.OutlineGray)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            )
            StepButton(symbol = "+", onClick = { onChange(value + 1) })
        }
    }
}

@Composable
private fun StepButton(symbol: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Text(
        text = symbol,
        color = MachineColors.IndicationYellow,
        style = MaterialTheme.typography.headlineLarge,
        modifier = Modifier
            .border(width = 1.dp, color = MachineColors.IndicationYellow)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun FailureAnalysis(
    cause: String,
    action: String,
    onCauseChange: (String) -> Unit,
    onActionChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = MachineColors.SignalRed)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.failure_analysis_header),
            color = MachineColors.SignalRed,
            style = MaterialTheme.typography.titleLarge,
        )
        AnalysisField(
            label = stringResource(R.string.evening_failure_cause_label),
            value = cause,
            onValueChange = onCauseChange,
        )
        AnalysisField(
            label = stringResource(R.string.evening_failure_action_label),
            value = action,
            onValueChange = onActionChange,
        )
    }
}

@Composable
private fun AnalysisField(label: String, value: String, onValueChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            color = MachineColors.OutlineGray,
            style = MaterialTheme.typography.labelMedium,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            cursorBrush = SolidColor(MachineColors.SignalRed),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MachineColors.TextPrimary),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Default,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = MachineColors.OutlineGray)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        )
    }
}

