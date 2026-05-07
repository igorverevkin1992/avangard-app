package com.avangard.app.core.domain.usecase

import com.avangard.app.core.common.DomainResult
import com.avangard.app.core.domain.FakeClock
import com.avangard.app.core.domain.FakeReportRepository
import com.avangard.app.core.domain.model.ReportError
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InitializeDayUseCaseTest {

    private lateinit var repository: FakeReportRepository
    private lateinit var clock: FakeClock
    private lateinit var useCase: InitializeDayUseCase

    @Before
    fun setUp() {
        repository = FakeReportRepository()
        clock = FakeClock()
        useCase = InitializeDayUseCase(repository, clock)
    }

    @Test
    fun `valid artifact inside morning window succeeds`() = runTest {
        clock.time = LocalTime.of(7, 0)
        val result = useCase("Написана спецификация")
        assertTrue(result is DomainResult.Ok)
    }

    @Test
    fun `outside morning window returns TimeSlotMismatch`() = runTest {
        clock.time = LocalTime.of(9, 30)
        val result = useCase("Написана спецификация")
        assertEquals(DomainResult.Err(ReportError.TimeSlotMismatch), result)
    }

    @Test
    fun `empty artifact returns ArtifactEmpty`() = runTest {
        val result = useCase("   ")
        assertEquals(DomainResult.Err(ReportError.ArtifactEmpty), result)
    }

    @Test
    fun `artifact longer than max returns ArtifactTooLong`() = runTest {
        val long = "артефакт ".repeat(20)
        val result = useCase(long)
        assertEquals(DomainResult.Err(ReportError.ArtifactTooLong), result)
    }

    @Test
    fun `single bare infinitive is rejected as invalid shape`() = runTest {
        val result = useCase("работать")
        assertEquals(DomainResult.Err(ReportError.ArtifactInvalidShape), result)
    }

    @Test
    fun `infinitive with object is accepted`() = runTest {
        val result = useCase("написать спецификацию")
        assertTrue(result is DomainResult.Ok)
    }

    @Test
    fun `second initialization on same day fails`() = runTest {
        useCase("Написана спецификация")
        val second = useCase("Собран прототип")
        assertEquals(DomainResult.Err(ReportError.AlreadyInitialized), second)
    }

    @Test
    fun `time window can be skipped via flag`() = runTest {
        clock.time = LocalTime.of(15, 0)
        val result = useCase("Написана спецификация", enforceTimeWindow = false)
        assertTrue(result is DomainResult.Ok)
    }
}
