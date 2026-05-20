package com.avangard.app.core.data.cloud

import android.content.Context
import androidx.room.InvalidationTracker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.avangard.app.core.database.AppDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges Room invalidation into a debounced WorkManager upload.
 *
 * Subscribes once at app launch to the three operator-mutated tables.
 * Every invalidation enqueues SyncUploadWorker uniquely with REPLACE
 * policy and a five-second initial delay — bursts of edits collapse
 * into one upload, and WorkManager handles network gating + retries.
 */
@Singleton
class SyncCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
) {

    private var started = false

    fun start() {
        if (started) return
        started = true
        val observer = object : InvalidationTracker.Observer(WATCHED_TABLES) {
            override fun onInvalidated(tables: Set<String>) = enqueueUpload()
        }
        database.invalidationTracker.addObserver(observer)
    }

    /**
     * Force-enqueue regardless of invalidation — used by Settings'
     * "sync now" button. Bypasses the debounce delay.
     */
    fun syncNow() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            SyncUploadWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            buildRequest(initialDelaySeconds = 0L),
        )
    }

    private fun enqueueUpload() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            SyncUploadWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            buildRequest(initialDelaySeconds = DEBOUNCE_SECONDS),
        )
    }

    private fun buildRequest(initialDelaySeconds: Long) =
        OneTimeWorkRequestBuilder<SyncUploadWorker>()
            .setInitialDelay(initialDelaySeconds, TimeUnit.SECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                BACKOFF_SECONDS,
                TimeUnit.SECONDS,
            )
            .build()

    companion object {
        private val WATCHED_TABLES = arrayOf(
            "daily_session",
            "focus_session",
            "habit_log",
        )
        private const val DEBOUNCE_SECONDS = 5L
        private const val BACKOFF_SECONDS = 30L
    }
}
