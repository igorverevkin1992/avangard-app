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
import com.avangard.app.core.domain.model.ChronometerBackup
import com.avangard.app.core.domain.repository.BackupRepository
import com.avangard.app.sync.scheduler.IgnitionScheduler
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomBackupRepository @Inject constructor(
    private val database: AppDatabase,
    private val dailyDao: DailySessionDao,
    private val focusDao: FocusSessionDao,
    private val habitLogDao: HabitLogDao,
    private val preferences: UserPreferencesRepository,
    private val ignitionScheduler: IgnitionScheduler,
) : BackupRepository {

    override suspend fun snapshot(exportedAt: Long): BackupBundle {
        val prefs = preferences.snapshot()
        return database.withTransaction {
            BackupBundle(
                exportedAt = exportedAt,
                dailySessions = dailyDao.getAll().map { it.toBackup() },
                focusSessions = focusDao.getAll().map { it.toBackup() },
                habitLogs = habitLogDao.getAll().map { it.toBackup() },
                chronometer = ChronometerBackup(
                    birthdayEpochDay = prefs.birthdayEpochDay,
                    lifeExpectancyYears = prefs.lifeExpectancyYears,
                    ignitionEnabled = prefs.ignitionEnabled,
                    ignitionHour = prefs.ignitionHour,
                    ignitionMinute = prefs.ignitionMinute,
                ),
            )
        }
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
        bundle.chronometer?.let { c ->
            preferences.setBirthday(c.birthdayEpochDay)
            preferences.setLifeExpectancy(
                c.lifeExpectancyYears.coerceIn(
                    com.avangard.app.core.data.UserPreferences.MIN_LIFE_EXPECTANCY,
                    com.avangard.app.core.data.UserPreferences.MAX_LIFE_EXPECTANCY,
                ),
            )
            preferences.setIgnitionEnabled(c.ignitionEnabled)
            preferences.setIgnitionTime(c.ignitionHour, c.ignitionMinute)
            ignitionScheduler.ensureScheduled()
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
    coreMode = coreMode,
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

private fun BackupDailySession.toEntity(): DailySessionEntity {
    // Mirror MIGRATION_6_7: on v1/v2 snapshots, coreMode is missing — derive
    // it from the legacy mvdActive flag so chronometer classification is
    // preserved across restore.
    val resolvedMode = coreMode ?: when {
        coreStatus == 1 && mvdActive == 1 -> "Mvd"
        coreStatus == 1 -> "Standard"
        else -> null
    }
    return DailySessionEntity(
        dateEpoch = dateEpoch,
        mvdActive = mvdActive,
        coreStatus = coreStatus,
        corePrompt = corePrompt,
        coreAuthorizedAt = coreAuthorizedAt,
        coreDefectKind = coreDefectKind,
        coreMode = resolvedMode,
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
}

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
