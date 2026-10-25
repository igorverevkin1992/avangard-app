package com.itermeum.app.sync.alarm

import android.content.Context
import android.content.Intent
import com.itermeum.app.core.common.Clock
import com.itermeum.app.core.data.MorningRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Section 5.1: the wake alarm is re-armed right after it fires and on every
 * BOOT_COMPLETED / TIME_SET / TIMEZONE_CHANGED / MY_PACKAGE_REPLACED /
 * settings change. Quarantine check (+35) and MISSED (+60) are exact alarms
 * armed when the morning actually starts.
 */
@Singleton
class WakeAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val morning: MorningRepository,
    private val clock: Clock,
) {
    private val alarms = ExactAlarms(context)

    /** Returns the epoch millis of the armed alarm. */
    suspend fun scheduleNext(): Long {
        val date = morning.nextAlarmDate()
        val at = morning.schedule(date)
        val fire = alarms.broadcast(ExactAlarms.RC_WAKE, AlarmReceiver.intent(context, date, test = false))
        val show = alarms.activity(ExactAlarms.RC_WAKE_SHOW, AlarmRingActivity.intent(context, date))
        alarms.setAlarmClock(at, fire, show)
        return at
    }

    fun scheduleTest(inMinutes: Long = 2) {
        val at = clock.nowEpochMillis() + inMinutes * 60_000L
        val fire = alarms.broadcast(ExactAlarms.RC_TEST_ALARM, AlarmReceiver.intent(context, clock.logicalToday(), test = true))
        alarms.setExact(at, fire)
    }

    fun scheduleMissed(date: LocalDate, alarmAt: Long) {
        val at = alarmAt + MorningRepository.MISSED_AFTER_MIN * 60_000L
        alarms.setExact(at, alarms.broadcast(ExactAlarms.RC_MISSED, MorningEventReceiver.intent(context, MorningEventReceiver.ACTION_MISSED, date)))
    }

    fun cancelMissed() = alarms.cancel(alarms.broadcast(ExactAlarms.RC_MISSED, Intent(context, MorningEventReceiver::class.java).setAction(MorningEventReceiver.ACTION_MISSED)))

    fun scheduleQuarantineCheck(date: LocalDate, provenAt: Long) {
        val at = provenAt + MorningRepository.QUARANTINE_CHECK_MIN * 60_000L
        alarms.setExact(at, alarms.broadcast(ExactAlarms.RC_QUARANTINE, MorningEventReceiver.intent(context, MorningEventReceiver.ACTION_QUARANTINE_CHECK, date)))
    }

    fun nextAlarmClock(): Long? = alarms.nextAlarmClockTrigger()
    fun canScheduleExact(): Boolean = alarms.canScheduleExact()
}
