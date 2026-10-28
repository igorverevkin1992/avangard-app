package com.itermeum.app.core.data

import com.itermeum.app.core.common.Clock
import com.itermeum.app.core.common.LogicalDay
import com.itermeum.app.core.content.NormCatalog
import com.itermeum.app.core.database.dao.DayPlanDao
import com.itermeum.app.core.database.dao.MorningDao
import com.itermeum.app.core.database.entity.CompletionQuality
import com.itermeum.app.core.database.entity.DayPlanEntity
import com.itermeum.app.core.database.entity.MorningStage
import com.itermeum.app.core.database.entity.MorningStateEntity
import com.itermeum.app.core.engine.DayScheduleEngine
import com.itermeum.app.core.engine.StreakEngine
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Section 5.1 state machine persisted in `morning_state`:
 * SCHEDULED → RINGING → PROOF_PENDING → PROVEN → HEROIC_DONE → QUARANTINE → DONE;
 * MISSED after 60 minutes without proof.
 */
@Singleton
class MorningRepository @Inject constructor(
    private val dao: MorningDao,
    private val dayPlanDao: DayPlanDao,
    private val profiles: ProfileRepository,
    private val norms: NormRepository,
    private val clock: Clock,
) {
    fun observe(date: LocalDate): Flow<MorningStateEntity?> = dao.observe(date)
    suspend fun get(date: LocalDate): MorningStateEntity? = dao.get(date)

    /** Wall-clock alarm for the logical date, honouring sick days (08:00, no proof). */
    suspend fun alarmFor(date: LocalDate): LocalDateTime {
        val profile = profiles.get()
        val sick = dayPlanDao.get(date)?.sick == true
        val time = if (sick) SICK_WAKE else DayScheduleEngine.wakeTimeFor(date, profile)
        return date.atTime(time)
    }

    /** Creates (or refreshes) the SCHEDULED row for [date] and returns its alarm epoch millis. */
    suspend fun schedule(date: LocalDate): Long {
        val alarm = alarmFor(date)
        val epoch = ZonedDateTime.of(alarm, clock.zone()).toInstant().toEpochMilli()
        val sick = dayPlanDao.get(date)?.sick == true
        val existing = dao.get(date)
        if (existing == null || existing.stage == MorningStage.SCHEDULED) {
            dao.upsert(MorningStateEntity(logicalDate = date, alarmAt = epoch, sick = sick))
        }
        return epoch
    }

    /** Only a scheduled (or re-rung) morning starts ringing; a proven one is never reset. */
    suspend fun markRinging(date: LocalDate) = transition(date) {
        if (it.stage == MorningStage.SCHEDULED || it.stage == MorningStage.RINGING) it.copy(stage = MorningStage.RINGING, rangAt = it.rangAt ?: clock.nowEpochMillis()) else it
    }

    suspend fun markProofPending(date: LocalDate) = transition(date) {
        if (it.stage == MorningStage.RINGING || it.stage == MorningStage.SCHEDULED) it.copy(stage = MorningStage.PROOF_PENDING) else it
    }

    /** Proof accepted: the «wake» norm is counted FULL inside +20 min, LATE until +60, not at all after. */
    suspend fun markProven(date: LocalDate, method: String): StreakEngine.WakeVerdict? {
        val now = clock.nowEpochMillis()
        // Without a row (alarm never armed, e.g. right after reboot) the verdict is
        // still measured against the planned alarm, never against «now».
        val state = dao.get(date) ?: MorningStateEntity(logicalDate = date, alarmAt = alarmEpoch(date))
        // A proof before the alarm (the evening before, from the status-bar chip) does not count.
        if (now < state.alarmAt) return null
        if (state.stage == MorningStage.PROVEN || state.provenAt != null) return StreakEngine.wakeVerdict(state.alarmAt, state.provenAt ?: now)
        val verdict = StreakEngine.wakeVerdict(state.alarmAt, now)
        dao.upsert(state.copy(stage = MorningStage.PROVEN, provenAt = now, proofMethod = method))
        when (verdict) {
            StreakEngine.WakeVerdict.ON_TIME -> norms.markDone(NormCatalog.WAKE, date, CompletionQuality.FULL)
            StreakEngine.WakeVerdict.LATE -> norms.markDone(NormCatalog.WAKE, date, CompletionQuality.LATE)
            StreakEngine.WakeVerdict.MISSED -> Unit
        }
        return verdict
    }

    /** Serviam pressed: counts if within 5 minutes of the proof. */
    suspend fun markHeroicDone(date: LocalDate) {
        val now = clock.nowEpochMillis()
        val state = dao.get(date) ?: return
        val proven = state.provenAt ?: return
        dao.upsert(state.copy(stage = MorningStage.QUARANTINE, heroicDoneAt = now))
        if (now - proven <= SERVIAM_WINDOW_MS) norms.markDone(NormCatalog.SERVIAM, date)
    }

    suspend fun answerQuarantine(date: LocalDate, kept: Boolean) {
        val now = clock.nowEpochMillis()
        // A missed wake stays missed: answering from Today (D-48) must not turn it into «подтверждён».
        transition(date) { it.copy(quarantineAnswer = kept, quarantineAnsweredAt = now, stage = if (it.stage == MorningStage.QUARANTINE) MorningStage.DONE else it.stage) }
        if (kept) norms.markDone(NormCatalog.QUARANTINE, date)
    }

    suspend fun markGym(date: LocalDate, home: Boolean) {
        transition(date) { it.copy(gymAt = clock.nowEpochMillis()) }
        norms.markDone(NormCatalog.GYM, date, note = if (home) "home20" else "gym")
    }

    suspend fun markMissed(date: LocalDate) = transition(date) {
        if (it.stage == MorningStage.RINGING || it.stage == MorningStage.PROOF_PENDING || it.stage == MorningStage.SCHEDULED) it.copy(stage = MorningStage.MISSED) else it
    }

    suspend fun setSick(date: LocalDate, sick: Boolean) {
        val plan = dayPlanDao.get(date) ?: DayPlanEntity(logicalDate = date)
        dayPlanDao.upsert(plan.copy(sick = sick))
        dao.get(date)?.let { dao.upsert(it.copy(sick = sick)) }
    }

    /** The logical date the next alarm belongs to: today if the alarm is still ahead, else tomorrow. */
    suspend fun nextAlarmDate(): LocalDate {
        val today = clock.logicalToday()
        val now = clock.now()
        val todayAlarm = alarmFor(today)
        val state = dao.get(today)
        val todayOpen = state == null || state.stage == MorningStage.SCHEDULED
        return if (todayOpen && now.isBefore(todayAlarm) && !now.isBefore(LogicalDay.startOf(today))) today else today.plusDays(1)
    }

    private suspend fun alarmEpoch(date: LocalDate): Long = ZonedDateTime.of(alarmFor(date), clock.zone()).toInstant().toEpochMilli()

    private suspend fun transition(date: LocalDate, f: (MorningStateEntity) -> MorningStateEntity) {
        val state = dao.get(date) ?: MorningStateEntity(logicalDate = date, alarmAt = alarmEpoch(date))
        dao.upsert(f(state))
    }

    companion object {
        val SICK_WAKE: java.time.LocalTime = java.time.LocalTime.of(8, 0)
        const val SERVIAM_WINDOW_MS = 5 * 60_000L
        const val QUARANTINE_CHECK_MIN = 35L
        const val MISSED_AFTER_MIN = 60L
    }
}
