package com.avangard.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avangard.app.core.domain.repository.HabitRepository
import com.avangard.app.core.domain.repository.ReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsState(
    val confirmingWipe: Boolean = false,
    val wipeInProgress: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: ReportRepository,
    private val habits: HabitRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    fun requestWipe() {
        _state.value = _state.value.copy(confirmingWipe = true)
    }

    fun cancelWipe() {
        _state.value = _state.value.copy(confirmingWipe = false)
    }

    fun confirmWipe() {
        if (_state.value.wipeInProgress) return
        _state.value = _state.value.copy(wipeInProgress = true, confirmingWipe = false)
        viewModelScope.launch {
            repository.wipe()
            habits.wipe()
            _state.value = _state.value.copy(wipeInProgress = false)
        }
    }
}
