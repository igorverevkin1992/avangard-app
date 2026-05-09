package com.avangard.app.core.domain.repository

import com.avangard.app.core.domain.model.Habit
import com.avangard.app.core.domain.model.HabitMonthlyView
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

interface HabitRepository {
    fun observeMonth(year: Int, month: Int): Flow<HabitMonthlyView>
    suspend fun toggle(date: LocalDate, habit: Habit, recordedAt: Long)
    suspend fun wipe()
}
