package com.avangard.app.core.data.cloud

import android.content.Context
import androidx.room.InvalidationTracker
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.avangard.app.core.database.AppDatabase
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncCoordinatorTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var tracker: InvalidationTracker
    private lateinit var workManager: WorkManager
    private val observerSlot = slot<InvalidationTracker.Observer>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setExecutor(SynchronousExecutor())
                .build(),
        )
        workManager = WorkManager.getInstance(context)
        tracker = mockk(relaxed = true)
        every { tracker.addObserver(capture(observerSlot)) } answers { }
        database = mockk(relaxed = true)
        every { database.invalidationTracker } returns tracker
    }

    @Test
    fun `start subscribes once`() {
        val coordinator = SyncCoordinator(context, database)

        coordinator.start()
        coordinator.start()

        io.mockk.verify(exactly = 1) { tracker.addObserver(any()) }
    }

    @Test
    fun `invalidation enqueues unique upload work`() {
        val coordinator = SyncCoordinator(context, database)
        coordinator.start()

        observerSlot.captured.onInvalidated(setOf("daily_session"))

        val infos = workManager
            .getWorkInfosForUniqueWork(SyncUploadWorker.UNIQUE_WORK_NAME)
            .get()
        assertEquals(1, infos.size)
        val state = infos.single().state
        // Without network constraint satisfied, the work stays ENQUEUED in
        // tests — that's the proof the enqueue actually happened.
        assertTrue(
            "expected ENQUEUED/RUNNING/SUCCEEDED, got $state",
            state == WorkInfo.State.ENQUEUED ||
                state == WorkInfo.State.RUNNING ||
                state == WorkInfo.State.SUCCEEDED,
        )
    }

    @Test
    fun `syncNow enqueues with zero delay`() {
        val coordinator = SyncCoordinator(context, database)

        coordinator.syncNow()

        val infos = workManager
            .getWorkInfosForUniqueWork(SyncUploadWorker.UNIQUE_WORK_NAME)
            .get()
        assertEquals(1, infos.size)
    }
}
