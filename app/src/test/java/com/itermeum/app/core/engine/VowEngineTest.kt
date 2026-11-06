package com.itermeum.app.core.engine

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class VowEngineTest {
    private val d = LocalDate.of(2026, 11, 1)
    private fun day(n: Long) = d.plusDays(n)

    @Test fun `kept days add up, a slip starts the series again, best and total stay`() {
        val answers = mapOf(day(0) to true, day(1) to true, day(2) to true, day(3) to false, day(4) to true)
        val s = VowEngine.stats(answers)
        assertEquals(1, s.current)
        assertEquals(3, s.best)
        assertEquals(4, s.total)
        assertEquals(1, s.slips)
        assertEquals(day(3), s.lastSlip)
    }

    @Test fun `an unanswered day neither counts nor breaks`() {
        val s = VowEngine.stats(mapOf(day(0) to true, day(2) to true))
        assertEquals(2, s.current)
        assertEquals(2, s.total)
    }

    @Test fun `order of answers does not matter`() {
        val s = VowEngine.stats(linkedMapOf(day(2) to true, day(0) to true, day(1) to false))
        assertEquals(1, s.current)
        assertEquals(1, s.best)
    }

    @Test fun `nothing answered is zero`() = assertEquals(VowEngine.Stats(), VowEngine.stats(emptyMap()))

    @Test fun `pending days run from the start to yesterday, today only in the evening`() {
        val today = day(3)
        assertEquals(listOf(day(1), day(2)), VowEngine.pending(day(1), today, emptySet(), todayOpen = false))
        assertEquals(listOf(day(2), day(3)), VowEngine.pending(day(1), today, setOf(day(1)), todayOpen = true))
        assertEquals(emptyList<LocalDate>(), VowEngine.pending(today, today, emptySet(), todayOpen = false))
    }

    @Test fun `pending looks back a week at most`() {
        val today = day(30)
        val p = VowEngine.pending(d, today, emptySet(), todayOpen = false)
        assertEquals(today.minusDays(7), p.first())
        assertEquals(7, p.size)
    }

    @Test fun `milestones follow the best series`() {
        assertEquals(listOf("sobriety_7", "sobriety_30"), VowEngine.milestoneIds(35))
        assertEquals(emptyList<String>(), VowEngine.milestoneIds(6))
    }
}
