package com.avangard.app.core.domain.usecase

import com.avangard.app.core.common.Clock
import com.avangard.app.core.common.DomainResult
import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.domain.model.SessionError
import com.avangard.app.core.domain.repository.SessionRepository
import javax.inject.Inject

class ToggleMvdUseCase @Inject constructor(
    private val repository: SessionRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(): DomainResult<Unit, SessionError> {
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        // MVD is a day-mode flag — toggling it after the shift has been
        // closed would retroactively rewrite the day's classification.
        // Reject the call instead of silently mutating a sealed record.
        val session = repository.findForDate(today)
        if (session?.eveningClosed == true) {
            return DomainResult.Err(SessionError.EveningClosedAlready)
        }
        repository.toggleMvd(today)
        return DomainResult.Ok(Unit)
    }
}
