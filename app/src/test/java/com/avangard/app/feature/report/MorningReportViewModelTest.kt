package com.avangard.app.feature.report

import app.cash.turbine.test
import com.avangard.app.core.domain.FakeClock
import com.avangard.app.core.domain.FakeReportRepository
import com.avangard.app.core.domain.usecase.InitializeDayUseCase
import com.avangard.app.feature.report.morning.MorningReportEffect
import com.avangard.app.feature.report.morning.MorningReportViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class MorningReportViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FakeReportRepository
    private lateinit var clock: FakeClock
    private lateinit var viewModel: MorningReportViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeReportRepository()
        clock = FakeClock()
        viewModel = MorningReportViewModel(InitializeDayUseCase(repository, clock))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `submit blocked until artifact and focus toggle are set`() = runTest(dispatcher) {
        assertFalse(viewModel.state.value.canSubmit)
        viewModel.onArtifactChange("Написана спецификация")
        assertFalse(viewModel.state.value.canSubmit)
        viewModel.onFocusModeEngaged(true)
        assertTrue(viewModel.state.value.canSubmit)
    }

    @Test
    fun `successful submit emits Submitted effect`() = runTest(dispatcher) {
        viewModel.onArtifactChange("Написана спецификация")
        viewModel.onFocusModeEngaged(true)
        viewModel.effects.test {
            viewModel.submit()
            val effect = awaitItem()
            assertTrue(effect is MorningReportEffect.Submitted)
            assertNull(viewModel.state.value.error)
        }
    }

    @Test
    fun `invalid artifact surfaces error in state`() = runTest(dispatcher) {
        viewModel.onArtifactChange("работать")
        viewModel.onFocusModeEngaged(true)
        viewModel.submit()
        assertTrue(viewModel.state.value.error != null)
    }
}
