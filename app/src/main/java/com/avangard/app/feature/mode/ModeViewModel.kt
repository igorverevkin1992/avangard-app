package com.avangard.app.feature.mode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avangard.app.core.common.DomainResult
import com.avangard.app.core.data.UserPreferencesRepository
import com.avangard.app.core.domain.model.CoreMode
import com.avangard.app.core.domain.model.HabitStandard
import com.avangard.app.core.domain.model.HabitStandardDefaults
import com.avangard.app.core.domain.model.SessionError
import com.avangard.app.core.domain.usecase.ObserveDailySessionUseCase
import com.avangard.app.core.domain.usecase.SetDayModeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ModeState(
    val dayMode: CoreMode? = null,
    val standards: Map<String, HabitStandard> = emptyMap(),
    val transientError: SessionError? = null,
)

@HiltViewModel
class ModeViewModel @Inject constructor(
    observeSession: ObserveDailySessionUseCase,
    private val preferences: UserPreferencesRepository,
    private val setDayMode: SetDayModeUseCase,
) : ViewModel() {

    private val transientError = MutableStateFlow<SessionError?>(null)
    private var transientClearJob: Job? = null

    val state: StateFlow<ModeState> = combine(
        observeSession(),
        preferences.flow,
        transientError,
    ) { session, prefs, err ->
        ModeState(
            dayMode = session.dayMode,
            standards = prefs.habitStandards,
            transientError = err,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ModeState(),
    )

    fun onPickDayMode(mode: CoreMode) = viewModelScope.launch {
        when (val r = setDayMode(mode)) {
            is DomainResult.Err -> raise(r.error)
            is DomainResult.Ok -> Unit
        }
    }

    fun onStandardChanged(habitCode: String, standard: String, mvd: String) {
        viewModelScope.launch {
            preferences.setHabitStandard(
                habitCode,
                HabitStandard(standard = standard.trim(), mvd = mvd.trim()),
            )
        }
    }

    /** Seed empty fields with the bundled defaults. Existing non-empty
     *  values stay as the operator wrote them. */
    fun onFillDefaults() {
        viewModelScope.launch {
            val current = preferences.snapshot().habitStandards
            val merged = HabitStandardDefaults.fullMap().mapValues { (code, def) ->
                val existing = current[code]
                if (existing == null || existing.isEmpty) def else existing
            }
            preferences.replaceHabitStandards(merged)
        }
    }

    fun acknowledgeError() {
        transientError.value = null
    }

    private fun raise(error: SessionError) {
        transientError.value = error
        transientClearJob?.cancel()
        transientClearJob = viewModelScope.launch {
            delay(3_000)
            transientError.value = null
        }
    }
}
