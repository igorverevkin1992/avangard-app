package com.avangard.app.feature.pulpit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avangard.app.core.common.Clock
import com.avangard.app.core.common.DomainResult
import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.data.QuoteRepository
import com.avangard.app.core.data.UserPreferences
import com.avangard.app.core.data.UserPreferencesRepository
import com.avangard.app.core.domain.model.CoreStatus
import com.avangard.app.core.domain.model.DailySession
import com.avangard.app.core.domain.model.FocusSession
import com.avangard.app.core.domain.model.Habit
import com.avangard.app.core.domain.model.InfraStatus
import com.avangard.app.core.domain.model.Quote
import com.avangard.app.core.domain.model.SessionError
import com.avangard.app.core.domain.repository.SessionRepository
import com.avangard.app.core.domain.usecase.EndFocusUseCase
import com.avangard.app.core.domain.usecase.ObserveActiveFocusUseCase
import com.avangard.app.core.domain.usecase.ObserveDailySessionUseCase
import com.avangard.app.core.domain.usecase.SetInfraStatusUseCase
import com.avangard.app.core.domain.usecase.StartFocusUseCase
import com.avangard.app.core.domain.usecase.ToggleMvdUseCase
import com.avangard.app.core.ui.components.DEFAULT_COLD_START_THRESHOLD_MS
import com.avangard.app.core.ui.components.tickerFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PulpitState(
    val today: LocalDate,
    val session: DailySession,
    val activeFocus: FocusSession?,
    val transientError: SessionError? = null,
    val coldStartThresholdMs: Long = DEFAULT_COLD_START_THRESHOLD_MS,
    val shouldNudgeEveningClose: Boolean = false,
    val dailyQuote: Quote? = null,
    /** Sum of completed (endedAt != null) focus durations today, per habit.
     *  Active session is excluded so the live elapsed can be added at the UI
     *  layer against [nowMs] without re-emitting state every tick. */
    val completedFocusByHabit: Map<Habit, Long> = emptyMap(),
    /** Number of completed focus sessions today, per habit. */
    val completedFocusCountByHabit: Map<Habit, Int> = emptyMap(),
) {
    val isCoreUnlocked: Boolean get() = session.isCoreUnlocked
    fun isFocusActiveOn(habit: Habit): Boolean = activeFocus?.habit == habit
}

