package com.avangard.app.core.domain

import com.avangard.app.core.domain.model.ChronometerProgress
import com.avangard.app.core.domain.repository.ChronometerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Test double — emits a single [ChronometerProgress.EMPTY] so the pulpit
 * VM's `combine` doesn't park waiting for a chronometer emission.
 */
object NoopChronometerRepository : ChronometerRepository {
    override fun observeProgress(): Flow<ChronometerProgress> = flowOf(ChronometerProgress.EMPTY)
}
