package com.avangard.app.feature.pulpit

import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.data.UserPreferences
import com.avangard.app.core.data.UserPreferencesRepository
import com.avangard.app.core.domain.FakeClock
import com.avangard.app.core.domain.FakeSessionRepository
import com.avangard.app.core.domain.NoopChronometerRepository
import com.avangard.app.core.domain.NoopStatusNotifier
import com.avangard.app.core.domain.StatusEventBus
import com.avangard.app.core.domain.model.Habit
import com.avangard.app.core.domain.model.CoreMode
import com.avangard.app.core.domain.model.InfraStatus
import com.avangard.app.core.domain.model.SessionError
import com.avangard.app.core.domain.usecase.EndFocusUseCase
import com.avangard.app.core.domain.usecase.FocusServiceController
import com.avangard.app.core.domain.usecase.ObserveActiveFocusUseCase
import com.avangard.app.core.domain.usecase.ObserveDailySessionUseCase
import com.avangard.app.core.domain.usecase.SetInfraStatusUseCase
import com.avangard.app.core.domain.usecase.StartFocusUseCase
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
        val quotes = mockk<com.avangard.app.core.data.QuoteRepository>(relaxed = true) {
            // Pulpit reads quote-of-day off a flow; an empty flow keeps state
            // emitting via the rest of the combine sources without dragging
            // assets into the test classpath.
            every { quoteOfDayFlow() } returns kotlinx.coroutines.flow.flowOf(null)
        }
        viewModel = OperatorPulpitViewModel(
            clock = clock,
            observeSession = ObserveDailySessionUseCase(repository),
            observeActiveFocus = ObserveActiveFocusUseCase(repository),
            preferences = preferences,
            startFocus = StartFocusUseCase(repository, clock, NoopFocusService),
            endFocus = EndFocusUseCase(repository, clock),
            setInfraStatus = SetInfraStatusUseCase(repository, clock, StatusEventBus(), NoopStatusNotifier),
            quotes = quotes,
            sessions = repository,
            statusBus = StatusEventBus(),
            chronometer = NoopChronometerRepository,
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
    fun `Evening Infra start is rejected while Core is not yet approved`() = runTest(dispatcher) {
        // Watching is gated by Core; morning habits (Spanish, Sport) are not.
        viewModel.onStartFocus(Habit.Watching)
        advanceUntilIdle()
        assertEquals(null, repository.findActiveFocus())
    }

    @Test
    fun `Morning Infra start succeeds with Core still idle`() = runTest(dispatcher) {
        // Sport runs before Core in the operator's schedule — it must not
        // wait on Approval.
        viewModel.onStartFocus(Habit.Sport)
        advanceUntilIdle()
        assertEquals(Habit.Sport, repository.findActiveFocus()?.habit)
    }

    @Test
    fun `mark evening Infra is rejected while Core is locked`() = runTest(dispatcher) {
        viewModel.onMarkInfra(Habit.Watching, InfraStatus.Standard)
        advanceUntilIdle()
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        val stored = repository.findForDate(today)
        // infra_04 (Watching) must stay NotDone — Core not approved.
        assertTrue(stored == null || stored.infra04 == InfraStatus.NotDone)
    }

    @Test
    fun `mark morning Infra succeeds without Core approval`() = runTest(dispatcher) {
        // Sport / Spanish can be marked Standard at any point in the day.
        viewModel.onMarkInfra(Habit.Sport, InfraStatus.Standard)
        advanceUntilIdle()
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        val stored = repository.findForDate(today)!!
        assertEquals(InfraStatus.Standard, stored.infra03)
    }

    @Test
    fun `mark evening Infra succeeds after Core approval`() = runTest(dispatcher) {
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        repository.approveCore(today, "Шот", CoreMode.Standard, clock.nowEpochMillis())
        viewModel.onMarkInfra(Habit.Reading, InfraStatus.Standard)
        advanceUntilIdle()
        val stored = repository.findForDate(today)!!
        assertEquals(InfraStatus.Standard, stored.infra05)
    }

    @Test
    fun `Evening Infra start rejection surfaces a transient error on state`() =
        runTest(dispatcher) {
            viewModel.onStartFocus(Habit.Watching) // Core idle → InfraLocked
            // Don't advanceUntilIdle — the 3s transient-error clear would
            // otherwise run on the virtual clock and wipe the error before
            // we observe it.
            val state = viewModel.state.filterNotNull().first()
            assertEquals(SessionError.InfraLocked, state.transientError)
        }

    @Test
    fun `onStopFocus ends active focus even when state has detached`() = runTest(dispatcher) {
        // Sport is a morning habit — starts immediately, no Core required.
        viewModel.onStartFocus(Habit.Sport)
        advanceUntilIdle()
        assertNotNull(repository.findActiveFocus())

        // No subscriber on state — WhileSubscribed(5s) means state.value is
        // back to its initialValue=null. onStopFocus must still close the
        // session by reading the source flow directly.
        viewModel.onStopFocus()
        advanceUntilIdle()

        assertEquals(null, repository.findActiveFocus())
    }

    @Test
    fun `nudge flag is false before target time on approved shift`() = runTest(dispatcher) {
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        repository.approveCore(today, "Шот", CoreMode.Standard, clock.nowEpochMillis())
        clock.time = java.time.LocalTime.of(15, 0)

        val state = viewModel.state.filterNotNull().first()
        org.junit.Assert.assertFalse(state.shouldNudgeEveningClose)
    }

    @Test
    fun `nudge flag is true after target time on unclosed approved shift`() = runTest(dispatcher) {
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        repository.approveCore(today, "Шот", CoreMode.Standard, clock.nowEpochMillis())
        clock.time = java.time.LocalTime.of(21, 1)

        val state = viewModel.state.filterNotNull().first()
        assertTrue(state.shouldNudgeEveningClose)
    }

    @Test
    fun `nudge flag is false on Idle core regardless of time`() = runTest(dispatcher) {
        // No approve — coreStatus stays Idle. User hasn't started the shift,
        // so the nudge would be noise.
        clock.time = java.time.LocalTime.of(22, 0)

        val state = viewModel.state.filterNotNull().first()
        org.junit.Assert.assertFalse(state.shouldNudgeEveningClose)
    }

    @Test
    fun `nudge flag is false after closeEvening`() = runTest(dispatcher) {
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        repository.approveCore(today, "Шот", CoreMode.Standard, clock.nowEpochMillis())
        repository.closeEvening(
            dateEpoch = today,
            virtues = com.avangard.app.core.domain.model.VirtueScores(0, 0, 0, 0),
            defectKind = null,
            recordedAt = clock.nowEpochMillis(),
        )
        clock.time = java.time.LocalTime.of(21, 1)

        val state = viewModel.state.filterNotNull().first()
        org.junit.Assert.assertFalse(state.shouldNudgeEveningClose)
    }

    private object NoopFocusService : FocusServiceController {
        override fun start() = Unit
    }
}
