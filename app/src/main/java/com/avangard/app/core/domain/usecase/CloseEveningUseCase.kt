package com.avangard.app.core.domain.usecase

import com.avangard.app.core.common.Clock
import com.avangard.app.core.common.DomainResult
import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.domain.model.CoreStatus
import com.avangard.app.core.domain.model.DefectKind
import com.avangard.app.core.domain.model.SessionError
import com.avangard.app.core.domain.model.VirtueScores
import com.avangard.app.core.domain.repository.SessionRepository
import javax.inject.Inject

class CloseEveningUseCase @Inject constructor(
    private val repository: SessionRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        virtues: VirtueScores,
        defectKindWhenIdle: DefectKind?,
    ): DomainResult<Unit, SessionError> {
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        val session = repository.findForDate(today)
        if (session?.eveningClosed == true) {
            return DomainResult.Err(SessionError.EveningClosedAlready)
        }
        // No row yet = no Core progress today = treat as Idle.
        val coreStatus = session?.coreStatus ?: CoreStatus.Idle
        if (coreStatus is CoreStatus.Idle && defectKindWhenIdle == null) {
            return DomainResult.Err(SessionError.MissingDefectKind)
        }
        repository.closeEvening(
            dateEpoch = today,
            virtues = virtues,
            defectKind = defectKindWhenIdle,
            recordedAt = clock.nowEpochMillis(),
        )
        return DomainResult.Ok(Unit)
    }
}
