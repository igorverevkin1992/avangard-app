package com.avangard.app.core.domain.usecase

import com.avangard.app.core.domain.model.HabitMonthlyView
import com.avangard.app.core.domain.repository.HabitRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveMonthHabitsUseCase @Inject constructor(
    private val repository: HabitRepository,
) {
    operator fun invoke(year: Int, month: Int): Flow<HabitMonthlyView> =
        repository.observeMonth(year, month)
}
