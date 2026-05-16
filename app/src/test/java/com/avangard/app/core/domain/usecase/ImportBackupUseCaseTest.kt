package com.avangard.app.core.domain.usecase

import com.avangard.app.core.common.DomainResult
import com.avangard.app.core.domain.model.BackupBundle
import com.avangard.app.core.domain.repository.BackupRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
