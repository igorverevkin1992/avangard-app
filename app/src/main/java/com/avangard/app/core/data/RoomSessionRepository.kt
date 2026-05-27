package com.avangard.app.core.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.avangard.app.core.common.Clock
import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.database.AppDatabase
import com.avangard.app.core.database.dao.DailySessionDao
import com.avangard.app.core.database.dao.FocusSessionDao
import com.avangard.app.core.database.dao.HabitLogDao
import com.avangard.app.core.database.entity.DailySessionEntity
import com.avangard.app.core.database.entity.FocusSessionEntity
import com.avangard.app.core.database.entity.HabitLogEntity
import com.avangard.app.core.domain.model.Bottleneck
import com.avangard.app.core.domain.model.CoreMode
import com.avangard.app.core.domain.model.CoreStatus
import com.avangard.app.core.domain.model.DailySession
import com.avangard.app.core.domain.model.DefectKind
import com.avangard.app.core.domain.model.FocusSession
import com.avangard.app.core.domain.model.Habit
import com.avangard.app.core.domain.model.InfraStatus
import com.avangard.app.core.domain.model.VirtueScores
import com.avangard.app.core.domain.repository.SessionRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomSessionRepository @Inject constructor(
    private val database: AppDatabase,
    private val dailyDao: DailySessionDao,
    private val focusDao: FocusSessionDao,
    private val habitLogDao: HabitLogDao,
    private val clock: Clock,
) : SessionRepository {

    /**
     * Emits today's session reactively. A minute-resolution ticker re-derives
     * the date-epoch from the injected Clock; when it crosses midnight the
     * inner observeForDate flow is rewired to the new day. Without this the
     * Flow was stuck on the date captured at first subscription and showed
     * yesterday's row after midnight if the UI never resubscribed.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeToday(): Flow<DailySession> =
        todayEpochTicker()
            .flatMapLatest { epoch -> observeForDate(epoch) }

    private fun todayEpochTicker(): Flow<Long> = flow {
        while (true) {
            emit(clock.today().toStartOfDayEpoch(clock.zone()))
            delay(TODAY_TICK_INTERVAL_MS)
        }
    }.distinctUntilChanged()

    override fun observeForDate(dateEpoch: Long): Flow<DailySession> =
        dailyDao.observeByDate(dateEpoch).map { it?.toDomain() ?: zeroed(dateEpoch) }

    override fun observeRange(fromEpoch: Long, toEpoch: Long): Flow<List<DailySession>> =
        dailyDao.observeRange(fromEpoch, toEpoch).map { list -> list.map { it.toDomain() } }

    override suspend fun findForDate(dateEpoch: Long): DailySession? =
        dailyDao.findByDate(dateEpoch)?.toDomain()

    override suspend fun approveCore(
        dateEpoch: Long,
        prompt: String,
        mode: CoreMode,
        approvedAt: Long,
    ) {
        database.withTransaction {
            val current = dailyDao.ensureRow(dateEpoch)
            // Re-read under tx — defends against approve-after-approve race within the use case layer.
            dailyDao.upsert(
                current.copy(
                    coreStatus = CORE_APPROVED,
                    corePrompt = prompt,
                    coreAuthorizedAt = approvedAt,
                    coreDefectKind = null,
                    coreMode = mode.name,
                )
            )
            focusDao.findActive()?.let { focusDao.endSession(it.id, approvedAt) }
        }
    }

    override suspend fun setInfraStatus(
        dateEpoch: Long,
        habit: Habit,
        status: InfraStatus,
        recordedAt: Long,
    ) {
        if (habit == Habit.Generations) return // Core is never updated through this path.
        database.withTransaction {
            val current = dailyDao.ensureRow(dateEpoch)
            val code = status.toCode()
            val updated = when (habit) {
                Habit.Spanish -> current.copy(infra02Status = code)
                Habit.Sport -> current.copy(infra03Status = code)
                Habit.Watching -> current.copy(infra04Status = code)
                Habit.Reading -> current.copy(infra05Status = code)
                Habit.Generations -> current
            }
            dailyDao.upsert(updated)
            // habit_log bridge: atomic with the daily_session update inside the same SQLite tx.
            if (status == InfraStatus.NotDone) {
                habitLogDao.delete(dateEpoch, habit.code)
            } else {
                habitLogDao.insert(
                    HabitLogEntity(
                        dateEpoch = dateEpoch,
                        habitCode = habit.code,
                        completedAt = recordedAt,
                    )
                )
            }
        }
    }

    override suspend fun closeEvening(
        dateEpoch: Long,
        virtues: VirtueScores,
        defectKind: DefectKind?,
        recordedAt: Long,
    ) {
        database.withTransaction {
            // Re-read inside the tx so a concurrent approveCore can't slip in and
            // leave us writing a Failed transition on a now-Approved core.
            val current = dailyDao.ensureRow(dateEpoch)
            val nextCoreStatus = if (current.coreStatus == CORE_IDLE && defectKind != null) {
                CORE_FAILED
            } else {
                current.coreStatus
            }
            dailyDao.upsert(
                current.copy(
                    eveningClosed = 1,
                    eveningClosedAt = recordedAt,
                    coreStatus = nextCoreStatus,
                    coreDefectKind = defectKind?.let { if (it == DefectKind.Defect) 0 else 1 }
                        ?: current.coreDefectKind,
                    virtRationality = virtues.rationality,
                    virtIndependence = virtues.independence,
                    virtHonesty = virtues.honesty,
                    virtJustice = virtues.justice,
                )
            )
        }
    }

    override suspend fun setBottleneck(dateEpoch: Long, bottleneck: Bottleneck) {
        database.withTransaction {
            val current = dailyDao.ensureRow(dateEpoch)
            dailyDao.upsert(current.copy(bottleneckForNextWeek = bottleneck.name))
        }
    }

    override suspend fun setJournalEntry(dateEpoch: Long, entry: String?) {
        // Blank input becomes null on disk so we don't litter the row with
        // empty strings — the domain treats null and "" identically.
        val normalised = entry?.trim()?.takeIf { it.isNotEmpty() }
        database.withTransaction {
            val current = dailyDao.ensureRow(dateEpoch)
            dailyDao.upsert(current.copy(journalEntry = normalised))
        }
    }

    override fun observeActiveFocus(): Flow<FocusSession?> =
        focusDao.observeActive().map { it?.toDomain() }

    override suspend fun findActiveFocus(): FocusSession? = focusDao.findActive()?.toDomain()

    override fun observeFocusForDay(dateEpoch: Long): Flow<List<FocusSession>> =
        focusDao.observeForDay(dateEpoch).map { list -> list.mapNotNull { it.toDomain() } }

    override fun observeFocusRange(fromEpoch: Long, toEpoch: Long): Flow<List<FocusSession>> =
        focusDao.observeRange(fromEpoch, toEpoch).map { list -> list.mapNotNull { it.toDomain() } }

    override suspend fun sumFocusDurationFor(dateEpoch: Long, habit: Habit): Long =
        focusDao.sumDurationByDayAndCode(dateEpoch, habit.code)

    /**
     * Insert succeeds atomically or throws [IllegalStateException] when the partial
     * unique index `uniq_focus_active` (see MIGRATION_4_5) refuses a second
     * `ended_at IS NULL` row. The use case layer translates the throw into
     * [com.avangard.app.core.domain.model.SessionError.AnotherFocusActive].
     */
    override suspend fun startFocus(dateEpoch: Long, habit: Habit, startedAt: Long): Long =
        try {
            focusDao.insert(
                FocusSessionEntity(
                    dateEpoch = dateEpoch,
                    habitCode = habit.code,
                    startedAt = startedAt,
                    endedAt = null,
                )
            )
        } catch (_: SQLiteConstraintException) {
            throw IllegalStateException("active focus already exists")
        }

    override suspend fun endFocus(id: Long, endedAt: Long) {
        focusDao.endSession(id, endedAt)
    }

    override suspend fun wipe() {
        database.withTransaction {
            dailyDao.deleteAll()
            focusDao.deleteAll()
            habitLogDao.deleteAll()
        }
    }

    private fun zeroed(dateEpoch: Long): DailySession =
        DailySessionEntity(dateEpoch = dateEpoch).toDomain()

    companion object {
        private const val CORE_IDLE = 0
        private const val CORE_APPROVED = 1
        private const val CORE_FAILED = 2
        private const val TODAY_TICK_INTERVAL_MS = 60_000L
    }
}

