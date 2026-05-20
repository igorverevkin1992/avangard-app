package com.avangard.app.sync.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.avangard.app.MainActivity
import com.avangard.app.core.common.Clock
import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.data.UserPreferencesRepository
import com.avangard.app.core.domain.model.CoreStatus
import com.avangard.app.core.domain.repository.SessionRepository
import com.avangard.app.navigation.NavRoute
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single evening-close nudge alarm. Re-armed daily from EveningCloseReceiver
 * and after any preference change in SettingsViewModel.
 *
 * Reliability guarantees:
 *  1. No silent miss after reboot — ensureScheduled fires immediately if
 *     today's slot has passed and the shift hasn't been closed.
 *  2. Doze precision — setAlarmClock with a fallback to
 *     setAndAllowWhileIdle when exact-alarm permission is missing.
 *  3. No re-fire loop after the receiver runs — scheduleNextAfterFire
 *     skips the fire-immediately branch.
 */
@Singleton
class EveningCloseScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
    private val preferences: UserPreferencesRepository,
    private val sessions: SessionRepository,
) {
    private val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    suspend fun ensureScheduled() {
        val prefs = preferences.snapshot()
        val triggerAt = nextTriggerEpochMs(prefs.eveningCloseHour, prefs.eveningCloseMinute)
        scheduleAt(triggerAt)
    }

    suspend fun scheduleNextAfterFire() {
        val prefs = preferences.snapshot()
        val target = LocalTime.of(prefs.eveningCloseHour, prefs.eveningCloseMinute)
        val triggerAt = LocalDateTime.of(clock.today().plusDays(1), target)
            .atZone(clock.zone()).toEpochSecond() * 1000L
        scheduleAt(triggerAt)
    }

    private fun scheduleAt(triggerAt: Long) {
        val operation = pendingIntent()
        if (canScheduleExact()) {
            // setAlarmClock surfaces a system alarm-chip on the lockscreen.
            // showIntent launches an Activity (chip-tap path), separate from
            // the broadcast that actually fires the alarm.
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerAt, lockscreenChipIntent()),
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

    private suspend fun nextTriggerEpochMs(hour: Int, minute: Int): Long {
        val now = clock.localTime()
        val today = clock.today()
        val target = LocalTime.of(hour, minute)
        if (now.isBefore(target)) {
            return LocalDateTime.of(today, target).atZone(clock.zone()).toEpochSecond() * 1000L
        }
        val todayEpoch = today.toStartOfDayEpoch(clock.zone())
        val daily = sessions.findForDate(todayEpoch)
        val shiftStartedButUnclosed =
            daily != null && !daily.eveningClosed && daily.coreStatus !is CoreStatus.Idle
        return if (shiftStartedButUnclosed) {
            clock.nowEpochMillis() + FIRE_IMMEDIATELY_BUFFER_MS
        } else {
            LocalDateTime.of(today.plusDays(1), target).atZone(clock.zone()).toEpochSecond() * 1000L
        }
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

    private fun lockscreenChipIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_START_DESTINATION, NavRoute.EveningClose.route)
        }
        return PendingIntent.getActivity(
            context,
            SHOW_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_FIRE = "com.avangard.app.alarm.EVENING_CLOSE"
        private const val REQUEST_CODE = 4001
        private const val SHOW_REQUEST_CODE = 4002
        private const val FIRE_IMMEDIATELY_BUFFER_MS = 5_000L
    }
}
