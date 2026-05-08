package com.avangard.app.feature.report.evening

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avangard.app.core.common.DomainResult
import com.avangard.app.core.domain.ReportRules
import com.avangard.app.core.domain.model.DailyReport
import com.avangard.app.core.domain.model.ReportError
import com.avangard.app.core.domain.usecase.ObserveTodayReportUseCase
import com.avangard.app.core.domain.usecase.SubmitEveningReportUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class EveningReportState(
    val todayArtifact: String? = null,
    val isCompleted: Boolean = true,
    val eliminatedWaste: Int = 0,
    val failureCause: String = "",
    val correctiveAction: String = "",
    val error: ReportError? = null,
    val submitting: Boolean = false,
) {
    val showFailureAnalysis: Boolean get() = !isCompleted

    val canSubmit: Boolean
        get() = !submitting && (
            isCompleted ||
                (failureCause.trim().length >= ReportRules.ANALYSIS_MIN_LENGTH &&
                    correctiveAction.trim().length >= ReportRules.ANALYSIS_MIN_LENGTH)
            )
}

sealed interface EveningReportEffect {
    data object Submitted : EveningReportEffect
}

@HiltViewModel
class EveningReportViewModel @Inject constructor(
    observeTodayReport: ObserveTodayReportUseCase,
    private val submitEvening: SubmitEveningReportUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(EveningReportState())
    val state: StateFlow<EveningReportState> = _state.asStateFlow()

    private val _effects = Channel<EveningReportEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            observeTodayReport().collect { report -> applyReport(report) }
        }
    }

    private fun applyReport(report: DailyReport?) {
        _state.value = _state.value.copy(
            todayArtifact = report?.targetArtifact,
            // Hydrate analysis fields if a previous failure was already saved.
            failureCause = report?.failureCause ?: _state.value.failureCause,
            correctiveAction = report?.correctiveAction ?: _state.value.correctiveAction,
        )
    }

    fun onIsCompletedChange(value: Boolean) {
        _state.value = _state.value.copy(isCompleted = value, error = null)
    }

    fun onWasteChange(value: Int) {
        _state.value = _state.value.copy(eliminatedWaste = value.coerceAtLeast(0))
    }

    fun onFailureCauseChange(value: String) {
        _state.value = _state.value.copy(failureCause = value, error = null)
    }

    fun onCorrectiveActionChange(value: String) {
        _state.value = _state.value.copy(correctiveAction = value, error = null)
    }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.value = current.copy(submitting = true, error = null)
        viewModelScope.launch {
            val input = SubmitEveningReportUseCase.Input(
                isCompleted = current.isCompleted,
                eliminatedWaste = current.eliminatedWaste,
                failureCause = current.failureCause.takeIf { !current.isCompleted },
                correctiveAction = current.correctiveAction.takeIf { !current.isCompleted },
            )
            when (val result = submitEvening(input)) {
                is DomainResult.Ok -> {
                    _state.value = current.copy(submitting = false)
                    _effects.send(EveningReportEffect.Submitted)
                }
                is DomainResult.Err -> {
                    _state.value = current.copy(submitting = false, error = result.error)
                }
            }
        }
    }
}
