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
            setDayMode = com.avangard.app.core.domain.usecase.SetDayModeUseCase(repository, clock),
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
    fun `Evening Infra is start-able while Core is still Idle`() = runTest(dispatcher) {
        // No-gate model: morning videos can count toward Watching even before
        // the operator sits down for Generations. Core's primacy is signalled
        // by the reminder banner, not by blocking the side modules.
        viewModel.onStartFocus(Habit.Watching)
        advanceUntilIdle()
        assertEquals(Habit.Watching, repository.findActiveFocus()?.habit)
    }

    @Test
    fun `Morning Infra start succeeds with Core still idle`() = runTest(dispatcher) {
        viewModel.onStartFocus(Habit.Sport)
        advanceUntilIdle()
        assertEquals(Habit.Sport, repository.findActiveFocus()?.habit)
    }

    @Test
    fun `mark evening Infra is allowed without Core approval`() = runTest(dispatcher) {
        viewModel.onMarkInfra(Habit.Watching, InfraStatus.Done)
        advanceUntilIdle()
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        val stored = repository.findForDate(today)!!
        assertEquals(InfraStatus.Done, stored.infra04)
    }

    @Test
    fun `mark morning Infra succeeds without Core approval`() = runTest(dispatcher) {
        // Sport / Spanish can be marked Standard at any point in the day.
        viewModel.onMarkInfra(Habit.Sport, InfraStatus.Done)
        advanceUntilIdle()
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        val stored = repository.findForDate(today)!!
        assertEquals(InfraStatus.Done, stored.infra03)
    }

    @Test
    fun `mark evening Infra succeeds after Core approval`() = runTest(dispatcher) {
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        repository.approveCore(today, "Шот", clock.nowEpochMillis())
        viewModel.onMarkInfra(Habit.Reading, InfraStatus.Done)
        advanceUntilIdle()
        val stored = repository.findForDate(today)!!
        assertEquals(InfraStatus.Done, stored.infra05)
    }

    @Test
    fun `second focus attempt while one is active surfaces AnotherFocusActive`() =
        runTest(dispatcher) {
            // First start parks a session on Sport; the second concurrent
            // start (on any habit) must hit the partial-unique guard and
            // surface a transient error on state for the banner to display.
            viewModel.onStartFocus(Habit.Sport)
            advanceUntilIdle()
            viewModel.onStartFocus(Habit.Watching)
            val state = viewModel.state.filterNotNull().first()
            assertEquals(SessionError.AnotherFocusActive, state.transientError)
        }

    @Test
    fun `onStopFocus ends active focus even when state has detached`() = runTest(dispatcher) {
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
        repository.approveCore(today, "Шот", clock.nowEpochMillis())
        clock.time = java.time.LocalTime.of(15, 0)

        val state = viewModel.state.filterNotNull().first()
        org.junit.Assert.assertFalse(state.shouldNudgeEveningClose)
    }

    @Test
    fun `nudge flag is true after target time on unclosed approved shift`() = runTest(dispatcher) {
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        repository.approveCore(today, "Шот", clock.nowEpochMillis())
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
        repository.approveCore(today, "Шот", clock.nowEpochMillis())
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
