package com.avangard.app.feature.closing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avangard.app.core.common.DomainResult
import com.avangard.app.core.domain.model.CoreStatus
import com.avangard.app.core.domain.model.DefectKind
import com.avangard.app.core.domain.model.SessionError
import com.avangard.app.core.domain.model.VirtueScores
import com.avangard.app.core.domain.usecase.CloseEveningUseCase
import com.avangard.app.core.domain.usecase.ObserveDailySessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

enum class Virtue { Rationality, Independence, Honesty, Justice }

data class EveningCloseState(
    val coreApproved: Boolean = false,
    val coreFailed: Boolean = false,
    val rationality: Int = 0,
    val independence: Int = 0,
    val honesty: Int = 0,
    val justice: Int = 0,
    val defectKind: DefectKind? = null,
    val submitting: Boolean = false,
    val error: SessionError? = null,
) {
    /** Idle core demands operator to pick Defect or Waste before closing. */
    val needsDefectKind: Boolean get() = !coreApproved && !coreFailed
    val productivityOk: Boolean get() = coreApproved
    val prideOk: Boolean get() = coreApproved
    val integrityOk: Boolean get() = !coreFailed

    val canSubmit: Boolean
        get() = !submitting && (!needsDefectKind || defectKind != null)
}

sealed interface EveningCloseEffect {
    data object Closed : EveningCloseEffect
}

@HiltViewModel
class EveningCloseViewModel @Inject constructor(
    observeSession: ObserveDailySessionUseCase,
    private val closeEvening: CloseEveningUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(EveningCloseState())
    val state: StateFlow<EveningCloseState> = _state.asStateFlow()

    private val _effects = Channel<EveningCloseEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            observeSession().collect { session ->
                _state.value = _state.value.copy(
                    coreApproved = session.coreStatus is CoreStatus.Approved,
                    coreFailed = session.coreStatus is CoreStatus.Failed,
                )
            }
        }
    }

    fun onVirtueChange(virtue: Virtue, value: Int) {
        val clamped = value.coerceIn(-1, 1)
        _state.value = when (virtue) {
            Virtue.Rationality -> _state.value.copy(rationality = clamped)
            Virtue.Independence -> _state.value.copy(independence = clamped)
            Virtue.Honesty -> _state.value.copy(honesty = clamped)
            Virtue.Justice -> _state.value.copy(justice = clamped)
        }
    }

    fun onDefectKindChange(kind: DefectKind?) {
        _state.value = _state.value.copy(defectKind = kind, error = null)
    }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.value = current.copy(submitting = true, error = null)
        viewModelScope.launch {
            val virtues = VirtueScores(
                rationality = current.rationality,
                independence = current.independence,
                honesty = current.honesty,
                justice = current.justice,
            )
            val defect = if (current.needsDefectKind) current.defectKind else null
            when (val result = closeEvening(virtues = virtues, defectKindWhenIdle = defect)) {
                is DomainResult.Ok -> {
                    _state.value = current.copy(submitting = false)
                    _effects.send(EveningCloseEffect.Closed)
                }
                is DomainResult.Err -> {
                    _state.value = current.copy(submitting = false, error = result.error)
                }
            }
        }
    }
}
