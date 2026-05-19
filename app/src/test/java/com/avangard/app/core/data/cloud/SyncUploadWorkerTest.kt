package com.avangard.app.core.data.cloud

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.avangard.app.core.common.Clock
import com.avangard.app.core.data.UserPreferencesRepository
import com.avangard.app.core.data.auth.AuthRepository
import com.avangard.app.core.domain.FakeClock
import com.avangard.app.core.domain.usecase.ExportBackupUseCase
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
class SyncUploadWorkerTest {

    private lateinit var context: Context
    private lateinit var exportBackup: ExportBackupUseCase
    private lateinit var driveClient: DriveBackupClient
    private lateinit var preferences: UserPreferencesRepository
    private lateinit var auth: AuthRepository
    private lateinit var clock: Clock

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        exportBackup = mockk(relaxed = true)
        driveClient = mockk(relaxed = true)
        preferences = mockk(relaxed = true)
        auth = mockk(relaxed = true)
        clock = FakeClock()
    }

    private fun buildWorker(): SyncUploadWorker =
        TestListenableWorkerBuilder<SyncUploadWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker = SyncUploadWorker(
                    appContext = appContext,
                    params = workerParameters,
                    exportBackup = exportBackup,
                    driveClient = driveClient,
                    preferences = preferences,
                    auth = auth,
                    clock = clock,
                )
            })
            .build()

    @Test
    fun `failure when not signed in`() = runBlocking {
        every { auth.isSignedIn } returns false

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        coVerify(exactly = 0) { driveClient.uploadOrReplace(any()) }
    }

    @Test
    fun `success path uploads and marks synced`() = runBlocking {
        every { auth.isSignedIn } returns true
        val payload = "{\"schemaVersion\":2}".toByteArray()
        coEvery { exportBackup.invoke() } returns payload
        coEvery { driveClient.uploadOrReplace(payload) } returns
            RemoteMetadata(fileId = "F1", modifiedTimeMs = 12345L)

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { preferences.markSynced(clock.nowEpochMillis(), 12345L) }
    }

    @Test
    fun `IOException becomes retry`() = runBlocking {
        every { auth.isSignedIn } returns true
        coEvery { exportBackup.invoke() } returns ByteArray(0)
        coEvery { driveClient.uploadOrReplace(any()) } throws IOException("network down")

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        coVerify(exactly = 0) { preferences.markSynced(any(), any()) }
    }

    @Test
    fun `unexpected throwable becomes failure`() = runBlocking {
        every { auth.isSignedIn } returns true
        coEvery { exportBackup.invoke() } throws IllegalStateException("boom")

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        coVerify(exactly = 0) { preferences.markSynced(any(), any()) }
    }
}
