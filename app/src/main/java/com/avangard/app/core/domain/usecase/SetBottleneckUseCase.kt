package com.avangard.app.core.domain.usecase

import com.avangard.app.core.common.Clock
import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.domain.model.Bottleneck
import com.avangard.app.core.domain.repository.SessionRepository
import javax.inject.Inject

class SetBottleneckUseCase @Inject constructor(
    private val repository: SessionRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(bottleneck: Bottleneck) {
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        repository.setBottleneck(today, bottleneck)
    }
}
