package com.avangard.app.feature.settings

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.os.Build
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.avangard.app.R
import com.avangard.app.ui.theme.MachineColors

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    SettingsContent(
        state = state,
        onRequestWipe = viewModel::requestWipe,
        onConfirmWipe = viewModel::confirmWipe,
        onCancelWipe = viewModel::cancelWipe,
        modifier = modifier,
    )
}

@Composable
internal fun SettingsContent(
    state: SettingsState,
    onRequestWipe: () -> Unit,
    onConfirmWipe: () -> Unit,
    onCancelWipe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MachineColors.Anthracite)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_header),
            color = MachineColors.Ivory,
            style = MaterialTheme.typography.headlineLarge,
        )

        PermissionsBlock(context)
        DataBlock(
            state = state,
            onRequestWipe = onRequestWipe,
            onConfirmWipe = onConfirmWipe,
            onCancelWipe = onCancelWipe,
        )
    }
}

@Composable
private fun PermissionsBlock(context: Context) {
    val notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    } else true
    val exactGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
    } else true

    SectionFrame(title = stringResource(R.string.settings_permissions)) {
        StatusRow(
            label = stringResource(R.string.settings_perm_notifications),
            granted = notificationsGranted,
        )
        StatusRow(
            label = stringResource(R.string.settings_perm_exact_alarms),
            granted = exactGranted,
        )
    }
}

@Composable
private fun DataBlock(
    state: SettingsState,
    onRequestWipe: () -> Unit,
    onConfirmWipe: () -> Unit,
    onCancelWipe: () -> Unit,
) {
    SectionFrame(title = stringResource(R.string.settings_data)) {
        if (!state.confirmingWipe) {
            ActionRow(
                label = stringResource(R.string.settings_wipe_request),
                tint = MachineColors.AtlasRed,
                onClick = onRequestWipe,
            )
        } else {
            Text(
                text = stringResource(R.string.settings_wipe_confirm_message),
                color = MachineColors.AtlasRed,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionRow(
                    label = stringResource(R.string.settings_wipe_confirm),
                    tint = MachineColors.AtlasRed,
                    onClick = onConfirmWipe,
                    modifier = Modifier.weight(1f),
                )
                ActionRow(
                    label = stringResource(R.string.settings_wipe_cancel),
                    tint = MachineColors.WarmGray,
                    onClick = onCancelWipe,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (state.wipeInProgress) {
            Text(
                text = stringResource(R.string.settings_wipe_in_progress),
                color = MachineColors.ReardenCopper,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun SectionFrame(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = MachineColors.WarmGray)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            color = MachineColors.WarmGray,
            style = MaterialTheme.typography.labelLarge,
        )
        content()
    }
}

@Composable
private fun StatusRow(label: String, granted: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = MachineColors.Ivory,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = if (granted) "OK" else "—",
            color = if (granted) MachineColors.ReardenCopper else MachineColors.AtlasRed,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun ActionRow(
    label: String,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Text(
        text = label,
        color = tint,
        style = MaterialTheme.typography.labelLarge,
        modifier = modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = tint)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 12.dp, horizontal = 8.dp),
    )
}
