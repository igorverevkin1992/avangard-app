package com.avangard.app.core.domain.usecase

import com.avangard.app.core.common.Clock
import com.avangard.app.core.common.DomainResult
import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.domain.model.CoreStatus
import com.avangard.app.core.domain.model.Habit
import com.avangard.app.core.domain.model.InfraStatus
import com.avangard.app.core.domain.model.SessionError
import com.avangard.app.core.domain.repository.SessionRepository
import javax.inject.Inject

class SetInfraStatusUseCase @Inject constructor(
    private val repository: SessionRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        habit: Habit,
        status: InfraStatus,
    ): DomainResult<Unit, SessionError> {
        if (habit == Habit.Generations) return DomainResult.Err(SessionError.InfraLocked)
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        val session = repository.findForDate(today)
        if (session?.coreStatus !is CoreStatus.Approved) {
            return DomainResult.Err(SessionError.InfraLocked)
        }
        repository.setInfraStatus(today, habit, status, clock.nowEpochMillis())
        return DomainResult.Ok(Unit)
    }
}
