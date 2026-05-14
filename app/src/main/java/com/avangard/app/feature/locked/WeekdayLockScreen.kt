package com.avangard.app.feature.locked

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.avangard.app.R
import com.avangard.app.core.ui.components.HardButton
import com.avangard.app.ui.theme.IsaColors

/**
 * Shown when the operator reaches a Sunday-only route on a weekday — either
 * via a stale back-stack entry or a deep link. Pure dry refusal; no nav-back
 * inside the screen beyond the explicit return button.
 */
@Composable
fun WeekdayLockScreen(
    onReturn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(IsaColors.Graphite)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.weekday_lock_title),
            color = IsaColors.Signal,
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.weekday_lock_body),
            color = IsaColors.Lattice,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        HardButton(
            label = stringResource(R.string.weekday_lock_return),
            onClick = onReturn,
        )
    }
}
