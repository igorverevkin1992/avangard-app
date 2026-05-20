package com.avangard.app.core.data.cloud

import com.avangard.app.core.common.DomainResult
import com.avangard.app.core.data.UserPreferences
import com.avangard.app.core.data.UserPreferencesRepository
import com.avangard.app.core.database.AppDatabase
import com.avangard.app.core.database.dao.DailySessionDao
import com.avangard.app.core.database.entity.DailySessionEntity
import com.avangard.app.core.domain.usecase.BackupImportError
import com.avangard.app.core.domain.usecase.ImportBackupUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RestoreBootstrapperTest {

    private lateinit var driveClient: DriveBackupClient
    private lateinit var importBackup: ImportBackupUseCase
    private lateinit var preferences: UserPreferencesRepository
    private lateinit var database: AppDatabase
    private lateinit var dailySessionDao: DailySessionDao

    private fun bootstrapper() =
        RestoreBootstrapper(driveClient, importBackup, preferences, database)
            .also { it.resetForTest() }

    @Before
    fun setUp() {
        driveClient = mockk()
        importBackup = mockk()
        preferences = mockk(relaxed = true)
        database = mockk()
        dailySessionDao = mockk()
        every { database.dailySessionDao() } returns dailySessionDao
        coEvery { dailySessionDao.getAll() } returns emptyList()
        coEvery { preferences.snapshot() } returns UserPreferences()
    }

    @Test
    fun `no remote backup leaves DB and marks restore-done`() = runBlocking {
        coEvery { driveClient.fetchRemote() } returns null

        val outcome = bootstrapper().bootstrap()

        assertEquals(RestoreBootstrapper.Outcome.SkippedNoRemote, outcome)
        coVerify { preferences.setInitialRestoreDone() }
        coVerify(exactly = 0) { importBackup(any()) }
    }

    @Test
    fun `local-newer skips restore`() = runBlocking {
        coEvery { driveClient.fetchRemote() } returns RemoteBackup(
            bytes = "{}".toByteArray(),
            fileId = "FILE",
            modifiedTimeMs = 1_000L,
        )
        coEvery { preferences.snapshot() } returns UserPreferences(
            remoteModifiedAt = 5_000L,
        )
        // Local has data — non-empty count gates the restore separately too.
        coEvery { dailySessionDao.getAll() } returns listOf(
            DailySessionEntity(dateEpoch = 0L),
        )

        val outcome = bootstrapper().bootstrap()

        assertEquals(RestoreBootstrapper.Outcome.SkippedLocalNewer, outcome)
        coVerify(exactly = 0) { importBackup(any()) }
        coVerify { preferences.setInitialRestoreDone() }
    }

    @Test
    fun `remote-newer triggers restore and marks synced`() = runBlocking {
        val bytes = "{}".toByteArray()
        coEvery { driveClient.fetchRemote() } returns RemoteBackup(
            bytes = bytes,
            fileId = "FILE",
            modifiedTimeMs = 9_999L,
        )
        coEvery { preferences.snapshot() } returns UserPreferences(
            remoteModifiedAt = 1_000L,
        )
        coEvery { dailySessionDao.getAll() } returns listOf(
            DailySessionEntity(dateEpoch = 0L),
        )
        coEvery { importBackup(bytes) } returns DomainResult.Ok(Unit)

        val outcome = bootstrapper().bootstrap()

        assertEquals(RestoreBootstrapper.Outcome.Restored, outcome)
        coVerify { preferences.markSynced(9_999L, 9_999L) }
        coVerify { preferences.setInitialRestoreDone() }
    }

    @Test
    fun `empty local DB always restores`() = runBlocking {
        val bytes = "{}".toByteArray()
        coEvery { driveClient.fetchRemote() } returns RemoteBackup(
            bytes = bytes,
            fileId = "FILE",
            modifiedTimeMs = 100L,
        )
        coEvery { preferences.snapshot() } returns UserPreferences(
            remoteModifiedAt = 500L, // local "newer" by timestamp but DB empty
        )
        coEvery { dailySessionDao.getAll() } returns emptyList()
        coEvery { importBackup(bytes) } returns DomainResult.Ok(Unit)

        val outcome = bootstrapper().bootstrap()

        assertEquals(RestoreBootstrapper.Outcome.Restored, outcome)
    }

    @Test
    fun `import failure surfaces error and does not mark restored`() = runBlocking {
        val bytes = "{}".toByteArray()
        coEvery { driveClient.fetchRemote() } returns RemoteBackup(
            bytes = bytes,
            fileId = "FILE",
            modifiedTimeMs = 100L,
        )
        coEvery { importBackup(bytes) } returns
            DomainResult.Err(BackupImportError.NotJson)

        val outcome = bootstrapper().bootstrap()

        assertEquals(
            RestoreBootstrapper.Outcome.Failed(
                RestoreBootstrapper.Outcome.Reason.Invalid(BackupImportError.NotJson)
            ),
            outcome,
        )
        coVerify(exactly = 0) { preferences.markSynced(any(), any()) }
        coVerify(exactly = 0) { preferences.setInitialRestoreDone() }
    }

    @Test
    fun `network error returns Failed without touching DB`() = runBlocking {
        coEvery { driveClient.fetchRemote() } throws IOException("offline")

        val outcome = bootstrapper().bootstrap()

        assertEquals(
            RestoreBootstrapper.Outcome.Failed(RestoreBootstrapper.Outcome.Reason.Network),
            outcome,
        )
        coVerify(exactly = 0) { importBackup(any()) }
        coVerify(exactly = 0) { preferences.setInitialRestoreDone() }
    }

    @Test
    fun `bootstrap is idempotent within one process`() = runBlocking {
        coEvery { driveClient.fetchRemote() } returns null
        val target = bootstrapper()

        target.bootstrap()
        target.bootstrap()

        coVerify(exactly = 1) { driveClient.fetchRemote() }
    }
}
