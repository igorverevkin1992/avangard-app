package com.avangard.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_header),
            color = IsaColors.LiveMetal,
            style = MaterialTheme.typography.headlineLarge,
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
