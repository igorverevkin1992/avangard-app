package com.itermeum.app.core.engine

import com.itermeum.app.core.engine.EscalationEngine.Budget
import com.itermeum.app.core.engine.EscalationEngine.Candidate
import com.itermeum.app.core.engine.EscalationEngine.Priority
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EscalationEngineTest {

    private val day = LocalDate.of(2026, 11, 2)
    private fun at(h: Int, m: Int = 0) = day.atTime(h, m)

    @Test
    fun `levels`() {
        val frog = at(12, 5)
        assertNull(EscalationEngine.level(frog, isCore = true, done = false, now = at(12, 0)))
        assertEquals(1, EscalationEngine.level(frog, true, false, at(12, 5)))
        assertEquals(1, EscalationEngine.level(frog, true, false, at(13, 34)))
        assertEquals(2, EscalationEngine.level(frog, true, false, at(13, 35)))
        assertEquals(1, EscalationEngine.level(frog, false, false, at(15, 0)))
        assertNull(EscalationEngine.level(frog, true, done = true, now = at(15, 0)))
    }

    @Test
    fun `threat is due two hours before bed, once, only when fidelity is short`() {
        val bed = LocalTime.of(23, 0)
        assertFalse(EscalationEngine.threatDue(at(20, 59), bed, fidelityMet = false, level3SentToday = 0))
        assertTrue(EscalationEngine.threatDue(at(21, 0), bed, false, 0))
        assertFalse(EscalationEngine.threatDue(at(21, 0), bed, true, 0))
        assertFalse(EscalationEngine.threatDue(at(21, 0), bed, false, 1))
    }

    @Test
    fun `quiet hours and daily limit, alarm always passes`() {
        val b = Budget(sentToday = 8, level3SentToday = 0)
        assertFalse(EscalationEngine.allow(Candidate("rosary", 1, at(20, 15), Priority.OTHER, false), b))
        assertTrue(EscalationEngine.allow(Candidate("wake", 1, at(6, 30), Priority.ALARM, false), b))
        val open = Budget(0, 0)
        assertFalse(EscalationEngine.allow(Candidate("x", 1, at(23, 45), Priority.OTHER, false), open))
        assertFalse(EscalationEngine.allow(Candidate("x", 1, at(5, 59), Priority.OTHER, false), open))
        assertTrue(EscalationEngine.allow(Candidate("x", 1, at(6, 0), Priority.OTHER, false), open))
        assertFalse(EscalationEngine.allow(Candidate("mass", 1, at(18, 0), Priority.OTHER, muted = true), open))
    }

    @Test
    fun `prioritisation keeps alarm then health then core`() {
        val budget = Budget(sentToday = 6, level3SentToday = 0)
        val cands = listOf(
            Candidate("rosary", 1, at(20, 15), Priority.OTHER, false),
            Candidate("frog", 2, at(13, 35), Priority.CORE, false),
            Candidate("health", 1, at(23, 15), Priority.HEALTH, false),
            Candidate("wake", 1, at(6, 30), Priority.ALARM, false),
            Candidate("gospel", 1, at(8, 30), Priority.OTHER, false),
        )
        val kept = EscalationEngine.prioritize(cands, budget).map { it.normId }
        assertEquals(listOf("wake", "health", "frog"), kept)
    }
}
