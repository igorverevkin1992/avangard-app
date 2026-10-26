package com.itermeum.app.core.data

import androidx.room.withTransaction
import com.itermeum.app.core.common.Clock
import com.itermeum.app.core.content.ContentAssets
import com.itermeum.app.core.content.NormCatalog
import com.itermeum.app.core.database.AppDatabase
import com.itermeum.app.core.database.dao.CompletionDao
import com.itermeum.app.core.database.dao.DayPlanDao
import com.itermeum.app.core.database.dao.LiturgyDao
import com.itermeum.app.core.database.dao.MorningDao
import com.itermeum.app.core.database.dao.OutreachDao
import com.itermeum.app.core.database.dao.SrsDao
import com.itermeum.app.core.database.dao.StreakDao
import com.itermeum.app.core.database.entity.CompletionQuality
import com.itermeum.app.core.database.entity.FreezeLedgerEntity
import com.itermeum.app.core.database.entity.MilestoneEntity
import com.itermeum.app.core.database.entity.StreakStateEntity
import com.itermeum.app.core.database.entity.StreakType
import com.itermeum.app.core.engine.DayScheduleEngine
import com.itermeum.app.core.engine.StreakEngine
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Section 6: evaluates closed days (idempotent per `lastCountedDate`),
 * applies the two monthly freezes, keeps best/total and checks milestones.
 */
