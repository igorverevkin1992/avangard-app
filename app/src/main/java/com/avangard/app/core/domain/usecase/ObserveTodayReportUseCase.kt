package com.avangard.app.core.domain.usecase

import com.avangard.app.core.common.Clock
import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.domain.model.DailyReport
import com.avangard.app.core.domain.repository.ReportRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveTodayReportUseCase @Inject constructor(
    private val repository: ReportRepository,
    private val clock: Clock,
) {
    operator fun invoke(): Flow<DailyReport?> {
        val dateEpoch = clock.today().toStartOfDayEpoch(clock.zone())
        return repository.observeForDate(dateEpoch)
    }
}
