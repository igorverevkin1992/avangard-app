package com.avangard.app.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avangard.app.core.data.QuoteRepository
import com.avangard.app.core.data.UserPreferencesRepository
import com.avangard.app.core.domain.model.Quote
import com.avangard.app.navigation.NavRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class QuoteDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val quotes: QuoteRepository,
    private val preferences: UserPreferencesRepository,
) : ViewModel() {

    private val id: Int = savedState.get<String>(NavRoute.QuoteDetail.ARG_ID)
        ?.toIntOrNull() ?: -1

    private val _quote = MutableStateFlow<Quote?>(null)
    val quote: StateFlow<Quote?> = _quote.asStateFlow()

    /** True when this quote is in the operator's «принципы» pinned set. */
    val pinned: StateFlow<Boolean> = preferences.flow
        .map { id in it.pinnedQuoteIds }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        viewModelScope.launch {
            _quote.value = quotes.byId(id)
        }
    }

    fun togglePinned() {
        if (id < 0) return
        viewModelScope.launch { preferences.togglePinnedQuote(id) }
    }
}
