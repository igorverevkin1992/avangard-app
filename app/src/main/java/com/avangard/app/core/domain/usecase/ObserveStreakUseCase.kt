package com.avangard.app.core.domain.usecase

import com.avangard.app.core.common.Clock
import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.domain.repository.ReportRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ObserveStreakUseCase @Inject constructor(
    private val repository: ReportRepository,
    private val clock: Clock,
) {
    operator fun invoke(): Flow<Int> = repository.observeAll().map { reports ->
        val completedDays = reports.asSequence()
            .filter { it.isCompleted }
            .map { it.dateEpoch }
            .toSet()
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        var cursor = if (today in completedDays) today else today - DAY_MILLIS
        var streak = 0
        while (cursor in completedDays) {
            streak++
            cursor -= DAY_MILLIS
        }
        streak
    }

    companion object {
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