private fun InfraStatus.toCode(): Int = when (this) {
    InfraStatus.NotDone -> 0
    InfraStatus.Standard -> 1
    InfraStatus.Mvd -> 2
}

private fun Int.toInfraStatus(): InfraStatus = when (this) {
    1 -> InfraStatus.Standard
    2 -> InfraStatus.Mvd
    else -> InfraStatus.NotDone
}

private fun DailySessionEntity.toDomain(): DailySession = DailySession(
    dateEpoch = dateEpoch,
    mvdActive = mvdActive == 1,
    coreStatus = when (coreStatus) {
        1 -> CoreStatus.Approved(
            prompt = corePrompt.orEmpty(),
            authorizedAt = coreAuthorizedAt ?: 0L,
            // Tolerate unknown / NULL persisted values (legacy rows on the cusp
            // of MIGRATION_6_7) by defaulting to Standard.
            mode = coreMode?.let { runCatching { CoreMode.valueOf(it) }.getOrNull() }
                ?: CoreMode.Standard,
        )
        2 -> CoreStatus.Failed(kind = if (coreDefectKind == 1) DefectKind.Waste else DefectKind.Defect)
        else -> CoreStatus.Idle
    },
    infra02 = infra02Status.toInfraStatus(),
    infra03 = infra03Status.toInfraStatus(),
    infra04 = infra04Status.toInfraStatus(),
    infra05 = infra05Status.toInfraStatus(),
    eveningClosed = eveningClosed == 1,
    eveningClosedAt = eveningClosedAt,
    virtues = if (
        virtRationality != null && virtIndependence != null &&
        virtHonesty != null && virtJustice != null
    ) {
        VirtueScores(virtRationality, virtIndependence, virtHonesty, virtJustice)
    } else null,
    // Gracefully tolerate a stale or unknown enum string (e.g. after enum rename) —
    // production builds should log this; the test suite covers the round-trip.
    bottleneckForNextWeek = bottleneckForNextWeek?.let { name ->
        runCatching { Bottleneck.valueOf(name) }.getOrNull()
    },
    journalEntry = journalEntry,
)

private fun FocusSessionEntity.toDomain(): FocusSession? {
    val habit = Habit.byCode(habitCode) ?: return null
    return FocusSession(
        id = id,
        dateEpoch = dateEpoch,
        habit = habit,
        startedAt = startedAt,
        endedAt = endedAt,
    )
}
