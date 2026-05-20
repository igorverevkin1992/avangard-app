package com.avangard.app.core.domain.usecase

import android.database.sqlite.SQLiteConstraintException
import com.avangard.app.core.common.DomainResult
import com.avangard.app.core.domain.model.BackupBundle
import com.avangard.app.core.domain.repository.BackupRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImportBackupUseCaseTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `valid bundle is restored and returns Ok`() = runBlocking {
        val bundle = BackupBundle(
            exportedAt = 1_700_000_000_000,
            dailySessions = emptyList(),
            focusSessions = emptyList(),
            habitLogs = emptyList(),
        )
        val bytes = json.encodeToString(BackupBundle.serializer(), bundle).toByteArray()
        val repository = mockk<BackupRepository>(relaxed = true)
        val useCase = ImportBackupUseCase(repository, json)

        val result = useCase(bytes)

        assertTrue(result is DomainResult.Ok)
        coVerify { repository.restore(bundle) }
    }

    @Test
    fun `garbage payload returns NotJson without touching repository`() = runBlocking {
        val repository = mockk<BackupRepository>(relaxed = true)
        val useCase = ImportBackupUseCase(repository, json)

        val result = useCase("definitely not json".toByteArray())

        assertEquals(DomainResult.Err(BackupImportError.NotJson), result)
        coVerify(exactly = 0) { repository.restore(any()) }
    }

    @Test
    fun `mismatched schemaVersion returns UnsupportedSchema with version`() = runBlocking {
        val payload = """
            {
              "schemaVersion": 99,
              "exportedAt": 0,
              "dailySessions": [],
              "focusSessions": [],
              "habitLogs": []
            }
        """.trimIndent().toByteArray()
        val repository = mockk<BackupRepository>(relaxed = true)
        val useCase = ImportBackupUseCase(repository, json)

        val result = useCase(payload)

        assertEquals(
            DomainResult.Err(BackupImportError.UnsupportedSchema(version = 99)),
            result,
        )
        coVerify(exactly = 0) { repository.restore(any()) }
    }

    @Test
    fun `repository SQLiteConstraintException maps to CorruptedSnapshot`() = runBlocking {
        // Simulates the partial unique index on focus_session firing during a
        // restore that contained two rows with ended_at IS NULL. The
        // withTransaction rollback inside RoomBackupRepository preserves the
        // original DB, so the use case must surface a clean error.
        val bundle = BackupBundle(
            exportedAt = 1_700_000_000_000,
            dailySessions = emptyList(),
            focusSessions = emptyList(),
            habitLogs = emptyList(),
        )
        val bytes = json.encodeToString(BackupBundle.serializer(), bundle).toByteArray()
        val repository = mockk<BackupRepository> {
            coEvery { restore(any()) } throws SQLiteConstraintException("uniq_focus_active")
        }
        val useCase = ImportBackupUseCase(repository, json)

        val result = useCase(bytes)

        assertEquals(DomainResult.Err(BackupImportError.CorruptedSnapshot), result)
    }
}
