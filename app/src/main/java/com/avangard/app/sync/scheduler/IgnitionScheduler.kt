package com.avangard.app.sync.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.avangard.app.MainActivity
import com.avangard.app.core.common.Clock
import com.avangard.app.core.data.UserPreferencesRepository
import com.avangard.app.navigation.NavRoute
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Daily ignition alarm — morning nudge for the Chronometer screen.
 * Mirrors EveningCloseScheduler's setAlarmClock + setAndAllowWhileIdle
 * fallback, but tied to its own preferences keys and broadcast action.
 */
@Singleton
class IgnitionScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
    private val preferences: UserPreferencesRepository,
) {
    private val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    suspend fun ensureScheduled() {
        val prefs = preferences.snapshot()
        if (!prefs.ignitionEnabled) {
            cancel()
            return
        }
        scheduleAt(nextTriggerEpochMs(prefs.ignitionHour, prefs.ignitionMinute))
    }

    suspend fun scheduleNextAfterFire() {
        val prefs = preferences.snapshot()
        if (!prefs.ignitionEnabled) return
        val target = LocalTime.of(prefs.ignitionHour, prefs.ignitionMinute)
        val triggerAt = LocalDateTime.of(clock.today().plusDays(1), target)
            .atZone(clock.zone()).toEpochSecond() * 1000L
        scheduleAt(triggerAt)
    }

    fun cancel() {
        alarmManager.cancel(pendingIntent())
    }

    private fun scheduleAt(triggerAt: Long) {
        val operation = pendingIntent()
        if (canScheduleExact()) {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerAt, chronometerChipIntent()),
                operation,
            )
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
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
        val base = if (now.isBefore(target)) today else today.plusDays(1)
        return LocalDateTime.of(base, target).atZone(clock.zone()).toEpochSecond() * 1000L
    }

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, IgnitionReceiver::class.java).apply {
            action = ACTION_FIRE
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun chronometerChipIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_START_DESTINATION, NavRoute.Chronometer.route)
        }
        return PendingIntent.getActivity(
            context,
            SHOW_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_FIRE = "com.avangard.app.alarm.IGNITION"
        private const val REQUEST_CODE = 4101
        private const val SHOW_REQUEST_CODE = 4102
    }
}
