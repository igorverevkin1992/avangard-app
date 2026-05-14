package com.avangard.app.core.domain.usecase

import com.avangard.app.core.domain.FakeClock
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IsHistoryUnlockedUseCaseTest {

    @Test
    fun `Sunday unlocks`() {
        val clock = FakeClock(today = LocalDate.of(2026, 5, 10))
        assertTrue(IsHistoryUnlockedUseCase(clock).invoke())
    }

    @Test
    fun `Tuesday blocks`() {
        val clock = FakeClock(today = LocalDate.of(2026, 5, 12))
        assertFalse(IsHistoryUnlockedUseCase(clock).invoke())
    }
}
