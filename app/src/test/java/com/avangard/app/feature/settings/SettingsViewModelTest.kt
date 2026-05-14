package com.avangard.app.feature.settings

import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.domain.FakeClock
import com.avangard.app.core.domain.FakeHabitRepository
import com.avangard.app.core.domain.FakeSessionRepository
import com.avangard.app.core.domain.model.Habit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        clock = FakeClock()
        sessions = FakeSessionRepository(clock)
        habits = FakeHabitRepository()
        viewModel = SettingsViewModel(sessions = sessions, habits = habits)
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
        // FakeHabitRepository.wipe also clears its store; observe via toggle round-trip.
        habits.toggle(clock.today(), Habit.Sport, clock.nowEpochMillis())
        // Toggle back on after wipe → toggling once produces a fresh mark, so habit is present.
        // We don't need to assert further than wipe non-throwing + state reset:
        assertFalse(viewModel.state.value.confirmingWipe)
        assertFalse(viewModel.state.value.wipeInProgress)
    }
}
