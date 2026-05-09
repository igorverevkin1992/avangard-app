package com.avangard.app.core.data

import com.avangard.app.core.database.dao.DailyLogDao
import com.avangard.app.core.database.dao.SystemMetricDao
import com.avangard.app.core.database.entity.DailyLogEntity
import com.avangard.app.core.database.entity.SystemMetricEntity
import com.avangard.app.core.domain.model.DailyReport
import com.avangard.app.core.domain.model.MiddayStatus
import com.avangard.app.core.domain.model.MiddayStatus.Companion.actionText
import com.avangard.app.core.domain.model.MiddayStatus.Companion.toCode
import com.avangard.app.core.domain.model.SystemFlag
import com.avangard.app.core.domain.repository.ReportRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomReportRepository @Inject constructor(
    private val dailyLogDao: DailyLogDao,
    private val systemMetricDao: SystemMetricDao,
) : ReportRepository {

    override fun observeForDate(dateEpoch: Long): Flow<DailyReport?> =
        dailyLogDao.observeByDate(dateEpoch).map { it?.toDomain() }

    override fun observeRange(fromEpoch: Long, toEpoch: Long): Flow<List<DailyReport>> =
        dailyLogDao.observeRange(fromEpoch, toEpoch).map { list -> list.map { it.toDomain() } }

    override fun observeAll(): Flow<List<DailyReport>> =
        dailyLogDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun findForDate(dateEpoch: Long): DailyReport? =
        dailyLogDao.findByDate(dateEpoch)?.toDomain()

    override suspend fun upsert(report: DailyReport): Long =
        dailyLogDao.upsert(report.toEntity())

    override suspend fun submitMidday(
        dateEpoch: Long,
        status: MiddayStatus,
        recordedAt: Long,
    ): Long {
        val current = dailyLogDao.findByDate(dateEpoch)
            ?: DailyLogEntity(dateEpoch = dateEpoch, targetArtifact = "")
        val updated = current.copy(
            middayStatus = status.toCode(),
            middayAction = status.actionText(),
            middayRecordedAt = recordedAt,
        )
        return dailyLogDao.upsert(updated)
    }

    override suspend fun wipe() {
        dailyLogDao.deleteAll()
        systemMetricDao.deleteAll()
    }

    override fun observeFlag(flag: SystemFlag): Flow<Boolean> =
        systemMetricDao.observe(flag.key).map { it == "1" }

    override suspend fun setFlag(flag: SystemFlag, enabled: Boolean) {
        systemMetricDao.put(SystemMetricEntity(flag.key, if (enabled) "1" else "0"))
    }
}

private fun DailyLogEntity.toDomain(): DailyReport = DailyReport(
    id = id,
    dateEpoch = dateEpoch,
    targetArtifact = targetArtifact,
    isCompleted = isCompleted == 1,
    eliminatedWaste = eliminatedWaste,
    failureCause = failureCause,
    correctiveAction = correctiveAction,
    midday = MiddayStatus.fromCode(middayStatus, middayAction),
    middayRecordedAt = middayRecordedAt,
)

private fun DailyReport.toEntity(): DailyLogEntity = DailyLogEntity(
    id = id,
    dateEpoch = dateEpoch,
    targetArtifact = targetArtifact,
    isCompleted = if (isCompleted) 1 else 0,
    eliminatedWaste = eliminatedWaste,
    failureCause = failureCause,
    correctiveAction = correctiveAction,
    middayStatus = midday.toCode(),
    middayAction = midday.actionText(),
    middayRecordedAt = middayRecordedAt,
)
