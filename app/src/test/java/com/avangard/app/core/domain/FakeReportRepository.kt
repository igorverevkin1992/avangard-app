package com.avangard.app.core.domain

import com.avangard.app.core.domain.model.DailyReport
import com.avangard.app.core.domain.model.MiddayStatus
import com.avangard.app.core.domain.model.SystemFlag
import com.avangard.app.core.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeReportRepository : ReportRepository {
    private val reports = MutableStateFlow<Map<Long, DailyReport>>(emptyMap())
    private val flags = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private var sequence = 1L

    override fun observeForDate(dateEpoch: Long): Flow<DailyReport?> =
        reports.map { it[dateEpoch] }

    override fun observeRange(fromEpoch: Long, toEpoch: Long): Flow<List<DailyReport>> =
        reports.map { map ->
            map.values.filter { it.dateEpoch in fromEpoch..toEpoch }
                .sortedBy { it.dateEpoch }
        }

    override fun observeAll(): Flow<List<DailyReport>> =
        reports.map { it.values.sortedByDescending { r -> r.dateEpoch } }

    override suspend fun findForDate(dateEpoch: Long): DailyReport? = reports.value[dateEpoch]

    override suspend fun upsert(report: DailyReport): Long {
        val id = if (report.id == 0L) sequence++ else report.id
        val stored = report.copy(id = id)
        reports.value = reports.value + (stored.dateEpoch to stored)
        return id
    }

    override suspend fun submitMidday(
        dateEpoch: Long,
        status: MiddayStatus,
        recordedAt: Long,
    ): Long {
        val current = reports.value[dateEpoch] ?: DailyReport(
            id = 0,
            dateEpoch = dateEpoch,
            targetArtifact = "",
            isCompleted = false,
            eliminatedWaste = 0,
            failureCause = null,
            correctiveAction = null,
        )
        return upsert(current.copy(midday = status, middayRecordedAt = recordedAt))
    }

    override suspend fun wipe() {
        reports.value = emptyMap()
        flags.value = emptyMap()
    }

    override fun observeFlag(flag: SystemFlag): Flow<Boolean> =
        flags.map { it[flag.key] ?: false }

    override suspend fun setFlag(flag: SystemFlag, enabled: Boolean) {
        flags.value = flags.value + (flag.key to enabled)
    }
}
