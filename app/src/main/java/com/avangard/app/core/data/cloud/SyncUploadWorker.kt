package com.avangard.app.core.data.cloud

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.avangard.app.core.common.Clock
import com.avangard.app.core.data.UserPreferencesRepository
import com.avangard.app.core.data.auth.AuthRepository
import com.avangard.app.core.domain.usecase.ExportBackupUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Single-pass cloud backup: snapshot the Room DB → upload to Drive AppData.
 *
 * Trigger: SyncCoordinator enqueues this worker (uniquely, REPLACE policy)
 * every time Room invalidates one of the operator-mutated tables, with a
 * five-second initial delay so rapid commits batch into one upload.
 *
 * Retry policy: any IOException becomes Result.retry(), letting WorkManager
 * apply its exponential backoff. Unrecoverable conditions (no signed-in
 * account, JSON serialisation failure) return Result.failure() so the user
 * sees a stalled sync rather than an infinite retry loop.
 */
@HiltWorker
class SyncUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val exportBackup: ExportBackupUseCase,
    private val driveClient: DriveBackupClient,
    private val preferences: UserPreferencesRepository,
    private val auth: AuthRepository,
    private val clock: Clock,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!auth.isSignedIn) {
            Log.w(LOG_TAG, "skipping upload — no Google account signed in")
            return Result.failure()
        }
        return try {
            val bytes = exportBackup()
            val metadata = driveClient.uploadOrReplace(bytes)
            preferences.markSynced(
                lastSyncedAt = clock.nowEpochMillis(),
                remoteModifiedAt = metadata.modifiedTimeMs,
            )
            Log.i(LOG_TAG, "uploaded ${bytes.size}B; fileId=${metadata.fileId}")
            Result.success()
        } catch (e: java.io.IOException) {
            Log.w(LOG_TAG, "transient upload failure — WorkManager will retry", e)
            Result.retry()
        } catch (e: Throwable) {
            // Non-recoverable (serialisation, missing context, token revoked).
            // Surface to logs and stop retrying — the operator can manually
            // re-trigger sync from Settings once the underlying issue is
            // addressed.
            Log.e(LOG_TAG, "unrecoverable upload failure", e)
            Result.failure()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "cloud_sync_upload"
        private const val LOG_TAG = "SyncUploadWorker"
    }
}
