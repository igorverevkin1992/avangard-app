package com.avangard.app.feature.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avangard.app.core.common.Clock
import com.avangard.app.core.common.DomainResult
import com.avangard.app.core.data.UserPreferences
import com.avangard.app.core.data.UserPreferencesRepository
import com.avangard.app.core.data.auth.AuthRepository
import com.avangard.app.core.data.cloud.SyncCoordinator
import com.avangard.app.core.domain.repository.HabitRepository
import com.avangard.app.core.domain.repository.SessionRepository
import com.avangard.app.core.domain.usecase.BackupImportError
import com.avangard.app.core.domain.usecase.ExportBackupUseCase
import com.avangard.app.core.domain.usecase.ImportBackupUseCase
import com.avangard.app.core.ui.components.DEFAULT_COLD_START_THRESHOLD_MS
import com.avangard.app.sync.scheduler.EveningCloseScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface BackupStatus {
    data object Idle : BackupStatus
    data object Exporting : BackupStatus
    data object Importing : BackupStatus
    data object ExportSucceeded : BackupStatus
    data object ImportSucceeded : BackupStatus
    data object ExportFailed : BackupStatus
    sealed interface ImportFailed : BackupStatus {
        data object NotJson : ImportFailed
        data class UnsupportedSchema(val version: Int) : ImportFailed
        data object CorruptedSnapshot : ImportFailed
        data object ReadFailed : ImportFailed
    }
}

