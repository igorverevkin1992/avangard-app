package com.itermeum.app.feature.prayer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.itermeum.app.R
import com.itermeum.app.core.designsystem.ImHeader
import com.itermeum.app.core.designsystem.ImPanel
import com.itermeum.app.core.designsystem.ImSpacing
import com.itermeum.app.feature.work.FlowRowCompat

/** Section 5.8: situation grid; long press = «произнёс». No counter on the main screen. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun JaculatoriaScreen(initialSituation: String?, onBack: () -> Unit, viewModel: PrayerHubViewModel = hiltViewModel()) {
    var situation by remember { mutableStateOf(initialSituation ?: viewModel.situations.first().id) }
    val items by viewModel.observeJaculatoria(situation).collectAsState(initial = emptyList())
    var toast by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ImSpacing.screen), verticalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
        ImHeader(stringResource(R.string.hub_jaculatory), onBack = onBack)
        FlowRowCompat {
            viewModel.situations.forEach { s -> FilterChip(selected = s.id == situation, onClick = { situation = s.id }, label = { Text(s.title) }) }
        }
        Text(stringResource(R.string.jaculatoria_hint), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        toast?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
        items.forEach { j ->
            ImPanel(Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = { viewModel.said(j); toast = "✓ ${j.text}" })) {
                Text(j.text, style = MaterialTheme.typography.titleMedium)
                Text(j.source + if (j.verify) " · ссылка не сверена" else "", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
