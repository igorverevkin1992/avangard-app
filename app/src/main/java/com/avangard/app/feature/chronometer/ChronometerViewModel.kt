package com.avangard.app.feature.chronometer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avangard.app.core.domain.model.ChronometerProgress
import com.avangard.app.core.domain.repository.ChronometerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ChronometerViewModel @Inject constructor(
    repository: ChronometerRepository,
) : ViewModel() {

    val state: StateFlow<ChronometerProgress> = repository.observeProgress()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ChronometerProgress.EMPTY,
        )
}
