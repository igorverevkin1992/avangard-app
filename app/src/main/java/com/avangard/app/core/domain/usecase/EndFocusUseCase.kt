package com.avangard.app.core.domain.usecase

import com.avangard.app.core.common.Clock
import com.avangard.app.core.domain.repository.SessionRepository
import javax.inject.Inject

class EndFocusUseCase @Inject constructor(
    private val repository: SessionRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(id: Long) {
        repository.endFocus(id, clock.nowEpochMillis())
    }
}
