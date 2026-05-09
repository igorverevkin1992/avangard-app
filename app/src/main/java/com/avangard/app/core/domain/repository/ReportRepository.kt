package com.avangard.app.core.domain.repository

import com.avangard.app.core.domain.model.DailyReport
import com.avangard.app.core.domain.model.MiddayStatus
import com.avangard.app.core.domain.model.SystemFlag
import kotlinx.coroutines.flow.Flow

interface ReportRepository {

    fun observeForDate(dateEpoch: Long): Flow<DailyReport?>

    fun observeRange(fromEpoch: Long, toEpoch: Long): Flow<List<DailyReport>>

    fun observeAll(): Flow<List<DailyReport>>

    suspend fun findForDate(dateEpoch: Long): DailyReport?

    suspend fun upsert(report: DailyReport): Long

    suspend fun submitMidday(dateEpoch: Long, status: MiddayStatus, recordedAt: Long): Long

    suspend fun wipe()

    fun observeFlag(flag: SystemFlag): Flow<Boolean>

    suspend fun setFlag(flag: SystemFlag, enabled: Boolean)
}
