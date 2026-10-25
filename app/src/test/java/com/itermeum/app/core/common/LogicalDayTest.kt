package com.itermeum.app.core.common

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class LogicalDayTest {

    @Test
    fun `00-30 belongs to the previous logical day`() {
        val dt = LocalDateTime.of(2026, 10, 26, 0, 30)
        assertEquals(LocalDate.of(2026, 10, 25), LogicalDay.of(dt))
    }

    @Test
    fun `02-59 still belongs to the previous logical day`() {
        val dt = LocalDateTime.of(2026, 10, 26, 2, 59)
        assertEquals(LocalDate.of(2026, 10, 25), LogicalDay.of(dt))
    }

    @Test
    fun `03-00 opens the new logical day`() {
        val dt = LocalDateTime.of(2026, 10, 26, 3, 0)
        assertEquals(LocalDate.of(2026, 10, 26), LogicalDay.of(dt))
    }

    @Test
    fun `start and end bracket the day`() {
        val day = LocalDate.of(2026, 10, 26)
        assertEquals(LocalDateTime.of(2026, 10, 26, 3, 0), LogicalDay.startOf(day))
        assertEquals(LocalDateTime.of(2026, 10, 27, 3, 0), LogicalDay.endOf(day))
    }
}
