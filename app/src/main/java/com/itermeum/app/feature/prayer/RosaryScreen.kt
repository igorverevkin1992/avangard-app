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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.itermeum.app.core.designsystem.ImSecondaryButton
import com.itermeum.app.core.designsystem.ImSectionLabel
import com.itermeum.app.core.designsystem.ImSpacing

/** One bead of the chaplet: a prayer said [times] times, a tap for each. */
private data class Bead(val label: Int, val prayers: List<String>, val times: Int = 1)

/** Вступление (D-56): крест и посвящение, Никейский Символ веры (D-66), Отче наш, 3 × Радуйся, Слава. */
private val OPENING = listOf(
    Bead(R.string.rosary_bead_cross, listOf("sign_of_cross", "rosary_intro")),
    Bead(R.string.rosary_bead_creed, listOf("creed_nicene")),
    Bead(R.string.rosary_bead_our_father, listOf("our_father")),
    Bead(R.string.rosary_bead_three_aves, listOf("hail_mary"), times = 3),
    Bead(R.string.rosary_bead_glory, listOf("glory_be")),
)

/** A decade: Отче наш, 10 × Радуйся, Слава with the two short prayers. */
private val DECADE = listOf(
    Bead(R.string.rosary_bead_our_father, listOf("our_father")),
    Bead(R.string.rosary_bead_ave, listOf("hail_mary"), times = 10),
    Bead(R.string.rosary_bead_glory_end, listOf("glory_be", "immaculate", "fatima")),
)

/**
 * Section 5.10, reworked by D-56 after the parish order of the Rosary: the
 * chaplet is walked bead by bead — one tap for each prayer, as a finger moves
 * on the beads. Opening, five decades (the mystery stays on top while its
 * prayers are said), then «Радуйся, Царица». Progress is kept per logical day.
 */
@Composable
fun RosaryScreen(onBack: () -> Unit, viewModel: RosaryViewModel = hiltViewModel(), book: PrayerHubViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsState()
    val prayers by book.prayerList.collectAsState()
    fun text(id: String) = prayers.firstOrNull { it.id == id }?.let { book.displayText(it) } ?: ""
    // step: 1..5 = decade n, 6 = closing. rememberSaveable: turning the phone or leaving the app keeps the place.
    val step = if (ui.decadesDone >= 5) 6 else ui.decadesDone + 1
    var opened by rememberSaveable { mutableIntStateOf(0) }
    val inOpening = opened == 0 && ui.decadesDone == 0
    val beads = if (inOpening) OPENING else DECADE
    var bead by rememberSaveable(step, inOpening) { mutableIntStateOf(0) }
    var count by rememberSaveable(step, inOpening, bead) { mutableIntStateOf(0) }

    fun advance() {
        val b = beads[bead]
        if (count + 1 < b.times) { count++; return }
        if (bead + 1 < beads.size) { bead++; return }
        if (inOpening) opened = 1 else viewModel.completeDecade(step)
    }
    fun back() {
        if (count > 0) { count--; return }
        if (bead > 0) bead--
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ImSpacing.screen), verticalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
        ImHeader(stringResource(R.string.hub_rosary), onBack = onBack)
        Text(ui.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.rosary_progress, ui.decadesDone), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (step <= 5 || inOpening) {
            if (inOpening) {
                ImSectionLabel(stringResource(R.string.rosary_opening))
                Text(stringResource(R.string.rosary_about), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val i = step - 1
                ImSectionLabel(stringResource(R.string.rosary_decade, step))
                ImPanel {
                    Text(ui.mysteries.getOrElse(i) { "" }, style = MaterialTheme.typography.titleLarge)
                    Text(ui.contemplations.getOrElse(i) { "" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val b = beads[bead]
            // The bead: tap anywhere on it after saying the prayer.
            ImPanel(Modifier.fillMaxWidth().clickable { advance() }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(b.label), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (b.times > 1) {
                        Text("${count + 1}/${b.times}", maxLines = 1, softWrap = false, style = MaterialTheme.typography.headlineMedium.copy(fontFamily = ImMonoFamily))
                    }
                }
                b.prayers.forEach { Text(text(it), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = ImSpacing.grid)) }
                Text(stringResource(R.string.rosary_tap_next), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(top = ImSpacing.grid))
            }
            Text(stringResource(R.string.rosary_bead_of, bead + 1, beads.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (bead > 0 || count > 0) TextButton(onClick = { back() }) { Text(stringResource(R.string.rosary_back)) }
            ImSecondaryButton(stringResource(R.string.rosary_pause), onClick = onBack)
        } else {
            ImSectionLabel(stringResource(R.string.rosary_closing))
            ImPanel { Text(text("salve_regina"), style = MaterialTheme.typography.bodyLarge) }
            Text(stringResource(R.string.rosary_complete), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
            ImSecondaryButton(stringResource(R.string.done), onClick = onBack)
        }
    }
}
