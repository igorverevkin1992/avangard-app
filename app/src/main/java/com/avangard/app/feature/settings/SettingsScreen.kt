package com.avangard.app.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.avangard.app.R
import com.avangard.app.core.data.UserPreferences
import com.avangard.app.core.domain.model.Habit
import com.avangard.app.core.domain.model.HabitStandard
import com.avangard.app.core.ui.components.HardButton
import com.avangard.app.core.ui.components.HardButtonVariant
import com.avangard.app.core.ui.components.IndustrialToggle
import com.avangard.app.core.ui.components.PulpitPanel
import com.avangard.app.ui.theme.IsaColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    onReturn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) {
            viewModel.acknowledgeBackupStatus()
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val bytes = viewModel.prepareExportBytes()
            if (bytes == null) return@launch
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: error("openOutputStream returned null")
                }.isSuccess
            }
            if (!ok) viewModel.onExportWriteFailed()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()
            }
            if (bytes == null) viewModel.onImportReadFailed() else viewModel.stageImport(bytes)
        }
    }

    SettingsContent(
        state = state,
        onEveningCloseChanged = viewModel::onEveningCloseChanged,
        onColdStartThresholdChanged = viewModel::onColdStartThresholdChanged,
        onBirthdayChanged = viewModel::onBirthdayChanged,
        onLifeExpectancyChanged = viewModel::onLifeExpectancyChanged,
        onIgnitionEnabledChanged = viewModel::onIgnitionEnabledChanged,
        onIgnitionTimeChanged = viewModel::onIgnitionTimeChanged,
        onHabitStandardChanged = viewModel::onHabitStandardChanged,
        onRequestWipe = viewModel::requestWipe,
        onConfirmWipe = viewModel::confirmWipe,
        onCancelWipe = viewModel::cancelWipe,
        onExportClick = { exportLauncher.launch(viewModel.proposedBackupFileName()) },
        onImportClick = { importLauncher.launch(arrayOf("application/json")) },
        onConfirmImport = viewModel::commitImport,
        onCancelImport = viewModel::cancelImport,
        onDismissBackupStatus = viewModel::acknowledgeBackupStatus,
        onForceSync = viewModel::onForceSync,
        onSignOut = viewModel::onSignOut,
        onReturn = onReturn,
        modifier = modifier,
    )
}

