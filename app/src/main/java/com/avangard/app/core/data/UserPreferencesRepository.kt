package com.avangard.app.core.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.avangard.app.core.database.AppDatabase
import com.avangard.app.core.domain.model.EvasionEvent
import com.avangard.app.core.domain.model.EvasionKind
import com.avangard.app.core.domain.model.HabitStandard
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

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
    /**
     * Set once after the first post-sign-in cold-start has consulted the
     * Drive snapshot (restored, skipped, or explicitly continued without
     * restore). Survives process death so an interrupted restore can be
     * retried on the next launch.
     */
    val initialRestoreDone: Boolean = false,
    /** Operator's birthday as LocalDate.toEpochDay(); null until configured. */
    val birthdayEpochDay: Long? = null,
    /** Planned resource budget in years for the chronometer grid. */
    val lifeExpectancyYears: Int = DEFAULT_LIFE_EXPECTANCY,
    /** Daily ignition notification toggle (chronometer reminder). */
    val ignitionEnabled: Boolean = true,
    val ignitionHour: Int = DEFAULT_IGNITION_HOUR,
    val ignitionMinute: Int = 0,
    /** Midday-check notification toggle — passive nudge when Core's still
     *  idle by the configured hour. */
    val middayCheckEnabled: Boolean = false,
    val middayCheckHour: Int = DEFAULT_MIDDAY_HOUR,
    val middayCheckMinute: Int = 0,
    /** Pomodoro auto-stop on focus sessions. Off by default. */
    val pomodoroEnabled: Boolean = false,
    val pomodoroMinutes: Int = DEFAULT_POMODORO_MINUTES,
    /** Operator-defined «СТАНДАРТ» / «МИНИМУМ» criteria per habit code. */
    val habitStandards: Map<String, HabitStandard> = emptyMap(),
    /** Ring buffer of recent evasion-diagnostic events (newest first). */
    val evasionLog: List<EvasionEvent> = emptyList(),
    /** Quotes the operator pinned as personal «принципы». */
    val pinnedQuoteIds: Set<Int> = emptySet(),
) {
    companion object {
        const val DEFAULT_COLD_START_MS: Long = 5L * 60 * 1000
        const val DEFAULT_LIFE_EXPECTANCY = 80
        const val MIN_LIFE_EXPECTANCY = 50
        const val MAX_LIFE_EXPECTANCY = 100
        const val DEFAULT_IGNITION_HOUR = 6
        const val DEFAULT_MIDDAY_HOUR = 12
        const val DEFAULT_POMODORO_MINUTES = 25
        const val MIN_POMODORO_MINUTES = 10
        const val MAX_POMODORO_MINUTES = 90
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

    /**
     * Best-effort synchronous read of `initialRestoreDone` for hot paths
     * (MainActivity start-destination resolution) that must not block the
     * main thread on DataStore I/O. Defaults to `false` until the first
     * suspend read populates it — first cold start falls through to the
     * Restoring overlay, which is the safe default.
     *
     * Populated by [warmInitialRestoreCache] from `AvangardApplication.onCreate`.
     */
    @Volatile
    var cachedInitialRestoreDone: Boolean = false
        private set

    suspend fun warmInitialRestoreCache() {
        cachedInitialRestoreDone = snapshot().initialRestoreDone
    }

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
            prefs.remove(KEY_INITIAL_RESTORE_DONE)
        }
        cachedInitialRestoreDone = false
    }

    suspend fun setInitialRestoreDone() {
        context.preferencesStore.edit { prefs ->
            prefs[KEY_INITIAL_RESTORE_DONE] = true
        }
        cachedInitialRestoreDone = true
    }

    suspend fun setBirthday(epochDay: Long?) {
        context.preferencesStore.edit { prefs ->
            if (epochDay == null) prefs.remove(KEY_BIRTHDAY_EPOCH_DAY)
            else prefs[KEY_BIRTHDAY_EPOCH_DAY] = epochDay
        }
    }

    suspend fun setLifeExpectancy(years: Int) {
        require(years in UserPreferences.MIN_LIFE_EXPECTANCY..UserPreferences.MAX_LIFE_EXPECTANCY) {
            "life expectancy out of range: $years"
        }
        context.preferencesStore.edit { prefs ->
            prefs[KEY_LIFE_EXPECTANCY] = years
        }
    }

    suspend fun setIgnitionEnabled(enabled: Boolean) {
        context.preferencesStore.edit { prefs ->
            prefs[KEY_IGNITION_ENABLED] = enabled
        }
    }

    suspend fun setIgnitionTime(hour: Int, minute: Int) {
        require(hour in 0..23) { "hour out of range: $hour" }
        require(minute in 0..59) { "minute out of range: $minute" }
        context.preferencesStore.edit { prefs ->
            prefs[KEY_IGNITION_HOUR] = hour
            prefs[KEY_IGNITION_MINUTE] = minute
        }
    }

    suspend fun setMiddayCheckEnabled(enabled: Boolean) {
        context.preferencesStore.edit { prefs ->
            prefs[KEY_MIDDAY_ENABLED] = enabled
        }
    }

    suspend fun setMiddayCheckTime(hour: Int, minute: Int) {
        require(hour in 0..23) { "midday hour out of range: $hour" }
        require(minute in 0..59) { "midday minute out of range: $minute" }
        context.preferencesStore.edit { prefs ->
            prefs[KEY_MIDDAY_HOUR] = hour
            prefs[KEY_MIDDAY_MINUTE] = minute
        }
    }

    suspend fun setPomodoroEnabled(enabled: Boolean) {
        context.preferencesStore.edit { prefs ->
            prefs[KEY_POMODORO_ENABLED] = enabled
        }
    }

    suspend fun setPomodoroMinutes(minutes: Int) {
        require(minutes in UserPreferences.MIN_POMODORO_MINUTES..UserPreferences.MAX_POMODORO_MINUTES) {
            "pomodoro minutes out of range: $minutes"
        }
        context.preferencesStore.edit { prefs ->
            prefs[KEY_POMODORO_MINUTES] = minutes
        }
    }

    suspend fun togglePinnedQuote(id: Int) {
        context.preferencesStore.edit { prefs ->
            val current = prefs[KEY_PINNED_QUOTES]?.let(::decodePinnedSet) ?: emptySet()
            val updated = if (id in current) current - id else current + id
            prefs[KEY_PINNED_QUOTES] = encodePinnedSet(updated)
        }
    }

    suspend fun setHabitStandard(habitCode: String, standard: HabitStandard) {
        context.preferencesStore.edit { prefs ->
            val current = prefs[KEY_HABIT_STANDARDS]?.let(::decodeHabitStandards) ?: emptyMap()
            val updated = if (standard.isEmpty) current - habitCode else current + (habitCode to standard)
            prefs[KEY_HABIT_STANDARDS] = encodeHabitStandards(updated)
        }
    }

    suspend fun replaceHabitStandards(map: Map<String, HabitStandard>) {
        context.preferencesStore.edit { prefs ->
            prefs[KEY_HABIT_STANDARDS] = encodeHabitStandards(map)
        }
    }

    /** Append an evasion-diagnostic event; ring-buffered to [EVASION_LOG_CAP]. */
    suspend fun appendEvasion(kind: EvasionKind, timestampMs: Long) {
        context.preferencesStore.edit { prefs ->
            val current = prefs[KEY_EVASION_LOG]?.let(::decodeEvasionLog) ?: emptyList()
            val updated = (listOf(EvasionEvent(timestampMs, kind)) + current).take(EVASION_LOG_CAP)
            prefs[KEY_EVASION_LOG] = encodeEvasionLog(updated)
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
        initialRestoreDone = this[KEY_INITIAL_RESTORE_DONE] ?: false,
        birthdayEpochDay = this[KEY_BIRTHDAY_EPOCH_DAY],
        lifeExpectancyYears = this[KEY_LIFE_EXPECTANCY] ?: UserPreferences.DEFAULT_LIFE_EXPECTANCY,
        ignitionEnabled = this[KEY_IGNITION_ENABLED] ?: true,
        ignitionHour = this[KEY_IGNITION_HOUR] ?: UserPreferences.DEFAULT_IGNITION_HOUR,
        ignitionMinute = this[KEY_IGNITION_MINUTE] ?: 0,
        middayCheckEnabled = this[KEY_MIDDAY_ENABLED] ?: false,
        middayCheckHour = this[KEY_MIDDAY_HOUR] ?: UserPreferences.DEFAULT_MIDDAY_HOUR,
        middayCheckMinute = this[KEY_MIDDAY_MINUTE] ?: 0,
        pomodoroEnabled = this[KEY_POMODORO_ENABLED] ?: false,
        pomodoroMinutes = this[KEY_POMODORO_MINUTES]
            ?: UserPreferences.DEFAULT_POMODORO_MINUTES,
        habitStandards = this[KEY_HABIT_STANDARDS]?.let(::decodeHabitStandards) ?: emptyMap(),
        evasionLog = this[KEY_EVASION_LOG]?.let(::decodeEvasionLog) ?: emptyList(),
        pinnedQuoteIds = this[KEY_PINNED_QUOTES]?.let(::decodePinnedSet) ?: emptySet(),
    )

    private fun encodeHabitStandards(map: Map<String, HabitStandard>): String =
        JSON.encodeToString(HABIT_STANDARDS_SERIALIZER, map)

    private fun decodeHabitStandards(value: String): Map<String, HabitStandard> =
        runCatching {
            JSON.decodeFromString(HABIT_STANDARDS_SERIALIZER, value)
        }.getOrElse {
            // Corrupt JSON in DataStore (manual edit, bad migration): swallow and
            // fall back to empty so the rest of the app still functions.
            Log.w("UserPrefs", "habit_standards decode failed", it)
            emptyMap()
        }

    private fun encodeEvasionLog(list: List<EvasionEvent>): String =
        JSON.encodeToString(EVASION_LOG_SERIALIZER, list)

    private fun decodeEvasionLog(value: String): List<EvasionEvent> =
        runCatching {
            JSON.decodeFromString(EVASION_LOG_SERIALIZER, value)
        }.getOrElse {
            Log.w("UserPrefs", "evasion_log decode failed", it)
            emptyList()
        }

    private fun encodePinnedSet(set: Set<Int>): String =
        JSON.encodeToString(PINNED_SET_SERIALIZER, set.toList())

    private fun decodePinnedSet(value: String): Set<Int> =
        runCatching { JSON.decodeFromString(PINNED_SET_SERIALIZER, value).toSet() }
            .getOrElse {
                Log.w("UserPrefs", "pinned_quotes decode failed", it)
                emptySet()
            }

    companion object {
        private val KEY_EVENING_HOUR = intPreferencesKey("evening_close_hour")
        private val KEY_EVENING_MINUTE = intPreferencesKey("evening_close_minute")
        private val KEY_COLD_START_MS = longPreferencesKey("cold_start_threshold_ms")
        private val KEY_APP_LAUNCH_COUNT = intPreferencesKey("app_launch_count")
        private val KEY_LAST_SYNCED_AT = longPreferencesKey("cloud_sync_last_at")
        private val KEY_REMOTE_MODIFIED_AT = longPreferencesKey("cloud_sync_remote_modified_at")
        private val KEY_INITIAL_RESTORE_DONE = booleanPreferencesKey("cloud_sync_initial_restore_done")
        private val KEY_BIRTHDAY_EPOCH_DAY = longPreferencesKey("chronometer_birthday_epoch_day")
        private val KEY_LIFE_EXPECTANCY = intPreferencesKey("chronometer_life_expectancy_years")
        private val KEY_IGNITION_ENABLED = booleanPreferencesKey("chronometer_ignition_enabled")
        private val KEY_IGNITION_HOUR = intPreferencesKey("chronometer_ignition_hour")
        private val KEY_IGNITION_MINUTE = intPreferencesKey("chronometer_ignition_minute")
        private val KEY_MIDDAY_ENABLED = booleanPreferencesKey("midday_check_enabled")
        private val KEY_MIDDAY_HOUR = intPreferencesKey("midday_check_hour")
        private val KEY_MIDDAY_MINUTE = intPreferencesKey("midday_check_minute")
        private val KEY_POMODORO_ENABLED = booleanPreferencesKey("pomodoro_enabled")
        private val KEY_POMODORO_MINUTES = intPreferencesKey("pomodoro_minutes")
        private val KEY_HABIT_STANDARDS = stringPreferencesKey("habit_standards_json")
        private val KEY_EVASION_LOG = stringPreferencesKey("evasion_log_json")
        private val KEY_PINNED_QUOTES = stringPreferencesKey("pinned_quotes_json")
        private const val VACUUM_EVERY_LAUNCHES = 30
        private const val EVASION_LOG_CAP = 50

        private val JSON = Json { ignoreUnknownKeys = true }
        private val HABIT_STANDARDS_SERIALIZER =
            MapSerializer(String.serializer(), HabitStandard.serializer())
        private val EVASION_LOG_SERIALIZER =
            ListSerializer(EvasionEvent.serializer())
        private val PINNED_SET_SERIALIZER =
            ListSerializer(Int.serializer())
    }
}
