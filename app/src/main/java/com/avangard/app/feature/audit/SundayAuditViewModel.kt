package com.avangard.app.feature.audit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avangard.app.core.domain.model.Bottleneck
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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SundayAuditState(
    val view: SundayAuditView? = null,
    val selectedBottleneck: Bottleneck? = null,
    val submitting: Boolean = false,
) {
    val canSubmit: Boolean get() = !submitting && selectedBottleneck != null
}

sealed interface SundayAuditEffect {
    data object Submitted : SundayAuditEffect
}

@HiltViewModel
class SundayAuditViewModel @Inject constructor(
    audit: SundayAuditUseCase,
    private val setBottleneck: SetBottleneckUseCase,
) : ViewModel() {

    private val selection = MutableStateFlow<Bottleneck?>(null)
    private val submitting = MutableStateFlow(false)

    private val _effects = Channel<SundayAuditEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    val state: StateFlow<SundayAuditState> = combine(
        audit(),
        selection,
        submitting,
    ) { view, picked, busy ->
        SundayAuditState(view = view, selectedBottleneck = picked, submitting = busy)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SundayAuditState(),
    )

    fun onPickBottleneck(bottleneck: Bottleneck) {
        selection.value = bottleneck
    }

    fun submit() {
        val picked = selection.value ?: return
        if (submitting.value) return
        submitting.value = true
        viewModelScope.launch {
            setBottleneck(picked)
            submitting.value = false
            _effects.send(SundayAuditEffect.Submitted)
        }
    }
}
