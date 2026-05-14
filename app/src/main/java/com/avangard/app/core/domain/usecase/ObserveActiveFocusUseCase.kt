package com.avangard.app.core.domain.usecase

import com.avangard.app.core.domain.model.FocusSession
import com.avangard.app.core.domain.repository.SessionRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveActiveFocusUseCase @Inject constructor(
    private val repository: SessionRepository,
) {
    operator fun invoke(): Flow<FocusSession?> = repository.observeActiveFocus()
}
