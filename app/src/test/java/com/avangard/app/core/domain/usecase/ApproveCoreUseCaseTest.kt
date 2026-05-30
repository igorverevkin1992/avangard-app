package com.avangard.app.core.domain.usecase

import com.avangard.app.core.common.DomainResult
import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.domain.FakeClock
import com.avangard.app.core.domain.FakeSessionRepository
import com.avangard.app.core.domain.NoopStatusNotifier
import com.avangard.app.core.domain.StatusEventBus
import com.avangard.app.core.domain.model.CoreMode
import com.avangard.app.core.domain.model.CoreStatus
import com.avangard.app.core.domain.model.Habit
import com.avangard.app.core.domain.model.SessionError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApproveCoreUseCaseTest {

    private lateinit var repository: FakeSessionRepository
    private lateinit var clock: FakeClock
    private lateinit var useCase: ApproveCoreUseCase
    private lateinit var setDayMode: SetDayModeUseCase

    @Before
    fun setUp() {
        clock = FakeClock()
        repository = FakeSessionRepository(clock)
        useCase = ApproveCoreUseCase(repository, clock, StatusEventBus(), NoopStatusNotifier)
        setDayMode = SetDayModeUseCase(repository, clock)
    }

    @Test
    fun `approval fails without the authorisation checkbox`() = runTest {
        val result = useCase(prompt = "Сохранённый шот", authorised = false)
        assertEquals(DomainResult.Err(SessionError.NotAuthorised), result)
    }

    @Test
    fun `approval fails on an empty prompt`() = runTest {
        val result = useCase(prompt = "   ", authorised = true)
        assertEquals(DomainResult.Err(SessionError.PromptEmpty), result)
    }

    @Test
    fun `approval is rejected when Core is already Approved`() = runTest {
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        repository.approveCore(today, "Шот первый", clock.nowEpochMillis())
        val result = useCase(prompt = "Шот второй", authorised = true)
        assertEquals(DomainResult.Err(SessionError.AlreadyApproved), result)
        val stored = repository.findForDate(today)!!
        // Existing prompt must be preserved.
        assertEquals("Шот первый", (stored.coreStatus as CoreStatus.Approved).prompt)
    }

    @Test
    fun `approval transitions Idle to Approved and closes active focus`() = runTest {
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        val focusId = repository.startFocus(today, Habit.Generations, clock.nowEpochMillis())
        val result = useCase(prompt = "Шот пять", authorised = true)
        assertTrue(result is DomainResult.Ok)
        val stored = repository.findForDate(today)!!
        assertTrue(stored.coreStatus is CoreStatus.Approved)
        assertEquals("Шот пять", (stored.coreStatus as CoreStatus.Approved).prompt)
        assertNull(repository.findActiveFocus())
        assertTrue(focusId > 0)
    }

    @Test
    fun `approval preserves the day mode picked earlier in the day`() = runTest {
        // Operator picks MVD via the header chip first; approval inherits it.
        setDayMode(CoreMode.Mvd)
        val result = useCase(prompt = "Минимум", authorised = true)
        assertTrue(result is DomainResult.Ok)
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        val stored = repository.findForDate(today)!!
        assertEquals(CoreMode.Mvd, stored.dayMode)
    }

    @Test
    fun `setDayMode is one-shot per day`() = runTest {
        assertEquals(DomainResult.Ok(Unit), setDayMode(CoreMode.Standard))
        // Second call rejects — mode is locked.
        assertEquals(
            DomainResult.Err(SessionError.AlreadyApproved),
            setDayMode(CoreMode.Mvd),
        )
    }
}