@Singleton
class StreakRepository @Inject constructor(
    private val db: AppDatabase,
    private val streakDao: StreakDao,
    private val completionDao: CompletionDao,
    private val morningDao: MorningDao,
    private val outreachDao: OutreachDao,
    private val dayPlanDao: DayPlanDao,
    private val srsDao: SrsDao,
    private val liturgyDao: LiturgyDao,
    private val norms: NormRepository,
    private val profiles: ProfileRepository,
    private val assets: ContentAssets,
    private val clock: Clock,
) {
    data class DayResult(val date: LocalDate, val outcomes: Map<StreakType, StreakEngine.Outcome>)

    fun observeStreaks(): Flow<List<StreakStateEntity>> = streakDao.observeAll()
    fun observeMilestones(): Flow<List<MilestoneEntity>> = streakDao.observeMilestones()
    fun observeUnseenMilestone(): Flow<MilestoneEntity?> = streakDao.observeUnseen()
    fun observeFreezes(yearMonth: String): Flow<List<FreezeLedgerEntity>> = streakDao.observeFreezes(yearMonth)

    /** Section 6.4: two «Милосердие» freezes a month, counted per streak (D-20). */
    suspend fun freezesLeft(yearMonth: String, type: StreakType): Int = (StreakEngine.FREEZES_PER_MONTH - streakDao.freezesUsed(yearMonth, type)).coerceAtLeast(0)

    /** Facts for one logical day, from what the day recorded. */
    suspend fun facts(date: LocalDate): StreakEngine.DayFacts {
        val input = norms.inputFor(date)
        val schedule = DayScheduleEngine.schedule(input)
        val active = schedule.map { it.normId }.toSet()
        val completions = completionDao.forDate(date)
        val done = completions.map { it.normId }.toSet()
        val wakeCompletion = completions.firstOrNull { it.normId == NormCatalog.WAKE }
        val wakeMet = wakeCompletion != null && wakeCompletion.quality == CompletionQuality.FULL
        val wakeLate = wakeCompletion != null && wakeCompletion.quality == CompletionQuality.LATE
        val weekday = date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY
        val target = schedule.firstOrNull { it.normId == NormCatalog.FROG }?.target ?: input.profile.outreachTarget
        return StreakEngine.DayFacts(
            sick = input.sick,
            travel = input.travel,
            activeNorms = active.size,
            completedNorms = (done intersect active).size,
            wakeMet = wakeMet,
            wakeLate = wakeLate,
            heroesActive = NormCatalog.FROG in active,
            examenDone = NormCatalog.EXAMEN in done,
            weekday = weekday,
            outreachBeforeCutoff = outreachDao.countBeforeCutoff(date),
            outreachTarget = target,
        )
    }

    /** Evaluates every uncounted day up to and including [upTo] (normally yesterday). */
    suspend fun closeDaysUpTo(upTo: LocalDate): List<DayResult> {
        val results = mutableListOf<DayResult>()
        val states = StreakType.entries.associateWith { streakDao.get(it) ?: StreakStateEntity(type = it) }
        val created = profiles.get().createdAt ?: upTo
        val earliest = states.values.mapNotNull { it.lastCountedDate }.minOrNull()?.plusDays(1) ?: created
        var date = maxOf(earliest, created)
        while (!date.isAfter(upTo)) {
            results += closeDay(date)
            date = date.plusDays(1)
        }
        return results
    }

    suspend fun closeDay(date: LocalDate): DayResult = db.withTransaction {
        val facts = facts(date)
        val yearMonth = "%04d-%02d".format(date.year, date.monthValue)
        val outcomes = mutableMapOf<StreakType, StreakEngine.Outcome>()
        for (type in StreakType.entries) {
            val state = streakDao.get(type) ?: StreakStateEntity(type = type)
            if (state.lastCountedDate != null && !state.lastCountedDate.isBefore(date)) continue
            val verdict = StreakEngine.verdict(type, facts)
            val left = freezesLeft(yearMonth, type)
            val outcome = StreakEngine.apply(StreakEngine.State(state.current, state.best, state.totalDays), verdict, left)
            if (outcome is StreakEngine.Outcome.Frozen) streakDao.insertFreeze(FreezeLedgerEntity(yearMonth = yearMonth, usedOn = date, streakType = type))
            streakDao.upsert(state.copy(current = outcome.state.current, best = outcome.state.best, totalDays = outcome.state.totalDays, lastCountedDate = date))
            outcomes[type] = outcome
        }
        checkMilestones()
        DayResult(date, outcomes)
    }

    /** Section 6.4 counters → milestone rows (idempotent). */
    suspend fun checkMilestones() {
        val now = clock.nowEpochMillis()
        val wake = streakDao.get(StreakType.WAKE)
        val fidelity = streakDao.get(StreakType.FIDELITY)
        val calls = outreachDao.totalAttempts()
        val agreements = outreachDao.totalAgreements()
        val known = srsDao.knownCount()
        val total = srsDao.count()
        val rosaries = liturgyDao.fullRosaries()
        val firstQr = morningDao.latest()?.let { completionDao.countFor(NormCatalog.WAKE) > 0 } ?: false
        val reached = buildList {
            if (firstQr) add("first_wake_qr")
            wake?.let { w -> listOf(7, 30, 100, 365).forEach { if (w.best >= it) add("wake_$it") } }
            fidelity?.let { f -> listOf(30, 100, 365).forEach { if (f.best >= it) add("fidelity_$it") } }
            if (calls >= 100) add("calls_100"); if (calls >= 500) add("calls_500")
            if (agreements >= 50) add("agreements_50"); if (agreements >= 200) add("agreements_200")
            if (known >= 1) add("prayer_known"); if (total > 0 && known >= total) add("all_prayers_known")
            if (rosaries >= 50) add("rosaries_50")
            if (profiles.get().baptized) add("baptized")
        }
        reached.forEach { streakDao.achieve(it, now) }
    }

    suspend fun markMilestoneSeen(id: String) = streakDao.markSeen(id)

    fun milestoneMeta(id: String) = assets.milestones.items.firstOrNull { it.id == id }

    suspend fun fidelityDaysBetween(from: LocalDate, to: LocalDate): Int {
        var d = from; var n = 0
        while (!d.isAfter(to)) { if (StreakEngine.fidelityMet(facts(d))) n++; d = d.plusDays(1) }
        return n
    }
}
