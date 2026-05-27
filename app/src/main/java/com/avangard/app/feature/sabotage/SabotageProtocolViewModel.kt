package com.avangard.app.feature.sabotage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avangard.app.core.common.Clock
import com.avangard.app.core.data.UserPreferencesRepository
import com.avangard.app.core.domain.model.EvasionKind
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Pure write-only VM for the evasion-diagnostic screen. The body of the
 * three scripts is static text; the only behaviour is logging the
 * diagnosis tap so the weekly audit can surface a breakdown.
 */
@HiltViewModel
class SabotageProtocolViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository,
    private val clock: Clock,
) : ViewModel() {

    fun onDiagnosisAcknowledged(kind: EvasionKind) {
        viewModelScope.launch {
            preferences.appendEvasion(kind, clock.nowEpochMillis())
        }
    }
}
