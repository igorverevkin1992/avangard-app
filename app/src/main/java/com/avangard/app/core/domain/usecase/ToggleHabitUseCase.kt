package com.avangard.app.core.domain.usecase

import com.avangard.app.core.common.Clock
import com.avangard.app.core.domain.model.Habit
import com.avangard.app.core.domain.repository.HabitRepository
import java.time.LocalDate
import javax.inject.Inject

class ToggleHabitUseCase @Inject constructor(
    private val repository: HabitRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(date: LocalDate, habit: Habit) {
        repository.toggle(date, habit, clock.nowEpochMillis())
    }
}
