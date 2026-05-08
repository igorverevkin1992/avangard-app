package com.avangard.app.feature.report

import app.cash.turbine.test
import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.domain.FakeClock
import com.avangard.app.core.domain.FakeReportRepository
import com.avangard.app.core.domain.model.DailyReport
import com.avangard.app.core.domain.usecase.ObserveTodayReportUseCase
import com.avangard.app.core.domain.usecase.SubmitEveningReportUseCase
import com.avangard.app.feature.report.evening.EveningReportEffect
import com.avangard.app.feature.report.evening.EveningReportViewModel
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
class EveningReportViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FakeReportRepository
    private lateinit var clock: FakeClock
    private lateinit var viewModel: EveningReportViewModel

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
            ),
        )
    }

    private fun buildViewModel() {
        viewModel = EveningReportViewModel(
            observeTodayReport = ObserveTodayReportUseCase(repository, clock),
            submitEvening = SubmitEveningReportUseCase(repository, clock),
        )
    }

    @Test
    fun `successful default state allows submit`() = runTest(dispatcher) {
        seedToday()
        buildViewModel()
        assertTrue(viewModel.state.value.isCompleted)
        assertTrue(viewModel.state.value.canSubmit)
    }

    @Test
    fun `failure state requires both analysis fields`() = runTest(dispatcher) {
        seedToday()
        buildViewModel()
        viewModel.onIsCompletedChange(false)
        assertFalse(viewModel.state.value.canSubmit)

        viewModel.onFailureCauseChange("Не выделил время на ключевую задачу.")
        assertFalse(viewModel.state.value.canSubmit)

        viewModel.onCorrectiveActionChange("Блокировать утренние слоты в календаре.")
        assertTrue(viewModel.state.value.canSubmit)
    }

    @Test
    fun `submit emits effect and persists`() = runTest(dispatcher) {
        seedToday()
        buildViewModel()
        viewModel.onIsCompletedChange(true)
        viewModel.onWasteChange(7)
        viewModel.effects.test {
            viewModel.submit()
            val effect = awaitItem()
            assertTrue(effect is EveningReportEffect.Submitted)
        }
        val stored = repository.findForDate(clock.today().toStartOfDayEpoch(clock.zone()))!!
        assertTrue(stored.isCompleted)
        assertEquals(7, stored.eliminatedWaste)
    }
}
