package com.avangard.app.feature.report.morning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avangard.app.core.common.DomainResult
import com.avangard.app.core.domain.ReportRules
import com.avangard.app.core.domain.model.ReportError
import com.avangard.app.core.domain.usecase.InitializeDayUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class MorningReportState(
    val artifact: String = "",
    val eliminateEmail: Boolean = false,
    val eliminateMeetings: Boolean = false,
    val eliminateMessengers: Boolean = false,
    val focusModeEngaged: Boolean = false,
    val error: ReportError? = null,
    val submitting: Boolean = false,
) {
    val canSubmit: Boolean
        get() = !submitting &&
            artifact.trim().isNotEmpty() &&
            artifact.trim().length <= ReportRules.ARTIFACT_MAX_LENGTH &&
            focusModeEngaged
}

sealed interface MorningReportEffect {
    data object Submitted : MorningReportEffect
}

@HiltViewModel
class MorningReportViewModel @Inject constructor(
    private val initializeDay: InitializeDayUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(MorningReportState())
    val state: StateFlow<MorningReportState> = _state.asStateFlow()

    private val _effects = Channel<MorningReportEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onArtifactChange(value: String) {
        _state.value = _state.value.copy(artifact = value, error = null)
    }

    fun onEliminateEmail(enabled: Boolean) {
        _state.value = _state.value.copy(eliminateEmail = enabled)
    }

    fun onEliminateMeetings(enabled: Boolean) {
        _state.value = _state.value.copy(eliminateMeetings = enabled)
    }

    fun onEliminateMessengers(enabled: Boolean) {
        _state.value = _state.value.copy(eliminateMessengers = enabled)
    }

    fun onFocusModeEngaged(enabled: Boolean) {
        _state.value = _state.value.copy(focusModeEngaged = enabled)
    }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.value = current.copy(submitting = true, error = null)
        viewModelScope.launch {
            when (val result = initializeDay(current.artifact, enforceTimeWindow = false)) {
                is DomainResult.Ok -> {
                    _state.value = current.copy(submitting = false)
                    _effects.send(MorningReportEffect.Submitted)
                }
                is DomainResult.Err -> {
                    _state.value = current.copy(submitting = false, error = result.error)
                }
            }
        }
    }
}
