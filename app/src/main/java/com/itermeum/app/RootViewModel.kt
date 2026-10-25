package com.itermeum.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itermeum.app.core.data.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Decides the start destination: onboarding until the profile says it is done. */
@HiltViewModel
class RootViewModel @Inject constructor(profiles: ProfileRepository) : ViewModel() {
    val onboardingDone: StateFlow<Boolean?> = profiles.flow.map { it.onboardingDone }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
