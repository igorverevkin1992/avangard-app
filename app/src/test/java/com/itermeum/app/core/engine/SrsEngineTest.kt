package com.itermeum.app.core.engine

import com.itermeum.app.core.database.entity.MemorizationItemEntity
import com.itermeum.app.core.database.entity.SrsStage
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SrsEngineTest {

    private val today = LocalDate.of(2026, 11, 2)
    private fun item(stage: SrsStage, reps: Int = 0, interval: Int = 0, easy: Int = 0, ef: Double = 2.5) =
        MemorizationItemEntity("our_father", stage, ef, interval, reps, null, null, 2, easy, today)

    @Test
    fun `three easy recalls in a row make the prayer KNOWN with interval at least 21`() {
        var it = item(SrsStage.RECALL, reps = 3, interval = 6)
        it = SrsEngine.review(it, SrsEngine.Grade.EASY, today)
        it = SrsEngine.review(it, SrsEngine.Grade.EASY, today)
        assertEquals(SrsStage.RECALL, it.stage)
        it = SrsEngine.review(it, SrsEngine.Grade.EASY, today)
        assertEquals(SrsStage.KNOWN, it.stage)
        assertTrue(it.intervalDays >= 21)
        assertEquals(today.plusDays(it.intervalDays.toLong()), it.dueDate)
    }

    @Test
    fun `two easy answers promote one stage`() {
        var it = item(SrsStage.READ)
        it = SrsEngine.review(it, SrsEngine.Grade.EASY, today)
        assertEquals(SrsStage.READ, it.stage)
        it = SrsEngine.review(it, SrsEngine.Grade.EASY, today)
        assertEquals(SrsStage.CLOZE_25, it.stage)
        assertEquals(0, it.consecutiveEasy)
    }

    @Test
    fun `a miss resets repetitions and drops one stage but not below CLOZE_25`() {
        val dropped = SrsEngine.review(item(SrsStage.CLOZE_100, reps = 4, interval = 15), SrsEngine.Grade.FORGOT, today)
        assertEquals(SrsStage.CLOZE_50, dropped.stage)
        assertEquals(0, dropped.repetitions)
        assertEquals(1, dropped.intervalDays)
        assertEquals(SrsStage.CLOZE_25, SrsEngine.review(item(SrsStage.CLOZE_25), SrsEngine.Grade.FORGOT, today).stage)
    }

    @Test
    fun `easiness never drops below 1_3 and hard answers keep repetitions`() {
        var it = item(SrsStage.CLOZE_50, ef = 1.3)
        it = SrsEngine.review(it, SrsEngine.Grade.HARD, today)
        assertTrue(it.easiness >= 1.3)
        assertEquals(1, it.repetitions)
        assertEquals(1, it.intervalDays)
        it = SrsEngine.review(it, SrsEngine.Grade.HARD, today)
        assertEquals(6, it.intervalDays)
    }

    @Test
    fun `one new prayer per week`() {
        assertTrue(SrsEngine.canIntroduce(null, today))
        assertFalse(SrsEngine.canIntroduce(today.minusDays(6), today))
        assertTrue(SrsEngine.canIntroduce(today.minusDays(7), today))
    }

    @Test
    fun `cloze is deterministic and hides the right share`() {
        val text = "Отче наш, сущий на небесах! Да святится имя Твоё; да приидет Царствие Твоё"
        val a = SrsEngine.cloze(text, SrsStage.CLOZE_50, "our_father")
        val b = SrsEngine.cloze(text, SrsStage.CLOZE_50, "our_father")
        assertEquals(a, b)
        val words = a.count { it.text.any(Char::isLetter) }
        val hidden = a.count { it.hidden }
        assertTrue("hidden=$hidden of $words", kotlin.math.abs(hidden - words / 2) <= 1)
        assertTrue(SrsEngine.cloze(text, SrsStage.READ, "x").none { it.hidden })
        assertTrue(SrsEngine.cloze(text, SrsStage.RECALL, "x").filter { it.text.any(Char::isLetter) }.all { it.hidden })
    }
}
