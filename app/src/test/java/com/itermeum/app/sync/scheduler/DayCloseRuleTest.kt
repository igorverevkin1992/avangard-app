package com.itermeum.app.sync.scheduler

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/** Sections 5.12 and 6.1/11.6: when yesterday may be closed. */
class DayCloseRuleTest {

    private val today = LocalDate.of(2026, 11, 3)
    private fun epoch(h: Int, m: Int) = today.atTime(h, m).atZone(ZoneId.of("Europe/Moscow")).toInstant().toEpochMilli()

    @Test
    fun `examen grace hour keeps yesterday open until 04_00`() {
        assertEquals(today.minusDays(2), DayCloseRunner.lastClosableDay(today, today.atTime(3, 59), 0, epoch(3, 59), false))
        assertEquals(today.minusDays(1), DayCloseRunner.lastClosableDay(today, today.atTime(4, 0), 0, epoch(4, 0), false))
    }

    @Test
    fun `after an eastward flight the new date starts with the wake proof or at noon`() {
        // Moscow → Vladivostok: landed and the zone changed at 05:00 local.
        val tz = epoch(5, 0)
        assertEquals(today.minusDays(2), DayCloseRunner.lastClosableDay(today, today.atTime(7, 0), tz, epoch(7, 0), false))
        assertEquals(today.minusDays(1), DayCloseRunner.lastClosableDay(today, today.atTime(7, 0), tz, epoch(7, 0), true))
        assertEquals(today.minusDays(1), DayCloseRunner.lastClosableDay(today, today.atTime(12, 0), tz, epoch(12, 0), false))
    }

    @Test
    fun `an old zone change does not hold the close`() {
        val tz = epoch(5, 0) - 30 * 3_600_000L
        assertEquals(today.minusDays(1), DayCloseRunner.lastClosableDay(today, today.atTime(7, 0), tz, epoch(7, 0), false))
    }
}
