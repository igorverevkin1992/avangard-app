package com.itermeum.app.feature.onboarding

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.itermeum.app.R

/** Section 13 HyperOS checklist: items 1–4 are checkable, 5–8 are questions to the user. */
data class HyperOsItem(val index: Int, val titleRes: Int, val checkable: Boolean, val ok: Boolean?)

object HyperOsChecks {

    fun items(context: Context): List<HyperOsItem> {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val notifications = NotificationManagerCompat.from(context).areNotificationsEnabled()
        val exact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.canScheduleExactAlarms() else true
        val fullScreen = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) nm.canUseFullScreenIntent() else true
        val battery = pm.isIgnoringBatteryOptimizations(context.packageName)
        val steps = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        val camera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        return listOf(
            HyperOsItem(1, R.string.hyperos_notifications, true, notifications),
            HyperOsItem(2, R.string.hyperos_exact_alarms, true, exact),
            HyperOsItem(3, R.string.hyperos_full_screen, true, fullScreen),
            HyperOsItem(4, R.string.hyperos_battery, true, battery),
            HyperOsItem(5, R.string.hyperos_autostart, false, null),
            HyperOsItem(6, R.string.hyperos_no_suspend, false, null),
            HyperOsItem(7, R.string.hyperos_lockscreen, false, null),
            HyperOsItem(8, R.string.hyperos_pin_recents, false, null),
            HyperOsItem(9, R.string.hyperos_steps, true, steps),
            HyperOsItem(10, R.string.hyperos_camera, true, camera),
        )
    }

    /** True when items 1–4 are all fine (section 13: checked on every launch). */
    fun coreHealthy(context: Context): Boolean = items(context).filter { it.index in 1..4 }.all { it.ok == true }

    fun open(context: Context, index: Int) {
        val pkg = context.packageName
        val intent = when (index) {
            1, 7 -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
            2 -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$pkg")) else appDetails(pkg)
            3 -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$pkg")) else appDetails(pkg)
            4 -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg"))
            5 -> Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"))
            6, 8 -> appDetails(pkg)
            else -> appDetails(pkg)
        }
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { runCatching { context.startActivity(appDetails(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }

    private fun appDetails(pkg: String) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
}
