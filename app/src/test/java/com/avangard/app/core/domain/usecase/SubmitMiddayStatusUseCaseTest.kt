package com.avangard.app.core.domain.usecase

import com.avangard.app.core.common.DomainResult
import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.domain.FakeClock
import com.avangard.app.core.domain.FakeReportRepository
import com.avangard.app.core.domain.model.DailyReport
import com.avangard.app.core.domain.model.MiddayStatus
import com.avangard.app.core.domain.model.ReportError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SubmitMiddayStatusUseCaseTest {

    private lateinit var repository: FakeReportRepository
    private lateinit var clock: FakeClock
    private lateinit var useCase: SubmitMiddayStatusUseCase

    @Before
    fun setUp() {
        repository = FakeReportRepository()
        clock = FakeClock()
        useCase = SubmitMiddayStatusUseCase(repository, clock)
    }

    private suspend fun seedToday(artifact: String = "Написана спецификация") {
        val epoch = clock.today().toStartOfDayEpoch(clock.zone())
        repository.upsert(
            DailyReport(
                id = 0,
                dateEpoch = epoch,
                targetArtifact = artifact,
                isCompleted = false,
                eliminatedWaste = 0,
                failureCause = null,
                correctiveAction = null,
            )
        )
    }

    @Test
    fun `submitting before morning init returns NotInitialized`() = runTest {
        val result = useCase(MiddayStatus.InProgress)
        assertEquals(DomainResult.Err(ReportError.NotInitialized), result)
    }

    @Test
    fun `submitting against an empty artifact returns NotInitialized`() = runTest {
        seedToday(artifact = "")
        val result = useCase(MiddayStatus.InProgress)
        assertEquals(DomainResult.Err(ReportError.NotInitialized), result)
    }

    @Test
    fun `in-progress persists without an action`() = runTest {
        seedToday()
        val result = useCase(MiddayStatus.InProgress)
        assertTrue(result is DomainResult.Ok)
        val stored = repository.findForDate(clock.today().toStartOfDayEpoch(clock.zone()))!!
        assertEquals(MiddayStatus.InProgress, stored.midday)
    }

    @Test
    fun `blocked without 20-char action returns UnblockingActionTooShort`() = runTest {
        seedToday()
        val result = useCase(MiddayStatus.Blocked("слишком коротко"))
        assertEquals(DomainResult.Err(ReportError.UnblockingActionTooShort), result)
    }

    @Test
    fun `blocked with valid action persists trimmed text`() = runTest {
        seedToday()
        val raw = "  Перенести встречу с CTO на пятницу  "
        val result = useCase(MiddayStatus.Blocked(raw))
        assertTrue(result is DomainResult.Ok)
        val stored = repository.findForDate(clock.today().toStartOfDayEpoch(clock.zone()))!!
        assertEquals(MiddayStatus.Blocked(raw.trim()), stored.midday)
    }

    @Test
    fun `submission timestamps the record with the clock`() = runTest {
        seedToday()
        useCase(MiddayStatus.InProgress)
        val stored = repository.findForDate(clock.today().toStartOfDayEpoch(clock.zone()))!!
        assertEquals(clock.nowEpochMillis(), stored.middayRecordedAt)
    }
}
