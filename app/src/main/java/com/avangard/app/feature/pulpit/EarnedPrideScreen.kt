package com.avangard.app.feature.pulpit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.avangard.app.R
import com.avangard.app.ui.theme.IsaColors
import kotlinx.coroutines.delay

private const val MANTRA_HOLD_MS = 2_500L

/**
 * Monochrome blocking screen shown after Core approval. Holds for 2.5s on
 * Carbon, then auto-dismisses. No user input, no animation, no sound — the
 * pause is the entire mechanic (serotonin integration window per the whitepaper).
 */
@Composable
fun EarnedPrideScreen(
    onAutoDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        delay(MANTRA_HOLD_MS)
        onAutoDismiss()
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(IsaColors.Carbon)
            .semantics { isTraversalGroup = true }
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.earned_pride_line_1),
            color = IsaColors.LiveMetal,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(R.string.earned_pride_line_2),
            color = IsaColors.LiveMetal,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(R.string.earned_pride_axiom),
            color = IsaColors.Approve,
            style = MaterialTheme.typography.headlineLarge,
        )
    }
}
