package com.avangard.app.sync.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.avangard.app.core.common.Clock
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

internal fun nextTriggerEpochMillis(
    today: LocalDate,
    now: LocalTime,
    target: LocalTime,
    zone: ZoneId,
): Long {
    val date = if (now.isBefore(target)) today else today.plusDays(1)
    return LocalDateTime.of(date, target).atZone(zone).toEpochSecond() * 1000L
}

/**
 * Wraps [AlarmManager] with the project's regulatory contract:
 *  - prefers `setExactAndAllowWhileIdle` when [AlarmManager.canScheduleExactAlarms];
 *  - degrades to inexact `setAndAllowWhileIdle` when the user/system revokes the
 *    `SCHEDULE_EXACT_ALARM` permission.
 *
 * The receiver is responsible for re-scheduling itself for the next occurrence
 * after firing — Android does not provide auto-repeating exact alarms post-API 19.
 */
@Singleton
class AlarmDispatcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExact(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        alarmManager.canScheduleExactAlarms()
    } else {
        true
    }

    fun scheduleAll() {
        AlarmSlot.entries.forEach { schedule(it) }
    }

    fun schedule(slot: AlarmSlot) {
        val triggerAt = nextTriggerEpochMillis(slot)
        val pending = pendingIntentFor(slot)
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    fun cancel(slot: AlarmSlot) {
        alarmManager.cancel(pendingIntentFor(slot))
    }

    fun cancelAll() {
        AlarmSlot.entries.forEach { cancel(it) }
    }

    internal fun nextTriggerEpochMillis(slot: AlarmSlot): Long =
        nextTriggerEpochMillis(
            today = clock.today(),
            now = clock.localTime(),
            target = slot.triggerTime,
            zone = clock.zone(),
        )

    private fun pendingIntentFor(slot: AlarmSlot): PendingIntent {
        val intent = Intent(context, ReportAlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_SLOT_ID, slot.id)
        }
        return PendingIntent.getBroadcast(
            context,
            slot.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_FIRE = "com.avangard.app.alarm.FIRE"
        const val EXTRA_SLOT_ID = "slot_id"
    }
}
