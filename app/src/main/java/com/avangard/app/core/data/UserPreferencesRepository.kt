package com.avangard.app.core.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.avangard.app.core.database.AppDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Snapshot of the operator-controlled knobs persisted across launches.
 * Defaults match the whitepaper: 21:00 evening close, 5-minute cold-start.
 */
data class UserPreferences(
    val eveningCloseHour: Int = 21,
    val eveningCloseMinute: Int = 0,
    val coldStartThresholdMs: Long = DEFAULT_COLD_START_MS,
    val appLaunchCount: Int = 0,
    /** Last successful cloud upload — epoch millis, null when never synced. */
    val lastSyncedAt: Long? = null,
    /** Drive-reported modifiedTime of the most recent successful upload. */
    val remoteModifiedAt: Long? = null,
) {
    companion object {
        const val DEFAULT_COLD_START_MS: Long = 5L * 60 * 1000
    }
}

private val Context.preferencesStore by preferencesDataStore(name = "user_preferences")

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
) {

    val flow: Flow<UserPreferences> = context.preferencesStore.data.map { it.toModel() }

    suspend fun snapshot(): UserPreferences = flow.first()

    suspend fun setEveningClose(hour: Int, minute: Int) {
        require(hour in 0..23) { "hour out of range: $hour" }
        require(minute in 0..59) { "minute out of range: $minute" }
        context.preferencesStore.edit { prefs ->
            prefs[KEY_EVENING_HOUR] = hour
            prefs[KEY_EVENING_MINUTE] = minute
        }
    }

    suspend fun setColdStartThresholdMs(thresholdMs: Long) {
        require(thresholdMs in 60_000..3_600_000) { "threshold must be 1-60 min: $thresholdMs" }
        context.preferencesStore.edit { prefs ->
            prefs[KEY_COLD_START_MS] = thresholdMs
        }
    }

    suspend fun markSynced(lastSyncedAt: Long, remoteModifiedAt: Long) {
        context.preferencesStore.edit { prefs ->
            prefs[KEY_LAST_SYNCED_AT] = lastSyncedAt
            prefs[KEY_REMOTE_MODIFIED_AT] = remoteModifiedAt
        }
    }

    suspend fun clearSyncMarkers() {
        context.preferencesStore.edit { prefs ->
            prefs.remove(KEY_LAST_SYNCED_AT)
            prefs.remove(KEY_REMOTE_MODIFIED_AT)
        }
    }

    /**
     * Increment the launch counter and, every [VACUUM_EVERY_LAUNCHES] launches,
     * defragment the SQLite file. Cheap maintenance to keep the DB compact on
     * long-running installs without WorkManager. Errors swallowed — VACUUM
     * failure is non-critical.
     */
    suspend fun incrementAppLaunchAndMaybeVacuum() {
        val newCount = context.preferencesStore.edit { prefs ->
            val cur = prefs[KEY_APP_LAUNCH_COUNT] ?: 0
            prefs[KEY_APP_LAUNCH_COUNT] = cur + 1
        }[KEY_APP_LAUNCH_COUNT] ?: 0
        if (newCount > 0 && newCount % VACUUM_EVERY_LAUNCHES == 0) {
            withContext(Dispatchers.IO) {
                runCatching { database.openHelper.writableDatabase.execSQL("VACUUM") }
            }
        }
    }

    private fun Preferences.toModel(): UserPreferences = UserPreferences(
        eveningCloseHour = this[KEY_EVENING_HOUR] ?: 21,
        eveningCloseMinute = this[KEY_EVENING_MINUTE] ?: 0,
        coldStartThresholdMs = this[KEY_COLD_START_MS] ?: UserPreferences.DEFAULT_COLD_START_MS,
        appLaunchCount = this[KEY_APP_LAUNCH_COUNT] ?: 0,
        lastSyncedAt = this[KEY_LAST_SYNCED_AT],
        remoteModifiedAt = this[KEY_REMOTE_MODIFIED_AT],
    )

    companion object {
        private val KEY_EVENING_HOUR = intPreferencesKey("evening_close_hour")
        private val KEY_EVENING_MINUTE = intPreferencesKey("evening_close_minute")
        private val KEY_COLD_START_MS = longPreferencesKey("cold_start_threshold_ms")
        private val KEY_APP_LAUNCH_COUNT = intPreferencesKey("app_launch_count")
        private val KEY_LAST_SYNCED_AT = longPreferencesKey("cloud_sync_last_at")
        private val KEY_REMOTE_MODIFIED_AT = longPreferencesKey("cloud_sync_remote_modified_at")
        private const val VACUUM_EVERY_LAUNCHES = 30
    }
}
