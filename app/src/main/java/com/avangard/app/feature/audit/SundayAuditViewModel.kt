package com.avangard.app.feature.audit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avangard.app.core.common.Clock
import com.avangard.app.core.common.DAY_MILLIS
import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.data.UserPreferencesRepository
import com.avangard.app.core.domain.model.Bottleneck
import com.avangard.app.core.domain.model.BottleneckFollowup
import com.avangard.app.core.domain.model.EvasionKind
import com.avangard.app.core.domain.repository.SessionRepository
import com.avangard.app.core.domain.usecase.ObserveDailySessionUseCase
import com.avangard.app.core.domain.usecase.SetBottleneckUseCase
import com.avangard.app.core.domain.usecase.SundayAuditUseCase
import com.avangard.app.core.domain.usecase.SundayAuditView
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SundayAuditState(
    val view: SundayAuditView? = null,
    val selectedBottleneck: Bottleneck? = null,
    val submitting: Boolean = false,
    /** Non-null when today's DailySession.bottleneckForNextWeek is set — the
     *  audit has been fixated. UI switches to a sealed "completed" layout
     *  rather than showing the picker + submit affordances. */
    val fixatedBottleneck: Bottleneck? = null,
    /** Count of evasion diagnoses recorded in the last 7 days, by kind. */
    val evasionsThisWeek: Map<EvasionKind, Int> = emptyMap(),
    /** Last week's bottleneck — what the operator now answers Yes/Partial/No against. */
    val priorBottleneck: Bottleneck? = null,
    /** Currently saved verdict on the prior bottleneck. Null until the operator picks one. */
    val priorBottleneckFollowup: BottleneckFollowup? = null,
) {
    val isCompleted: Boolean get() = fixatedBottleneck != null
    val canSubmit: Boolean get() = !submitting && !isCompleted && selectedBottleneck != null
}

sealed interface SundayAuditEffect {
    data object Submitted : SundayAuditEffect
}

@HiltViewModel
class SundayAuditViewModel @Inject constructor(
    audit: SundayAuditUseCase,
    observeSession: ObserveDailySessionUseCase,
    private val setBottleneck: SetBottleneckUseCase,
    preferences: UserPreferencesRepository,
    private val clock: Clock,
    private val sessions: SessionRepository,
) : ViewModel() {

    private val selection = MutableStateFlow<Bottleneck?>(null)
    private val submitting = MutableStateFlow(false)

    private val _effects = Channel<SundayAuditEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    // Look back 14 days for the latest bottleneck the operator set. Today
    // is excluded so the audit can't reference itself before submit.
    private val priorFlow = run {
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        sessions.observeRange(today - 13 * DAY_MILLIS, today - 1)
            .map { rows ->
                rows.asReversed().firstOrNull { it.bottleneckForNextWeek != null }
                    ?.bottleneckForNextWeek
            }
    }

    private data class CurrentInputs(
        val view: SundayAuditView,
        val picked: Bottleneck?,
        val busy: Boolean,
        val today: com.avangard.app.core.domain.model.DailySession,
        val evasions: Map<EvasionKind, Int>,
    )

    val state: StateFlow<SundayAuditState> = combine(
        combine(
            audit(),
            selection,
            submitting,
            observeSession(),
            preferences.flow.map { prefs ->
                val cutoff = clock.nowEpochMillis() - 7L * 24 * 60 * 60 * 1000
                prefs.evasionLog
                    .filter { it.timestampMs >= cutoff }
                    .groupingBy { it.kind }
                    .eachCount()
            },
        ) { view, picked, busy, today, evasions ->
            CurrentInputs(view, picked, busy, today, evasions)
        },
        priorFlow,
    ) { current, prior ->
        SundayAuditState(
            view = current.view,
            selectedBottleneck = current.picked,
            submitting = current.busy,
            fixatedBottleneck = current.today.bottleneckForNextWeek,
            evasionsThisWeek = current.evasions,
            priorBottleneck = prior,
            priorBottleneckFollowup = current.today.bottleneckFollowup,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SundayAuditState(),
    )

    fun onPickFollowup(answer: BottleneckFollowup) {
        viewModelScope.launch {
            val today = clock.today().toStartOfDayEpoch(clock.zone())
            sessions.setBottleneckFollowup(today, answer)
        }
    }

    fun onPickBottleneck(bottleneck: Bottleneck) {
        // Sealed once the audit is fixated for the day — the picker is hidden
        // in the UI but guard defensively in case a stale recomposition fires
        // the callback during the swap.
        if (state.value.isCompleted) return
        selection.value = bottleneck
    }

    fun submit() {
        val picked = selection.value ?: return
        if (submitting.value) return
        if (state.value.isCompleted) return
        submitting.value = true
        viewModelScope.launch {
            setBottleneck(picked)
            submitting.value = false
            _effects.send(SundayAuditEffect.Submitted)
        }
    }
}
