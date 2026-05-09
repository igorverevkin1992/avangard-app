package com.avangard.app.core.domain.model

import java.time.LocalDate

data class HabitMonthlyView(
    val year: Int,
    val month: Int,
    val daysInMonth: Int,
    val cells: Map<LocalDate, Set<Habit>>,
) {
    fun completedCount(habit: Habit): Int = cells.values.count { habit in it }

    fun isCompleted(date: LocalDate, habit: Habit): Boolean =
        cells[date]?.contains(habit) == true
}
