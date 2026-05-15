package com.avangard.app.feature.pulpit

import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.data.UserPreferences
import com.avangard.app.core.data.UserPreferencesRepository
import com.avangard.app.core.domain.FakeClock
import com.avangard.app.core.domain.FakeSessionRepository
import com.avangard.app.core.domain.model.Habit
import com.avangard.app.core.domain.model.InfraStatus
import com.avangard.app.core.domain.model.SessionError
import com.avangard.app.core.domain.usecase.EndFocusUseCase
import com.avangard.app.core.domain.usecase.ObserveActiveFocusUseCase
import com.avangard.app.core.domain.usecase.ObserveDailySessionUseCase
import com.avangard.app.core.domain.usecase.SetInfraStatusUseCase
import com.avangard.app.core.domain.usecase.StartFocusUseCase
import com.avangard.app.core.domain.usecase.ToggleMvdUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OperatorPulpitViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FakeSessionRepository
    private lateinit var clock: FakeClock
    private lateinit var viewModel: OperatorPulpitViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        clock = FakeClock()
        repository = FakeSessionRepository(clock)
        val preferences = mockk<UserPreferencesRepository>(relaxed = true) {
            every { flow } returns MutableStateFlow(UserPreferences())
            coEvery { snapshot() } returns UserPreferences()
        }
        viewModel = OperatorPulpitViewModel(
            clock = clock,
            observeSession = ObserveDailySessionUseCase(repository),
            observeActiveFocus = ObserveActiveFocusUseCase(repository),
            preferences = preferences,
            startFocus = StartFocusUseCase(repository, clock),
            endFocus = EndFocusUseCase(repository, clock),
            toggleMvd = ToggleMvdUseCase(repository, clock),
            setInfraStatus = SetInfraStatusUseCase(repository, clock),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state reflects zeroed Idle session`() = runTest(dispatcher) {
        val first = viewModel.state.filterNotNull().first()
        assertNotNull(first)
        assertFalse(first.isCoreUnlocked)
        assertEquals(null, first.activeFocus)
    }

    @Test
    fun `starting Core focus puts the active focus on Generations`() = runTest(dispatcher) {
        viewModel.onStartFocus(Habit.Generations)
        advanceUntilIdle()
        val focus = repository.findActiveFocus()
        assertEquals(Habit.Generations, focus?.habit)
    }

    @Test
    fun `Infra start is rejected while Core is not yet approved`() = runTest(dispatcher) {
        viewModel.onStartFocus(Habit.Sport)
        advanceUntilIdle()
        assertEquals(null, repository.findActiveFocus())
    }

    @Test
    fun `mark Infra is rejected while Core is locked`() = runTest(dispatcher) {
        viewModel.onMarkInfra(Habit.Sport, InfraStatus.Standard)
        advanceUntilIdle()
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        val stored = repository.findForDate(today)
        // Either a zeroed row was created or no row at all — but infra_03 must stay NotDone.
        assertTrue(stored == null || stored.infra03 == InfraStatus.NotDone)
    }

    @Test
    fun `mark Infra succeeds after Core approval`() = runTest(dispatcher) {
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        repository.approveCore(today, "Шот", clock.nowEpochMillis())
        viewModel.onMarkInfra(Habit.Sport, InfraStatus.Standard)
        advanceUntilIdle()
        val stored = repository.findForDate(today)!!
        assertEquals(InfraStatus.Standard, stored.infra03)
    }

    @Test
    fun `toggle MVD flips the flag`() = runTest(dispatcher) {
        viewModel.onToggleMvd()
        advanceUntilIdle()
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        assertTrue(repository.findForDate(today)!!.mvdActive)
    }

    @Test
    fun `Infra start rejection surfaces a transient error on state`() = runTest(dispatcher) {
        viewModel.onStartFocus(Habit.Sport) // Core idle → InfraLocked
        // Don't advanceUntilIdle — the 3s transient-error clear would otherwise
        // run on the virtual clock and wipe the error before we observe it.
        val state = viewModel.state.filterNotNull().first()
        assertEquals(SessionError.InfraLocked, state.transientError)
    }
}
