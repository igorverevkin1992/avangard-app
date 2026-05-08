package com.avangard.app.sync.permissions

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.avangard.app.ui.theme.MachineColors

/**
 * Single composable that:
 *  - silently requests POST_NOTIFICATIONS on Android 13+;
 *  - shows a degradation banner when SCHEDULE_EXACT_ALARM is denied, sending
 *    the user to system settings on tap.
 */
@Composable
fun PermissionGate(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    var canExact by remember { mutableStateOf(checkExactAlarms(context)) }

    val notificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* no-op: silent ask */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        canExact = checkExactAlarms(context)
    }

    if (!canExact) {
        DegradationBanner(
            modifier = modifier,
            onClick = { openExactAlarmsSettings(context) },
        )
    }
}

@Composable
private fun DegradationBanner(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = MachineColors.SignalRed)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "ТОЧНОСТЬ СНИЖЕНА",
            color = MachineColors.SignalRed,
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = "Требуется разрешение на точный будильник",
            color = MachineColors.TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun checkExactAlarms(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    return manager.canScheduleExactAlarms()
}

private fun openExactAlarmsSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
        data = Uri.fromParts("package", context.packageName, null)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    runCatching { context.startActivity(intent) }
}
