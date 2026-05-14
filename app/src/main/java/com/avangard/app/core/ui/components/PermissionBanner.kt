package com.avangard.app.core.ui.components

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.avangard.app.R
import com.avangard.app.ui.theme.IsaColors

/**
 * POST_NOTIFICATIONS gate. On Android 13+ this permission is runtime-granted;
 * without it the 21:00 evening-close notification silently fails. Renders
 * nothing on older API levels or when the permission is already granted.
 *
 * Re-checks the permission on every ON_RESUME so returning from Settings
 * dismisses the banner without restart.
 */
@Composable
fun NotificationPermissionBanner(modifier: Modifier = Modifier) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    var granted by remember { mutableStateOf(checkNotificationsGranted(context)) }
    ResumeRefresh { granted = checkNotificationsGranted(context) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { result ->
        granted = result
        if (!result) {
            // User denied (possibly forever). Send them to app details so they can
            // flip the toggle manually.
            context.startActivity(appDetailsIntent(context))
        }
    }
    if (granted) return

    GateBanner(
        title = stringResource(R.string.permission_notifications_title),
        body = stringResource(R.string.permission_notifications_body),
        action = stringResource(R.string.permission_grant),
        modifier = modifier,
        onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) },
    )
}

/**
 * SCHEDULE_EXACT_ALARM gate. On Android 12+ even the manifest permission is
 * insufficient — the user must grant it in system Settings. Without it the
 * 21:00 alarm degrades to inexact, which can drift by tens of minutes.
 */
@Composable
fun ExactAlarmPermissionBanner(modifier: Modifier = Modifier) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

    val context = LocalContext.current
    var granted by remember { mutableStateOf(checkExactAlarmsGranted(context)) }
    ResumeRefresh { granted = checkExactAlarmsGranted(context) }
    if (granted) return

    GateBanner(
        title = stringResource(R.string.permission_exact_alarm_title),
        body = stringResource(R.string.permission_exact_alarm_body),
        action = stringResource(R.string.permission_open_settings),
        modifier = modifier,
    ) {
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        runCatching { context.startActivity(intent) }
            .onFailure { context.startActivity(appDetailsIntent(context)) }
    }
}

private fun checkNotificationsGranted(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    } else true

private fun checkExactAlarmsGranted(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
            .canScheduleExactAlarms()
    } else true

private fun appDetailsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
    }

@Composable
private fun ResumeRefresh(onResume: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

@Composable
private fun GateBanner(
    title: String,
    body: String,
    action: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = IsaColors.Signal)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            color = IsaColors.Signal,
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = body,
            color = IsaColors.LiveMetal,
            style = MaterialTheme.typography.labelMedium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                text = action,
                color = IsaColors.Signal,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .border(width = 1.dp, color = IsaColors.Signal)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}
