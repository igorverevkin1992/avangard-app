package com.avangard.app.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avangard.app.core.domain.model.DailyReport
import com.avangard.app.core.domain.model.SystemFlag
import com.avangard.app.core.domain.usecase.ObserveTodayReportUseCase
import com.avangard.app.core.domain.usecase.ToggleSwitchUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardState(
    val report: DailyReport? = null,
    val focusMode: Boolean = false,
    val silenceMode: Boolean = false,
) {
    val targetArtifact: String? get() = report?.targetArtifact
    val isInitialized: Boolean get() = report != null
    val isCompleted: Boolean get() = report?.isCompleted == true
    val progress: Float get() = when {
        report == null -> 0f
        report.isCompleted -> 1f
        else -> 0.5f
    }
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    observeToday: ObserveTodayReportUseCase,
    private val toggle: ToggleSwitchUseCase,
) : ViewModel() {

    val state: StateFlow<DashboardState> = combine(
        observeToday(),
        toggle.observe(SystemFlag.FocusMode),
        toggle.observe(SystemFlag.SilenceMode),
    ) { report, focus, silence ->
        DashboardState(report = report, focusMode = focus, silenceMode = silence)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardState(),
    )

    fun setFocusMode(enabled: Boolean) = viewModelScope.launch {
        toggle.set(SystemFlag.FocusMode, enabled)
    }

    fun setSilenceMode(enabled: Boolean) = viewModelScope.launch {
        toggle.set(SystemFlag.SilenceMode, enabled)
    }
}
