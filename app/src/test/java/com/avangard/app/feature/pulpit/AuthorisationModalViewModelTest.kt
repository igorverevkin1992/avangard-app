package com.avangard.app.feature.pulpit

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.domain.FakeClock
import com.avangard.app.core.domain.FakeSessionRepository
import com.avangard.app.core.domain.model.CoreStatus
import com.avangard.app.core.domain.usecase.ApproveCoreUseCase
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
class AuthorisationModalViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FakeSessionRepository
    private lateinit var clock: FakeClock
    private lateinit var viewModel: AuthorisationModalViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        clock = FakeClock()
        repository = FakeSessionRepository(clock)
        viewModel = AuthorisationModalViewModel(
            approveCore = ApproveCoreUseCase(repository, clock),
            savedState = SavedStateHandle(),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `submit blocked until both prompt and checkbox provided`() = runTest(dispatcher) {
        assertFalse(viewModel.state.value.canSubmit)
        viewModel.onPromptChange("Шот пять")
        assertFalse(viewModel.state.value.canSubmit)
        viewModel.onAuthorisedChange(true)
        assertTrue(viewModel.state.value.canSubmit)
    }

    @Test
    fun `submit emits Submitted and writes Approved core`() = runTest(dispatcher) {
        viewModel.onPromptChange("Шот пять")
        viewModel.onAuthorisedChange(true)
        viewModel.effects.test {
            viewModel.submit()
            val effect = awaitItem()
            assertTrue(effect is AuthorisationEffect.Submitted)
        }
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        val stored = repository.findForDate(today)!!
        assertTrue(stored.coreStatus is CoreStatus.Approved)
        assertEquals("Шот пять", (stored.coreStatus as CoreStatus.Approved).prompt)
    }

    @Test
    fun `prompt and authorised flag survive process death via SavedStateHandle`() = runTest(dispatcher) {
        // Emulate the system restoring state: the previous VM wrote prompt and
        // authorised to SavedStateHandle, the new VM rebuilds from the bundle.
        val restored = SavedStateHandle(
            mapOf(
                "prompt" to "Сохранённый шот",
                "authorised" to true,
            ),
        )
        val viewModelAfterDeath = AuthorisationModalViewModel(
            approveCore = ApproveCoreUseCase(repository, clock),
            savedState = restored,
        )
        val state = viewModelAfterDeath.state.value
        assertEquals("Сохранённый шот", state.prompt)
        assertTrue(state.authorised)
        assertTrue(state.canSubmit)
    }
}
