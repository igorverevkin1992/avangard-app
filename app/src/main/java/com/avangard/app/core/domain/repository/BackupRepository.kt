package com.avangard.app.core.domain.repository

import com.avangard.app.core.domain.model.BackupBundle

/**
 * Snapshot the entire user store into a versioned [BackupBundle], and restore
 * from one atomically (wipe + insert under a single transaction so a partial
 * import can never leave the DB inconsistent).
 */
interface BackupRepository {
    suspend fun snapshot(exportedAt: Long): BackupBundle
    suspend fun restore(bundle: BackupBundle)
}
