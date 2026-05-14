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
) {
    suspend operator fun invoke(habit: Habit): DomainResult<Long, SessionError> {
        if (repository.findActiveFocus() != null) {
            return DomainResult.Err(SessionError.AnotherFocusActive)
        }
        if (habit != Habit.Generations) {
            val today = clock.today().toStartOfDayEpoch(clock.zone())
            val session = repository.findForDate(today)
            if (session?.coreStatus !is CoreStatus.Approved) {
                return DomainResult.Err(SessionError.InfraLocked)
            }
        }
        val dateEpoch = clock.today().toStartOfDayEpoch(clock.zone())
        val id = repository.startFocus(dateEpoch, habit, clock.nowEpochMillis())
        return DomainResult.Ok(id)
    }
}
