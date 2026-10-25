package com.itermeum.app.feature.prayer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import com.itermeum.app.R
import com.itermeum.app.core.designsystem.ImHeader
import com.itermeum.app.core.designsystem.ImMonoFamily
import com.itermeum.app.core.designsystem.ImPanel
import com.itermeum.app.core.designsystem.ImPrimaryButton
import com.itermeum.app.core.designsystem.ImSecondaryButton
import com.itermeum.app.core.designsystem.ImSectionLabel
import com.itermeum.app.core.designsystem.ImSpacing

/**
 * Section 5.10: opening → 5 decades (mystery, ≤2-line contemplation,
 * Отче наш, 10 × Радуйся Мария by tap, Слава, Фатимская) → Славься, Царица.
 * Progress is kept per logical day and can be continued in parts.
 */
@Composable
fun RosaryScreen(onBack: () -> Unit, viewModel: RosaryViewModel = hiltViewModel(), book: PrayerHubViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsState()
    val prayers by book.prayerList.collectAsState()
    fun text(id: String) = prayers.firstOrNull { it.id == id }?.let { book.displayText(it) } ?: ""
    // step: 0 = opening, 1..5 = decade n, 6 = closing, 7 = done
    var step by remember(ui.decadesDone) { mutableIntStateOf(if (ui.decadesDone >= 5) 6 else ui.decadesDone + 1) }
    var opened by remember { mutableIntStateOf(0) }
    var aves by remember(step) { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ImSpacing.screen), verticalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
        ImHeader(stringResource(R.string.hub_rosary), onBack = onBack)
        Text(ui.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        if (ui.verify) Text(stringResource(R.string.prayer_unverified), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.rosary_progress, ui.decadesDone), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (opened == 0 && ui.decadesDone == 0) {
            ImSectionLabel(stringResource(R.string.rosary_opening))
            ImPanel {
                Text(text("sign_of_cross"), style = MaterialTheme.typography.bodyLarge)
                Text(text("creed_apostles"), style = MaterialTheme.typography.bodyMedium)
                Text(text("our_father"), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.rosary_three_aves), style = MaterialTheme.typography.bodyMedium)
                Text(text("glory_be"), style = MaterialTheme.typography.bodyMedium)
            }
            ImPrimaryButton(stringResource(R.string.rosary_begin_decades), onClick = { opened = 1 })
        } else if (step in 1..5) {
            val i = step - 1
            ImSectionLabel(stringResource(R.string.rosary_decade, step))
            ImPanel {
                Text(ui.mysteries.getOrElse(i) { "" }, style = MaterialTheme.typography.titleLarge)
                Text(ui.contemplations.getOrElse(i) { "" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ImPanel { Text(text("our_father"), style = MaterialTheme.typography.bodyMedium) }
            ImPanel(Modifier.fillMaxWidth().clickable { if (aves < 10) aves++ }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.rosary_ave_tap), style = MaterialTheme.typography.titleMedium)
                    Text("$aves/10", style = MaterialTheme.typography.headlineMedium.copy(fontFamily = ImMonoFamily), color = if (aves >= 10) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
                Text(text("hail_mary"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ImPanel {
                Text(text("glory_be"), style = MaterialTheme.typography.bodyMedium)
                Text(text("fatima"), style = MaterialTheme.typography.bodyMedium)
            }
            ImPrimaryButton(stringResource(R.string.rosary_decade_done), enabled = aves >= 10, onClick = { viewModel.completeDecade(step) })
            ImSecondaryButton(stringResource(R.string.rosary_pause), onClick = onBack)
        } else {
            ImSectionLabel(stringResource(R.string.rosary_closing))
            ImPanel { Text(text("salve_regina"), style = MaterialTheme.typography.bodyLarge) }
            Text(stringResource(R.string.rosary_complete), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
            ImSecondaryButton(stringResource(R.string.done), onClick = onBack)
        }
    }
}
