package com.avangard.app.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avangard.app.core.data.QuoteRepository
import com.avangard.app.core.domain.model.Quote
import com.avangard.app.core.domain.model.VirtueTag
import com.avangard.app.navigation.NavRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class VirtueQuotesState(
    val virtue: VirtueTag,
    val quotes: List<Quote> = emptyList(),
    val loaded: Boolean = false,
)

@HiltViewModel
class VirtueQuotesViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val quotes: QuoteRepository,
) : ViewModel() {

    private val virtue: VirtueTag = savedState.get<String>(NavRoute.VirtueQuotes.ARG_VIRTUE)
        ?.let { runCatching { VirtueTag.valueOf(it) }.getOrNull() }
        ?: VirtueTag.RATIONALITY

    private val _state = MutableStateFlow(VirtueQuotesState(virtue = virtue))
    val state: StateFlow<VirtueQuotesState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                quotes = quotes.byVirtue(virtue),
                loaded = true,
            )
        }
    }
}
