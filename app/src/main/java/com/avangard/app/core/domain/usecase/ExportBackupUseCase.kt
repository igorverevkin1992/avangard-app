package com.avangard.app.core.domain.usecase

import com.avangard.app.core.common.Clock
import com.avangard.app.core.domain.model.BackupBundle
import com.avangard.app.core.domain.repository.BackupRepository
import javax.inject.Inject
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Snapshot the entire user store and return its JSON encoding as a byte
 * array. The caller (Settings screen) writes those bytes into the URI
 * returned by the SAF CreateDocument launcher.
 */
class ExportBackupUseCase @Inject constructor(
    private val repository: BackupRepository,
    private val clock: Clock,
    private val json: Json,
) {
    suspend operator fun invoke(): ByteArray {
        val bundle: BackupBundle = repository.snapshot(clock.nowEpochMillis())
        return json.encodeToString(bundle).toByteArray(Charsets.UTF_8)
    }
}
