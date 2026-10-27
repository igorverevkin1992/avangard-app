package com.itermeum.app.feature.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.itermeum.app.R
import com.itermeum.app.core.content.ContentAssets
import com.itermeum.app.core.content.PracticeJson
import com.itermeum.app.core.content.PracticesFile
import com.itermeum.app.core.designsystem.ImHeader
import com.itermeum.app.core.designsystem.ImPanel
import com.itermeum.app.core.designsystem.ImSectionLabel
import com.itermeum.app.core.designsystem.ImSpacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PracticesViewModel @Inject constructor(assets: ContentAssets) : ViewModel() {
    val practices: PracticesFile = assets.practices
}

/** Reference of every practice: what it is, where it comes from, how the app counts it. */
@Composable
fun PracticesScreen(onBack: () -> Unit, viewModel: PracticesViewModel = hiltViewModel()) {
    val p = viewModel.practices
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ImSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(ImSpacing.grid),
    ) {
        ImHeader(stringResource(R.string.practices_title), onBack = onBack)
        Text(p.intro, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        p.legend.forEach { (mark, meaning) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(mark, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.width(32.dp))
                Text(meaning, style = MaterialTheme.typography.bodySmall)
            }
        }
        p.sections.forEach { section ->
            ImSectionLabel(section.title)
            section.items.forEach { PracticeCard(it) }
        }
    }
}

/** Folded to its title and time; a tap opens the description. */
@Composable
private fun PracticeCard(item: PracticeJson) {
    var open by rememberSaveable(item.title) { mutableStateOf(false) }
    ImPanel(Modifier.fillMaxWidth().clickable { open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                if (item.`when`.isNotBlank()) {
                    Text(item.`when`, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(item.origin, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.tertiary)
            Text(if (open) "  ▴" else "  ▾", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.tertiary)
        }
        if (open) {
            item.text.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = ImSpacing.tight)) }
        }
    }
}