data class SettingsState(
    val preferences: UserPreferences = UserPreferences(),
    val confirmingWipe: Boolean = false,
    val wipeInProgress: Boolean = false,
    val pendingImportBytes: ByteArray? = null,
    val backupStatus: BackupStatus = BackupStatus.Idle,
    /** Currently signed-in account's display email, or null when out. */
    val signedInEmail: String? = null,
) {
    // ByteArray breaks data-class equals; keep generated equals/hashCode honest.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SettingsState) return false
        if (preferences != other.preferences) return false
        if (confirmingWipe != other.confirmingWipe) return false
        if (wipeInProgress != other.wipeInProgress) return false
        if (backupStatus != other.backupStatus) return false
        if (signedInEmail != other.signedInEmail) return false
        return when {
            pendingImportBytes == null && other.pendingImportBytes == null -> true
            pendingImportBytes != null && other.pendingImportBytes != null ->
                pendingImportBytes.contentEquals(other.pendingImportBytes)
            else -> false
        }
    }

    override fun hashCode(): Int {
        var result = preferences.hashCode()
        result = 31 * result + confirmingWipe.hashCode()
        result = 31 * result + wipeInProgress.hashCode()
        result = 31 * result + (pendingImportBytes?.contentHashCode() ?: 0)
        result = 31 * result + backupStatus.hashCode()
        result = 31 * result + (signedInEmail?.hashCode() ?: 0)
        return result
    }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sessions: SessionRepository,
    private val habits: HabitRepository,
    private val preferences: UserPreferencesRepository,
    private val scheduler: EveningCloseScheduler,
    private val exportBackup: ExportBackupUseCase,
    private val importBackup: ImportBackupUseCase,
    private val clock: Clock,
    private val auth: AuthRepository,
    private val syncCoordinator: SyncCoordinator,
) : ViewModel() {

    /** Backup filename uses the injected Clock so timezone-sensitive paths
     *  agree with the rest of the app (Pulpit, scheduler, etc.). */
    fun proposedBackupFileName(): String = "avangard-${clock.today()}.json"

    private val wipeFlags = MutableStateFlow(WipeFlags())
    private val backupState = MutableStateFlow(BackupFlags())

    val state: StateFlow<SettingsState> = combine(
        preferences.flow,
        wipeFlags,
        backupState,
        auth.account,
    ) { prefs, flags, backup, account ->
        SettingsState(
            preferences = prefs,
            confirmingWipe = flags.confirming,
            wipeInProgress = flags.inProgress,
            pendingImportBytes = backup.pendingImportBytes,
            backupStatus = backup.status,
            signedInEmail = account?.email,
        )
    }.stateIn(
        scope = viewModelScope,
        // Eagerly so state.value is always the live combined value, including
        // mutations from request/cancel/confirmWipe — tests read state.value
        // directly without spinning up a collector.
        started = SharingStarted.Eagerly,
        initialValue = SettingsState(),
    )

    fun onEveningCloseChanged(hour: Int, minute: Int) {
        if (hour !in 0..23 || minute !in 0..59) return
        viewModelScope.launch {
            preferences.setEveningClose(hour, minute)
            // Re-arm immediately so the next trigger reflects the new time.
            scheduler.ensureScheduled()
        }
    }

    fun onColdStartThresholdChanged(minutes: Int) {
        if (minutes !in 1..60) return
        viewModelScope.launch {
            preferences.setColdStartThresholdMs(minutes * 60L * 1000)
        }
    }

    fun requestWipe() {
        wipeFlags.value = wipeFlags.value.copy(confirming = true)
    }

    fun cancelWipe() {
        wipeFlags.value = wipeFlags.value.copy(confirming = false)
    }

    fun confirmWipe() {
        if (wipeFlags.value.inProgress) return
        wipeFlags.value = WipeFlags(confirming = false, inProgress = true)
        viewModelScope.launch {
            sessions.wipe()
            habits.wipe()
            wipeFlags.value = WipeFlags()
        }
    }

    /**
     * Snapshot the store and return the JSON bytes for the screen to write
     * into the SAF URI. Surfaces success/failure status in state.
     */
    suspend fun prepareExportBytes(): ByteArray? {
        backupState.value = backupState.value.copy(status = BackupStatus.Exporting)
        return try {
            val bytes = exportBackup()
            backupState.value = backupState.value.copy(status = BackupStatus.ExportSucceeded)
            bytes
        } catch (e: Throwable) {
            // Log to Logcat — Sentry's breadcrumb integration mirrors these
            // automatically, so an opt-in release will see the stack trace.
            Log.e(LOG_TAG, "backup export failed", e)
            backupState.value = backupState.value.copy(status = BackupStatus.ExportFailed)
            null
        }
    }

    fun onExportWriteFailed() {
        backupState.value = backupState.value.copy(status = BackupStatus.ExportFailed)
    }

    /**
     * Stash bytes read from the SAF URI and request a confirmation. Restore
     * is destructive so the screen must show a confirm dialog before
     * commitImport().
     */
    fun stageImport(bytes: ByteArray) {
        backupState.value = BackupFlags(pendingImportBytes = bytes, status = BackupStatus.Idle)
    }

    fun onImportReadFailed() {
        backupState.value = backupState.value.copy(
            pendingImportBytes = null,
            status = BackupStatus.ImportFailed.ReadFailed,
        )
    }

    fun cancelImport() {
        backupState.value = backupState.value.copy(pendingImportBytes = null)
    }

    fun commitImport() {
        val bytes = backupState.value.pendingImportBytes ?: return
        backupState.value = backupState.value.copy(
            pendingImportBytes = null,
            status = BackupStatus.Importing,
        )
        viewModelScope.launch {
            val result = importBackup(bytes)
            backupState.value = backupState.value.copy(
                status = when (result) {
                    is DomainResult.Ok -> BackupStatus.ImportSucceeded
                    is DomainResult.Err -> when (val e = result.error) {
                        is BackupImportError.NotJson -> BackupStatus.ImportFailed.NotJson
                        is BackupImportError.UnsupportedSchema ->
                            BackupStatus.ImportFailed.UnsupportedSchema(e.version)
                        is BackupImportError.CorruptedSnapshot ->
                            BackupStatus.ImportFailed.CorruptedSnapshot
                    }
                }
            )
        }
    }

    fun acknowledgeBackupStatus() {
        backupState.value = backupState.value.copy(status = BackupStatus.Idle)
    }

    /**
     * "СИНХРОНИЗОВАТЬ СЕЙЧАС" — bypass the 5-second debounce and run the
     * uploader immediately (still subject to WorkManager's CONNECTED
     * constraint).
     */
    fun onForceSync() {
        syncCoordinator.syncNow()
    }

    /**
     * "ВЫЙТИ" — drop the Google account and the cloud markers so a fresh
     * sign-in re-triggers the restore overlay against the new account.
     * The local DB is untouched — local-only operators can keep working
     * offline; SAF backup remains the manual fallback.
     */
    fun onSignOut() {
        viewModelScope.launch {
            auth.signOut()
            preferences.clearSyncMarkers()
        }
    }

    private data class WipeFlags(
        val confirming: Boolean = false,
        val inProgress: Boolean = false,
    )

    private data class BackupFlags(
        val pendingImportBytes: ByteArray? = null,
        val status: BackupStatus = BackupStatus.Idle,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is BackupFlags) return false
            if (status != other.status) return false
            return when {
                pendingImportBytes == null && other.pendingImportBytes == null -> true
                pendingImportBytes != null && other.pendingImportBytes != null ->
                    pendingImportBytes.contentEquals(other.pendingImportBytes)
                else -> false
            }
        }

        override fun hashCode(): Int {
            var result = pendingImportBytes?.contentHashCode() ?: 0
            result = 31 * result + status.hashCode()
            return result
        }
    }

    companion object {
        private const val LOG_TAG = "SettingsViewModel"
        val COLD_START_OPTIONS_MINUTES = listOf(3, 5, 7, 10)
        const val DEFAULT_COLD_START_MINUTES =
            (DEFAULT_COLD_START_THRESHOLD_MS / 1000 / 60).toInt()
    }
}
