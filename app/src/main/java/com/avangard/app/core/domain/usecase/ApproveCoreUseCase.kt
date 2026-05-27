package com.avangard.app.core.domain.usecase

import com.avangard.app.core.common.Clock
import com.avangard.app.core.common.DomainResult
import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.domain.model.CoreMode
import com.avangard.app.core.domain.model.CoreStatus
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
     * @param mode SP-bucket the day lands in: Standard → Extracted in chronometer,
     * Mvd → Partial.
     */
    suspend operator fun invoke(
        prompt: String,
        authorised: Boolean,
        mode: CoreMode,
    ): DomainResult<Unit, SessionError> {
        val trimmed = prompt.trim()
        if (!authorised) return DomainResult.Err(SessionError.NotAuthorised)
        if (trimmed.isEmpty()) return DomainResult.Err(SessionError.PromptEmpty)
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        // Prevent overwriting an existing approval — re-submitting the modal would
        // otherwise replace the saved prompt and authorisation timestamp.
        if (repository.findForDate(today)?.coreStatus is CoreStatus.Approved) {
            return DomainResult.Err(SessionError.AlreadyApproved)
        }
        repository.approveCore(today, trimmed, mode, clock.nowEpochMillis())
        return DomainResult.Ok(Unit)
    }
}
