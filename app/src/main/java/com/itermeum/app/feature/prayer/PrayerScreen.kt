package com.itermeum.app.feature.prayer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import com.itermeum.app.core.designsystem.ImCollapsible
import com.itermeum.app.core.designsystem.ImHeader
import com.itermeum.app.core.designsystem.ImNavRow
import com.itermeum.app.core.designsystem.ImSectionLabel
import com.itermeum.app.core.designsystem.ImSpacing
import java.time.DayOfWeek

/**
 * «Молитва» (D-33): today's prayers in the order of the day, then a folded
 * library (prayer book, memorisation, calendar).
 */
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
    val weekend = viewModel.date.dayOfWeek == DayOfWeek.SATURDAY || viewModel.date.dayOfWeek == DayOfWeek.SUNDAY
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ImSpacing.screen), verticalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
        ImHeader(stringResource(R.string.nav_prayer))
        Text(viewModel.liturgical.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

        ImSectionLabel(stringResource(R.string.prayer_today))
        ImNavRow(stringResource(R.string.hub_mental), onMental, subtitle = stringResource(R.string.prayer_mental_sub), done = NormCatalog.MENTAL_PRAYER in done)
        ImNavRow(stringResource(R.string.hub_gospel), onGospel, subtitle = stringResource(R.string.prayer_gospel_sub), done = NormCatalog.GOSPEL in done && NormCatalog.READING in done)
        ImNavRow(if (regina) stringResource(R.string.hub_regina) else stringResource(R.string.hub_angelus), onAngelus, subtitle = stringResource(R.string.angelus_window), done = NormCatalog.ANGELUS in done)
        ImNavRow(stringResource(R.string.hub_jaculatory), onJaculatory, subtitle = stringResource(R.string.prayer_arrows_sub, arrows), done = NormCatalog.JACULATORIA in done)
        ImNavRow(stringResource(R.string.hub_rosary), onRosary, done = NormCatalog.ROSARY in done)
        if (weekend) {
            ImNavRow(if (profile.baptized) stringResource(R.string.hub_mass_baptized) else stringResource(R.string.hub_mass), onMass, done = NormCatalog.MASS in done)
        }
        if (viewModel.date.dayOfWeek == DayOfWeek.SATURDAY) {
            ImNavRow(stringResource(R.string.hub_marian), { viewModel.markMarianSaturday(); onBook() }, done = NormCatalog.MARIAN_SATURDAY in done)
        }
        ImNavRow(stringResource(R.string.hub_examen), onExamen, subtitle = stringResource(R.string.prayer_examen_sub), done = NormCatalog.EXAMEN in done)

        ImCollapsible(title = stringResource(R.string.prayer_library), summary = if (due > 0) stringResource(R.string.prayer_due, due) else null, initiallyExpanded = due > 0) {
            ImNavRow(stringResource(R.string.hub_srs), onSrs, subtitle = if (due > 0) stringResource(R.string.prayer_due, due) else null, done = NormCatalog.SRS in done)
            ImNavRow(stringResource(R.string.hub_book), onBook)
            ImNavRow(stringResource(R.string.hub_calendar), onCalendar)
            if (!weekend) ImNavRow(if (profile.baptized) stringResource(R.string.hub_mass_baptized) else stringResource(R.string.hub_mass), onMass)
        }
    }
}
