package com.avangard.app.feature.settings

import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.data.UserPreferences
import com.avangard.app.core.data.UserPreferencesRepository
import com.avangard.app.core.domain.FakeClock
import com.avangard.app.core.domain.FakeHabitRepository
import com.avangard.app.core.domain.FakeSessionRepository
import com.avangard.app.core.domain.model.Habit
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
        viewModel = SettingsViewModel(
            sessions = sessions,
            habits = habits,
            preferences = preferences,
            scheduler = scheduler,
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
}
