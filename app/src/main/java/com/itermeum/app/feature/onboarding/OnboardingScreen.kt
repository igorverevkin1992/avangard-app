package com.itermeum.app.feature.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.itermeum.app.core.designsystem.ImPanel
import com.itermeum.app.core.designsystem.ImPrimaryButton
import com.itermeum.app.core.designsystem.ImSecondaryButton
import com.itermeum.app.core.designsystem.ImSectionLabel
import com.itermeum.app.core.designsystem.ImSpacing
import com.itermeum.app.core.designsystem.ImTimeField
import com.itermeum.app.feature.morning.QrScannerView

/** Section 13: nine steps, ≤7 minutes, not a «programme». */
@Composable
fun OnboardingScreen(onFinished: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsState()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ImSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(ImSpacing.grid),
    ) {
        ImSectionLabel(stringResource(R.string.onboarding_step, ui.step + 1, OnboardingViewModel.LAST_STEP + 1))
        when (ui.step) {
            0 -> StepSplash()
            1 -> StepName(ui, viewModel)
            2 -> StepDay(ui, viewModel)
            3 -> StepFormation(ui, viewModel)
            4 -> StepQr(ui, viewModel)
            5 -> StepOutreach(ui, viewModel)
            6 -> StepHyperOs(viewModel)
            7 -> StepTestAlarm(ui, viewModel)
            else -> StepFinal(ui)
        }
        Spacer(Modifier.height(ImSpacing.screen))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
            if (ui.step > 0) TextButton(onClick = viewModel::back) { Text(stringResource(R.string.back)) }
            Spacer(Modifier.weight(1f))
        }
        if (ui.step < OnboardingViewModel.LAST_STEP) {
            ImPrimaryButton(stringResource(R.string.next), onClick = viewModel::next, enabled = ui.step != 1 || ui.name.isNotBlank())
        } else {
            ImPrimaryButton(stringResource(R.string.onboarding_finish), onClick = { viewModel.finish(onFinished) })
        }
    }
}

@Composable
private fun StepSplash() {
    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displayLarge)
    Text(stringResource(R.string.app_tagline), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    ImPanel { Text(stringResource(R.string.about_body), style = MaterialTheme.typography.bodyMedium) }
}

