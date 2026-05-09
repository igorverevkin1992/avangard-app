package com.avangard.app.feature.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avangard.app.core.common.Clock
import com.avangard.app.core.domain.model.Habit
import com.avangard.app.core.domain.model.HabitMonthlyView
import com.avangard.app.core.domain.usecase.ObserveMonthHabitsUseCase
import com.avangard.app.core.domain.usecase.ToggleHabitUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HabitTrackerState(
    val today: LocalDate,
    val selected: YearMonth,
    val view: HabitMonthlyView? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HabitTrackerViewModel @Inject constructor(
    clock: Clock,
    observeMonth: ObserveMonthHabitsUseCase,
    private val toggleUseCase: ToggleHabitUseCase,
) : ViewModel() {

    private val today: LocalDate = clock.today()
    private val selected = MutableStateFlow(YearMonth.from(today))

    val state: StateFlow<HabitTrackerState> = selected
        .flatMapLatest { ym ->
            observeMonth(ym.year, ym.monthValue).map { view ->
                HabitTrackerState(today = today, selected = ym, view = view)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HabitTrackerState(today = today, selected = YearMonth.from(today)),
        )

    fun selectMonth(yearMonth: YearMonth) {
        selected.value = yearMonth
    }

    fun toggle(date: LocalDate, habit: Habit) {
        viewModelScope.launch { toggleUseCase(date, habit) }
    }
}
