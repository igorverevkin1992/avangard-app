package com.itermeum.app.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.itermeum.app.R
import com.itermeum.app.core.designsystem.ImHeader
import com.itermeum.app.core.designsystem.ImPanel
import com.itermeum.app.core.designsystem.ImSecondaryButton
import com.itermeum.app.core.designsystem.ImSectionLabel
import com.itermeum.app.core.designsystem.ImSpacing
import com.itermeum.app.core.designsystem.ImTimeField
import com.itermeum.app.feature.onboarding.HyperOsChecklist

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenAbout: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val p by viewModel.profile.collectAsState()
    var showQr by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ImSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(ImSpacing.grid),
    ) {
        ImHeader(stringResource(R.string.settings_title), onBack = onBack)

        ImSectionLabel(stringResource(R.string.settings_day_mode))
        ImTimeField(stringResource(R.string.settings_wake_weekday), p.wakeTime, { t -> viewModel.update { it.copy(wakeTime = t) } }, Modifier.fillMaxWidth())
        ImTimeField(stringResource(R.string.settings_wake_weekend), p.wakeTimeWeekend, { t -> viewModel.update { it.copy(wakeTimeWeekend = t) } }, Modifier.fillMaxWidth())
        ImTimeField(stringResource(R.string.settings_bed), p.bedTime, { t -> viewModel.update { it.copy(bedTime = t) } }, Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
            ImTimeField(stringResource(R.string.settings_work_start), p.workStart, { t -> viewModel.update { it.copy(workStart = t) } }, Modifier.weight(1f))
            ImTimeField(stringResource(R.string.settings_work_end), p.workEnd, { t -> viewModel.update { it.copy(workEnd = t) } }, Modifier.weight(1f))
        }

        ImSectionLabel(stringResource(R.string.settings_wake_method))
        Row(horizontalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
            ImSecondaryButton(stringResource(R.string.settings_wake_qr), { viewModel.update { it.copy(wakeProofMethod = "QR") } }, Modifier.weight(1f), enabled = p.wakeProofMethod != "QR")
            ImSecondaryButton(stringResource(R.string.settings_wake_steps), { viewModel.update { it.copy(wakeProofMethod = "STEPS") } }, Modifier.weight(1f), enabled = p.wakeProofMethod != "STEPS")
        }
        ImSecondaryButton(stringResource(R.string.settings_show_qr), { showQr = !showQr })
        if (showQr) {
            viewModel.qrBitmap()?.let { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.size(240.dp)) } }
        }

        ImSectionLabel(stringResource(R.string.settings_alarm_sound))
        Row(horizontalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
            (1..3).forEach { i ->
                ImSecondaryButton("♩ $i", { viewModel.update { it.copy(alarmSound = i) } }, Modifier.weight(1f), enabled = p.alarmSound != i)
            }
        }
        OutlinedTextField(
            value = p.alarmVolume.toString(),
            onValueChange = { v -> v.toIntOrNull()?.coerceIn(10, 100)?.let { vol -> viewModel.update { it.copy(alarmVolume = vol) } } },
            label = { Text(stringResource(R.string.settings_alarm_volume)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        ImSecondaryButton(stringResource(R.string.settings_test_alarm), viewModel::testAlarm)

        ImSectionLabel(stringResource(R.string.settings_outreach_target))
        OutlinedTextField(
            value = p.outreachTarget.toString(),
            onValueChange = { v -> v.toIntOrNull()?.coerceIn(1, 20)?.let { n -> viewModel.update { it.copy(outreachTarget = n) } } },
            label = { Text(stringResource(R.string.settings_outreach_target)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )

        ImSectionLabel(stringResource(R.string.settings_mass_time))
        Row(horizontalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
            ImTimeField(stringResource(R.string.mass_time_saturday), p.massTimeSaturday, { t -> viewModel.update { it.copy(massTimeSaturday = t) } }, Modifier.weight(1f))
            ImTimeField(stringResource(R.string.mass_time_sunday), p.massTimeSunday, { t -> viewModel.update { it.copy(massTimeSunday = t) } }, Modifier.weight(1f))
        }
        Text(stringResource(R.string.mass_usual_day), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
            ImSecondaryButton(stringResource(R.string.mass_saturday), { viewModel.update { it.copy(massDay = 6) } }, Modifier.weight(1f), enabled = p.massDay != 6)
            ImSecondaryButton(stringResource(R.string.mass_sunday), { viewModel.update { it.copy(massDay = 7) } }, Modifier.weight(1f), enabled = p.massDay != 7)
        }

        ImSectionLabel(stringResource(R.string.settings_tone))
        Row(horizontalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
            listOf("CALM" to R.string.tone_calm, "FIRM" to R.string.tone_firm, "STERN" to R.string.tone_stern).forEach { (code, res) ->
                ImSecondaryButton(stringResource(res), { viewModel.update { it.copy(notificationTone = code) } }, Modifier.weight(1f), enabled = p.notificationTone != code)
            }
        }

        ImSectionLabel(stringResource(R.string.settings_travel))
        val travel by viewModel.travel.collectAsState()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_travel_auto), style = MaterialTheme.typography.bodyLarge)
            androidx.compose.material3.Switch(checked = p.travelAuto, onCheckedChange = { v -> viewModel.update { it.copy(travelAuto = v) } })
        }
        if (travel == null) ImSecondaryButton(stringResource(R.string.settings_travel_start), viewModel::startTravel)
        else ImSecondaryButton(stringResource(R.string.settings_travel_end) + " (с ${travel!!.startDate})", viewModel::endTravel)

        ImSectionLabel(stringResource(R.string.settings_baptized))
        if (p.baptized) {
            Text(stringResource(R.string.baptized_since, p.baptismDate?.toString() ?: ""), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(
                value = p.confessionEveryDays.toString(),
                onValueChange = { v -> v.toIntOrNull()?.coerceIn(7, 90)?.let { n -> viewModel.update { it.copy(confessionEveryDays = n) } } },
                label = { Text(stringResource(R.string.settings_confession_every)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
        } else {
            var bDate by remember { mutableStateOf("") }
            var bWord by remember { mutableStateOf("") }
            var bError by remember { mutableStateOf(false) }
            Text(stringResource(R.string.settings_baptized_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(value = bDate, onValueChange = { bDate = it }, label = { Text(stringResource(R.string.settings_baptism_date)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = bWord, onValueChange = { bWord = it; bError = false }, label = { Text(stringResource(R.string.settings_baptism_word)) }, singleLine = true, isError = bError, modifier = Modifier.fillMaxWidth())
            ImSecondaryButton(stringResource(R.string.settings_baptized), onClick = {
                val d = runCatching { java.time.LocalDate.parse(bDate) }.getOrNull()
                if (d == null || !viewModel.markBaptized(d, bWord)) bError = true
            })
        }

        ImSectionLabel(stringResource(R.string.settings_hyperos))
        HyperOsChecklist()

        BackupSection()

        ImPanel(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenAbout)) {
            Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.bodyLarge)
        }
    }
}
