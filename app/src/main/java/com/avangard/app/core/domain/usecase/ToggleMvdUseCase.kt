package com.avangard.app.core.domain.usecase

import com.avangard.app.core.common.Clock
import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.domain.repository.SessionRepository
import javax.inject.Inject

class ToggleMvdUseCase @Inject constructor(
    private val repository: SessionRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke() {
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        repository.toggleMvd(today)
    }
}
