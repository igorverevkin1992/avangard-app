package com.avangard.app.feature.pulpit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.avangard.app.R
import com.avangard.app.core.ui.components.HardButton
import com.avangard.app.core.ui.components.HardButtonVariant
import com.avangard.app.core.ui.components.IndustrialCheckbox
import com.avangard.app.ui.theme.IsaColors

@Composable
fun AuthorisationModalScreen(
    onAuthorised: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuthorisationModalViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { onAuthorised() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(IsaColors.Carbon)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.auth_title),
            color = IsaColors.LiveMetal,
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.auth_question),
            color = IsaColors.Lattice,
            style = MaterialTheme.typography.bodyMedium,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = IsaColors.Steel)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.auth_prompt_label),
                color = IsaColors.Lattice,
                style = MaterialTheme.typography.labelMedium,
            )
            BasicTextField(
                value = state.prompt,
                onValueChange = viewModel::onPromptChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = IsaColors.LiveMetal),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Default,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 1.dp, color = IsaColors.Steel)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
            )
        }

        IndustrialCheckbox(
            label = stringResource(R.string.auth_checkbox_label),
            checked = state.authorised,
            onCheckedChange = viewModel::onAuthorisedChange,
        )

        Spacer(Modifier.height(4.dp))

        HardButton(
            label = stringResource(R.string.auth_submit),
            onClick = viewModel::submit,
            enabled = state.canSubmit,
            variant = HardButtonVariant.Primary,
        )
        HardButton(
            label = stringResource(R.string.auth_cancel),
            onClick = onCancel,
            variant = HardButtonVariant.Default,
        )
    }
}
