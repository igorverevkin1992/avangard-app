package com.itermeum.app.sync.scheduler

import android.content.Context
import android.content.Intent
import com.itermeum.app.core.common.Clock
import com.itermeum.app.core.content.ContentSeeder
import com.itermeum.app.core.content.NormCatalog
import com.itermeum.app.core.data.NormRepository
import com.itermeum.app.core.data.ProfileRepository
import com.itermeum.app.core.engine.DayScheduleEngine
import com.itermeum.app.core.engine.EscalationEngine
import com.itermeum.app.sync.alarm.AlarmHealthReceiver
import com.itermeum.app.sync.alarm.ExactAlarms
import com.itermeum.app.sync.alarm.WakeAlarmScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Section 10. Called on app start, BOOT_COMPLETED, TIME_SET,
 * TIMEZONE_CHANGED, MY_PACKAGE_REPLACED, settings changes, «Я крещён» and at
 * 03:00. Arms: the wake alarm, every remaining reminder of the current
 * logical day (levels 1/2 per the table), the streak threat, the 23:15
 * health check and the 03:00 day close. ≤20 exact alarms a day (section 13).
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val seeder: ContentSeeder,
    private val profiles: ProfileRepository,
    private val norms: NormRepository,
    private val wake: WakeAlarmScheduler,
    private val clock: Clock,
) {
    private val alarms = ExactAlarms(context)

    suspend fun rescheduleAll() {
        seeder.seedIfNeeded()
        val profile = profiles.get()
        if (!profile.onboardingDone) return
        wake.scheduleNext()
        scheduleAt(nextDaily(HEALTH_CHECK), ExactAlarms.RC_HEALTH, Intent(context, AlarmHealthReceiver::class.java).setAction(AlarmHealthReceiver.ACTION))
        scheduleAt(nextDaily(DAY_CLOSE), ExactAlarms.RC_DAY_CLOSE, Intent(context, DayCloseReceiver::class.java).setAction(DayCloseReceiver.ACTION))
        scheduleNormReminders()
    }

    private suspend fun scheduleNormReminders() {
        val date = clock.logicalToday()
        val input = norms.inputFor(date)
        val items = DayScheduleEngine.schedule(input)
        val done = norms.observeDoneIds(date).first()
        val now = clock.now()
        var rc = ExactAlarms.RC_NORM_BASE
        val planned = mutableListOf<Triple<LocalDateTime, String, Int>>()
        for (item in items) {
            if (item.normId in done || item.normId == NormCatalog.JACULATORIA) continue
            val time = item.time ?: continue
            val at = date.atTime(time)
            planned += Triple(at, item.normId, 1)
            // Level 2 per the section 10 table.
            when (item.normId) {
                NormCatalog.DAY_PLAN -> planned += Triple(date.atTime(input.profile.workStart).plusMinutes(25), item.normId, 2)
                NormCatalog.FROG -> { planned += Triple(date.atTime(13, 0), item.normId, 2); planned += Triple(date.atTime(13, 40), item.normId, 3) }
                NormCatalog.LAST_STONE -> planned += Triple(date.atTime(20, 30), item.normId, 2)
                NormCatalog.EXAMEN -> planned += Triple(date.atTime(23, 10), item.normId, 2)
                NormCatalog.CATECHESIS -> { planned += Triple(date.atTime(15, 30), item.normId, 1); planned += Triple(date.atTime(17, 45), item.normId, 2) }
                NormCatalog.MASS -> planned += Triple(at.minusMinutes(60), item.normId, 1)
                else -> if (item.isCore) planned += Triple(at.plusMinutes(EscalationEngine.LEVEL2_AFTER_MIN), item.normId, 2)
            }
        }
        // Streak threat: bedTime − 30 min (section 10), evaluated at fire time.
        planned += Triple(date.atTime(input.profile.bedTime).minusMinutes(30), NormReminderReceiver.THREAT, 3)
        planned.filter { it.first.isAfter(now) && it.second != NormCatalog.WAKE && it.second != NormCatalog.SERVIAM && it.second != NormCatalog.QUARANTINE }
            .sortedBy { it.first }
            .take(MAX_NORM_ALARMS)
            .forEach { (at, normId, level) ->
                scheduleAt(at, rc++, NormReminderReceiver.intent(context, normId, level))
            }
    }

    private fun nextDaily(time: LocalTime): LocalDateTime {
        val now = clock.now()
        var at = now.toLocalDate().atTime(time)
        if (!at.isAfter(now)) at = at.plusDays(1)
        return at
    }

    private fun scheduleAt(at: LocalDateTime, requestCode: Int, intent: Intent) {
        val epoch = ZonedDateTime.of(at, clock.zone()).toInstant().toEpochMilli()
        alarms.setExact(epoch, alarms.broadcast(requestCode, intent))
    }

    companion object {
        val HEALTH_CHECK: LocalTime = LocalTime.of(23, 15)
        val DAY_CLOSE: LocalTime = LocalTime.of(3, 0)
        /** Wake + health + day close + ≤17 norm reminders ≈ 20 exact alarms (section 13). */
        const val MAX_NORM_ALARMS = 17
    }
}
