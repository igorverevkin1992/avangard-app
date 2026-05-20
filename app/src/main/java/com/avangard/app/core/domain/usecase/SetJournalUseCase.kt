package com.avangard.app.core.domain.usecase

import com.avangard.app.core.common.Clock
import com.avangard.app.core.common.DomainResult
import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.domain.model.DailySession
import com.avangard.app.core.domain.repository.SessionRepository
import javax.inject.Inject

sealed interface JournalError {
    /** Caller submitted more than [DailySession.JOURNAL_MAX_CHARS] characters. */
    data class TooLong(val length: Int, val limit: Int) : JournalError
}

/**
 * Stores the day's journal entry. Blank input clears the field (treated as
 * null on disk). The 500-char cap enforces the "succinct end-of-day summary"
 * intent — the screen also clamps input length, but this is the domain-level
 * guarantee for any caller that bypasses the textarea.
 */
class SetJournalUseCase @Inject constructor(
    private val repository: SessionRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(entry: String?): DomainResult<Unit, JournalError> {
        val trimmed = entry?.trim()
        if (trimmed != null && trimmed.length > DailySession.JOURNAL_MAX_CHARS) {
            return DomainResult.Err(
                JournalError.TooLong(trimmed.length, DailySession.JOURNAL_MAX_CHARS)
            )
        }
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        repository.setJournalEntry(today, trimmed)
        return DomainResult.Ok(Unit)
    }
}
