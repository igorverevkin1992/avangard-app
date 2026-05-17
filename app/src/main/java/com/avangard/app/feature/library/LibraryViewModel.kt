package com.avangard.app.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avangard.app.core.data.QuoteRepository
import com.avangard.app.core.domain.model.Quote
import com.avangard.app.core.domain.model.VirtueTag
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryState(
    val quoteOfDay: Quote? = null,
    val counts: Map<VirtueTag, Int> = emptyMap(),
    val loaded: Boolean = false,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val quotes: QuoteRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    /** Live quote-of-day so the panel flips at midnight without leaving
     *  the library tab. */
    val quoteOfDay: StateFlow<Quote?> = quotes.quoteOfDayFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    init {
        viewModelScope.launch {
            val all = quotes.all()
            val counts = VirtueTag.entries.associateWith { virtue ->
                all.count { virtue in it.virtues }
            }
            _state.value = LibraryState(
                quoteOfDay = quotes.quoteOfDay(),
                counts = counts,
                loaded = true,
            )
        }
    }
}
