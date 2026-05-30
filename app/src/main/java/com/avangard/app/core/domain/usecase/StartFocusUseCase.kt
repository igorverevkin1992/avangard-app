package com.avangard.app.core.domain.usecase

import com.avangard.app.core.common.Clock
import com.avangard.app.core.common.DomainResult
import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.domain.model.CoreStatus
import com.avangard.app.core.domain.model.Habit
import com.avangard.app.core.domain.model.SessionError
import com.avangard.app.core.domain.repository.SessionRepository
import javax.inject.Inject

class StartFocusUseCase @Inject constructor(
    private val repository: SessionRepository,
    private val clock: Clock,
    private val focusService: FocusServiceController,
) {
    suspend operator fun invoke(
        habit: Habit,
        intent: String? = null,
    ): DomainResult<Long, SessionError> {
        // Pre-flight check first — surfaces a clean error when the conflict is
        // already visible in the read model. The partial unique index in
        // MIGRATION_4_5 is the actual atomic guarantor — catch it below too.
        if (repository.findActiveFocus() != null) {
            return DomainResult.Err(SessionError.AnotherFocusActive)
        }
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        val session = repository.findForDate(today)
        if (habit == Habit.Generations &&
            session?.coreStatus is CoreStatus.Approved
        ) {
            // Core already Approved → starting again would double-count effort
            // when sumFocusDurationFor aggregates the day.
            return DomainResult.Err(SessionError.AlreadyApproved)
        }
        // Infra habits (Spanish, Sport, Watching, Reading) are start-able at
        // any point in the day — the operator owns sequencing. The pulpit
        // surfaces a Core-reminder banner while Generations is still Idle so
        // the central act doesn't get lost behind the side habits.
        val id = try {
            repository.startFocus(today, habit, clock.nowEpochMillis(), intent)
        } catch (_: IllegalStateException) {
            // Partial unique index uniq_focus_active fired between the pre-flight
            // and the insert — concurrent tap. Surface the same error as the
            // pre-flight branch.
            return DomainResult.Err(SessionError.AnotherFocusActive)
        }
        // Bring up the ongoing-notification companion. The service tears
        // itself down once observeActiveFocus emits null again, so
        // EndFocusUseCase doesn't need a symmetric stop. The persisted row
        // is the source of truth — if startForegroundService is blocked
        // (background restriction edge case), the pulpit still recovers
        // the session on next foreground and a stale Err here would lie
        // to the caller. Swallow + log.
        runCatching { focusService.start() }
            .onFailure { android.util.Log.w("StartFocus", "FlashForegroundService not started", it) }
        return DomainResult.Ok(id)
    }
}
