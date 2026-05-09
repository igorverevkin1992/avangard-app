package com.avangard.app.feature.habits

import com.avangard.app.core.domain.model.Habit
import com.avangard.app.core.domain.model.HabitMonthlyView
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitMonthlyViewTest {

    private fun emptyView() = HabitMonthlyView(
        year = 2026,
        month = 5,
        daysInMonth = 31,
        cells = emptyMap(),
    )

    @Test
    fun `empty view reports zero completions for every habit`() {
        val view = emptyView()
        Habit.entries.forEach { habit ->
            assertEquals(0, view.completedCount(habit))
        }
    }

    @Test
    fun `completedCount counts distinct days only`() {
        val view = HabitMonthlyView(
            year = 2026,
            month = 5,
            daysInMonth = 31,
            cells = mapOf(
                LocalDate.of(2026, 5, 1) to setOf(Habit.Sport, Habit.Reading),
                LocalDate.of(2026, 5, 2) to setOf(Habit.Sport),
                LocalDate.of(2026, 5, 3) to setOf(Habit.Reading),
            ),
        )
        assertEquals(2, view.completedCount(Habit.Sport))
        assertEquals(2, view.completedCount(Habit.Reading))
        assertEquals(0, view.completedCount(Habit.Generations))
    }

    @Test
    fun `isCompleted returns false for absent date`() {
        val view = HabitMonthlyView(
            year = 2026,
            month = 5,
            daysInMonth = 31,
            cells = mapOf(LocalDate.of(2026, 5, 7) to setOf(Habit.Spanish)),
        )
        assertTrue(view.isCompleted(LocalDate.of(2026, 5, 7), Habit.Spanish))
        assertFalse(view.isCompleted(LocalDate.of(2026, 5, 7), Habit.Sport))
        assertFalse(view.isCompleted(LocalDate.of(2026, 5, 8), Habit.Spanish))
    }
}
