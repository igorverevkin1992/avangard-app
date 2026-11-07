package com.itermeum.app.feature.prayer

import com.itermeum.app.core.designsystem.ImTextField
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.itermeum.app.core.database.entity.PrayerEntity
import com.itermeum.app.core.designsystem.ImHeader
import com.itermeum.app.core.designsystem.ImPanel
import com.itermeum.app.core.designsystem.ImPrimaryButton
import com.itermeum.app.core.designsystem.ImSecondaryButton
import com.itermeum.app.core.designsystem.ImSectionLabel
import com.itermeum.app.core.designsystem.ImSpacing

@Composable
fun VerifyBadge(prayer: PrayerEntity) {
    if (prayer.verify && prayer.userEditedText == null) {
        Text(stringResource(R.string.prayer_unverified), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun PrayerBookScreen(onBack: () -> Unit, onOpen: (String) -> Unit, viewModel: PrayerHubViewModel = hiltViewModel()) {
    val prayers by viewModel.prayerList.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ImSpacing.screen), verticalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
        ImHeader(stringResource(R.string.hub_book), onBack = onBack)
        Text(stringResource(R.string.prayer_book_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        prayers.forEach { p ->
            ImPanel(Modifier.fillMaxWidth().clickable { onOpen(p.id) }) {
                Text(p.title, style = MaterialTheme.typography.titleMedium)
                Text(p.status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                VerifyBadge(p)
            }
        }
    }
}

@Composable
fun PrayerItemScreen(id: String, onBack: () -> Unit, viewModel: PrayerHubViewModel = hiltViewModel()) {
    val prayer by viewModel.observePrayer(id).collectAsState(initial = null)
    val p = prayer ?: return
    var editing by remember { mutableStateOf(false) }
    var text by remember(p.id, p.userEditedText) { mutableStateOf(viewModel.displayText(p)) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ImSpacing.screen), verticalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
        ImHeader(p.title, onBack = onBack)
        VerifyBadge(p)
        if (editing) {
            ImTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), minLines = 6)
            ImSecondaryButton(stringResource(R.string.save), onClick = { viewModel.saveUserText(p.id, text); editing = false })
            ImSecondaryButton(stringResource(R.string.prayer_reset), onClick = { viewModel.saveUserText(p.id, null); editing = false })
        } else {
            ImPanel { Text(viewModel.displayText(p), style = MaterialTheme.typography.bodyLarge) }
            ImSecondaryButton(stringResource(R.string.prayer_edit), onClick = { editing = true })
        }
        // D-62: a prayer that is a norm today (morning, before work, night) is marked here.
        com.itermeum.app.core.content.NormCatalog.normOfPrayerText(p.id)?.let { normId ->
            val done by viewModel.done.collectAsState()
            ImPrimaryButton(
                if (normId in done) stringResource(R.string.marked) else stringResource(R.string.angelus_mark),
                onClick = { viewModel.markNorm(normId); onBack() },
            )
        }
        ImSectionLabel(stringResource(R.string.prayer_source))
        Text("${p.source} · ${p.status}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(p.legal, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** D-53: Saturday's «Славься, Царица» — what it is, the prayer itself, then «Помолился». */
@Composable
fun MarianSaturdayScreen(onBack: () -> Unit, viewModel: PrayerHubViewModel = hiltViewModel()) {
    val prayer by viewModel.observePrayer("salve_regina").collectAsState(initial = null)
    val done by viewModel.done.collectAsState()
    val p = prayer ?: return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ImSpacing.screen), verticalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
        ImHeader(p.title, onBack = onBack)
        Text(stringResource(R.string.marian_saturday_about), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        VerifyBadge(p)
        ImPanel { Text(viewModel.displayText(p), style = MaterialTheme.typography.bodyLarge) }
        ImPrimaryButton(
            if (com.itermeum.app.core.content.NormCatalog.MARIAN_SATURDAY in done) stringResource(R.string.marked) else stringResource(R.string.angelus_mark),
            onClick = { viewModel.markMarianSaturday(); onBack() },
        )
    }
}

/** Section 5.7: Angelus or Regina Caeli by season; counted in the 11:30–14:00 window. */
@Composable
fun AngelusScreen(onBack: () -> Unit, viewModel: PrayerHubViewModel = hiltViewModel()) {
    val prayer by viewModel.observePrayer(viewModel.angelusPrayerId()).collectAsState(initial = null)
    val done by viewModel.done.collectAsState()
    val p = prayer ?: return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ImSpacing.screen), verticalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
        ImHeader(p.title, onBack = onBack)
        VerifyBadge(p)
        ImPanel { Text(viewModel.displayText(p), style = MaterialTheme.typography.bodyLarge) }
        ImSecondaryButton(
            if (com.itermeum.app.core.content.NormCatalog.ANGELUS in done) stringResource(R.string.marked) else stringResource(R.string.angelus_mark),
            onClick = { viewModel.markAngelus(); onBack() },
            enabled = viewModel.angelusWindowOpen(),
        )
        Text(stringResource(R.string.angelus_window), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Section 5.11: before baptism — stay in place and say the spiritual communion. */
@Composable
fun MassScreen(onBack: () -> Unit, viewModel: PrayerHubViewModel = hiltViewModel()) {
    val profile by viewModel.profile.collectAsState()
    val communion by viewModel.observePrayer("spiritual_communion").collectAsState(initial = null)
    val done by viewModel.done.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ImSpacing.screen), verticalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
        ImHeader(if (profile.baptized) stringResource(R.string.hub_mass_baptized) else stringResource(R.string.hub_mass), onBack = onBack)
        Text(
            if (profile.baptized) stringResource(R.string.mass_hint_baptized) else stringResource(R.string.mass_hint_catechumen),
            style = MaterialTheme.typography.bodyLarge,
        )
        communion?.let { c -> ImPanel { Text(c.title, style = MaterialTheme.typography.titleMedium); Text(viewModel.displayText(c), style = MaterialTheme.typography.bodyLarge); VerifyBadge(c) } }
        ImSecondaryButton(if (com.itermeum.app.core.content.NormCatalog.MASS in done) stringResource(R.string.marked) else stringResource(R.string.mass_mark), onClick = { viewModel.markMass(); onBack() })
        if (profile.baptized) {
            ImSecondaryButton(stringResource(R.string.confession_mark), onClick = { viewModel.markConfession(); onBack() })
        }
        Text(stringResource(R.string.mass_weekly), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
