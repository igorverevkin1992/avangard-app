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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric so the focus-service failure-swallow case can call Log.w
// without hitting "not mocked" on android.util.Log.
@RunWith(RobolectricTestRunner::class)
class StartFocusUseCaseTest {

    private lateinit var repository: FakeSessionRepository
    private lateinit var clock: FakeClock
    private lateinit var focusService: RecordingFocusService
    private lateinit var useCase: StartFocusUseCase

    @Before
    fun setUp() {
        clock = FakeClock()
        repository = FakeSessionRepository(clock)
        focusService = RecordingFocusService()
        useCase = StartFocusUseCase(repository, clock, focusService)
    }

    private class RecordingFocusService : FocusServiceController {
        var starts: Int = 0
            private set

        override fun start() {
            starts++
        }
    }

    @Test
    fun `Core can always be started when idle and no other focus active`() = runTest {
        val result = useCase(Habit.Generations)
        assertTrue(result is DomainResult.Ok)
        val active = repository.findActiveFocus()
        assertEquals(Habit.Generations, active?.habit)
        // Side effect: the ongoing-notification service was poked exactly once.
        assertEquals(1, focusService.starts)
    }

    @Test
    fun `successful Infra start also pokes the focus service`() = runTest {
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        repository.approveCore(today, "Шот", clock.nowEpochMillis())

        val result = useCase(Habit.Sport)
        assertTrue(result is DomainResult.Ok)
        assertEquals(1, focusService.starts)
    }

    @Test
    fun `rejected start does not poke the focus service`() = runTest {
        val result = useCase(Habit.Sport)
        assertEquals(DomainResult.Err(SessionError.InfraLocked), result)
        assertEquals(0, focusService.starts)
    }

    @Test
    fun `start succeeds even when focus service throws`() = runTest {
        // The persisted row is the source of truth; a failed
        // startForegroundService (e.g. background restriction edge case)
        // must not roll back the Ok return — the pulpit recovers state from
        // the row on next foreground.
        val throwingService = object : FocusServiceController {
            override fun start(): Unit = throw IllegalStateException("background restriction")
        }
        val resilient = StartFocusUseCase(repository, clock, throwingService)
        val result = resilient(Habit.Generations)
        assertTrue(result is DomainResult.Ok)
        assertEquals(Habit.Generations, repository.findActiveFocus()?.habit)
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
