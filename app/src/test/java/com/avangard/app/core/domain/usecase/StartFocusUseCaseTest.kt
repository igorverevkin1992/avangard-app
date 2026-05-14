package com.avangard.app.core.domain.usecase

import com.avangard.app.core.common.DomainResult
import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.domain.FakeClock
import com.avangard.app.core.domain.FakeSessionRepository
import com.avangard.app.core.domain.model.Habit
import com.avangard.app.core.domain.model.SessionError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StartFocusUseCaseTest {

    private lateinit var repository: FakeSessionRepository
    private lateinit var clock: FakeClock
    private lateinit var useCase: StartFocusUseCase

    @Before
    fun setUp() {
        clock = FakeClock()
        repository = FakeSessionRepository(clock)
        useCase = StartFocusUseCase(repository, clock)
    }

    @Test
    fun `Core can always be started when idle and no other focus active`() = runTest {
        val result = useCase(Habit.Generations)
        assertTrue(result is DomainResult.Ok)
        val active = repository.findActiveFocus()
        assertEquals(Habit.Generations, active?.habit)
    }

    @Test
    fun `Infra is locked when Core is not yet approved`() = runTest {
        val result = useCase(Habit.Sport)
        assertEquals(DomainResult.Err(SessionError.InfraLocked), result)
    }

    @Test
    fun `Infra unlocks after Core is approved`() = runTest {
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        repository.approveCore(today, "Сохранённый шот", clock.nowEpochMillis())
        val result = useCase(Habit.Sport)
        assertTrue(result is DomainResult.Ok)
    }

    @Test
    fun `Starting Core when already Approved returns AlreadyApproved`() = runTest {
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        repository.approveCore(today, "Шот", clock.nowEpochMillis())
        // After approval the auto-closed focus session leaves no active row.
        val result = useCase(Habit.Generations)
        assertEquals(DomainResult.Err(SessionError.AlreadyApproved), result)
    }

    @Test
    fun `Starting a second focus while one is active returns AnotherFocusActive`() = runTest {
        useCase(Habit.Generations)
        val result = useCase(Habit.Generations)
        assertEquals(DomainResult.Err(SessionError.AnotherFocusActive), result)
    }
}
