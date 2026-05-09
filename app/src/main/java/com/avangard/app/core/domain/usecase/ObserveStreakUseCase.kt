package com.avangard.app.core.domain.usecase

import com.avangard.app.core.common.Clock
import com.avangard.app.core.common.DAY_MILLIS
import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.domain.repository.ReportRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ObserveStreakUseCase @Inject constructor(
    private val repository: ReportRepository,
    private val clock: Clock,
) {
    operator fun invoke(): Flow<Int> {
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        val from = today - WINDOW_DAYS * DAY_MILLIS
        val to = today + DAY_MILLIS - 1
        return repository.observeRange(from, to).map { reports ->
            val completedDays = reports.asSequence()
                .filter { it.isCompleted }
                .map { it.dateEpoch }
                .toSet()
            var cursor = if (today in completedDays) today else today - DAY_MILLIS
            var streak = 0
            while (cursor in completedDays) {
                streak++
                cursor -= DAY_MILLIS
            }
            streak
        }
    }

    companion object {
        private const val WINDOW_DAYS = 60L
    }
}
