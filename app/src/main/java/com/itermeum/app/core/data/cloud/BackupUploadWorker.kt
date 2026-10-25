package com.itermeum.app.core.data.cloud

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.itermeum.app.core.backup.BackupRepository
import com.itermeum.app.core.common.Clock
import com.itermeum.app.core.data.auth.AuthRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Duration
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** Section 5.17: daily at 03:30 with network + charging, and «Сохранить сейчас». */
@HiltWorker
class BackupUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val backup: BackupRepository,
    private val drive: DriveBackupClient,
    private val settings: BackupSettings,
    private val auth: AuthRepository,
    private val clock: Clock,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val password = settings.password ?: return Result.failure()
        if (!auth.isSignedIn) return Result.failure()
        return try {
            val bytes = backup.export(password.toCharArray())
            drive.uploadOrReplace(bytes)
            settings.lastUploadAt = clock.nowEpochMillis()
            settings.lastError = null
            Result.success()
        } catch (e: java.io.IOException) {
            Log.w(TAG, "transient upload failure", e)
            settings.lastError = e.message
            Result.retry()
        } catch (e: Throwable) {
            Log.e(TAG, "upload failed", e)
            settings.lastError = e.message
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "BackupUpload"
        const val PERIODIC = "backup_daily"
        const val ONE_SHOT = "backup_now"

        fun scheduleDaily(context: Context, now: LocalTime) {
            val target = LocalTime.of(3, 30)
            var delay = Duration.between(now, target)
            if (delay.isNegative) delay = delay.plusDays(1)
            val request = PeriodicWorkRequestBuilder<BackupUploadWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delay.toMinutes(), TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresCharging(true).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun uploadNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<BackupUploadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(ONE_SHOT, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        }
    }
}
