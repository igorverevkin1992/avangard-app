package com.avangard.app.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.avangard.app.R
import com.avangard.app.core.ui.components.IndustrialGauge
import com.avangard.app.core.ui.components.IndustrialToggle
import com.avangard.app.ui.theme.MachineColors
import com.avangard.app.ui.theme.MachineTheme

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    DashboardContent(
        state = state,
        onFocusToggle = viewModel::setFocusMode,
        onSilenceToggle = viewModel::setSilenceMode,
        modifier = modifier,
    )
}

@Composable
internal fun DashboardContent(
    state: DashboardState,
    onFocusToggle: (Boolean) -> Unit,
    onSilenceToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MachineColors.Background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        TargetBlock(state.targetArtifact, isFailure = state.isInitialized && !state.isCompleted)
        GaugeBlock(progress = state.progress)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            IndustrialToggle(
                label = stringResource(R.string.toggle_focus_mode),
                checked = state.focusMode,
                onCheckedChange = onFocusToggle,
            )
            IndustrialToggle(
                label = stringResource(R.string.toggle_silence),
                checked = state.silenceMode,
                onCheckedChange = onSilenceToggle,
            )
        }
    }
}

@Composable
private fun TargetBlock(target: String?, isFailure: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = MachineColors.OutlineGray)
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.dashboard_target_label),
            color = MachineColors.OutlineGray,
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = target ?: stringResource(R.string.dashboard_target_empty),
            color = if (target == null || isFailure) MachineColors.SignalRed else MachineColors.TextPrimary,
            style = MaterialTheme.typography.headlineLarge,
        )
    }
}

@Composable
private fun GaugeBlock(progress: Float) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.dashboard_progress_label),
            color = MachineColors.OutlineGray,
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.4f),
            contentAlignment = Alignment.Center,
        ) {
            IndustrialGauge(
                progress = progress,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF010006, widthDp = 360, heightDp = 700)
@Composable
private fun DashboardEmptyPreview() {
    MachineTheme {
        DashboardContent(
            state = DashboardState(),
            onFocusToggle = {},
            onSilenceToggle = {},
        )
    }
}
