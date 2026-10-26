package com.itermeum.app.sync.alarm

import android.content.Context
import android.content.Intent
import com.itermeum.app.core.common.Clock
import com.itermeum.app.MainActivity
import com.itermeum.app.core.data.MorningRepository
import com.itermeum.app.core.data.ProfileRepository
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
    private val profiles: ProfileRepository,
    private val clock: Clock,
) {
    private val alarms = ExactAlarms(context)
    private val directBoot = DirectBootStore(context)

    /** Returns the epoch millis of the armed alarm. */
    suspend fun scheduleNext(): Long {
        val date = morning.nextAlarmDate()
        val at = morning.schedule(date)
        arm(at, date)
        val profile = profiles.get()
        directBoot.save(at, date, profile.alarmSound, profile.alarmVolume)
        return at
    }

    /**
     * LOCKED_BOOT_COMPLETED: Room is still locked, so the alarm is re-armed
     * from the device-protected copy written by [scheduleNext].
     */
    fun rearmFromDirectBoot(): Boolean {
        val at = directBoot.nextAt()
        val date = directBoot.nextDate() ?: return false
        if (at <= clock.nowEpochMillis()) return false
        arm(at, date)
        return true
    }

    private fun arm(at: Long, date: LocalDate) {
        val fire = alarms.broadcast(ExactAlarms.RC_WAKE, AlarmReceiver.intent(context, date, test = false))
        // The status-bar chip opens Today, never the ring screen: tapping it the
        // evening before must not let the wake be «proven» in advance.
        val show = alarms.activity(
            ExactAlarms.RC_WAKE_SHOW,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
        alarms.setAlarmClock(at, fire, show)
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

    /** Trigger of the system-wide next alarm clock, only if it is ours. */
    /** Section 10: if Serviam has not been pressed 3 minutes after the proof, one reminder (fire-time checked). */
    fun scheduleServiamNudge(provenAt: Long) {
        val intent = com.itermeum.app.sync.scheduler.NormReminderReceiver.intent(context, com.itermeum.app.core.content.NormCatalog.SERVIAM, 1, slot = 0)
        alarms.setExact(provenAt + 3 * 60_000L, alarms.broadcast(ExactAlarms.RC_SERVIAM_NUDGE, intent))
    }

    fun ourNextAlarmClock(): Long? = alarms.nextAlarmClockTriggerOf(context.packageName)
    fun canScheduleExact(): Boolean = alarms.canScheduleExact()
}
