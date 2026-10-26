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
import androidx.compose.material3.TextButton
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
import com.itermeum.app.core.designsystem.ImCollapsible
import com.itermeum.app.core.designsystem.ImHeader
import com.itermeum.app.core.designsystem.ImPanel
import com.itermeum.app.core.designsystem.ImSecondaryButton
import com.itermeum.app.core.designsystem.ImSectionLabel
import com.itermeum.app.core.designsystem.ImSpacing
import com.itermeum.app.core.designsystem.ImStepper
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

        ImCollapsible(stringResource(R.string.settings_day_mode)) {
            ImTimeField(stringResource(R.string.settings_wake_weekday), p.wakeTime, { t -> viewModel.update { it.copy(wakeTime = t) } }, Modifier.fillMaxWidth())
            ImTimeField(stringResource(R.string.settings_wake_weekend), p.wakeTimeWeekend, { t -> viewModel.update { it.copy(wakeTimeWeekend = t) } }, Modifier.fillMaxWidth())
            ImTimeField(stringResource(R.string.settings_bed), p.bedTime, { t -> viewModel.update { it.copy(bedTime = t) } }, Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
                ImTimeField(stringResource(R.string.settings_work_start), p.workStart, { t -> viewModel.update { it.copy(workStart = t) } }, Modifier.weight(1f))
                ImTimeField(stringResource(R.string.settings_work_end), p.workEnd, { t -> viewModel.update { it.copy(workEnd = t) } }, Modifier.weight(1f))
            }
        }

        ImCollapsible(stringResource(R.string.settings_group_alarm)) {
            ImSectionLabel(stringResource(R.string.settings_wake_method))
            Row(horizontalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
                ImSecondaryButton(stringResource(R.string.settings_wake_qr), { viewModel.update { it.copy(wakeProofMethod = "QR") } }, Modifier.weight(1f), enabled = p.wakeProofMethod != "QR")
                ImSecondaryButton(stringResource(R.string.settings_wake_steps), { viewModel.update { it.copy(wakeProofMethod = "STEPS") } }, Modifier.weight(1f), enabled = p.wakeProofMethod != "STEPS")
            }
            ImSecondaryButton(stringResource(R.string.settings_show_qr), { showQr = !showQr })
            if (showQr) {
                val qr by viewModel.qr.collectAsState()
                qr?.let { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Image(it.asImageBitmap(), contentDescription = stringResource(R.string.settings_show_qr), modifier = Modifier.size(240.dp)) } }
            }

            ImSectionLabel(stringResource(R.string.settings_alarm_sound))
            Row(horizontalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
                (1..3).forEach { i ->
                    ImSecondaryButton(stringResource(R.string.settings_bell_n, i), { viewModel.update { it.copy(alarmSound = i) } }, Modifier.weight(1f), enabled = p.alarmSound != i)
                }
            }
            ImStepper(stringResource(R.string.settings_alarm_volume), p.alarmVolume, 10..100, { v -> viewModel.update { it.copy(alarmVolume = v) } }, step = 10, suffix = " %")
            ImSecondaryButton(stringResource(R.string.settings_test_alarm), viewModel::testAlarm)
        }

        ImCollapsible(stringResource(R.string.settings_group_norms)) {
            ImStepper(stringResource(R.string.settings_outreach_target), p.outreachTarget, 1..20, { n -> viewModel.update { it.copy(outreachTarget = n) } })

            val normList by viewModel.normList.collectAsState()
            normList.filter { !it.requiresBaptism || p.baptized }.forEach { n ->
                ImPanel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(n.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        // The core (wake, frog, Examen) cannot be switched off (section 4).
                        androidx.compose.material3.Switch(checked = n.enabled, enabled = !n.isCore, onCheckedChange = { viewModel.setNormEnabled(n.id, it) })
                    }
                    if (n.enabled && n.defaultTime != null && !n.followsWakeTime) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ImTimeField(stringResource(R.string.settings_norm_time), n.userTime ?: n.defaultTime, { t -> viewModel.setNormTime(n.id, t) }, Modifier.weight(1f))
                            if (n.userTime != null) TextButton(onClick = { viewModel.setNormTime(n.id, null) }) { Text(stringResource(R.string.settings_norm_time_reset)) }
                        }
                    }
                }
            }
        }

        ImCollapsible(stringResource(R.string.settings_group_church)) {
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

            ImSectionLabel(stringResource(R.string.settings_baptized))
            if (p.baptized) {
                Text(stringResource(R.string.baptized_since, p.baptismDate?.toString() ?: ""), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                ImStepper(stringResource(R.string.settings_confession_every), p.confessionEveryDays, 7..90, { n -> viewModel.update { it.copy(confessionEveryDays = n) } }, step = 7)
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
        }

        ImCollapsible(stringResource(R.string.settings_group_notifications)) {
            ImSectionLabel(stringResource(R.string.settings_tone))
            Row(horizontalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
                listOf("CALM" to R.string.tone_calm, "FIRM" to R.string.tone_firm, "STERN" to R.string.tone_stern).forEach { (code, res) ->
                    ImSecondaryButton(stringResource(res), { viewModel.update { it.copy(notificationTone = code) } }, Modifier.weight(1f), enabled = p.notificationTone != code)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_examen_lock), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                androidx.compose.material3.Switch(checked = p.examenPinEnabled, onCheckedChange = { v -> viewModel.update { it.copy(examenPinEnabled = v) } })
            }
            val light by viewModel.lightTheme.collectAsState()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_light_theme), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                androidx.compose.material3.Switch(checked = light, onCheckedChange = viewModel::setLightTheme)
            }
        }

        ImCollapsible(stringResource(R.string.settings_travel)) {
            val travel by viewModel.travel.collectAsState()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_travel_auto), style = MaterialTheme.typography.bodyLarge)
                androidx.compose.material3.Switch(checked = p.travelAuto, onCheckedChange = { v -> viewModel.update { it.copy(travelAuto = v) } })
            }
            if (travel == null) ImSecondaryButton(stringResource(R.string.settings_travel_start), viewModel::startTravel)
            else ImSecondaryButton(stringResource(R.string.settings_travel_end_since, travel!!.startDate.toString()), viewModel::endTravel)
        }

        ImCollapsible(stringResource(R.string.settings_hyperos)) {
            HyperOsChecklist()
        }

        ImCollapsible(stringResource(R.string.backup_title)) {
            BackupSection()
        }

        ImPanel(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenAbout)) {
            Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.bodyLarge)
        }
    }
}
