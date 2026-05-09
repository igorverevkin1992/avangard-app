package com.avangard.app.core.domain.usecase

import com.avangard.app.core.common.Clock
import com.avangard.app.core.common.DomainResult
import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.domain.ReportRules
import com.avangard.app.core.domain.model.MiddayStatus
import com.avangard.app.core.domain.model.ReportError
import com.avangard.app.core.domain.repository.ReportRepository
import javax.inject.Inject

class SubmitMiddayStatusUseCase @Inject constructor(
    private val repository: ReportRepository,
    private val clock: Clock,
) {

    suspend operator fun invoke(status: MiddayStatus): DomainResult<Long, ReportError> {
        if (status is MiddayStatus.Blocked &&
            status.unblockingAction.trim().length < ReportRules.ANALYSIS_MIN_LENGTH
        ) {
            return DomainResult.Err(ReportError.UnblockingActionTooShort)
        }

        val dateEpoch = clock.today().toStartOfDayEpoch(clock.zone())
        val current = repository.findForDate(dateEpoch)
        if (current == null || current.targetArtifact.isBlank()) {
            return DomainResult.Err(ReportError.NotInitialized)
        }

        val normalized = if (status is MiddayStatus.Blocked) {
            MiddayStatus.Blocked(status.unblockingAction.trim())
        } else {
            status
        }

        val id = repository.submitMidday(dateEpoch, normalized, clock.nowEpochMillis())
        return DomainResult.Ok(id)
    }
}
