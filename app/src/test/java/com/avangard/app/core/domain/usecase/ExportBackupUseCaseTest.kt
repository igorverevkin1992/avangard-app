package com.avangard.app.core.domain.usecase

import com.avangard.app.core.domain.FakeClock
import com.avangard.app.core.domain.model.BackupBundle
import com.avangard.app.core.domain.model.BackupFocusSession
import com.avangard.app.core.domain.repository.BackupRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ExportBackupUseCaseTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val clock = FakeClock()

    @Test
    fun `encodes repository snapshot into UTF-8 JSON bytes`() = runBlocking {
        val snapshot = BackupBundle(
            exportedAt = clock.nowEpochMillis(),
            dailySessions = emptyList(),
            focusSessions = listOf(
                BackupFocusSession(
                    id = 1,
                    dateEpoch = 1_700_000_000_000,
                    habitCode = "01",
                    startedAt = 1_700_000_010_000,
                    endedAt = 1_700_000_020_000,
                ),
            ),
            habitLogs = emptyList(),
        )
        val repository = mockk<BackupRepository> {
            coEvery { snapshot(any()) } returns snapshot
        }
        val useCase = ExportBackupUseCase(repository, clock, json)

        val bytes = useCase()
        val decoded = json.decodeFromString(BackupBundle.serializer(), bytes.toString(Charsets.UTF_8))

        assertEquals(snapshot, decoded)
    }
}
