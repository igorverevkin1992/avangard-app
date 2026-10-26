package com.itermeum.app

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itermeum.app.core.common.Clock
import com.itermeum.app.core.data.ProfileRepository
import com.itermeum.app.core.liturgy.LiturgicalCalendar
import com.itermeum.app.feature.prayer.accent
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Decides the start destination (onboarding until the profile says it is done)
 * and owns the current logical day: when it changes (03:00, or a resume after a
 * night in the background) the navigation graph and every screen's ViewModel
 * are rebuilt for the new date, so no screen keeps showing yesterday.
 */
@HiltViewModel
class RootViewModel @Inject constructor(
    profiles: ProfileRepository,
    private val calendar: LiturgicalCalendar,
    private val clock: Clock,
    uiPrefs: com.itermeum.app.core.designsystem.UiPrefs,
) : ViewModel() {

    val lightTheme: StateFlow<Boolean> = uiPrefs.light

    private val _logicalDate = MutableStateFlow(clock.logicalToday())
    val logicalDate: StateFlow<LocalDate> = _logicalDate.asStateFlow()

    /** Section 12: primary = liturgical colour of the season (follows the logical day). */
    val accent: StateFlow<Color> = logicalDate.map { calendar.day(it).color.accent() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, calendar.day(clock.logicalToday()).color.accent())

    val onboardingDone: StateFlow<Boolean?> = profiles.flow.map { it.onboardingDone }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            while (true) { delay(60_000); refreshDate() }
        }
    }

    fun refreshDate() { _logicalDate.value = clock.logicalToday() }
}
