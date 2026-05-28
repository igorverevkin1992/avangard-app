package com.avangard.app.core.domain.usecase

import com.avangard.app.core.common.Clock
import com.avangard.app.core.common.DomainResult
import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.domain.model.CoreMode
import com.avangard.app.core.domain.model.SessionError
import com.avangard.app.core.domain.repository.SessionRepository
import javax.inject.Inject

/**
 * Commits the day-level СТАНДАРТ / МИНИМУМ mode chosen by the operator via
 * the header toggle. One-shot per day: once a mode is set, this use case
 * surfaces [SessionError.AlreadyApproved] on every subsequent call so the
 * picker can be locked at the UI layer too.
 *
 * Chronometer + audit metrics derive Extracted vs Partial classification
 * from this single field.
 */
class SetDayModeUseCase @Inject constructor(
    private val repository: SessionRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(mode: CoreMode): DomainResult<Unit, SessionError> {
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        val current = repository.findForDate(today)
        if (current?.dayMode != null) {
            return DomainResult.Err(SessionError.AlreadyApproved)
        }
        repository.setDayMode(today, mode)
        return DomainResult.Ok(Unit)
    }
}
