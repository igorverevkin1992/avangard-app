package com.itermeum.app.core.engine

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingProgramTest {
    private val created = LocalDate.of(2026, 11, 2)

    @Test
    fun `month 1 day 10 is still month 1`() {
        assertEquals(1, ReadingProgram.monthIndex(created, created.plusDays(9), 7))
    }

    @Test
    fun `calendar month boundaries advance the index`() {
        assertEquals(2, ReadingProgram.monthIndex(created, LocalDate.of(2026, 12, 1), 7))
        assertEquals(7, ReadingProgram.monthIndex(created, LocalDate.of(2027, 5, 3), 7))
    }

    @Test
    fun `clamped to the last month`() {
        assertEquals(7, ReadingProgram.monthIndex(created, LocalDate.of(2030, 1, 1), 7))
    }
}
