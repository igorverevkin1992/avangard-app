package com.avangard.app.core.domain.usecase

import com.avangard.app.core.common.Clock
import com.avangard.app.core.common.DomainResult
import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.domain.model.SessionError
import com.avangard.app.core.domain.repository.SessionRepository
import javax.inject.Inject

class ApproveCoreUseCase @Inject constructor(
    private val repository: SessionRepository,
    private val clock: Clock,
) {
    /**
     * @param authorised the explicit checkbox from the AuthorisationModal —
     * without it Core cannot be approved (conscious-authorisation gate).
     */
    suspend operator fun invoke(
        prompt: String,
        authorised: Boolean,
    ): DomainResult<Unit, SessionError> {
        val trimmed = prompt.trim()
        if (!authorised) return DomainResult.Err(SessionError.NotAuthorised)
        if (trimmed.isEmpty()) return DomainResult.Err(SessionError.PromptEmpty)
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        repository.approveCore(today, trimmed, clock.nowEpochMillis())
        return DomainResult.Ok(Unit)
    }
}
