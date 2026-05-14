package com.avangard.app.feature.pulpit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avangard.app.core.common.Clock
import com.avangard.app.core.domain.model.DailySession
import com.avangard.app.core.domain.model.FocusSession
import com.avangard.app.core.domain.model.Habit
import com.avangard.app.core.domain.model.InfraStatus
import com.avangard.app.core.domain.usecase.EndFocusUseCase
import com.avangard.app.core.domain.usecase.ObserveActiveFocusUseCase
import com.avangard.app.core.domain.usecase.ObserveDailySessionUseCase
import com.avangard.app.core.domain.usecase.SetInfraStatusUseCase
import com.avangard.app.core.domain.usecase.StartFocusUseCase
import com.avangard.app.core.domain.usecase.ToggleMvdUseCase
import com.avangard.app.core.ui.components.tickerFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PulpitState(
    val today: LocalDate,
    val session: DailySession,
    val activeFocus: FocusSession?,
    val now: Long,
) {
    val isCoreUnlocked: Boolean get() = session.isCoreUnlocked
    val activeFocusElapsedMs: Long get() = activeFocus?.durationMillis(now) ?: 0L
    fun isFocusActiveOn(habit: Habit): Boolean = activeFocus?.habit == habit
}

sealed interface PulpitEffect {
    data object OpenAuthorisationModal : PulpitEffect
    data object OpenSabotage : PulpitEffect
    data object OpenEveningClose : PulpitEffect
}

@HiltViewModel
class OperatorPulpitViewModel @Inject constructor(
    private val clock: Clock,
    observeSession: ObserveDailySessionUseCase,
    observeActiveFocus: ObserveActiveFocusUseCase,
    private val startFocus: StartFocusUseCase,
    private val endFocus: EndFocusUseCase,
    private val toggleMvd: ToggleMvdUseCase,
    private val setInfraStatus: SetInfraStatusUseCase,
) : ViewModel() {

    private val _effects = Channel<PulpitEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    val state: StateFlow<PulpitState?> = combine(
        observeSession(),
        observeActiveFocus(),
        tickerFlow(),
    ) { session, focus, now ->
        PulpitState(
            today = clock.today(),
            session = session,
            activeFocus = focus,
            now = now,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    fun onStartFocus(habit: Habit) = viewModelScope.launch { startFocus(habit) }

    fun onStopFocus() = viewModelScope.launch {
        state.value?.activeFocus?.let { endFocus(it.id) }
    }

    fun onToggleMvd() = viewModelScope.launch { toggleMvd() }

    fun onMarkInfra(habit: Habit, status: InfraStatus) = viewModelScope.launch {
        setInfraStatus(habit, status)
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
}
