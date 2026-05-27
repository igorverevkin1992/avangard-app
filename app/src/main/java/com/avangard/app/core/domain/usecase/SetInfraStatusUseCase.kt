package com.avangard.app.core.domain.usecase

import com.avangard.app.core.common.Clock
import com.avangard.app.core.common.DomainResult
import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.domain.StatusEventBus
import com.avangard.app.core.domain.StatusFixedEvent
import com.avangard.app.core.domain.model.CoreStatus
import com.avangard.app.core.domain.model.Habit
import com.avangard.app.core.domain.model.InfraStatus
import com.avangard.app.core.domain.model.SessionError
import com.avangard.app.core.domain.repository.SessionRepository
import com.avangard.app.sync.notifications.StatusNotifier
import javax.inject.Inject

class SetInfraStatusUseCase @Inject constructor(
    private val repository: SessionRepository,
    private val clock: Clock,
    private val statusBus: StatusEventBus,
    private val statusNotifier: StatusNotifier,
) {
    suspend operator fun invoke(
        habit: Habit,
        status: InfraStatus,
    ): DomainResult<Unit, SessionError> {
        if (habit == Habit.Generations) return DomainResult.Err(SessionError.InfraLocked)
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        val session = repository.findForDate(today)
        // Morning habits (Spanish, Sport) can be marked Standard/MVD at any
        // point in the day per the operator's real schedule. Evening habits
        // (Watching, Reading) still wait for Core Approved.
        if (habit.requiresCoreApproval &&
            session?.coreStatus !is CoreStatus.Approved
        ) {
            return DomainResult.Err(SessionError.InfraLocked)
        }
        repository.setInfraStatus(today, habit, status, clock.nowEpochMillis())
        if (status != InfraStatus.NotDone) {
            val label = if (status == InfraStatus.Standard) STANDARD_LABEL else MVD_LABEL
            statusBus.tryEmit(StatusFixedEvent(habit, label))
            statusNotifier.notifyStatusFix(habit, label)
        }
        return DomainResult.Ok(Unit)
    }

    private companion object {
        const val STANDARD_LABEL = "СТАНДАРТ"
        const val MVD_LABEL = "МИНИМУМ"
    }
}
