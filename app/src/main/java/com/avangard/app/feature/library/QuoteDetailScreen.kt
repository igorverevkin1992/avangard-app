package com.avangard.app.feature.library

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.avangard.app.R
import com.avangard.app.core.domain.model.Quote
import com.avangard.app.core.ui.components.HardButton
import com.avangard.app.ui.theme.IsaColors

@Composable
fun QuoteDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QuoteDetailViewModel = hiltViewModel(),
) {
    val quote by viewModel.quote.collectAsState()
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(IsaColors.Graphite)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (quote == null) {
            Text(
                text = stringResource(R.string.library_quote_not_found),
                color = IsaColors.Lattice,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            val q = quote!!
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .border(width = 1.dp, color = IsaColors.Steel)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = q.text,
                    color = IsaColors.LiveMetal,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.padding(top = 8.dp))
                Text(
                    text = q.source,
                    color = IsaColors.Lattice,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HardButton(
                    label = stringResource(R.string.library_share),
                    onClick = { shareQuote(context, q) },
                    modifier = Modifier.weight(1f),
                )
                HardButton(
                    label = stringResource(R.string.library_back),
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun shareQuote(context: android.content.Context, quote: Quote) {
    // Plain ACTION_SEND with text/plain — opens the system share-sheet so
    // the user picks Telegram / messaging / clipboard themselves.
    val payload = "${quote.text}\n\n— ${quote.source}"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, payload)
    }
    val chooser = Intent.createChooser(intent, null).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}
