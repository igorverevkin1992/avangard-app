package com.avangard.app.core.domain.usecase

import com.avangard.app.core.common.DomainResult
import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.domain.FakeClock
import com.avangard.app.core.domain.FakeSessionRepository
import com.avangard.app.core.domain.model.SessionError
import com.avangard.app.core.domain.model.VirtueScores
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ToggleMvdUseCaseTest {

    private lateinit var repository: FakeSessionRepository
    private lateinit var clock: FakeClock
    private lateinit var useCase: ToggleMvdUseCase

    @Before
    fun setUp() {
        clock = FakeClock()
        repository = FakeSessionRepository(clock)
        useCase = ToggleMvdUseCase(repository, clock)
    }

    @Test
    fun `toggle flips the flag on an open shift`() = runTest {
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        repository.approveCore(today, "Шот", clock.nowEpochMillis())

        val result = useCase()

        assertTrue(result is DomainResult.Ok)
        assertTrue(repository.findForDate(today)!!.mvdActive)
    }

    @Test
    fun `toggle is rejected after evening close`() = runTest {
        // MVD is a day-mode classifier; rewriting it after closeEvening
        // would retroactively change a sealed day.
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        repository.approveCore(today, "Шот", clock.nowEpochMillis())
        repository.closeEvening(
            dateEpoch = today,
            virtues = VirtueScores(0, 0, 0, 0),
            defectKind = null,
            recordedAt = clock.nowEpochMillis(),
        )

        val result = useCase()

        assertEquals(DomainResult.Err(SessionError.EveningClosedAlready), result)
        // The fake's pre-close state had mvdActive = false; it must still
        // be false after the rejection.
        assertEquals(false, repository.findForDate(today)!!.mvdActive)
    }
}
