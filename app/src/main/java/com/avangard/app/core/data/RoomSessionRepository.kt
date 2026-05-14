package com.avangard.app.core.data

import com.avangard.app.core.database.dao.DailySessionDao
import com.avangard.app.core.database.dao.FocusSessionDao
import com.avangard.app.core.database.entity.DailySessionEntity
import com.avangard.app.core.database.entity.FocusSessionEntity
import com.avangard.app.core.domain.model.Bottleneck
import com.avangard.app.core.domain.model.CoreStatus
import com.avangard.app.core.domain.model.DailySession
import com.avangard.app.core.domain.model.DefectKind
import com.avangard.app.core.domain.model.FocusSession
import com.avangard.app.core.domain.model.Habit
import com.avangard.app.core.domain.model.InfraStatus
import com.avangard.app.core.domain.model.VirtueScores
import com.avangard.app.core.domain.repository.SessionRepository
import com.avangard.app.core.common.Clock
import com.avangard.app.core.common.toStartOfDayEpoch
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomSessionRepository @Inject constructor(
    private val dailyDao: DailySessionDao,
    private val focusDao: FocusSessionDao,
    private val clock: Clock,
) : SessionRepository {

    override fun observeToday(): Flow<DailySession> {
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        return observeForDate(today)
    }

    override fun observeForDate(dateEpoch: Long): Flow<DailySession> =
        dailyDao.observeByDate(dateEpoch).map { it?.toDomain() ?: zeroed(dateEpoch) }

    override fun observeRange(fromEpoch: Long, toEpoch: Long): Flow<List<DailySession>> =
        dailyDao.observeRange(fromEpoch, toEpoch).map { list -> list.map { it.toDomain() } }

    override suspend fun findForDate(dateEpoch: Long): DailySession? =
        dailyDao.findByDate(dateEpoch)?.toDomain()

    override suspend fun toggleMvd(dateEpoch: Long) {
        val current = ensureRow(dateEpoch)
        dailyDao.upsert(current.copy(mvdActive = if (current.mvdActive == 1) 0 else 1))
    }

    override suspend fun approveCore(dateEpoch: Long, prompt: String, approvedAt: Long) {
        val current = ensureRow(dateEpoch)
        dailyDao.upsert(
            current.copy(
                coreStatus = CORE_APPROVED,
                corePrompt = prompt,
                coreAuthorizedAt = approvedAt,
                coreDefectKind = null,
            )
        )
        // Close any active focus session to keep accounting consistent.
        focusDao.findActive()?.let { focusDao.endSession(it.id, approvedAt) }
    }

    override suspend fun failCore(dateEpoch: Long, kind: DefectKind, recordedAt: Long) {
        val current = ensureRow(dateEpoch)
        dailyDao.upsert(
            current.copy(
                coreStatus = CORE_FAILED,
                coreDefectKind = if (kind == DefectKind.Defect) 0 else 1,
            )
        )
    }

    override suspend fun setInfraStatus(
        dateEpoch: Long,
        habit: Habit,
        status: InfraStatus,
        recordedAt: Long,
    ) {
        val current = ensureRow(dateEpoch)
        val code = status.toCode()
        val updated = when (habit) {
            Habit.Generations -> current // Core is never updated through this path.
            Habit.Spanish -> current.copy(infra02Status = code)
            Habit.Sport -> current.copy(infra03Status = code)
            Habit.Watching -> current.copy(infra04Status = code)
            Habit.Reading -> current.copy(infra05Status = code)
        }
        dailyDao.upsert(updated)
        // habit_log bridge lands in commit 6 — for now the daily_session row is the source of truth.
    }

    override suspend fun closeEvening(
        dateEpoch: Long,
        virtues: VirtueScores,
        defectKind: DefectKind?,
        recordedAt: Long,
    ) {
        val current = ensureRow(dateEpoch)
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

    override suspend fun setBottleneck(dateEpoch: Long, bottleneck: Bottleneck) {
        val current = ensureRow(dateEpoch)
        dailyDao.upsert(current.copy(bottleneckForNextWeek = bottleneck.name))
    }

    override fun observeActiveFocus(): Flow<FocusSession?> =
        focusDao.observeActive().map { it?.toDomain() }

    override suspend fun findActiveFocus(): FocusSession? = focusDao.findActive()?.toDomain()

    override fun observeFocusForDay(dateEpoch: Long): Flow<List<FocusSession>> =
        focusDao.observeForDay(dateEpoch).map { list -> list.mapNotNull { it.toDomain() } }

    override suspend fun sumFocusDurationFor(dateEpoch: Long, habit: Habit): Long =
        focusDao.sumDurationByDayAndCode(dateEpoch, habit.code)

    override suspend fun startFocus(dateEpoch: Long, habit: Habit, startedAt: Long): Long =
        focusDao.insert(
            FocusSessionEntity(
                dateEpoch = dateEpoch,
                habitCode = habit.code,
                startedAt = startedAt,
                endedAt = null,
            )
        )

    override suspend fun endFocus(id: Long, endedAt: Long) {
        focusDao.endSession(id, endedAt)
    }

    override suspend fun wipe() {
        dailyDao.deleteAll()
        focusDao.deleteAll()
    }

    private suspend fun ensureRow(dateEpoch: Long): DailySessionEntity =
        dailyDao.findByDate(dateEpoch) ?: DailySessionEntity(dateEpoch = dateEpoch).also {
            dailyDao.upsert(it)
        }

    private fun zeroed(dateEpoch: Long): DailySession =
        DailySessionEntity(dateEpoch = dateEpoch).toDomain()

    companion object {
        private const val CORE_IDLE = 0
        private const val CORE_APPROVED = 1
        private const val CORE_FAILED = 2
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
        1 -> CoreStatus.Approved(prompt = corePrompt.orEmpty(), authorizedAt = coreAuthorizedAt ?: 0L)
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
    bottleneckForNextWeek = bottleneckForNextWeek?.let { name ->
        runCatching { Bottleneck.valueOf(name) }.getOrNull()
    },
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
