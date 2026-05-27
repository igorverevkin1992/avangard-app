package com.avangard.app.feature.sabotage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.avangard.app.R
import com.avangard.app.core.ui.components.HardButton
import com.avangard.app.core.ui.components.PulpitPanel
import com.avangard.app.ui.theme.IsaColors

/**
 * Emergency module: three CBT scripts depersonalising the typical sabotage
 * triggers per the whitepaper. Pure read-only static text; no interaction
 * beyond returning to the pulpit.
 */
@Composable
fun SabotageProtocolScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(IsaColors.Graphite)
            .semantics { isTraversalGroup = true }
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.sabotage_header),
            color = IsaColors.Signal,
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() },
        )

        ScriptBlock(
            title = stringResource(R.string.sabotage_substitution_title),
            body = stringResource(R.string.sabotage_substitution_body),
        )
        ScriptBlock(
            title = stringResource(R.string.sabotage_defect_title),
            body = stringResource(R.string.sabotage_defect_body),
        )
        ScriptBlock(
            title = stringResource(R.string.sabotage_comparison_title),
            body = stringResource(R.string.sabotage_comparison_body),
        )

        HardButton(
            label = stringResource(R.string.sabotage_return),
            onClick = onClose,
        )
    }
}

@Composable
private fun ScriptBlock(title: String, body: String) {
    PulpitPanel(label = title) {
        Text(
            text = body,
            color = IsaColors.LiveMetal,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
