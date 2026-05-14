package com.avangard.app.core.domain.usecase

import com.avangard.app.core.common.Clock
import com.avangard.app.core.domain.model.AccessPolicy
import javax.inject.Inject

class IsHistoryUnlockedUseCase @Inject constructor(
    private val clock: Clock,
) {
    operator fun invoke(): Boolean = AccessPolicy.isHistoryUnlocked(clock.today())
}
