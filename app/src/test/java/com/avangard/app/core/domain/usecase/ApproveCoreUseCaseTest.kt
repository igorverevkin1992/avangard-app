package com.avangard.app.core.domain.usecase

import com.avangard.app.core.common.DomainResult
import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.domain.FakeClock
import com.avangard.app.core.domain.FakeSessionRepository
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

    @Before
    fun setUp() {
        clock = FakeClock()
        repository = FakeSessionRepository(clock)
        useCase = ApproveCoreUseCase(repository, clock)
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
    fun `approval transitions Idle to Approved and closes active focus`() = runTest {
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        val focusId = repository.startFocus(today, Habit.Generations, clock.nowEpochMillis())
        val result = useCase(prompt = "Шот пять", authorised = true)
        assertTrue(result is DomainResult.Ok)
        val stored = repository.findForDate(today)!!
        assertTrue(stored.coreStatus is CoreStatus.Approved)
        assertEquals("Шот пять", (stored.coreStatus as CoreStatus.Approved).prompt)
        // Active focus should have been closed.
        assertNull(repository.findActiveFocus())
        // And the id sequence stays valid.
        assertTrue(focusId > 0)
    }
}
