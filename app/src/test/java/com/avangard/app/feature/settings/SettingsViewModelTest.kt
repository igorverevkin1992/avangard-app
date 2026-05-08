package com.avangard.app.feature.settings

import com.avangard.app.core.domain.FakeReportRepository
import com.avangard.app.core.domain.model.DailyReport
import com.avangard.app.core.domain.model.SystemFlag
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
class SettingsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FakeReportRepository
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeReportRepository()
        viewModel = SettingsViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `cancel resets confirmation`() = runTest(dispatcher) {
        viewModel.requestWipe()
        assertTrue(viewModel.state.value.confirmingWipe)
        viewModel.cancelWipe()
        assertFalse(viewModel.state.value.confirmingWipe)
    }

    @Test
    fun `confirm wipes reports and flags`() = runTest(dispatcher) {
        repository.upsert(
            DailyReport(
                id = 0,
                dateEpoch = 1_000L,
                targetArtifact = "X",
                isCompleted = true,
                eliminatedWaste = 1,
                failureCause = null,
                correctiveAction = null,
            ),
        )
        repository.setFlag(SystemFlag.FocusMode, true)
        viewModel.requestWipe()
        viewModel.confirmWipe()
        assertNull(repository.findForDate(1_000L))
        assertFalse(viewModel.state.value.confirmingWipe)
        assertFalse(viewModel.state.value.wipeInProgress)
    }
}
