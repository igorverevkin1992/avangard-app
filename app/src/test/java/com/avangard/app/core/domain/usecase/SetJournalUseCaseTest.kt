package com.avangard.app.core.domain.usecase

import com.avangard.app.core.common.DomainResult
import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.domain.FakeClock
import com.avangard.app.core.domain.FakeSessionRepository
import com.avangard.app.core.domain.model.DailySession
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SetJournalUseCaseTest {

    private lateinit var repository: FakeSessionRepository
    private lateinit var clock: FakeClock
    private lateinit var useCase: SetJournalUseCase

    @Before
    fun setUp() {
        clock = FakeClock()
        repository = FakeSessionRepository(clock)
        useCase = SetJournalUseCase(repository, clock)
    }

    @Test
    fun `valid entry persists trimmed text to today's session`() = runTest {
        val result = useCase("  Финальный шот собран. Контур держим.   ")
        assertTrue(result is DomainResult.Ok)
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        assertEquals(
            "Финальный шот собран. Контур держим.",
            repository.findForDate(today)!!.journalEntry,
        )
    }

    @Test
    fun `blank or null entry clears the field`() = runTest {
        useCase("seed")
        val cleared = useCase("   ")
        assertTrue(cleared is DomainResult.Ok)
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        assertNull(repository.findForDate(today)!!.journalEntry)
    }

    @Test
    fun `entry over 500 chars is rejected without touching the row`() = runTest {
        useCase("baseline")
        val overflowing = "x".repeat(DailySession.JOURNAL_MAX_CHARS + 1)
        val result = useCase(overflowing)
        assertEquals(
            DomainResult.Err(JournalError.TooLong(501, DailySession.JOURNAL_MAX_CHARS)),
            result,
        )
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        assertEquals("baseline", repository.findForDate(today)!!.journalEntry)
    }

    @Test
    fun `entry at exactly 500 chars is accepted`() = runTest {
        val limit = "x".repeat(DailySession.JOURNAL_MAX_CHARS)
        val result = useCase(limit)
        assertTrue(result is DomainResult.Ok)
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        assertEquals(limit, repository.findForDate(today)!!.journalEntry)
    }
}
