package com.avangard.app.feature.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.avangard.app.R
import com.avangard.app.core.ui.components.OscilloscopeChart
import com.avangard.app.ui.theme.MachineColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val journalDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd")

@Composable
fun AnalyticsScreen(
    modifier: Modifier = Modifier,
    viewModel: AnalyticsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    AnalyticsContent(state = state, modifier = modifier)
}

@Composable
internal fun AnalyticsContent(
    state: AnalyticsState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MachineColors.Anthracite)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Header(state)
        OscilloscopeChart(
            points = state.points,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.6f)
                .border(width = 1.dp, color = MachineColors.WarmGray)
                .padding(8.dp),
        )
        JournalTable(entries = state.entries)
    }
}

@Composable
private fun Header(state: AnalyticsState) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        StatBlock(
            label = stringResource(R.string.analytics_recorded),
            value = state.totalRecorded.toString(),
        )
        StatBlock(
            label = stringResource(R.string.analytics_completed),
            value = state.totalCompleted.toString(),
        )
        StatBlock(
            label = stringResource(R.string.analytics_rate),
            value = "${(state.completionRate * 100).toInt()}%",
        )
    }
}

@Composable
private fun StatBlock(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            color = MachineColors.WarmGray,
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            text = value,
            color = MachineColors.ReardenCopper,
            style = MaterialTheme.typography.headlineLarge,
        )
    }
}

@Composable
private fun JournalTable(entries: List<JournalEntry>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = MachineColors.WarmGray),
    ) {
        TableRow(
            cells = listOf(
                stringResource(R.string.journal_col_date),
                stringResource(R.string.journal_col_status),
                stringResource(R.string.journal_col_artifact),
                stringResource(R.string.journal_col_failure),
            ),
            isHeader = true,
        )
        if (entries.isEmpty()) {
            Text(
                text = stringResource(R.string.journal_empty),
                color = MachineColors.WarmGray,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(12.dp),
            )
        } else {
            LazyColumn {
                items(entries, key = { it.dateEpoch }) { entry -> JournalRow(entry) }
            }
        }
    }
}

@Composable
private fun JournalRow(entry: JournalEntry) {
    val date = Instant.ofEpochMilli(entry.dateEpoch)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(journalDateFormatter)
    val statusLabel = entry.statusCode.toString()
    val failureLabel = if (entry.statusCode == 0) {
        if (entry.hasFailureAnalysis) "+" else "—"
    } else ""
    TableRow(
        cells = listOf(date, statusLabel, entry.artifact, failureLabel),
        isHeader = false,
        statusCode = entry.statusCode,
    )
}

@Composable
private fun TableRow(
    cells: List<String>,
    isHeader: Boolean,
    statusCode: Int = 1,
) {
    val accent = if (statusCode == 0) MachineColors.AtlasRed else MachineColors.Ivory
    val color = if (isHeader) MachineColors.WarmGray else accent
    val style = MaterialTheme.typography.labelMedium.copy(
        fontFamily = FontFamily.Monospace,
        fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = cells.getOrElse(0) { "" },
            color = color,
            style = style,
            modifier = Modifier.weight(2.5f),
        )
        Text(
            text = cells.getOrElse(1) { "" },
            color = color,
            style = style,
            modifier = Modifier.weight(0.6f),
        )
        Text(
            text = cells.getOrElse(2) { "" },
            color = color,
            style = style,
            modifier = Modifier.weight(4f),
            maxLines = 1,
        )
        Text(
            text = cells.getOrElse(3) { "" },
            color = color,
            style = style,
            modifier = Modifier.weight(0.6f),
        )
    }
}