sealed interface PulpitEffect {
    data object OpenAuthorisationModal : PulpitEffect
    data object OpenSabotage : PulpitEffect
    data object OpenEveningClose : PulpitEffect
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OperatorPulpitViewModel @Inject constructor(
    private val clock: Clock,
    observeSession: ObserveDailySessionUseCase,
    private val observeActiveFocus: ObserveActiveFocusUseCase,
    preferences: UserPreferencesRepository,
    private val startFocus: StartFocusUseCase,
    private val endFocus: EndFocusUseCase,
    private val toggleMvd: ToggleMvdUseCase,
    private val setInfraStatus: SetInfraStatusUseCase,
    quotes: QuoteRepository,
    sessions: SessionRepository,
) : ViewModel() {

    private val _effects = Channel<PulpitEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    /** Stable fallback for the first frame, before the combined state emits.
     *  Uses the injected Clock so the value agrees with the configured zone
     *  rather than the system default. */
    val initialToday: LocalDate = clock.today()

    private val transientError = MutableStateFlow<SessionError?>(null)
    private var transientErrorClearJob: Job? = null

    private data class Inputs(
        val session: DailySession,
        val focus: FocusSession?,
        val error: SessionError?,
        val prefs: UserPreferences,
        val quote: Quote?,
        val completedFocusToday: List<FocusSession>,
    )

    // Today's epoch is recomputed off the ticker so observeFocusForDay
    // switches to the new day's slice automatically when midnight passes.
    private val todayEpochFlow = tickerFlow(clock, intervalMs = 60_000L)
        .map { clock.today().toStartOfDayEpoch(clock.zone()) }
        .distinctUntilChanged()

    private val completedFocusTodayFlow: Flow<List<FocusSession>> =
        todayEpochFlow.flatMapLatest { epoch ->
            sessions.observeFocusForDay(epoch).map { list ->
                list.filter { it.endedAt != null }
            }
        }

    // PulpitState is split from the 1Hz ticker so the screen-wide recomp only
    // happens when something the user can act on actually changes. The current
    // wall-clock millis are exposed separately via [nowMs] and collected only
    // inside the CoreTimerDisplay subtree.
    val state: StateFlow<PulpitState?> = combine(
        combine(
            observeSession(),
            observeActiveFocus(),
            tickerFlow(clock),
            transientError,
            preferences.flow,
        ) { session, focus, _, error, prefs ->
            Inputs(session, focus, error, prefs, null, emptyList())
        },
        quotes.quoteOfDayFlow(),
        completedFocusTodayFlow,
    ) { inputs, quote, completed ->
        inputs.copy(quote = quote, completedFocusToday = completed)
    }
        .map { i ->
            val completedByHabit = i.completedFocusToday.groupBy { it.habit }
            PulpitState(
                today = clock.today(),
                session = i.session,
                activeFocus = i.focus,
                transientError = i.error,
                coldStartThresholdMs = i.prefs.coldStartThresholdMs,
                shouldNudgeEveningClose = computeEveningNudge(i.session, i.prefs),
                dailyQuote = i.quote,
                completedFocusByHabit = completedByHabit.mapValues { (_, list) ->
                    list.sumOf { (it.endedAt ?: 0L) - it.startedAt }
                },
                completedFocusCountByHabit = completedByHabit.mapValues { (_, list) ->
                    list.size
                },
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    /** 1 Hz ticker exposed as a StateFlow so only the timer-rendering
     *  subtree collects it; the rest of the pulpit doesn't recomp on tick. */
    val nowMs: StateFlow<Long> = tickerFlow(clock).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = clock.nowEpochMillis(),
    )

    private fun computeEveningNudge(session: DailySession, prefs: UserPreferences): Boolean {
        if (session.eveningClosed) return false
        if (session.coreStatus is CoreStatus.Idle) return false
        val target = LocalTime.of(prefs.eveningCloseHour, prefs.eveningCloseMinute)
        return !clock.localTime().isBefore(target)
    }

    fun onStartFocus(habit: Habit) = viewModelScope.launch {
        when (val r = startFocus(habit)) {
            is DomainResult.Err -> raise(r.error)
            is DomainResult.Ok -> Unit
        }
    }

    // Read the source flow directly: state uses WhileSubscribed(5s), so after
    // a brief UI detachment state.value collapses to initialValue=null and
    // onStopFocus() would silently no-op despite an active focus on disk.
    fun onStopFocus() = viewModelScope.launch {
        val id = observeActiveFocus().first()?.id ?: return@launch
        endFocus(id)
    }

    fun onToggleMvd() = viewModelScope.launch {
        when (val r = toggleMvd()) {
            is DomainResult.Err -> raise(r.error)
            is DomainResult.Ok -> Unit
        }
    }

    fun onMarkInfra(habit: Habit, status: InfraStatus) = viewModelScope.launch {
        when (val r = setInfraStatus(habit, status)) {
            is DomainResult.Err -> raise(r.error)
            is DomainResult.Ok -> Unit
        }
    }

    fun onRequestApproveCore() {
        viewModelScope.launch { _effects.send(PulpitEffect.OpenAuthorisationModal) }
    }

    fun onSabotageClicked() {
        viewModelScope.launch { _effects.send(PulpitEffect.OpenSabotage) }
    }

    fun onCloseShiftClicked() {
        viewModelScope.launch { _effects.send(PulpitEffect.OpenEveningClose) }
    }

    private fun raise(error: SessionError) {
        transientError.value = error
        transientErrorClearJob?.cancel()
        transientErrorClearJob = viewModelScope.launch {
            delay(ERROR_HOLD_MS)
            transientError.value = null
        }
    }

    companion object {
        private const val ERROR_HOLD_MS = 3_000L
    }
}
