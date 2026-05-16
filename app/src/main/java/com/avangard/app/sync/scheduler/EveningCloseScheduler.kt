package com.avangard.app.sync.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.avangard.app.core.common.Clock
import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.data.UserPreferencesRepository
import com.avangard.app.core.domain.model.CoreStatus
import com.avangard.app.core.domain.repository.SessionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single alarm v3 keeps: an idempotent evening-close nudge at the time
 * configured in UserPreferences (default 21:00). Re-arms itself daily from
 * EveningCloseReceiver and after any preference change in SettingsViewModel.
 *
 * Two reliability guarantees added in v3.4:
 *
 *  1. **No silent miss after reboot.** If the device booted after today's
 *     target and today's shift is not yet closed, schedule a near-immediate
 *     fire instead of pushing to tomorrow.
 *  2. **Doze precision.** Prefer `setAlarmClock` (treated as a user-visible
 *     alarm by the system, exempt from Doze deferrals) with
 *     `setExactAndAllowWhileIdle` as fallback when exact permission is
 *     missing.
 *
 *  v3.5 splits the post-fire path into [scheduleNextAfterFire] so the
 *  receiver can re-arm for tomorrow without re-engaging the
 *  fire-immediately branch — that previously created a loop where the
 *  receiver's re-arm immediately re-fired the alarm while the shift was
 *  still unclosed.
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

    /**
     * Receiver-only path: today's alarm just fired, so move directly to
     * tomorrow's target without consulting the fire-immediately heuristic.
     * Calling [ensureScheduled] here would loop — `now >= target` is still
     * true and the shift typically isn't closed yet.
     */
    suspend fun scheduleNextAfterFire() {
        val prefs = preferences.snapshot()
        val target = LocalTime.of(prefs.eveningCloseHour, prefs.eveningCloseMinute)
        val triggerAt = LocalDateTime.of(clock.today().plusDays(1), target)
            .atZone(clock.zone()).toEpochSecond() * 1000L
        scheduleAt(triggerAt)
    }

    private fun scheduleAt(triggerAt: Long) {
        val operation = pendingIntent()
        // Temporarily reverted from setAlarmClock to setExactAndAllowWhileIdle
        // while a fresh-install startup crash is being isolated. The
        // lockscreen alarm-chip becomes non-functional, but the broadcast
        // still fires at target time. Restore once the trigger is found.
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
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
        // Today's slot has already passed. Two branches:
        //   - shift not yet closed AND core actually started → fire shortly,
        //     covering the reboot-after-target case.
        //   - shift already closed OR not started → schedule for tomorrow.
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

    companion object {
        const val ACTION_FIRE = "com.avangard.app.alarm.EVENING_CLOSE"
        private const val REQUEST_CODE = 4001
        // 5s lets the boot path settle (DataStore warm, Hilt graph resolved)
        // before the AlarmManager broadcast lands.
        private const val FIRE_IMMEDIATELY_BUFFER_MS = 5_000L
    }
}
