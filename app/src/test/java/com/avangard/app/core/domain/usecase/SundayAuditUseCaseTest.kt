package com.avangard.app.core.domain.usecase

import com.avangard.app.core.common.DAY_MILLIS
import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.domain.FakeClock
import com.avangard.app.core.domain.FakeSessionRepository
import com.avangard.app.core.domain.model.DefectKind
import com.avangard.app.core.domain.model.CoreMode
import com.avangard.app.core.domain.model.Habit
import com.avangard.app.core.domain.model.InfraStatus
import com.avangard.app.core.domain.model.VirtueScores
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SundayAuditUseCaseTest {

    private lateinit var repository: FakeSessionRepository
    private lateinit var clock: FakeClock
    private lateinit var useCase: SundayAuditUseCase

    @Before
    fun setUp() {
        clock = FakeClock()
        repository = FakeSessionRepository(clock)
        useCase = SundayAuditUseCase(repository, clock)
    }

    private fun day(offset: Int): Long {
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        return today + offset * DAY_MILLIS
    }

    @Test
    fun `empty week yields isEmpty true view`() = runTest {
        val view = useCase().first()
        assertTrue(view.isEmpty())
        assertEquals(0L, view.coreHoursMillis)
    }

    @Test
    fun `mixed week aggregates approved, defects, wastes, mvd, virtues`() = runTest {
        // today: Approved as MVD, with virtues
        val today = day(0)
        repository.approveCore(today, "Шот", CoreMode.Mvd, clock.nowEpochMillis())
        repository.closeEvening(
            dateEpoch = today,
            virtues = VirtueScores(1, 1, 1, 1),
            defectKind = null,
            recordedAt = clock.nowEpochMillis(),
        )
        // yesterday: Failed via evening-close with Waste defect
        val yesterday = day(-1)
        repository.closeEvening(
            dateEpoch = yesterday,
            virtues = VirtueScores(-1, 0, 0, 1),
            defectKind = DefectKind.Waste,
            recordedAt = clock.nowEpochMillis(),
        )
        // 2 days ago: Failed with Defect (no Core approved, so no mode applies).
        val twoBack = day(-2)
        repository.closeEvening(
            dateEpoch = twoBack,
            virtues = VirtueScores(0, 0, 1, 0),
            defectKind = DefectKind.Defect,
            recordedAt = clock.nowEpochMillis(),
        )
        // today: an Infra Standard mark on Sport
        repository.setInfraStatus(
            today,
            Habit.Sport,
            InfraStatus.Done,
            clock.nowEpochMillis(),
        )

        val view = useCase().first()
        assertEquals(1, view.daysApproved)
        assertEquals(1, view.defectCount)
        assertEquals(1, view.wasteCount)
        // Post-MIGRATION_6_7 mvdDays counts Approved-with-mode=Mvd only.
        // Failed days no longer carry a meaningful "MVD" flag.
        assertEquals(1, view.mvdDays)
        // Justice virtues across the three closed days: 1 + 1 + 0 = 2.
        assertEquals(2, view.virtueSums.justice)
        // Rationality: 1 + (-1) + 0 = 0.
        assertEquals(0, view.virtueSums.rationality)
        assertEquals(1, view.infraBreakdown[Habit.Sport]?.done)
    }

    @Test
    fun `core hours are computed from completed focus sessions in the window`() = runTest {
        val today = day(0)
        repository.approveCore(today, "Шот", CoreMode.Standard, clock.nowEpochMillis())
        val id = repository.startFocus(today, Habit.Generations, 1_000L)
        repository.endFocus(id, 1_000L + 30 * 60 * 1000L) // 30-minute session
        val view = useCase().first()
        assertEquals(30 * 60 * 1000L, view.coreHoursMillis)
    }
}
