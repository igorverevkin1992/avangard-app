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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.itermeum.app.R
import com.itermeum.app.core.content.NormCatalog
import com.itermeum.app.core.designsystem.ImHeader
import com.itermeum.app.core.designsystem.ImPanel
import com.itermeum.app.core.designsystem.ImSpacing
import java.time.DayOfWeek

/** Section 9 «Молитва» hub. */
@Composable
fun PrayerScreen(
    onBook: () -> Unit, onRosary: () -> Unit, onJaculatory: () -> Unit, onGospel: () -> Unit,
    onSrs: () -> Unit, onCalendar: () -> Unit, onMental: () -> Unit, onExamen: () -> Unit, onAngelus: () -> Unit, onMass: () -> Unit,
    viewModel: PrayerHubViewModel = hiltViewModel(),
) {
    val done by viewModel.done.collectAsState()
    val arrows by viewModel.jaculatoriaToday.collectAsState()
    val due by viewModel.dueCards.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val regina = viewModel.liturgical.reginaCaeli
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ImSpacing.screen), verticalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
        ImHeader(stringResource(R.string.nav_prayer))
        Text(viewModel.liturgical.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

        HubRow(stringResource(R.string.hub_mental), NormCatalog.MENTAL_PRAYER in done, onMental)
        HubRow(if (regina) stringResource(R.string.hub_regina) else stringResource(R.string.hub_angelus), NormCatalog.ANGELUS in done, onAngelus)
        HubRow(stringResource(R.string.hub_gospel), NormCatalog.GOSPEL in done && NormCatalog.READING in done, onGospel)
        HubRow(stringResource(R.string.hub_srs) + if (due > 0) " · $due" else "", NormCatalog.SRS in done, onSrs)
        HubRow(stringResource(R.string.hub_jaculatory) + " · $arrows/3", NormCatalog.JACULATORIA in done, onJaculatory)
        HubRow(stringResource(R.string.hub_rosary), NormCatalog.ROSARY in done, onRosary)
        HubRow(if (profile.baptized) stringResource(R.string.hub_mass_baptized) else stringResource(R.string.hub_mass), NormCatalog.MASS in done, onMass)
        if (viewModel.date.dayOfWeek == DayOfWeek.SATURDAY) {
            HubRow(stringResource(R.string.hub_marian), NormCatalog.MARIAN_SATURDAY in done) { viewModel.markMarianSaturday(); onBook() }
        }
        HubRow(stringResource(R.string.hub_examen), NormCatalog.EXAMEN in done, onExamen)
        HubRow(stringResource(R.string.hub_book), null, onBook)
        HubRow(stringResource(R.string.hub_calendar), null, onCalendar)
    }
}

@Composable
private fun HubRow(title: String, done: Boolean?, onClick: () -> Unit) {
    ImPanel(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (done != null) Text(if (done) "■" else "□", color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
