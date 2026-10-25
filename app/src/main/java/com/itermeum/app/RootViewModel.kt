package com.itermeum.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itermeum.app.core.common.Clock
import com.itermeum.app.core.data.ProfileRepository
import com.itermeum.app.core.liturgy.LiturgicalCalendar
import com.itermeum.app.feature.prayer.accent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Decides the start destination: onboarding until the profile says it is done. */
@HiltViewModel
class RootViewModel @Inject constructor(profiles: ProfileRepository, calendar: LiturgicalCalendar, clock: Clock) : ViewModel() {
    /** Section 12: primary = liturgical colour of the season. */
    val accent = calendar.day(clock.logicalToday()).color.accent()

    val onboardingDone: StateFlow<Boolean?> = profiles.flow.map { it.onboardingDone }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
