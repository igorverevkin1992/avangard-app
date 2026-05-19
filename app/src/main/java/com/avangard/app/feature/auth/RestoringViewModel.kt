package com.avangard.app.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avangard.app.core.data.UserPreferencesRepository
import com.avangard.app.core.data.auth.AuthRepository
import com.avangard.app.core.data.cloud.RestoreBootstrapper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface RestoringStage {
    data object Running : RestoringStage
    data object Done : RestoringStage
    data class Failed(val reason: RestoreBootstrapper.Outcome.Reason) : RestoringStage
}

@HiltViewModel
class RestoringViewModel @Inject constructor(
    private val bootstrapper: RestoreBootstrapper,
    private val auth: AuthRepository,
    private val preferences: UserPreferencesRepository,
) : ViewModel() {

    private val _stage = MutableStateFlow<RestoringStage>(RestoringStage.Running)
    val stage: StateFlow<RestoringStage> = _stage.asStateFlow()

    init {
        kick()
    }

    private fun kick() {
        viewModelScope.launch {
            _stage.value = when (val outcome = bootstrapper.bootstrap()) {
                RestoreBootstrapper.Outcome.SkippedNoRemote,
                RestoreBootstrapper.Outcome.SkippedLocalNewer,
                RestoreBootstrapper.Outcome.Restored,
                -> RestoringStage.Done
                is RestoreBootstrapper.Outcome.Failed ->
                    RestoringStage.Failed(outcome.reason)
            }
        }
    }

    /** "Продолжить без восстановления" — leave the local DB intact. */
    fun continueWithoutRestore() {
        viewModelScope.launch {
            preferences.setInitialRestoreDone()
            _stage.value = RestoringStage.Done
        }
    }

    /**
     * "Выйти" — drop the Google account and reset the restore marker so the
     * next sign-in (potentially a different account) re-consults Drive.
     */
    fun signOut() {
        viewModelScope.launch {
            auth.signOut()
            preferences.clearSyncMarkers()
        }
    }
}
