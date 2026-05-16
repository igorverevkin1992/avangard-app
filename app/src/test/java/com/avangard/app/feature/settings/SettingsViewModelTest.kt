package com.avangard.app.feature.settings

import com.avangard.app.core.common.DomainResult
import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.data.UserPreferences
import com.avangard.app.core.data.UserPreferencesRepository
import com.avangard.app.core.domain.FakeClock
import com.avangard.app.core.domain.FakeHabitRepository
import com.avangard.app.core.domain.FakeSessionRepository
import com.avangard.app.core.domain.model.Habit
import com.avangard.app.core.domain.usecase.BackupImportError
import com.avangard.app.core.domain.usecase.ExportBackupUseCase
import com.avangard.app.core.domain.usecase.ImportBackupUseCase
import com.avangard.app.sync.scheduler.EveningCloseScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var sessions: FakeSessionRepository
    private lateinit var habits: FakeHabitRepository
    private lateinit var clock: FakeClock
    private lateinit var preferences: UserPreferencesRepository
    private lateinit var scheduler: EveningCloseScheduler
    private lateinit var exportBackup: ExportBackupUseCase
    private lateinit var importBackup: ImportBackupUseCase
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        clock = FakeClock()
        sessions = FakeSessionRepository(clock)
        habits = FakeHabitRepository()
        preferences = mockk(relaxed = true) {
            every { flow } returns MutableStateFlow(UserPreferences())
            coEvery { snapshot() } returns UserPreferences()
        }
        scheduler = mockk(relaxed = true)
        exportBackup = mockk(relaxed = true)
        importBackup = mockk(relaxed = true)
        coEvery { exportBackup.invoke() } returns "{}".toByteArray()
        viewModel = SettingsViewModel(
            sessions = sessions,
            habits = habits,
            preferences = preferences,
            scheduler = scheduler,
            exportBackup = exportBackup,
            importBackup = importBackup,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `request then cancel resets confirmation`() = runTest(dispatcher) {
        viewModel.requestWipe()
        assertTrue(viewModel.state.value.confirmingWipe)
        viewModel.cancelWipe()
        assertFalse(viewModel.state.value.confirmingWipe)
    }

    @Test
    fun `confirm wipes both session and habit stores`() = runTest(dispatcher) {
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        sessions.approveCore(today, "Шот", clock.nowEpochMillis())
        habits.toggle(clock.today(), Habit.Sport, clock.nowEpochMillis())

        viewModel.requestWipe()
        viewModel.confirmWipe()
        advanceUntilIdle()

        assertNull(sessions.findForDate(today))
        assertFalse(viewModel.state.value.confirmingWipe)
        assertFalse(viewModel.state.value.wipeInProgress)
    }

    @Test
    fun `changing evening close re-arms the scheduler`() = runTest(dispatcher) {
        viewModel.onEveningCloseChanged(22, 30)
        advanceUntilIdle()
        coVerify { preferences.setEveningClose(22, 30) }
        coVerify { scheduler.ensureScheduled() }
    }

    @Test
    fun `out-of-range evening close is ignored`() = runTest(dispatcher) {
        viewModel.onEveningCloseChanged(25, 0)
        advanceUntilIdle()
        // preferences.setEveningClose must not be called with invalid args.
        coVerify(exactly = 0) { preferences.setEveningClose(any(), any()) }
    }

    @Test
    fun `cold-start threshold change persists in minutes`() = runTest(dispatcher) {
        viewModel.onColdStartThresholdChanged(7)
        advanceUntilIdle()
        coVerify { preferences.setColdStartThresholdMs(7 * 60L * 1000) }
    }

    @Test
    fun `prepareExportBytes returns use-case payload and marks success`() = runTest(dispatcher) {
        val payload = "{\"schemaVersion\":1}".toByteArray()
        coEvery { exportBackup.invoke() } returns payload

        val out = viewModel.prepareExportBytes()
        advanceUntilIdle()

        assertArrayEquals(payload, out)
        assertEquals(BackupStatus.ExportSucceeded, viewModel.state.value.backupStatus)
    }

    @Test
    fun `prepareExportBytes surfaces failure on use-case throw`() = runTest(dispatcher) {
        coEvery { exportBackup.invoke() } throws IllegalStateException("io")

        val out = viewModel.prepareExportBytes()
        advanceUntilIdle()

        assertNull(out)
        assertEquals(BackupStatus.ExportFailed, viewModel.state.value.backupStatus)
    }

    @Test
    fun `stageImport then commitImport runs use-case and clears pending`() = runTest(dispatcher) {
        val bytes = "{}".toByteArray()
        coEvery { importBackup.invoke(bytes) } returns DomainResult.Ok(Unit)

        viewModel.stageImport(bytes)
        assertArrayEquals(bytes, viewModel.state.value.pendingImportBytes)

        viewModel.commitImport()
        advanceUntilIdle()

        assertNull(viewModel.state.value.pendingImportBytes)
        assertEquals(BackupStatus.ImportSucceeded, viewModel.state.value.backupStatus)
    }

    @Test
    fun `cancelImport discards the pending payload without invoking restore`() = runTest(dispatcher) {
        viewModel.stageImport("{}".toByteArray())
        viewModel.cancelImport()
        advanceUntilIdle()

        assertNull(viewModel.state.value.pendingImportBytes)
        coVerify(exactly = 0) { importBackup.invoke(any()) }
    }

    @Test
    fun `commitImport surfaces NotJson error from use-case`() = runTest(dispatcher) {
        val bytes = "garbage".toByteArray()
        coEvery { importBackup.invoke(bytes) } returns
            DomainResult.Err(BackupImportError.NotJson)

        viewModel.stageImport(bytes)
        viewModel.commitImport()
        advanceUntilIdle()

        assertEquals(BackupStatus.ImportFailed.NotJson, viewModel.state.value.backupStatus)
    }

    @Test
    fun `commitImport surfaces UnsupportedSchema with version from use-case`() = runTest(dispatcher) {
        val bytes = "{}".toByteArray()
        coEvery { importBackup.invoke(bytes) } returns
            DomainResult.Err(BackupImportError.UnsupportedSchema(version = 99))

        viewModel.stageImport(bytes)
        viewModel.commitImport()
        advanceUntilIdle()

        assertEquals(
            BackupStatus.ImportFailed.UnsupportedSchema(99),
            viewModel.state.value.backupStatus,
        )
    }
}
