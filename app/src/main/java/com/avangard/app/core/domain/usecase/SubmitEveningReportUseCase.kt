package com.avangard.app.core.domain.usecase

import com.avangard.app.core.common.Clock
import com.avangard.app.core.common.DomainResult
import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.domain.ReportRules
import com.avangard.app.core.domain.model.ReportError
import com.avangard.app.core.domain.repository.ReportRepository
import javax.inject.Inject

class SubmitEveningReportUseCase @Inject constructor(
    private val repository: ReportRepository,
    private val clock: Clock,
) {

    data class Input(
        val isCompleted: Boolean,
        val eliminatedWaste: Int,
        val failureCause: String? = null,
        val correctiveAction: String? = null,
    )

    suspend operator fun invoke(input: Input): DomainResult<Long, ReportError> {
        val dateEpoch = clock.today().toStartOfDayEpoch(clock.zone())
        val current = repository.findForDate(dateEpoch)
            ?: return DomainResult.Err(ReportError.NotInitialized)

        if (!input.isCompleted) {
            val cause = input.failureCause?.trim().orEmpty()
            val action = input.correctiveAction?.trim().orEmpty()
            if (cause.length < ReportRules.ANALYSIS_MIN_LENGTH) {
                return DomainResult.Err(ReportError.FailureCauseTooShort)
            }
            if (action.length < ReportRules.ANALYSIS_MIN_LENGTH) {
                return DomainResult.Err(ReportError.CorrectiveActionTooShort)
            }
        }

        val updated = current.copy(
            isCompleted = input.isCompleted,
            eliminatedWaste = input.eliminatedWaste.coerceAtLeast(0),
            failureCause = input.failureCause?.trim()?.takeIf { !input.isCompleted },
            correctiveAction = input.correctiveAction?.trim()?.takeIf { !input.isCompleted },
        )
        val id = repository.upsert(updated)
        return DomainResult.Ok(id)
    }
}
