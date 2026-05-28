package com.avangard.app.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avangard.app.core.common.Clock
import com.avangard.app.core.data.QuoteRepository
import com.avangard.app.core.domain.model.Quote
import com.avangard.app.core.domain.model.VirtueTag
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Past quote-of-day entry shown in the library history strip. */
data class HistoryEntry(val date: LocalDate, val quote: Quote)

data class LibraryState(
    val quoteOfDay: Quote? = null,
    val counts: Map<VirtueTag, Int> = emptyMap(),
    val loaded: Boolean = false,
    /** Last [HISTORY_DEPTH] days of quote-of-day, newest first (excluding today). */
    val history: List<HistoryEntry> = emptyList(),
) {
    companion object {
        const val HISTORY_DEPTH = 7
    }
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val quotes: QuoteRepository,
    private val clock: Clock,
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
            val today = clock.today()
            val history = (1..LibraryState.HISTORY_DEPTH).mapNotNull { offset ->
                val date = today.minusDays(offset.toLong())
                quotes.quoteOfDay(date.toEpochDay())?.let { HistoryEntry(date, it) }
            }
            _state.value = LibraryState(
                quoteOfDay = quotes.quoteOfDay(),
                counts = counts,
                loaded = true,
                history = history,
            )
        }
    }
}
