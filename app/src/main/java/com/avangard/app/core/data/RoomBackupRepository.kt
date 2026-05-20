package com.avangard.app.core.data

import androidx.room.withTransaction
import com.avangard.app.core.database.AppDatabase
import com.avangard.app.core.database.dao.DailySessionDao
import com.avangard.app.core.database.dao.FocusSessionDao
import com.avangard.app.core.database.dao.HabitLogDao
import com.avangard.app.core.database.entity.DailySessionEntity
import com.avangard.app.core.database.entity.FocusSessionEntity
import com.avangard.app.core.database.entity.HabitLogEntity
import com.avangard.app.core.domain.model.BackupBundle
import com.avangard.app.core.domain.model.BackupDailySession
import com.avangard.app.core.domain.model.BackupFocusSession
import com.avangard.app.core.domain.model.BackupHabitLog
import com.avangard.app.core.domain.repository.BackupRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomBackupRepository @Inject constructor(
    private val database: AppDatabase,
    private val dailyDao: DailySessionDao,
    private val focusDao: FocusSessionDao,
    private val habitLogDao: HabitLogDao,
) : BackupRepository {

    override suspend fun snapshot(exportedAt: Long): BackupBundle = database.withTransaction {
        BackupBundle(
            exportedAt = exportedAt,
            dailySessions = dailyDao.getAll().map { it.toBackup() },
            focusSessions = focusDao.getAll().map { it.toBackup() },
            habitLogs = habitLogDao.getAll().map { it.toBackup() },
        )
    }

    override suspend fun restore(bundle: BackupBundle) {
        database.withTransaction {
            dailyDao.deleteAll()
            focusDao.deleteAll()
            habitLogDao.deleteAll()
            bundle.dailySessions.forEach { dailyDao.upsert(it.toEntity()) }
            bundle.focusSessions.forEach { focusDao.insert(it.toEntity()) }
            bundle.habitLogs.forEach { habitLogDao.insert(it.toEntity()) }
        }
    }
}

private fun DailySessionEntity.toBackup() = BackupDailySession(
    dateEpoch = dateEpoch,
    mvdActive = mvdActive,
    coreStatus = coreStatus,
    corePrompt = corePrompt,
    coreAuthorizedAt = coreAuthorizedAt,
    coreDefectKind = coreDefectKind,
    infra02Status = infra02Status,
    infra03Status = infra03Status,
    infra04Status = infra04Status,
    infra05Status = infra05Status,
    eveningClosed = eveningClosed,
    eveningClosedAt = eveningClosedAt,
    virtRationality = virtRationality,
    virtIndependence = virtIndependence,
    virtHonesty = virtHonesty,
    virtJustice = virtJustice,
    bottleneckForNextWeek = bottleneckForNextWeek,
    journalEntry = journalEntry,
)

private fun BackupDailySession.toEntity() = DailySessionEntity(
    dateEpoch = dateEpoch,
    mvdActive = mvdActive,
    coreStatus = coreStatus,
    corePrompt = corePrompt,
    coreAuthorizedAt = coreAuthorizedAt,
    coreDefectKind = coreDefectKind,
    infra02Status = infra02Status,
    infra03Status = infra03Status,
    infra04Status = infra04Status,
    infra05Status = infra05Status,
    eveningClosed = eveningClosed,
    eveningClosedAt = eveningClosedAt,
    virtRationality = virtRationality,
    virtIndependence = virtIndependence,
    virtHonesty = virtHonesty,
    virtJustice = virtJustice,
    bottleneckForNextWeek = bottleneckForNextWeek,
    journalEntry = journalEntry,
)

private fun FocusSessionEntity.toBackup() = BackupFocusSession(
    id = id,
    dateEpoch = dateEpoch,
    habitCode = habitCode,
    startedAt = startedAt,
    endedAt = endedAt,
)

private fun BackupFocusSession.toEntity() = FocusSessionEntity(
    id = id,
    dateEpoch = dateEpoch,
    habitCode = habitCode,
    startedAt = startedAt,
    endedAt = endedAt,
)

private fun HabitLogEntity.toBackup() = BackupHabitLog(
    dateEpoch = dateEpoch,
    habitCode = habitCode,
    completedAt = completedAt,
)

private fun BackupHabitLog.toEntity() = HabitLogEntity(
    dateEpoch = dateEpoch,
    habitCode = habitCode,
    completedAt = completedAt,
)
