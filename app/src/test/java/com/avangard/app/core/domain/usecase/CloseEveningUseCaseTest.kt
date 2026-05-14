package com.avangard.app.core.domain.usecase

import com.avangard.app.core.common.DomainResult
import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.domain.FakeClock
import com.avangard.app.core.domain.FakeSessionRepository
import com.avangard.app.core.domain.model.CoreStatus
import com.avangard.app.core.domain.model.DefectKind
import com.avangard.app.core.domain.model.SessionError
import com.avangard.app.core.domain.model.VirtueScores
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CloseEveningUseCaseTest {

    private lateinit var repository: FakeSessionRepository
    private lateinit var clock: FakeClock
    private lateinit var useCase: CloseEveningUseCase

    private val virtues = VirtueScores(1, 1, 1, 1)

    @Before
    fun setUp() {
        clock = FakeClock()
        repository = FakeSessionRepository(clock)
        useCase = CloseEveningUseCase(repository, clock)
    }

    @Test
    fun `Idle core demands a defect kind`() = runTest {
        val result = useCase(virtues = virtues, defectKindWhenIdle = null)
        assertEquals(DomainResult.Err(SessionError.MissingDefectKind), result)
    }

    @Test
    fun `Idle close with Waste persists Failed core and virtues`() = runTest {
        val result = useCase(virtues = virtues, defectKindWhenIdle = DefectKind.Waste)
        assertTrue(result is DomainResult.Ok)
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        val stored = repository.findForDate(today)!!
        assertTrue(stored.eveningClosed)
        assertEquals(CoreStatus.Failed(DefectKind.Waste), stored.coreStatus)
        assertEquals(virtues, stored.virtues)
    }

    @Test
    fun `Approved core closes evening without requiring a defect`() = runTest {
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        repository.approveCore(today, "Шот", clock.nowEpochMillis())
        val result = useCase(virtues = virtues, defectKindWhenIdle = null)
        assertTrue(result is DomainResult.Ok)
        val stored = repository.findForDate(today)!!
        assertTrue(stored.eveningClosed)
        assertTrue(stored.coreStatus is CoreStatus.Approved)
    }

    @Test
    fun `Second close attempt is rejected`() = runTest {
        useCase(virtues = virtues, defectKindWhenIdle = DefectKind.Defect)
        val result = useCase(virtues = virtues, defectKindWhenIdle = DefectKind.Defect)
        assertEquals(DomainResult.Err(SessionError.EveningClosedAlready), result)
    }
}
