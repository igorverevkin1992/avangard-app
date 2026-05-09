package com.avangard.app.feature.dashboard

import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.domain.FakeClock
import com.avangard.app.core.domain.FakeReportRepository
import com.avangard.app.core.domain.model.DailyReport
import com.avangard.app.core.domain.usecase.ObserveStreakUseCase
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class StreakUseCaseTest {

    private lateinit var repository: FakeReportRepository
    private lateinit var clock: FakeClock
    private lateinit var useCase: ObserveStreakUseCase

    @Before
    fun setUp() {
        repository = FakeReportRepository()
        clock = FakeClock()
        useCase = ObserveStreakUseCase(repository, clock)
    }

    private suspend fun seed(date: LocalDate, completed: Boolean) {
        val epoch = date.toStartOfDayEpoch(clock.zone())
        repository.upsert(
            DailyReport(
                id = 0,
                dateEpoch = epoch,
                targetArtifact = "X",
                isCompleted = completed,
                eliminatedWaste = 0,
                failureCause = null,
                correctiveAction = null,
            )
        )
    }

    @Test
    fun `empty history yields zero`() = runTest {
        assertEquals(0, useCase().first())
    }

    @Test
    fun `today completed alone yields one`() = runTest {
        seed(clock.today(), completed = true)
        assertEquals(1, useCase().first())
    }

    @Test
    fun `five consecutive completed days yields five`() = runTest {
        for (offset in 0..4) {
            seed(clock.today().minusDays(offset.toLong()), completed = true)
        }
        assertEquals(5, useCase().first())
    }

    @Test
    fun `gap in the middle truncates the streak`() = runTest {
        seed(clock.today(), completed = true)
        seed(clock.today().minusDays(1), completed = true)
        // day -2 missing entirely -> streak ends at 2
        seed(clock.today().minusDays(3), completed = true)
        assertEquals(2, useCase().first())
    }

    @Test
    fun `today not completed but yesterday completed yields one`() = runTest {
        seed(clock.today(), completed = false)
        seed(clock.today().minusDays(1), completed = true)
        seed(clock.today().minusDays(2), completed = true)
        assertEquals(2, useCase().first())
    }
}
