package com.avangard.app.core.domain.usecase

import com.avangard.app.core.common.DomainResult
import com.avangard.app.core.domain.model.BackupBundle
import com.avangard.app.core.domain.repository.BackupRepository
import javax.inject.Inject
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

sealed interface BackupImportError {
    data object NotJson : BackupImportError
    data class UnsupportedSchema(val version: Int) : BackupImportError
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
        if (bundle.schemaVersion != BackupBundle.SCHEMA_VERSION) {
            return DomainResult.Err(BackupImportError.UnsupportedSchema(bundle.schemaVersion))
        }
        repository.restore(bundle)
        return DomainResult.Ok(Unit)
    }
}
