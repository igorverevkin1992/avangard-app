package com.avangard.app.core.domain

import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.domain.model.Bottleneck
import com.avangard.app.core.domain.model.CoreStatus
import com.avangard.app.core.domain.model.DailySession
import com.avangard.app.core.domain.model.DefectKind
import com.avangard.app.core.domain.model.FocusSession
import com.avangard.app.core.domain.model.Habit
import com.avangard.app.core.domain.model.InfraStatus
import com.avangard.app.core.domain.model.VirtueScores
import com.avangard.app.core.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeSessionRepository(
    private val clock: FakeClock = FakeClock(),
) : SessionRepository {

    private val sessions = MutableStateFlow<Map<Long, DailySession>>(emptyMap())
    private val focus = MutableStateFlow<List<FocusSession>>(emptyList())
    private var sequence = 1L

    private fun zeroed(dateEpoch: Long): DailySession = DailySession(
        dateEpoch = dateEpoch,
        mvdActive = false,
        coreStatus = CoreStatus.Idle,
        infra02 = InfraStatus.NotDone,
        infra03 = InfraStatus.NotDone,
        infra04 = InfraStatus.NotDone,
        infra05 = InfraStatus.NotDone,
        eveningClosed = false,
        eveningClosedAt = null,
        virtues = null,
        bottleneckForNextWeek = null,
    )

    private fun mutate(dateEpoch: Long, block: (DailySession) -> DailySession) {
        val current = sessions.value[dateEpoch] ?: zeroed(dateEpoch)
        sessions.value = sessions.value + (dateEpoch to block(current))
    }

    override fun observeToday(): Flow<DailySession> {
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        return observeForDate(today)
    }

    override fun observeForDate(dateEpoch: Long): Flow<DailySession> =
        sessions.map { it[dateEpoch] ?: zeroed(dateEpoch) }

    override fun observeRange(fromEpoch: Long, toEpoch: Long): Flow<List<DailySession>> =
        sessions.map { map ->
            map.values
                .filter { it.dateEpoch in fromEpoch..toEpoch }
                .sortedBy { it.dateEpoch }
        }

    override suspend fun findForDate(dateEpoch: Long): DailySession? = sessions.value[dateEpoch]

    override suspend fun toggleMvd(dateEpoch: Long) {
        mutate(dateEpoch) { it.copy(mvdActive = !it.mvdActive) }
    }

    override suspend fun approveCore(dateEpoch: Long, prompt: String, approvedAt: Long) {
        mutate(dateEpoch) {
            it.copy(coreStatus = CoreStatus.Approved(prompt, approvedAt))
        }
        focus.value = focus.value.map { f ->
            if (f.endedAt == null) f.copy(endedAt = approvedAt) else f
        }
    }

    override suspend fun failCore(dateEpoch: Long, kind: DefectKind, recordedAt: Long) {
        mutate(dateEpoch) { it.copy(coreStatus = CoreStatus.Failed(kind)) }
    }

    override suspend fun setInfraStatus(
        dateEpoch: Long,
        habit: Habit,
        status: InfraStatus,
        recordedAt: Long,
    ) {
        mutate(dateEpoch) {
            when (habit) {
                Habit.Spanish -> it.copy(infra02 = status)
                Habit.Sport -> it.copy(infra03 = status)
                Habit.Watching -> it.copy(infra04 = status)
                Habit.Reading -> it.copy(infra05 = status)
                Habit.Generations -> it
            }
        }
    }

    override suspend fun closeEvening(
        dateEpoch: Long,
        virtues: VirtueScores,
        defectKind: DefectKind?,
        recordedAt: Long,
    ) {
        mutate(dateEpoch) {
            val nextStatus = if (it.coreStatus is CoreStatus.Idle && defectKind != null) {
                CoreStatus.Failed(defectKind)
            } else it.coreStatus
            it.copy(
                coreStatus = nextStatus,
                eveningClosed = true,
                eveningClosedAt = recordedAt,
                virtues = virtues,
            )
        }
    }

    override suspend fun setBottleneck(dateEpoch: Long, bottleneck: Bottleneck) {
        mutate(dateEpoch) { it.copy(bottleneckForNextWeek = bottleneck) }
    }

    override fun observeActiveFocus(): Flow<FocusSession?> =
        focus.map { list -> list.firstOrNull { it.endedAt == null } }

    override suspend fun findActiveFocus(): FocusSession? =
        focus.value.firstOrNull { it.endedAt == null }

    override fun observeFocusForDay(dateEpoch: Long): Flow<List<FocusSession>> =
        focus.map { list -> list.filter { it.dateEpoch == dateEpoch } }

    override suspend fun sumFocusDurationFor(dateEpoch: Long, habit: Habit): Long =
        focus.value
            .filter {
                it.dateEpoch == dateEpoch && it.habit == habit && it.endedAt != null
            }
            .sumOf { (it.endedAt ?: 0L) - it.startedAt }

    override suspend fun startFocus(dateEpoch: Long, habit: Habit, startedAt: Long): Long {
        val id = sequence++
        focus.value = focus.value + FocusSession(id, dateEpoch, habit, startedAt, null)
        return id
    }

    override suspend fun endFocus(id: Long, endedAt: Long) {
        focus.value = focus.value.map { if (it.id == id) it.copy(endedAt = endedAt) else it }
    }

    override suspend fun wipe() {
        sessions.value = emptyMap()
        focus.value = emptyList()
    }
}
