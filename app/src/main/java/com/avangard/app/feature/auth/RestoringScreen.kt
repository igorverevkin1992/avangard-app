package com.avangard.app.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.avangard.app.R
import com.avangard.app.core.ui.components.HardButton
import com.avangard.app.core.ui.components.HardButtonVariant
import com.avangard.app.ui.theme.IsaColors

@Composable
fun RestoringScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RestoringViewModel = hiltViewModel(),
) {
    val stage by viewModel.stage.collectAsState()

    LaunchedEffect(stage) {
        if (stage is RestoringStage.Done) onDone()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(IsaColors.Graphite)
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (stage) {
            RestoringStage.Running -> {
                CircularProgressIndicator(color = IsaColors.LiveMetal)
                Text(
                    text = stringResource(R.string.restoring_title),
                    color = IsaColors.LiveMetal,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.restoring_subtitle),
                    color = IsaColors.Lattice,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
            is RestoringStage.Failed -> {
                Text(
                    text = stringResource(R.string.restoring_failed_title),
                    color = IsaColors.Signal,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.restoring_failed_body),
                    color = IsaColors.Lattice,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                HardButton(
                    label = stringResource(R.string.restoring_failed_continue),
                    onClick = { viewModel.continueWithoutRestore() },
                    variant = HardButtonVariant.Primary,
                )
                HardButton(
                    label = stringResource(R.string.restoring_failed_sign_out),
                    onClick = { viewModel.signOut() },
                    variant = HardButtonVariant.Danger,
                )
            }
            RestoringStage.Done -> Unit
        }
    }
}