@Composable
private fun StepName(ui: OnboardingUi, vm: OnboardingViewModel) {
    Text(stringResource(R.string.onboarding_name_title), style = MaterialTheme.typography.headlineMedium)
    OutlinedTextField(value = ui.name, onValueChange = vm::setName, label = { Text(stringResource(R.string.onboarding_name_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun StepDay(ui: OnboardingUi, vm: OnboardingViewModel) {
    Text(stringResource(R.string.onboarding_day_title), style = MaterialTheme.typography.headlineMedium)
    Text(stringResource(R.string.onboarding_day_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    ImTimeField(stringResource(R.string.settings_wake_weekday), ui.wakeTime, vm::setWake, Modifier.fillMaxWidth())
    ImTimeField(stringResource(R.string.settings_wake_weekend), ui.wakeTimeWeekend, vm::setWakeWeekend, Modifier.fillMaxWidth())
    ImTimeField(stringResource(R.string.settings_bed), ui.bedTime, vm::setBed, Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(ImSpacing.grid)) {
        ImTimeField(stringResource(R.string.settings_work_start), ui.workStart, vm::setWorkStart, Modifier.weight(1f))
        ImTimeField(stringResource(R.string.settings_work_end), ui.workEnd, vm::setWorkEnd, Modifier.weight(1f))
    }
}

@Composable
private fun StepFormation(ui: OnboardingUi, vm: OnboardingViewModel) {
    Text(stringResource(R.string.onboarding_formation_title), style = MaterialTheme.typography.headlineMedium)
    ImPanel { Text(stringResource(R.string.onboarding_catechesis_fixed), style = MaterialTheme.typography.bodyLarge) }
    Text(stringResource(R.string.settings_mass_time_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(ImSpacing.grid), verticalAlignment = Alignment.CenterVertically) {
        ImSecondaryButton(stringResource(R.string.mass_saturday), onClick = { vm.setMassDay(6) }, Modifier.weight(1f), enabled = ui.massDay != 6)
        ImSecondaryButton(stringResource(R.string.mass_sunday), onClick = { vm.setMassDay(7) }, Modifier.weight(1f), enabled = ui.massDay != 7)
    }
    ImTimeField(stringResource(R.string.settings_mass_time), ui.massTime, vm::setMassTime, Modifier.fillMaxWidth())
}

@Composable
private fun StepQr(ui: OnboardingUi, vm: OnboardingViewModel) {
    var scanning by remember { mutableStateOf(false) }
    Text(stringResource(R.string.onboarding_qr_title), style = MaterialTheme.typography.headlineMedium)
    Text(stringResource(R.string.onboarding_qr_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val bitmap = remember(ui.qrUuid) { vm.qrBitmap() }
    Image(bitmap.asImageBitmap(), contentDescription = stringResource(R.string.onboarding_qr_title), modifier = Modifier.size(240.dp).align(Alignment.CenterHorizontally))
    ImSecondaryButton(if (ui.qrSaved) stringResource(R.string.onboarding_qr_saved) else stringResource(R.string.onboarding_qr_save), onClick = { vm.saveQr() })
    if (!scanning) {
        ImSecondaryButton(if (ui.qrTestPassed) stringResource(R.string.onboarding_qr_test_ok) else stringResource(R.string.onboarding_qr_test), onClick = { scanning = true })
    } else {
        QrScannerView(modifier = Modifier.fillMaxWidth().height(280.dp)) { payload -> if (vm.onTestScan(payload)) scanning = false }
        TextButton(onClick = { scanning = false }) { Text(stringResource(R.string.cancel)) }
    }
}

@Composable
private fun StepOutreach(ui: OnboardingUi, vm: OnboardingViewModel) {
    Text(stringResource(R.string.onboarding_outreach_title), style = MaterialTheme.typography.headlineMedium)
    Text(stringResource(R.string.onboarding_outreach_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ImSpacing.screen)) {
        TextButton(onClick = { vm.setOutreachTarget(ui.outreachTarget - 1) }) { Text("−", style = MaterialTheme.typography.headlineLarge) }
        Text(ui.outreachTarget.toString(), style = MaterialTheme.typography.displayLarge)
        TextButton(onClick = { vm.setOutreachTarget(ui.outreachTarget + 1) }) { Text("+", style = MaterialTheme.typography.headlineLarge) }
    }
}

@Composable
private fun StepHyperOs(vm: OnboardingViewModel) {
    Text(stringResource(R.string.hyperos_title), style = MaterialTheme.typography.headlineMedium)
    HyperOsChecklist(onChanged = vm::refreshHyperOs)
}

@Composable
private fun StepTestAlarm(ui: OnboardingUi, vm: OnboardingViewModel) {
    Text(stringResource(R.string.onboarding_test_title), style = MaterialTheme.typography.headlineMedium)
    Text(stringResource(R.string.onboarding_test_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    ImSecondaryButton(if (ui.testAlarmArmed) stringResource(R.string.onboarding_test_armed) else stringResource(R.string.onboarding_test_arm), onClick = vm::armTestAlarm, enabled = !ui.testAlarmArmed)
    if (!ui.hyperOsCore) {
        Text(stringResource(R.string.onboarding_test_warning), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StepFinal(ui: OnboardingUi) {
    Text(stringResource(R.string.onboarding_final_title, ui.wakeTime.toString()), style = MaterialTheme.typography.headlineLarge)
    ImPanel { Text(stringResource(R.string.onboarding_final_hint), style = MaterialTheme.typography.bodyLarge) }
}
