package com.avangard.app.core.domain

import com.avangard.app.core.domain.model.Habit
import com.avangard.app.core.domain.model.HabitMonthlyView
import com.avangard.app.core.domain.repository.HabitRepository
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeHabitRepository : HabitRepository {
    private val state = MutableStateFlow<Map<LocalDate, Set<Habit>>>(emptyMap())

    override fun observeMonth(year: Int, month: Int): Flow<HabitMonthlyView> =
        state.map { all ->
            val ym = YearMonth.of(year, month)
            val cells = all.filter { it.key.year == year && it.key.monthValue == month }
            HabitMonthlyView(
                year = year,
                month = month,
                daysInMonth = ym.lengthOfMonth(),
                cells = cells,
            )
        }

    override suspend fun toggle(date: LocalDate, habit: Habit, recordedAt: Long) {
        val current = state.value[date].orEmpty().toMutableSet()
        if (habit in current) current -= habit else current += habit
        state.value = if (current.isEmpty()) {
            state.value - date
        } else {
            state.value + (date to current)
        }
    }

    override suspend fun wipe() {
        state.value = emptyMap()
    }
}