@Composable
internal fun SettingsContent(
    state: SettingsState,
    onEveningCloseChanged: (Int, Int) -> Unit,
    onColdStartThresholdChanged: (Int) -> Unit,
    onBirthdayChanged: (Long?) -> Unit,
    onLifeExpectancyChanged: (Int) -> Unit,
    onIgnitionEnabledChanged: (Boolean) -> Unit,
    onIgnitionTimeChanged: (Int, Int) -> Unit,
    onHabitStandardChanged: (String, String, String) -> Unit,
    onRequestWipe: () -> Unit,
    onConfirmWipe: () -> Unit,
    onCancelWipe: () -> Unit,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    onConfirmImport: () -> Unit,
    onCancelImport: () -> Unit,
    onDismissBackupStatus: () -> Unit,
    onForceSync: () -> Unit,
    onSignOut: () -> Unit,
    onReturn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(IsaColors.Graphite)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_header),
            color = IsaColors.LiveMetal,
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() },
        )

        CollapsibleSection(label = stringResource(R.string.settings_evening_close_label)) {
            EveningCloseBlock(
                hour = state.preferences.eveningCloseHour,
                minute = state.preferences.eveningCloseMinute,
                onChanged = onEveningCloseChanged,
            )
        }

        CollapsibleSection(label = stringResource(R.string.settings_cold_start_label)) {
            ColdStartBlock(
                currentMinutes = (state.preferences.coldStartThresholdMs / 60 / 1000).toInt(),
                onChanged = onColdStartThresholdChanged,
            )
        }

        CollapsibleSection(label = stringResource(R.string.settings_chronometer_label)) {
            ChronometerBlock(
                preferences = state.preferences,
                onBirthdayChanged = onBirthdayChanged,
                onLifeExpectancyChanged = onLifeExpectancyChanged,
                onIgnitionEnabledChanged = onIgnitionEnabledChanged,
                onIgnitionTimeChanged = onIgnitionTimeChanged,
            )
        }

        CollapsibleSection(
            label = stringResource(R.string.settings_standards_label),
            initiallyOpen = false,
        ) {
            HabitStandardsBlock(
                standards = state.preferences.habitStandards,
                onChanged = onHabitStandardChanged,
            )
        }

        CollapsibleSection(label = stringResource(R.string.settings_cloud_label)) {
            CloudSyncPanel(
                email = state.signedInEmail,
                lastSyncedAt = state.preferences.lastSyncedAt,
                onForceSync = onForceSync,
                onSignOut = onSignOut,
            )
        }

        CollapsibleSection(
            label = stringResource(R.string.settings_backup_label),
            initiallyOpen = false,
        ) {
            BackupBlock(
                state = state,
                onExportClick = onExportClick,
                onImportClick = onImportClick,
                onConfirmImport = onConfirmImport,
                onCancelImport = onCancelImport,
                onDismissBackupStatus = onDismissBackupStatus,
            )
        }

        CollapsibleSection(
            label = stringResource(R.string.settings_wipe_label),
            initiallyOpen = false,
        ) {
            if (!state.confirmingWipe) {
                HardButton(
                    label = stringResource(R.string.settings_wipe_request),
                    onClick = onRequestWipe,
                    variant = HardButtonVariant.Danger,
                )
            } else {
                Text(
                    text = stringResource(R.string.settings_wipe_confirm_message),
                    color = IsaColors.Signal,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HardButton(
                        label = stringResource(R.string.settings_wipe_confirm),
                        onClick = onConfirmWipe,
                        variant = HardButtonVariant.Danger,
                        modifier = Modifier.weight(1f),
                    )
                    HardButton(
                        label = stringResource(R.string.settings_wipe_cancel),
                        onClick = onCancelWipe,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (state.wipeInProgress) {
                Text(
                    text = stringResource(R.string.settings_wipe_in_progress),
                    color = IsaColors.Approve,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        HardButton(
            label = stringResource(R.string.settings_return),
            onClick = onReturn,
        )
    }
}

@Composable
private fun BackupBlock(
    state: SettingsState,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    onConfirmImport: () -> Unit,
    onCancelImport: () -> Unit,
    onDismissBackupStatus: () -> Unit,
) {
    PulpitPanel {
        Text(
            text = stringResource(R.string.settings_backup_hint),
            color = IsaColors.Lattice,
            style = MaterialTheme.typography.labelMedium,
        )

        val pending = state.pendingImportBytes != null
        val busy = state.backupStatus == BackupStatus.Exporting ||
            state.backupStatus == BackupStatus.Importing

        if (pending) {
            Text(
                text = stringResource(R.string.settings_restore_confirm_message),
                color = IsaColors.Signal,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HardButton(
                    label = stringResource(R.string.settings_restore_confirm),
                    onClick = onConfirmImport,
                    variant = HardButtonVariant.Danger,
                    modifier = Modifier.weight(1f),
                )
                HardButton(
                    label = stringResource(R.string.settings_restore_cancel),
                    onClick = onCancelImport,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HardButton(
                    label = stringResource(R.string.settings_backup_export),
                    onClick = onExportClick,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                )
                HardButton(
                    label = stringResource(R.string.settings_backup_import),
                    onClick = onImportClick,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        BackupStatusLine(state.backupStatus, onDismissBackupStatus)
    }
}

@Composable
private fun BackupStatusLine(
    status: BackupStatus,
    onDismiss: () -> Unit,
) {
    val (text, color) = when (status) {
        BackupStatus.Idle -> null to IsaColors.Lattice
        BackupStatus.Exporting -> stringResource(R.string.settings_backup_exporting) to IsaColors.LiveMetal
        BackupStatus.Importing -> stringResource(R.string.settings_backup_importing) to IsaColors.LiveMetal
        BackupStatus.ExportSucceeded -> stringResource(R.string.settings_backup_export_ok) to IsaColors.Approve
        BackupStatus.ImportSucceeded -> stringResource(R.string.settings_backup_import_ok) to IsaColors.Approve
        BackupStatus.ExportFailed -> stringResource(R.string.settings_backup_export_failed) to IsaColors.Signal
        BackupStatus.ImportFailed.NotJson ->
            stringResource(R.string.settings_backup_import_not_json) to IsaColors.Signal
        is BackupStatus.ImportFailed.UnsupportedSchema ->
            stringResource(R.string.settings_backup_import_unsupported, status.version) to IsaColors.Signal
        BackupStatus.ImportFailed.CorruptedSnapshot ->
            stringResource(R.string.settings_backup_import_corrupted) to IsaColors.Signal
        BackupStatus.ImportFailed.ReadFailed ->
            stringResource(R.string.settings_backup_import_read_failed) to IsaColors.Signal
    }
    val message = text ?: return
    // Terminal statuses auto-clear after a short read window so the next
    // export/import starts from a clean slate.
    val terminal = status is BackupStatus.ExportSucceeded ||
        status is BackupStatus.ImportSucceeded ||
        status is BackupStatus.ExportFailed ||
        status is BackupStatus.ImportFailed
    LaunchedEffect(status) {
        if (terminal) {
            delay(4_000)
            onDismiss()
        }
    }
    Text(
        text = message,
        color = color,
        style = MaterialTheme.typography.labelMedium,
    )
}

@Composable
private fun CloudSyncPanel(
    email: String?,
    lastSyncedAt: Long?,
    onForceSync: () -> Unit,
    onSignOut: () -> Unit,
) {
    val signedIn = email != null
    PulpitPanel {
        LabelValueRow(
            label = stringResource(R.string.settings_cloud_account_label),
            value = email ?: stringResource(R.string.settings_cloud_account_empty),
            valueColor = if (signedIn) IsaColors.LiveMetal else IsaColors.Mute,
        )
        LabelValueRow(
            label = stringResource(R.string.settings_cloud_last_sync_label),
            value = lastSyncedAt
                ?.let { formatEpochAsHumanLocal(it) }
                ?: stringResource(R.string.settings_cloud_last_sync_never),
            valueColor = if (lastSyncedAt != null) IsaColors.LiveMetal else IsaColors.Mute,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HardButton(
                label = stringResource(R.string.settings_cloud_sync_now),
                onClick = onForceSync,
                enabled = signedIn,
                variant = HardButtonVariant.Primary,
                modifier = Modifier.weight(1f),
            )
            HardButton(
                label = stringResource(R.string.settings_cloud_sign_out),
                onClick = onSignOut,
                enabled = signedIn,
                variant = HardButtonVariant.Danger,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LabelValueRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            color = IsaColors.Lattice,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            color = valueColor,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(2f),
        )
    }
}

private fun formatEpochAsHumanLocal(epochMs: Long): String {
    val instant = java.time.Instant.ofEpochMilli(epochMs)
    val zoned = instant.atZone(java.time.ZoneId.systemDefault())
    val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm · dd.MM.yyyy")
    return zoned.format(formatter)
}

@Composable
private fun EveningCloseBlock(
    hour: Int,
    minute: Int,
    onChanged: (Int, Int) -> Unit,
) {
    var hourDraft by rememberSaveable(hour) { mutableIntStateOf(hour) }
    var minuteDraft by rememberSaveable(minute) { mutableIntStateOf(minute) }

    PulpitPanel {
        Text(
            text = stringResource(R.string.settings_evening_close_hint),
            color = IsaColors.Lattice,
            style = MaterialTheme.typography.labelMedium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Stepper(
                label = stringResource(R.string.settings_hour_label),
                value = hourDraft,
                range = 0..23,
                onChange = { hourDraft = it; onChanged(hourDraft, minuteDraft) },
                modifier = Modifier.weight(1f),
            )
            Stepper(
                label = stringResource(R.string.settings_minute_label),
                value = minuteDraft,
                range = 0..59,
                step = 5,
                onChange = { minuteDraft = it; onChanged(hourDraft, minuteDraft) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ColdStartBlock(
    currentMinutes: Int,
    onChanged: (Int) -> Unit,
) {
    PulpitPanel {
        Text(
            text = stringResource(R.string.settings_cold_start_hint),
            color = IsaColors.Lattice,
            style = MaterialTheme.typography.labelMedium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingsViewModel.COLD_START_OPTIONS_MINUTES.forEach { minutes ->
                val selected = minutes == currentMinutes
                val color = if (selected) IsaColors.Approve else IsaColors.Lattice
                val interactionSource = remember(minutes) { MutableInteractionSource() }
                Text(
                    text = "$minutes МИН",
                    color = color,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .border(width = if (selected) 2.dp else 1.dp, color = color)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { onChanged(minutes) },
                        )
                        .padding(vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun Stepper(
    label: String,
    value: Int,
    range: IntRange,
    step: Int = 1,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            color = IsaColors.Lattice,
            style = MaterialTheme.typography.labelMedium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StepperButton(symbol = "−", onClick = {
                val next = (value - step).coerceAtLeast(range.first)
                if (next != value) onChange(next)
            })
            Text(
                text = "%02d".format(java.util.Locale.US, value),
                color = IsaColors.LiveMetal,
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .border(width = 1.dp, color = IsaColors.Steel)
                    .padding(vertical = 6.dp),
            )
            StepperButton(symbol = "+", onClick = {
                val next = (value + step).coerceAtMost(range.last)
                if (next != value) onChange(next)
            })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChronometerBlock(
    preferences: UserPreferences,
    onBirthdayChanged: (Long?) -> Unit,
    onLifeExpectancyChanged: (Int) -> Unit,
    onIgnitionEnabledChanged: (Boolean) -> Unit,
    onIgnitionTimeChanged: (Int, Int) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    PulpitPanel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_chronometer_birthday_label),
                color = IsaColors.Lattice,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = preferences.birthdayEpochDay
                    ?.let { java.time.LocalDate.ofEpochDay(it).toString() }
                    ?: stringResource(R.string.settings_chronometer_birthday_empty),
                color = if (preferences.birthdayEpochDay != null) IsaColors.LiveMetal else IsaColors.Mute,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
        }
        HardButton(
            label = stringResource(R.string.settings_chronometer_birthday_set),
            onClick = { showPicker = true },
        )

        var expectancyDraft by rememberSaveable(preferences.lifeExpectancyYears) {
            mutableIntStateOf(preferences.lifeExpectancyYears)
        }
        Stepper(
            label = stringResource(R.string.settings_chronometer_expectancy_label),
            value = expectancyDraft,
            range = UserPreferences.MIN_LIFE_EXPECTANCY..UserPreferences.MAX_LIFE_EXPECTANCY,
            onChange = { expectancyDraft = it; onLifeExpectancyChanged(it) },
        )

        IndustrialToggle(
            label = stringResource(R.string.settings_chronometer_ignition_toggle),
            checked = preferences.ignitionEnabled,
            onCheckedChange = onIgnitionEnabledChanged,
        )
        Text(
            text = stringResource(R.string.settings_chronometer_ignition_hint),
            color = IsaColors.Lattice,
            style = MaterialTheme.typography.labelMedium,
        )

        if (preferences.ignitionEnabled) {
            var ignitionHourDraft by rememberSaveable(preferences.ignitionHour) {
                mutableIntStateOf(preferences.ignitionHour)
            }
            var ignitionMinuteDraft by rememberSaveable(preferences.ignitionMinute) {
                mutableIntStateOf(preferences.ignitionMinute)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Stepper(
                    label = stringResource(R.string.settings_hour_label),
                    value = ignitionHourDraft,
                    range = 0..23,
                    onChange = {
                        ignitionHourDraft = it
                        onIgnitionTimeChanged(it, ignitionMinuteDraft)
                    },
                    modifier = Modifier.weight(1f),
                )
                Stepper(
                    label = stringResource(R.string.settings_minute_label),
                    value = ignitionMinuteDraft,
                    range = 0..59,
                    step = 5,
                    onChange = {
                        ignitionMinuteDraft = it
                        onIgnitionTimeChanged(ignitionHourDraft, it)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (showPicker) {
        val today = java.time.LocalDate.now()
        val initialMillis = preferences.birthdayEpochDay
            ?.let { it * 86_400_000L }
            ?: (today.minusYears(30).toEpochDay() * 86_400_000L)
        val maxMillis = today.toEpochDay() * 86_400_000L
        val minMillis = java.time.LocalDate.of(1900, 1, 1).toEpochDay() * 86_400_000L
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis in minMillis..maxMillis
            },
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                HardButton(
                    label = stringResource(R.string.settings_chronometer_birthday_set),
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            onBirthdayChanged(millis / 86_400_000L)
                        }
                        showPicker = false
                    },
                )
            },
            dismissButton = {
                HardButton(
                    label = stringResource(R.string.settings_restore_cancel),
                    onClick = { showPicker = false },
                )
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/**
 * Settings page is a long scroll of unrelated sections; wrapping each block
 * in a collapsible strip cuts the visible surface to roughly the headers
 * after the first paint and lets the operator jump straight to the section
 * they want. Default state is preserved per-section via the `initiallyOpen`
 * argument — write-once sections (Standards, Backup, Wipe) start closed,
 * tunable knobs stay open.
 */
/**
 * Settings page is a long scroll of unrelated sections; wrapping each block
 * in a collapsible header strip cuts the visible surface to roughly the
 * header rows after the first paint and lets the operator jump straight to
 * the section they want. Write-once sections (Standards, Backup, Wipe)
 * start collapsed; tunable knobs (evening close, chronometer, …) stay open.
 *
 * The header row sits outside the inner PulpitPanel's own border — no
 * double frame, no visual noise.
 */
@Composable
private fun CollapsibleSection(
    label: String,
    initiallyOpen: Boolean = true,
    content: @Composable () -> Unit,
) {
    var open by rememberSaveable(label) { mutableStateOf(initiallyOpen) }
    val interactionSource = remember(label) { MutableInteractionSource() }
    val chevron = if (open) "▼" else "▶"
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = { open = !open },
                )
                .padding(vertical = 6.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                color = IsaColors.LiveMetal,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = chevron,
                color = IsaColors.Lattice,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        if (open) content()
    }
}

@Composable
private fun StepperButton(symbol: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Text(
        text = symbol,
        color = IsaColors.LiveMetal,
        style = MaterialTheme.typography.headlineLarge,
        modifier = Modifier
            .border(width = 1.dp, color = IsaColors.Steel)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 2.dp),
    )
}

@Composable
private fun HabitStandardsBlock(
    standards: Map<String, HabitStandard>,
    onChanged: (String, String, String) -> Unit,
) {
    PulpitPanel {
        Text(
            text = stringResource(R.string.settings_standards_hint),
            color = IsaColors.Lattice,
            style = MaterialTheme.typography.labelSmall,
        )
        Habit.entries.forEach { habit ->
            val current = standards[habit.code] ?: HabitStandard()
            HabitStandardRow(
                habit = habit,
                current = current,
                onChanged = onChanged,
            )
        }
    }
}

@Composable
private fun HabitStandardRow(
    habit: Habit,
    current: HabitStandard,
    onChanged: (String, String, String) -> Unit,
) {
    // Local drafts so each keystroke doesn't round-trip through DataStore; the
    // store is updated on focus loss / explicit save below.
    var standardDraft by rememberSaveable(habit.code, current.standard) {
        mutableStateOf(current.standard)
    }
    var mvdDraft by rememberSaveable(habit.code, current.mvd) {
        mutableStateOf(current.mvd)
    }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = IsaColors.Steel)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "${habit.code}·${habit.displayName}",
            color = IsaColors.LiveMetal,
            style = MaterialTheme.typography.labelMedium,
        )
        StandardField(
            label = stringResource(R.string.settings_standards_field_standard),
            value = standardDraft,
            onValueChange = {
                standardDraft = it
                // Debounce-by-coroutine: cancel previous write attempt and
                // commit after the user pauses for ~600ms.
                scope.launch {
                    delay(600)
                    if (standardDraft == it) {
                        onChanged(habit.code, standardDraft, mvdDraft)
                    }
                }
            },
            accent = IsaColors.Approve,
        )
        StandardField(
            label = stringResource(R.string.settings_standards_field_mvd),
            value = mvdDraft,
            onValueChange = {
                mvdDraft = it
                scope.launch {
                    delay(600)
                    if (mvdDraft == it) {
                        onChanged(habit.code, standardDraft, mvdDraft)
                    }
                }
            },
            accent = IsaColors.Caution,
        )
    }
}

@Composable
private fun StandardField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    accent: androidx.compose.ui.graphics.Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            color = accent,
            style = MaterialTheme.typography.labelSmall,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = IsaColors.LiveMetal),
            keyboardOptions = KeyboardOptions.Default,
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = IsaColors.Steel)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

