package com.avangard.app.core.domain.usecase

import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import com.avangard.app.core.common.DomainResult
import com.avangard.app.core.domain.model.BackupBundle
import com.avangard.app.core.domain.repository.BackupRepository
import javax.inject.Inject
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

sealed interface BackupImportError {
    data object NotJson : BackupImportError
    data class UnsupportedSchema(val version: Int) : BackupImportError
    // Snapshot decoded fine but a DB invariant rejected it (e.g. two
    // focus_session rows with ended_at IS NULL hit uniq_focus_active).
    // RoomBackupRepository wraps restore in withTransaction so the original
    // store is intact after rollback — surfacing this is safe.
    data object CorruptedSnapshot : BackupImportError
}

/**
 * Decode JSON bytes and atomically replace the user store with the contents.
 * Refuses malformed JSON and unsupported schema versions before touching the
 * database — the restore inside RoomBackupRepository runs in withTransaction,
 * so a successful decode is the gate.
 */
class ImportBackupUseCase @Inject constructor(
    private val repository: BackupRepository,
    private val json: Json,
) {
    suspend operator fun invoke(bytes: ByteArray): DomainResult<Unit, BackupImportError> {
        val text = bytes.toString(Charsets.UTF_8)
        val bundle = try {
            json.decodeFromString<BackupBundle>(text)
        } catch (_: SerializationException) {
            return DomainResult.Err(BackupImportError.NotJson)
        } catch (_: IllegalArgumentException) {
            return DomainResult.Err(BackupImportError.NotJson)
        }
        if (bundle.schemaVersion !in
            BackupBundle.MIN_SUPPORTED_SCHEMA_VERSION..BackupBundle.SCHEMA_VERSION
        ) {
            return DomainResult.Err(BackupImportError.UnsupportedSchema(bundle.schemaVersion))
        }
        return try {
            repository.restore(bundle)
            DomainResult.Ok(Unit)
        } catch (e: SQLiteConstraintException) {
            Log.e(LOG_TAG, "snapshot violated a DB invariant during restore", e)
            DomainResult.Err(BackupImportError.CorruptedSnapshot)
        }
    }

    private companion object {
        const val LOG_TAG = "ImportBackup"
    }
}
