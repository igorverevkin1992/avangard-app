package com.avangard.app.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.avangard.app.core.domain.model.VirtueTag
import com.avangard.app.core.ui.components.PulpitPanel
import com.avangard.app.ui.theme.IsaColors

@Composable
fun LibraryScreen(
    onOpenVirtue: (VirtueTag) -> Unit,
    onOpenQuote: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val liveQuoteOfDay by viewModel.quoteOfDay.collectAsState()
    val pinned by viewModel.pinnedQuotes.collectAsState()
    val quoteOfDay = liveQuoteOfDay ?: state.quoteOfDay
    LibraryContent(
        quoteOfDay = quoteOfDay,
        counts = state.counts,
        history = state.history,
        pinned = pinned,
        onOpenVirtue = onOpenVirtue,
        onOpenQuote = onOpenQuote,
        modifier = modifier,
    )
}

@Composable
internal fun LibraryContent(
    quoteOfDay: Quote?,
    counts: Map<VirtueTag, Int>,
    history: List<HistoryEntry> = emptyList(),
    pinned: List<Quote> = emptyList(),
    onOpenVirtue: (VirtueTag) -> Unit,
    onOpenQuote: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(IsaColors.Graphite)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.library_header),
            color = IsaColors.LiveMetal,
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() },
        )

        if (pinned.isNotEmpty()) {
            PrinciplesPanel(pinned = pinned, onOpenQuote = onOpenQuote)
        }

        if (quoteOfDay != null) {
            QuoteOfDayPanel(
                quote = quoteOfDay,
                onClick = { onOpenQuote(quoteOfDay.id) },
            )
        }

        if (history.isNotEmpty()) {
            QuoteHistoryPanel(history = history, onOpenQuote = onOpenQuote)
        }

        PulpitPanel(label = stringResource(R.string.library_virtues_label)) {
            VirtueTag.entries.forEach { virtue ->
                VirtueRow(
                    virtue = virtue,
                    count = counts[virtue] ?: 0,
                    onClick = { onOpenVirtue(virtue) },
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.library_footer_note),
            color = IsaColors.Lattice,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun PrinciplesPanel(pinned: List<Quote>, onOpenQuote: (Int) -> Unit) {
    PulpitPanel(label = stringResource(R.string.library_principles_label)) {
        pinned.forEach { quote ->
            val interactionSource = remember(quote.id) { MutableInteractionSource() }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 1.dp, color = IsaColors.Approve)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onOpenQuote(quote.id) },
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = quote.text,
                    color = IsaColors.LiveMetal,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    text = quote.source,
                    color = IsaColors.Lattice,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun QuoteHistoryPanel(
    history: List<HistoryEntry>,
    onOpenQuote: (Int) -> Unit,
) {
    PulpitPanel(label = stringResource(R.string.library_history_label)) {
        val fmt = java.time.format.DateTimeFormatter.ofPattern(
            "dd.MM",
            java.util.Locale("ru", "RU"),
        )
        history.forEach { entry ->
            val interactionSource = remember(entry.date) { MutableInteractionSource() }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 1.dp, color = IsaColors.Steel)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onOpenQuote(entry.quote.id) },
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = entry.date.format(fmt),
                    color = IsaColors.Lattice,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    text = entry.quote.text,
                    color = IsaColors.LiveMetal,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun QuoteOfDayPanel(quote: Quote, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    PulpitPanel(label = stringResource(R.string.library_quote_of_day_label)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = quote.text,
                color = IsaColors.LiveMetal,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = quote.source,
                color = IsaColors.Lattice,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun VirtueRow(virtue: VirtueTag, count: Int, onClick: () -> Unit) {
    val interactionSource = remember(virtue) { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = IsaColors.Steel)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(virtue.labelRes()),
            color = IsaColors.LiveMetal,
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = count.toString(),
            color = IsaColors.Approve,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

internal fun VirtueTag.labelRes(): Int = when (this) {
    VirtueTag.RATIONALITY -> R.string.virtue_rationality
    VirtueTag.INDEPENDENCE -> R.string.virtue_independence
    VirtueTag.HONESTY -> R.string.virtue_honesty
    VirtueTag.JUSTICE -> R.string.virtue_justice
}
