package com.avangard.app.feature.locked

import androidx.lifecycle.ViewModel
import com.avangard.app.core.domain.usecase.IsHistoryUnlockedUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Thin Hilt-aware wrapper around [IsHistoryUnlockedUseCase] so the nav-host
 * composable can ask the gate question without owning a Clock dependency.
 */
@HiltViewModel
class HistoryGateViewModel @Inject constructor(
    private val isHistoryUnlocked: IsHistoryUnlockedUseCase,
) : ViewModel() {
    fun isUnlocked(): Boolean = isHistoryUnlocked()
}
