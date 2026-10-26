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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    /** App start, boot, day close and settings may all reschedule at once; one at a time. */
    private val mutex = Mutex()

    suspend fun rescheduleAll() = mutex.withLock {
        seeder.seedIfNeeded()
        val profile = profiles.get()
        if (!profile.onboardingDone) return@withLock
        wake.scheduleNext()
        scheduleAt(nextDaily(HEALTH_CHECK), ExactAlarms.RC_HEALTH, Intent(context, AlarmHealthReceiver::class.java).setAction(AlarmHealthReceiver.ACTION))
        scheduleAt(nextDaily(DAY_CLOSE), ExactAlarms.RC_DAY_CLOSE, Intent(context, DayCloseReceiver::class.java).setAction(DayCloseReceiver.ACTION))
        scheduleNormReminders()
    }

    /**
     * Every reminder has a stable identity (norm, slot) → request code, so a
     * reschedule replaces instead of piling up; the whole set is cancelled first.
     */
    private fun cancelNormReminders() {
        (NormCatalog.all.map { it.id } + NormReminderReceiver.THREAT).forEach { normId ->
            for (slot in 0 until SLOTS_PER_NORM) {
                alarms.cancel(alarms.broadcast(requestCode(normId, slot), NormReminderReceiver.intent(context, normId, 1, slot)))
            }
        }
    }

    private suspend fun scheduleNormReminders() {
        cancelNormReminders()
        val date = clock.logicalToday()
        val input = norms.inputFor(date)
        val items = DayScheduleEngine.schedule(input)
        val done = norms.observeDoneIds(date).first()
        val now = clock.now()
        val coreIds = items.filter { it.isCore }.map { it.normId }.toSet()
        val planned = mutableListOf<Triple<LocalDateTime, String, Int>>()
        for (item in items) {
            if (item.normId in done || item.normId == NormCatalog.JACULATORIA) continue
            val time = item.time ?: continue
            val at = date.atTime(time)
            planned += Triple(at, item.normId, 1)
            // Level 2 per the section 10 table.
            when (item.normId) {
                NormCatalog.DAY_PLAN -> planned += Triple(date.atTime(input.profile.workStart).plusMinutes(25), item.normId, 2)
                NormCatalog.FROG -> {
                    val cutoff = date.atTime(DayScheduleEngine.frogCutoff(input.profile))
                    planned += Triple(cutoff.minusMinutes(60), item.normId, 2); planned += Triple(cutoff.minusMinutes(20), item.normId, 3)
                }
                NormCatalog.LAST_STONE -> planned += Triple(date.atTime(20, 30), item.normId, 2)
                NormCatalog.EXAMEN -> planned += Triple(date.atTime(23, 10), item.normId, 2)
                NormCatalog.CATECHESIS -> { planned += Triple(date.atTime(15, 30), item.normId, 1); planned += Triple(date.atTime(17, 45), item.normId, 2) }
                NormCatalog.MASS -> planned += Triple(at.minusMinutes(60), item.normId, 1)
                else -> if (item.isCore) planned += Triple(at.plusMinutes(EscalationEngine.LEVEL2_AFTER_MIN), item.normId, 2)
            }
        }
        // Streak threat: bedTime − 30 min (section 10), evaluated at fire time.
        // A bedtime after midnight belongs to the same logical day, on the next calendar date.
        val bed = input.profile.bedTime.let { if (it.isBefore(LocalTime.of(3, 0))) date.plusDays(1).atTime(it) else date.atTime(it) }
        planned += Triple(bed.minusMinutes(30), NormReminderReceiver.THREAT, 3)
        // Slot = position of the reminder within its norm, in time order.
        val slotted = planned.groupBy { it.second }.flatMap { (_, list) ->
            list.sortedBy { it.first }.mapIndexed { slot, t -> Planned(t.first, t.second, t.third, slot) }
        }
        slotted.filter { it.at.isAfter(now) && it.normId != NormCatalog.WAKE && it.normId != NormCatalog.SERVIAM && it.normId != NormCatalog.QUARANTINE && it.slot < SLOTS_PER_NORM }
            // Over the cap the streak threat and core norms survive; the rest by time.
            .sortedWith(compareBy<Planned>({ !(it.normId == NormReminderReceiver.THREAT || it.normId in coreIds) }, { it.at }))
            .take(MAX_NORM_ALARMS)
            .forEach { p ->
                scheduleAt(p.at, requestCode(p.normId, p.slot), NormReminderReceiver.intent(context, p.normId, p.level, p.slot))
            }
    }

    private data class Planned(val at: LocalDateTime, val normId: String, val level: Int, val slot: Int)

    private fun requestCode(normId: String, slot: Int): Int {
        val index = if (normId == NormReminderReceiver.THREAT) NormCatalog.all.size else NormCatalog.all.indexOfFirst { it.id == normId }
        return ExactAlarms.RC_NORM_BASE + index * SLOTS_PER_NORM + slot
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
        /** 04:00, not 03:00: Examen «за вчера» is accepted until 03:59 (section 5.12). */
        val DAY_CLOSE: LocalTime = LocalTime.of(4, 0)
        /** Wake + health + day close + ≤17 norm reminders ≈ 20 exact alarms (section 13). */
        const val MAX_NORM_ALARMS = 17
        /** Max reminders per norm in a day (catechesis has three). */
        const val SLOTS_PER_NORM = 4
    }
}
