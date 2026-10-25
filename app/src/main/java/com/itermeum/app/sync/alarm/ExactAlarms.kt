package com.itermeum.app.sync.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/** Thin wrapper over AlarmManager with the section 5.1 / 10 fallbacks. */
class ExactAlarms(private val context: Context) {
    private val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExact(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.canScheduleExactAlarms() else true

    /** The wake alarm: `setAlarmClock` so the system shows the alarm chip and never defers it. */
    fun setAlarmClock(triggerAt: Long, operation: PendingIntent, show: PendingIntent) {
        if (canScheduleExact()) {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, show), operation)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
        }
    }

    fun setExact(triggerAt: Long, operation: PendingIntent) {
        if (canScheduleExact()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
    }

    fun cancel(operation: PendingIntent) = am.cancel(operation)

    /** Section 13: `nextAlarmClock` check used by the health worker. */
    fun nextAlarmClockTrigger(): Long? = am.nextAlarmClock?.triggerTime

    fun broadcast(requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun activity(requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    companion object {
        const val RC_WAKE = 1001
        const val RC_WAKE_SHOW = 1002
        const val RC_MISSED = 1003
        const val RC_QUARANTINE = 1004
        const val RC_HEALTH = 1005
        const val RC_DAY_CLOSE = 1006
        const val RC_TEST_ALARM = 1007
        const val RC_NORM_BASE = 2000
    }
}
