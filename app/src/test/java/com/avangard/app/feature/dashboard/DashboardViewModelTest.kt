package com.avangard.app.feature.dashboard

import app.cash.turbine.test
import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.domain.FakeClock
import com.avangard.app.core.domain.FakeReportRepository
import com.avangard.app.core.domain.model.DailyReport
import com.avangard.app.core.domain.usecase.ObserveStreakUseCase
import com.avangard.app.core.domain.usecase.ObserveTodayReportUseCase
import com.avangard.app.core.domain.usecase.ToggleSwitchUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FakeReportRepository
    private lateinit var clock: FakeClock
    private lateinit var viewModel: DashboardViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeReportRepository()
        clock = FakeClock()
        viewModel = DashboardViewModel(
            observeToday = ObserveTodayReportUseCase(repository, clock),
            observeStreak = ObserveStreakUseCase(repository, clock),
            toggle = ToggleSwitchUseCase(repository),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has no report and toggles off`() = runTest(dispatcher) {
        viewModel.state.test {
            val initial = awaitItem()
            assertNull(initial.report)
            assertFalse(initial.focusMode)
            assertFalse(initial.silenceMode)
            assertEquals(0f, initial.progress, 0.0001f)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `report flowing through repository updates state`() = runTest(dispatcher) {
        viewModel.state.test {
            awaitItem() // initial
            val epoch = clock.today().toStartOfDayEpoch(clock.zone())
            repository.upsert(
                DailyReport(
                    id = 0,
                    dateEpoch = epoch,
                    targetArtifact = "Написана спецификация",
                    isCompleted = true,
                    eliminatedWaste = 3,
                    failureCause = null,
                    correctiveAction = null,
                ),
            )
            val updated = awaitItem()
            assertEquals("Написана спецификация", updated.targetArtifact)
            assertTrue(updated.isCompleted)
            assertEquals(1f, updated.progress, 0.0001f)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setFocusMode persists flag`() = runTest(dispatcher) {
        viewModel.state.test {
            awaitItem() // initial
            viewModel.setFocusMode(true)
            assertTrue(awaitItem().focusMode)
            viewModel.setFocusMode(false)
            assertFalse(awaitItem().focusMode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `failed report still in progress shows partial progress`() = runTest(dispatcher) {
        viewModel.state.test {
            awaitItem() // initial
            val epoch = clock.today().toStartOfDayEpoch(clock.zone())
            repository.upsert(
                DailyReport(
                    id = 0,
                    dateEpoch = epoch,
                    targetArtifact = "Собран прототип",
                    isCompleted = false,
                    eliminatedWaste = 0,
                    failureCause = null,
                    correctiveAction = null,
                ),
            )
            assertEquals(0.5f, awaitItem().progress, 0.0001f)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
