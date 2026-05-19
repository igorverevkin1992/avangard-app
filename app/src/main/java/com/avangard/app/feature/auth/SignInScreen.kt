package com.avangard.app.feature.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.avangard.app.R
import com.avangard.app.core.ui.components.HardButton
import com.avangard.app.core.ui.components.HardButtonVariant
import com.avangard.app.ui.theme.IsaColors

@Composable
fun SignInScreen(
    onSignedIn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SignInViewModel = hiltViewModel(),
) {
    val account by viewModel.account.collectAsState()
    val state by viewModel.state.collectAsState()

    // After a successful sign-in (cached or just-completed) leave the screen.
    LaunchedEffect(account) {
        if (account != null) onSignedIn()
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.onSignInResult(result.data)
        } else {
            viewModel.onSignInCancelled()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(IsaColors.Graphite)
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.auth_title),
            color = IsaColors.LiveMetal,
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.auth_subtitle),
            color = IsaColors.Lattice,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        HardButton(
            label = stringResource(R.string.auth_signin_button),
            onClick = { launcher.launch(viewModel.signInIntent()) },
            variant = HardButtonVariant.Primary,
            enabled = !state.signingIn,
        )
        if (state.errorCode != null) {
            Text(
                text = stringResource(R.string.auth_signin_failed),
                color = IsaColors.Signal,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "code=${state.errorCode}",
                color = IsaColors.Lattice,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
