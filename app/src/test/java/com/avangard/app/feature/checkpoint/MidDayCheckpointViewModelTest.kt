package com.avangard.app.feature.checkpoint

import app.cash.turbine.test
import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.domain.FakeClock
import com.avangard.app.core.domain.FakeReportRepository
import com.avangard.app.core.domain.model.DailyReport
import com.avangard.app.core.domain.model.MiddayStatus
import com.avangard.app.core.domain.model.ReportError
import com.avangard.app.core.domain.usecase.ObserveTodayReportUseCase
import com.avangard.app.core.domain.usecase.SubmitMiddayStatusUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class MidDayCheckpointViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FakeReportRepository
    private lateinit var clock: FakeClock
    private lateinit var viewModel: MidDayCheckpointViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeReportRepository()
        clock = FakeClock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun seedToday(artifact: String = "Написана спецификация") {
        val epoch = clock.today().toStartOfDayEpoch(clock.zone())
        repository.upsert(
            DailyReport(
                id = 0,
                dateEpoch = epoch,
                targetArtifact = artifact,
                isCompleted = false,
                eliminatedWaste = 0,
                failureCause = null,
                correctiveAction = null,
            )
        )
    }

    private fun buildViewModel() {
        viewModel = MidDayCheckpointViewModel(
            observeTodayReport = ObserveTodayReportUseCase(repository, clock),
            submit = SubmitMiddayStatusUseCase(repository, clock),
        )
    }

    @Test
    fun `closed shift surfaces in state and blocks submit`() = runTest(dispatcher) {
        buildViewModel()
        val state = viewModel.state.value
        assertFalse(state.isShiftOpen)
        viewModel.selectInProgress()
        assertFalse(viewModel.state.value.canSubmit)
    }

    @Test
    fun `open shift defaults to no choice and disabled submit`() = runTest(dispatcher) {
        seedToday()
        buildViewModel()
        assertTrue(viewModel.state.value.isShiftOpen)
        assertEquals(MiddayChoice.Unset, viewModel.state.value.choice)
        assertFalse(viewModel.state.value.canSubmit)
    }

    @Test
    fun `selecting in-progress enables submit`() = runTest(dispatcher) {
        seedToday()
        buildViewModel()
        viewModel.selectInProgress()
        assertTrue(viewModel.state.value.canSubmit)
    }

    @Test
    fun `blocked path requires twenty char unblocking action`() = runTest(dispatcher) {
        seedToday()
        buildViewModel()
        viewModel.selectBlocked()
        assertFalse(viewModel.state.value.canSubmit)
        viewModel.onActionChange("слишком коротко")
        assertFalse(viewModel.state.value.canSubmit)
        viewModel.onActionChange("Перенести встречу с CTO на пятницу.")
        assertTrue(viewModel.state.value.canSubmit)
    }

    @Test
    fun `submit emits effect and persists Blocked with trimmed action`() = runTest(dispatcher) {
        seedToday()
        buildViewModel()
        viewModel.selectBlocked()
        viewModel.onActionChange("  Заморозить ветку и поднять прод-инцидент  ")
        viewModel.effects.test {
            viewModel.submit()
            val effect = awaitItem()
            assertTrue(effect is MidDayCheckpointEffect.Submitted)
        }
        val stored = repository.findForDate(clock.today().toStartOfDayEpoch(clock.zone()))!!
        assertEquals(
            MiddayStatus.Blocked("Заморозить ветку и поднять прод-инцидент"),
            stored.midday,
        )
    }

    @Test
    fun `submit on closed shift surfaces NotInitialized error`() = runTest(dispatcher) {
        buildViewModel()
        viewModel.selectInProgress()
        viewModel.submit()
        assertEquals(ReportError.NotInitialized, viewModel.state.value.error)
    }
}
