package com.avangard.app.core.domain.repository

import com.avangard.app.core.domain.model.ChronometerProgress
import kotlinx.coroutines.flow.Flow

interface ChronometerRepository {
    fun observeProgress(): Flow<ChronometerProgress>
}
