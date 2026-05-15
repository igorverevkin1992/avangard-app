package com.avangard.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avangard.app.core.data.UserPreferences
import com.avangard.app.core.data.UserPreferencesRepository
import com.avangard.app.core.domain.repository.HabitRepository
import com.avangard.app.core.domain.repository.SessionRepository
import com.avangard.app.core.ui.components.DEFAULT_COLD_START_THRESHOLD_MS
import com.avangard.app.sync.scheduler.EveningCloseScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsState(
    val preferences: UserPreferences = UserPreferences(),
    val confirmingWipe: Boolean = false,
    val wipeInProgress: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sessions: SessionRepository,
    private val habits: HabitRepository,
    private val preferences: UserPreferencesRepository,
    private val scheduler: EveningCloseScheduler,
) : ViewModel() {

    private val wipeFlags = MutableStateFlow(WipeFlags())

    val state: StateFlow<SettingsState> = combine(
        preferences.flow,
        wipeFlags,
    ) { prefs, flags ->
        SettingsState(
            preferences = prefs,
            confirmingWipe = flags.confirming,
            wipeInProgress = flags.inProgress,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsState(),
    )

    fun onEveningCloseChanged(hour: Int, minute: Int) {
        if (hour !in 0..23 || minute !in 0..59) return
        viewModelScope.launch {
            preferences.setEveningClose(hour, minute)
            // Re-arm immediately so the next trigger reflects the new time.
            scheduler.ensureScheduled()
        }
    }

    fun onColdStartThresholdChanged(minutes: Int) {
        if (minutes !in 1..60) return
        viewModelScope.launch {
            preferences.setColdStartThresholdMs(minutes * 60L * 1000)
        }
    }

    fun requestWipe() {
        wipeFlags.value = wipeFlags.value.copy(confirming = true)
    }

    fun cancelWipe() {
        wipeFlags.value = wipeFlags.value.copy(confirming = false)
    }

    fun confirmWipe() {
        if (wipeFlags.value.inProgress) return
        wipeFlags.value = WipeFlags(confirming = false, inProgress = true)
        viewModelScope.launch {
            sessions.wipe()
            habits.wipe()
            wipeFlags.value = WipeFlags()
        }
    }

    private data class WipeFlags(
        val confirming: Boolean = false,
        val inProgress: Boolean = false,
    )

    companion object {
        val COLD_START_OPTIONS_MINUTES = listOf(3, 5, 7, 10)
        const val DEFAULT_COLD_START_MINUTES =
            (DEFAULT_COLD_START_THRESHOLD_MS / 1000 / 60).toInt()
    }
}
