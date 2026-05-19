package com.avangard.app.core.data.cloud

import android.util.Log
import com.avangard.app.core.common.DomainResult
import com.avangard.app.core.data.UserPreferencesRepository
import com.avangard.app.core.database.AppDatabase
import com.avangard.app.core.domain.usecase.BackupImportError
import com.avangard.app.core.domain.usecase.ImportBackupUseCase
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * First-launch (and post-sign-in) restore from the Drive AppData snapshot.
 *
 * Runs once per cold-start, guarded by [hasRun] so a navigation back to
 * RestoringScreen never triggers a second download. Decision tree:
 *   * no remote file        → nothing to restore, mark idle
 *   * local DB empty        → restore unconditionally
 *   * local newer (lastSync ≥ remote.modifiedTime) → skip; local is fresher
 *   * remote newer          → restore (last-write-wins)
 */
@Singleton
class RestoreBootstrapper @Inject constructor(
    private val driveClient: DriveBackupClient,
    private val importBackup: ImportBackupUseCase,
    private val preferences: UserPreferencesRepository,
    private val database: AppDatabase,
) {

    sealed interface Outcome {
        data object SkippedNoRemote : Outcome
        data object SkippedLocalNewer : Outcome
        data object Restored : Outcome
        data class Failed(val reason: Reason) : Outcome

        sealed interface Reason {
            data object Network : Reason
            data class Invalid(val error: BackupImportError) : Reason
        }
    }

    @Volatile private var hasRun = false

    suspend fun bootstrap(): Outcome = withContext(Dispatchers.IO) {
        if (hasRun) return@withContext Outcome.SkippedNoRemote
        hasRun = true
        val remote = try {
            driveClient.fetchRemote()
        } catch (e: IOException) {
            Log.w(LOG_TAG, "fetchRemote failed; skipping restore", e)
            return@withContext Outcome.Failed(Outcome.Reason.Network)
        } ?: run {
            preferences.setInitialRestoreDone()
            return@withContext Outcome.SkippedNoRemote
        }

        val localDailyCount = database.dailySessionDao().getAll().size
        val lastSyncedAt = preferences.snapshot().remoteModifiedAt
        val shouldRestore =
            localDailyCount == 0 ||
                lastSyncedAt == null ||
                remote.modifiedTimeMs > lastSyncedAt
        if (!shouldRestore) {
            preferences.setInitialRestoreDone()
            return@withContext Outcome.SkippedLocalNewer
        }

        when (val result = importBackup(remote.bytes)) {
            is DomainResult.Ok -> {
                preferences.markSynced(
                    lastSyncedAt = remote.modifiedTimeMs,
                    remoteModifiedAt = remote.modifiedTimeMs,
                )
                preferences.setInitialRestoreDone()
                Outcome.Restored
            }
            is DomainResult.Err ->
                Outcome.Failed(Outcome.Reason.Invalid(result.error))
        }
    }

    /** For tests — re-arm the one-shot guard. */
    internal fun resetForTest() {
        hasRun = false
    }

    companion object {
        private const val LOG_TAG = "RestoreBootstrapper"
    }
}
