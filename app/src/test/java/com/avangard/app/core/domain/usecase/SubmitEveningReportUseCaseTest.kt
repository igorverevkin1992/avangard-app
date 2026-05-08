package com.avangard.app.core.domain.usecase

import com.avangard.app.core.common.DomainResult
import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.domain.FakeClock
import com.avangard.app.core.domain.FakeReportRepository
import com.avangard.app.core.domain.model.DailyReport
import com.avangard.app.core.domain.model.ReportError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SubmitEveningReportUseCaseTest {

    private lateinit var repository: FakeReportRepository
    private lateinit var clock: FakeClock
    private lateinit var useCase: SubmitEveningReportUseCase

    @Before
    fun setUp() {
        repository = FakeReportRepository()
        clock = FakeClock()
        useCase = SubmitEveningReportUseCase(repository, clock)
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
            ),
        )
    }

    @Test
    fun `submitting before morning init returns NotInitialized`() = runTest {
        val result = useCase(
            SubmitEveningReportUseCase.Input(
                isCompleted = true,
                eliminatedWaste = 3,
            ),
        )
        assertEquals(DomainResult.Err(ReportError.NotInitialized), result)
    }

    @Test
    fun `successful day persists isCompleted=true`() = runTest {
        seedToday()
        val result = useCase(
            SubmitEveningReportUseCase.Input(
                isCompleted = true,
                eliminatedWaste = 5,
            ),
        )
        assertTrue(result is DomainResult.Ok)
        val stored = repository.findForDate(clock.today().toStartOfDayEpoch(clock.zone()))!!
        assertTrue(stored.isCompleted)
        assertEquals(5, stored.eliminatedWaste)
        assertNull(stored.failureCause)
    }

    @Test
    fun `failure without cause fails validation`() = runTest {
        seedToday()
        val result = useCase(
            SubmitEveningReportUseCase.Input(
                isCompleted = false,
                eliminatedWaste = 0,
                failureCause = "коротко",
                correctiveAction = "тоже коротко",
            ),
        )
        assertEquals(DomainResult.Err(ReportError.FailureCauseTooShort), result)
    }

    @Test
    fun `failure with cause but short action fails`() = runTest {
        seedToday()
        val result = useCase(
            SubmitEveningReportUseCase.Input(
                isCompleted = false,
                eliminatedWaste = 0,
                failureCause = "Не выделил время на ключевую задачу.",
                correctiveAction = "коротко",
            ),
        )
        assertEquals(DomainResult.Err(ReportError.CorrectiveActionTooShort), result)
    }

    @Test
    fun `failure with both fields valid persists analysis`() = runTest {
        seedToday()
        val cause = "Не выделил время на ключевую задачу."
        val action = "Блокировать утренние слоты в календаре."
        val result = useCase(
            SubmitEveningReportUseCase.Input(
                isCompleted = false,
                eliminatedWaste = 2,
                failureCause = cause,
                correctiveAction = action,
            ),
        )
        assertTrue(result is DomainResult.Ok)
        val stored = repository.findForDate(clock.today().toStartOfDayEpoch(clock.zone()))!!
        assertEquals(false, stored.isCompleted)
        assertEquals(cause, stored.failureCause)
        assertEquals(action, stored.correctiveAction)
    }

    @Test
    fun `negative waste is clamped to zero`() = runTest {
        seedToday()
        useCase(
            SubmitEveningReportUseCase.Input(
                isCompleted = true,
                eliminatedWaste = -5,
            ),
        )
        val stored = repository.findForDate(clock.today().toStartOfDayEpoch(clock.zone()))!!
        assertEquals(0, stored.eliminatedWaste)
    }
}
