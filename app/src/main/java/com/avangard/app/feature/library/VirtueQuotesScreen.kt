package com.avangard.app.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.avangard.app.R
import com.avangard.app.core.domain.model.Quote
import com.avangard.app.core.ui.components.HardButton
import com.avangard.app.ui.theme.IsaColors

@Composable
fun VirtueQuotesScreen(
    onBack: () -> Unit,
    onOpenQuote: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VirtueQuotesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(IsaColors.Graphite)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(state.virtue.labelRes()),
            color = IsaColors.LiveMetal,
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() },
        )
        if (state.loaded && state.quotes.isEmpty()) {
            Text(
                text = stringResource(R.string.library_empty_state),
                color = IsaColors.Lattice,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.quotes, key = { it.id }) { quote ->
                QuoteRow(quote = quote, onClick = { onOpenQuote(quote.id) })
            }
        }
        HardButton(
            label = stringResource(R.string.library_back),
            onClick = onBack,
        )
    }
}

@Composable
private fun QuoteRow(quote: Quote, onClick: () -> Unit) {
    val interactionSource = remember(quote.id) { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = IsaColors.Steel)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = quote.text,
            color = IsaColors.LiveMetal,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 4,
        )
        Text(
            text = quote.source,
            color = IsaColors.Lattice,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
