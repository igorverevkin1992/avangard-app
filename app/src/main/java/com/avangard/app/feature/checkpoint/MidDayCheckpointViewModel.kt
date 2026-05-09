package com.avangard.app.feature.checkpoint

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avangard.app.core.common.DomainResult
import com.avangard.app.core.domain.ReportRules
import com.avangard.app.core.domain.model.MiddayStatus
import com.avangard.app.core.domain.model.ReportError
import com.avangard.app.core.domain.usecase.ObserveTodayReportUseCase
import com.avangard.app.core.domain.usecase.SubmitMiddayStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

enum class MiddayChoice { Unset, InProgress, Blocked }

data class MidDayCheckpointState(
    val todayArtifact: String? = null,
    val choice: MiddayChoice = MiddayChoice.Unset,
    val unblockingAction: String = "",
    val error: ReportError? = null,
    val submitting: Boolean = false,
) {
    val showActionField: Boolean get() = choice == MiddayChoice.Blocked
    val isShiftOpen: Boolean get() = !todayArtifact.isNullOrBlank()

    val canSubmit: Boolean
        get() = isShiftOpen && !submitting && when (choice) {
            MiddayChoice.Unset -> false
            MiddayChoice.InProgress -> true
            MiddayChoice.Blocked ->
                unblockingAction.trim().length >= ReportRules.ANALYSIS_MIN_LENGTH
        }
}

sealed interface MidDayCheckpointEffect {
    data object Submitted : MidDayCheckpointEffect
}

@HiltViewModel
class MidDayCheckpointViewModel @Inject constructor(
    observeTodayReport: ObserveTodayReportUseCase,
    private val submit: SubmitMiddayStatusUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(MidDayCheckpointState())
    val state: StateFlow<MidDayCheckpointState> = _state.asStateFlow()

    private val _effects = Channel<MidDayCheckpointEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            observeTodayReport().collect { report ->
                val current = _state.value
                val rehydratedChoice = when (report?.midday) {
                    MiddayStatus.InProgress -> MiddayChoice.InProgress
                    is MiddayStatus.Blocked -> MiddayChoice.Blocked
                    else -> current.choice
                }
                val rehydratedAction = (report?.midday as? MiddayStatus.Blocked)
                    ?.unblockingAction
                    ?: current.unblockingAction
                _state.value = current.copy(
                    todayArtifact = report?.targetArtifact,
                    choice = rehydratedChoice,
                    unblockingAction = rehydratedAction,
                )
            }
        }
    }

    fun selectInProgress() {
        _state.value = _state.value.copy(choice = MiddayChoice.InProgress, error = null)
    }

    fun selectBlocked() {
        _state.value = _state.value.copy(choice = MiddayChoice.Blocked, error = null)
    }

    fun onActionChange(value: String) {
        _state.value = _state.value.copy(unblockingAction = value, error = null)
    }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        val status: MiddayStatus = when (current.choice) {
            MiddayChoice.InProgress -> MiddayStatus.InProgress
            MiddayChoice.Blocked -> MiddayStatus.Blocked(current.unblockingAction)
            MiddayChoice.Unset -> return
        }
        _state.value = current.copy(submitting = true, error = null)
        viewModelScope.launch {
            when (val result = submit(status)) {
                is DomainResult.Ok -> {
                    _state.value = current.copy(submitting = false)
                    _effects.send(MidDayCheckpointEffect.Submitted)
                }
                is DomainResult.Err -> {
                    _state.value = current.copy(submitting = false, error = result.error)
                }
            }
        }
    }
}
