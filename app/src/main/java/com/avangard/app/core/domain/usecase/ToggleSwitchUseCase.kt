package com.avangard.app.core.domain.usecase

import com.avangard.app.core.domain.model.SystemFlag
import com.avangard.app.core.domain.repository.ReportRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ToggleSwitchUseCase @Inject constructor(
    private val repository: ReportRepository,
) {
    fun observe(flag: SystemFlag): Flow<Boolean> = repository.observeFlag(flag)

    suspend fun set(flag: SystemFlag, enabled: Boolean) {
        repository.setFlag(flag, enabled)
    }
}
