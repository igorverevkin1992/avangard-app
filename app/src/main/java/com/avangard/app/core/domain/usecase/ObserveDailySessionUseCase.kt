package com.avangard.app.core.domain.usecase

import com.avangard.app.core.domain.model.DailySession
import com.avangard.app.core.domain.repository.SessionRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveDailySessionUseCase @Inject constructor(
    private val repository: SessionRepository,
) {
    operator fun invoke(): Flow<DailySession> = repository.observeToday()
}
