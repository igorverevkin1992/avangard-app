package com.avangard.app.feature.habits

import com.avangard.app.core.domain.FakeClock
import com.avangard.app.core.domain.FakeHabitRepository
import com.avangard.app.core.domain.model.Habit
import com.avangard.app.core.domain.usecase.ObserveMonthHabitsUseCase
import com.avangard.app.core.domain.usecase.ToggleHabitUseCase
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HabitTrackerViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FakeHabitRepository
    private lateinit var clock: FakeClock
    private lateinit var viewModel: HabitTrackerViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        clock = FakeClock(today = LocalDate.of(2026, 5, 7))
        repository = FakeHabitRepository()
        viewModel = HabitTrackerViewModel(
            clock = clock,
            observeMonth = ObserveMonthHabitsUseCase(repository),
            toggleUseCase = ToggleHabitUseCase(repository, clock),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Keep [HabitTrackerViewModel.state] hot for the duration of the block so
     * the WhileSubscribed upstream actually runs and the flatMapLatest pipeline
     * emits HabitMonthlyView snapshots.
     */
    private fun TestScope.withState(body: () -> Unit) {
        val job: Job = launch { viewModel.state.collect {} }
        try {
            advanceUntilIdle()
            body()
        } finally {
            job.cancel()
        }
    }

    @Test
    fun `initial state targets current month`() = runTest(dispatcher) {
        withState {
            assertEquals(YearMonth.of(2026, 5), viewModel.state.value.selected)
            assertEquals(LocalDate.of(2026, 5, 7), viewModel.state.value.today)
        }
    }

    @Test
    fun `toggle inserts a habit and observation reflects it`() = runTest(dispatcher) {
        withState {
            viewModel.toggle(LocalDate.of(2026, 5, 7), Habit.Sport)
            advanceUntilIdle()
            val view = viewModel.state.value.view!!
            assertTrue(view.isCompleted(LocalDate.of(2026, 5, 7), Habit.Sport))
            assertEquals(1, view.completedCount(Habit.Sport))
        }
    }

    @Test
    fun `toggle twice removes the habit again`() = runTest(dispatcher) {
        withState {
            viewModel.toggle(LocalDate.of(2026, 5, 7), Habit.Sport)
            advanceUntilIdle()
            viewModel.toggle(LocalDate.of(2026, 5, 7), Habit.Sport)
            advanceUntilIdle()
            val view = viewModel.state.value.view!!
            assertFalse(view.isCompleted(LocalDate.of(2026, 5, 7), Habit.Sport))
        }
    }

    @Test
    fun `selectMonth swaps the observed range`() = runTest(dispatcher) {
        withState {
            viewModel.toggle(LocalDate.of(2026, 5, 7), Habit.Reading)
            advanceUntilIdle()
            viewModel.selectMonth(YearMonth.of(2026, 6))
            advanceUntilIdle()
            val view = viewModel.state.value.view!!
            assertEquals(2026, view.year)
            assertEquals(6, view.month)
            assertEquals(0, view.completedCount(Habit.Reading))
        }
    }
}
