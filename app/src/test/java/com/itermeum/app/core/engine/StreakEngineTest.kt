package com.itermeum.app.core.engine

import com.itermeum.app.core.database.entity.StreakType
import com.itermeum.app.core.engine.StreakEngine.DayFacts
import com.itermeum.app.core.engine.StreakEngine.Outcome
import com.itermeum.app.core.engine.StreakEngine.State
import com.itermeum.app.core.engine.StreakEngine.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakEngineTest {

    private fun facts(
        sick: Boolean = false, travel: Boolean = false, active: Int = 10, done: Int = 6,
        wake: Boolean = true, examen: Boolean = false, weekday: Boolean = true, calls: Int = 5, target: Int = 5,
    ) = DayFacts(sick, travel, active, done, wake, examen, weekday, calls, target)

    @Test
    fun `fidelity needs 60 percent and a core norm`() {
        assertTrue(StreakEngine.fidelityMet(facts(done = 6)))
        assertFalse(StreakEngine.fidelityMet(facts(done = 5)))
        assertFalse(StreakEngine.fidelityMet(facts(done = 6, wake = false, examen = false)))
        assertTrue(StreakEngine.fidelityMet(facts(done = 6, wake = false, examen = true)))
    }

    @Test
    fun `travel lowers the threshold to 50 percent`() {
        assertTrue(StreakEngine.fidelityMet(facts(travel = true, done = 5)))
        assertFalse(StreakEngine.fidelityMet(facts(travel = true, done = 4)))
    }

    @Test
    fun `frog counts attempts before cutoff on weekdays only`() {
        assertEquals(Verdict.MET, StreakEngine.verdict(StreakType.FROG, facts(calls = 5)))
        assertEquals(Verdict.NOT_MET, StreakEngine.verdict(StreakType.FROG, facts(calls = 4)))
        assertEquals(Verdict.SKIP, StreakEngine.verdict(StreakType.FROG, facts(weekday = false, calls = 0)))
        assertEquals(Verdict.MET, StreakEngine.verdict(StreakType.FROG, facts(calls = 2, target = 2)))
    }

    @Test
    fun `sick day skips every streak`() {
        StreakType.entries.forEach { assertEquals(Verdict.SKIP, StreakEngine.verdict(it, facts(sick = true, done = 0, wake = false))) }
        val out = StreakEngine.apply(State(7, 7, 7), Verdict.SKIP, freezesLeft = 2)
        assertTrue(out is Outcome.Skipped)
        assertEquals(7, out.state.current)
    }

    @Test
    fun `first and second miss freeze, third breaks`() {
        var s = State(10, 10, 10)
        val o1 = StreakEngine.apply(s, Verdict.NOT_MET, freezesLeft = 2)
        assertTrue(o1 is Outcome.Frozen); s = o1.state
        val o2 = StreakEngine.apply(s, Verdict.NOT_MET, freezesLeft = 1)
        assertTrue(o2 is Outcome.Frozen); s = o2.state
        val o3 = StreakEngine.apply(s, Verdict.NOT_MET, freezesLeft = 0)
        assertTrue(o3 is Outcome.Broken)
        assertEquals(0, o3.state.current)
        assertEquals(10, o3.state.best)
        assertEquals(10, o3.state.totalDays)
    }

    @Test
    fun `advance updates best and total`() {
        val o = StreakEngine.apply(State(3, 5, 20), Verdict.MET, 2)
        assertEquals(State(4, 5, 21), o.state)
        val o2 = StreakEngine.apply(State(5, 5, 21), Verdict.MET, 2)
        assertEquals(State(6, 6, 22), o2.state)
    }

    @Test
    fun `wake window`() {
        val alarm = 1_000_000L
        assertEquals(StreakEngine.WakeVerdict.ON_TIME, StreakEngine.wakeVerdict(alarm, alarm + 20 * 60_000))
        assertEquals(StreakEngine.WakeVerdict.LATE, StreakEngine.wakeVerdict(alarm, alarm + 21 * 60_000))
        assertEquals(StreakEngine.WakeVerdict.LATE, StreakEngine.wakeVerdict(alarm, alarm + 60 * 60_000))
        assertEquals(StreakEngine.WakeVerdict.MISSED, StreakEngine.wakeVerdict(alarm, alarm + 61 * 60_000))
        assertEquals(StreakEngine.WakeVerdict.MISSED, StreakEngine.wakeVerdict(alarm, null))
    }
}
