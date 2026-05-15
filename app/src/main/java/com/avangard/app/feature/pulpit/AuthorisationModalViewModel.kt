package com.avangard.app.feature.pulpit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avangard.app.core.common.DomainResult
import com.avangard.app.core.domain.model.SessionError
import com.avangard.app.core.domain.usecase.ApproveCoreUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class AuthorisationModalState(
    val prompt: String = "",
    val authorised: Boolean = false,
    val submitting: Boolean = false,
    val error: SessionError? = null,
) {
    val canSubmit: Boolean
        get() = !submitting && authorised && prompt.trim().isNotEmpty()
}

sealed interface AuthorisationEffect {
    data object Submitted : AuthorisationEffect
}

@HiltViewModel
class AuthorisationModalViewModel @Inject constructor(
    private val approveCore: ApproveCoreUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthorisationModalState())
    val state: StateFlow<AuthorisationModalState> = _state.asStateFlow()

    private val _effects = Channel<AuthorisationEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onPromptChange(value: String) {
        _state.value = _state.value.copy(prompt = value, error = null)
    }

    fun onAuthorisedChange(value: Boolean) {
        _state.value = _state.value.copy(authorised = value, error = null)
    }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.value = current.copy(submitting = true, error = null)
        viewModelScope.launch {
            when (val result = approveCore(prompt = current.prompt, authorised = current.authorised)) {
                is DomainResult.Ok -> {
                    _state.value = current.copy(submitting = false)
                    _effects.send(AuthorisationEffect.Submitted)
                }
                is DomainResult.Err -> {
                    _state.value = current.copy(submitting = false, error = result.error)
                }
            }
        }
    }
}
