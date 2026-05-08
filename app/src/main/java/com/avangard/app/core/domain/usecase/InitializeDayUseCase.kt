package com.avangard.app.core.domain.usecase

import com.avangard.app.core.common.Clock
import com.avangard.app.core.common.DomainResult
import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.domain.ReportRules
import com.avangard.app.core.domain.model.DailyReport
import com.avangard.app.core.domain.model.ReportError
import com.avangard.app.core.domain.repository.ReportRepository
import javax.inject.Inject

class InitializeDayUseCase @Inject constructor(
    private val repository: ReportRepository,
    private val clock: Clock,
) {

    suspend operator fun invoke(
        targetArtifact: String,
        enforceTimeWindow: Boolean = true,
    ): DomainResult<Long, ReportError> {
        if (enforceTimeWindow) {
            val now = clock.localTime()
            if (now !in ReportRules.MORNING_WINDOW) {
                return DomainResult.Err(ReportError.TimeSlotMismatch)
            }
        }

        val trimmed = targetArtifact.trim()
        when {
            trimmed.isEmpty() -> return DomainResult.Err(ReportError.ArtifactEmpty)
            trimmed.length > ReportRules.ARTIFACT_MAX_LENGTH ->
                return DomainResult.Err(ReportError.ArtifactTooLong)
            !looksLikeArtifact(trimmed) ->
                return DomainResult.Err(ReportError.ArtifactInvalidShape)
        }

        val dateEpoch = clock.today().toStartOfDayEpoch(clock.zone())
        val existing = repository.findForDate(dateEpoch)
        if (existing != null) return DomainResult.Err(ReportError.AlreadyInitialized)

        val id = repository.upsert(
            DailyReport(
                id = 0,
                dateEpoch = dateEpoch,
                targetArtifact = trimmed,
                isCompleted = false,
                eliminatedWaste = 0,
                failureCause = null,
                correctiveAction = null,
            ),
        )
        return DomainResult.Ok(id)
    }

    private fun looksLikeArtifact(input: String): Boolean {
        // Reject imperatives and vague verbs ("сделать", "поработать", ...).
        // Heuristic: forbid bare infinitives ending with -ть / -ться (Russian)
        // unless the phrase has multiple tokens (e.g. "написать спецификацию" passes).
        val tokens = input.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.size == 1) {
            val lower = tokens.single().lowercase()
            if (lower.endsWith("ть") || lower.endsWith("ться")) return false
        }
        return true
    }
}
