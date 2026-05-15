package com.avangard.app.feature.pulpit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avangard.app.core.common.DomainResult
import com.avangard.app.core.domain.model.SessionError
import com.avangard.app.core.domain.usecase.ApproveCoreUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
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
    private val savedState: SavedStateHandle,
) : ViewModel() {

    // The two user-typed fields survive process death — Android kills the app
    // mid-prompt, the prompt comes back on relaunch.
    private val promptFlow: StateFlow<String> =
        savedState.getStateFlow(KEY_PROMPT, "")
    private val authorisedFlow: StateFlow<Boolean> =
        savedState.getStateFlow(KEY_AUTHORISED, false)

    // Submitting flag and transient error are intentionally NOT persisted —
    // they belong to the current submission attempt, not the user's intent.
    private val submitting = MutableStateFlow(false)
    private val error = MutableStateFlow<SessionError?>(null)

    // Eagerly so state.value is always the live combined value, including
    // updates triggered by the on*Change handlers — submit() reads it without
    // worrying about whether a UI subscriber happens to be active.
    val state: StateFlow<AuthorisationModalState> = combine(
        promptFlow,
        authorisedFlow,
        submitting,
        error,
    ) { prompt, authorised, submitting, error ->
        AuthorisationModalState(
            prompt = prompt,
            authorised = authorised,
            submitting = submitting,
            error = error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AuthorisationModalState(
            prompt = promptFlow.value,
            authorised = authorisedFlow.value,
        ),
    )

    private val _effects = Channel<AuthorisationEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onPromptChange(value: String) {
        savedState[KEY_PROMPT] = value
        error.value = null
    }

    fun onAuthorisedChange(value: Boolean) {
        savedState[KEY_AUTHORISED] = value
        error.value = null
    }

    fun submit() {
        // Read source flows directly — defensive against any stale state.value.
        val currentPrompt = promptFlow.value
        val currentAuthorised = authorisedFlow.value
        if (submitting.value) return
        if (!currentAuthorised) return
        if (currentPrompt.trim().isEmpty()) return

        submitting.value = true
        error.value = null
        viewModelScope.launch {
            when (val result = approveCore(prompt = currentPrompt, authorised = currentAuthorised)) {
                is DomainResult.Ok -> {
                    submitting.value = false
                    _effects.send(AuthorisationEffect.Submitted)
                }
                is DomainResult.Err -> {
                    submitting.value = false
                    error.value = result.error
                }
            }
        }
    }

    companion object {
        private const val KEY_PROMPT = "prompt"
        private const val KEY_AUTHORISED = "authorised"
    }
}
