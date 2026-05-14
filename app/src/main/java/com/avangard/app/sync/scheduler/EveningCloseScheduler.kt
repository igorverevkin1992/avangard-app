package com.avangard.app.sync.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.avangard.app.core.common.Clock
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single alarm v3 keeps: an idempotent 21:00 nudge to close the
 * operational day. Re-arms itself daily from EveningCloseReceiver.
 */
@Singleton
class EveningCloseScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) {
    private val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun ensureScheduled() {
        val triggerAt = nextTriggerEpochMs()
        val pending = pendingIntent()
        val exact = canScheduleExact()
        if (exact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    private fun canScheduleExact(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else true

    private fun nextTriggerEpochMs(): Long {
        val now = clock.localTime()
        val today = clock.today()
        val target = LocalTime.of(21, 0)
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
