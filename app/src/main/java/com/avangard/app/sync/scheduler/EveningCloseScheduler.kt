package com.avangard.app.sync.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.avangard.app.core.common.Clock
import com.avangard.app.core.data.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single alarm v3 keeps: an idempotent evening-close nudge at the time
 * configured in UserPreferences (default 21:00). Re-arms itself daily from
 * EveningCloseReceiver and after any preference change in SettingsViewModel.
 */
@Singleton
class EveningCloseScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
    private val preferences: UserPreferencesRepository,
) {
    private val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    suspend fun ensureScheduled() {
        val prefs = preferences.snapshot()
        val triggerAt = nextTriggerEpochMs(prefs.eveningCloseHour, prefs.eveningCloseMinute)
        val pending = pendingIntent()
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    private fun canScheduleExact(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else true

    private fun nextTriggerEpochMs(hour: Int, minute: Int): Long {
        val now = clock.localTime()
        val today = clock.today()
        val target = LocalTime.of(hour, minute)
        // At HH:mm:00.000 sharp `isBefore` is false → trigger shifts to tomorrow.
        // This is intentional: prevents the receiver from re-firing the alarm in
        // the same minute it just presented the notification.
        val date = if (now.isBefore(target)) today else today.plusDays(1)
        return LocalDateTime.of(date, target).atZone(clock.zone()).toEpochSecond() * 1000L
    }

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, EveningCloseReceiver::class.java).apply {
            action = ACTION_FIRE
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_FIRE = "com.avangard.app.alarm.EVENING_CLOSE"
        private const val REQUEST_CODE = 4001
    }
}
