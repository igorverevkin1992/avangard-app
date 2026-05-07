package com.avangard.app.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.avangard.app.R
import com.avangard.app.ui.theme.MachineColors
import com.avangard.app.ui.theme.MachineTheme

@Composable
fun DashboardScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MachineColors.Background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TargetBlock()
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.dashboard_progress_label),
            color = MachineColors.OutlineGray,
            style = MaterialTheme.typography.labelLarge,
        )
        // TODO Phase 2: Gauge (Canvas) here
        Spacer(Modifier.height(8.dp))
        Text(
            text = "// TODO: ТУМБЛЕРЫ",
            color = MachineColors.OutlineGray,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun TargetBlock() {
    Column(
        modifier = Modifier
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
            text = stringResource(R.string.dashboard_target_empty),
            color = MachineColors.SignalRed,
            style = MaterialTheme.typography.headlineLarge,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF010006)
@Composable
private fun DashboardPreview() {
    MachineTheme { DashboardScreen() }
}
