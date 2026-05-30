package com.avangard.app.feature.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avangard.app.core.common.Clock
import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.domain.model.FocusSession
import com.avangard.app.core.domain.model.Habit
import com.avangard.app.core.domain.model.HabitMonthlyView
import com.avangard.app.core.domain.repository.SessionRepository
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DayDetail(
    val date: LocalDate,
    val focusByHabit: Map<Habit, Long>,
    val focusCountByHabit: Map<Habit, Int>,
    val journal: String?,
    /** Per-habit list of non-blank intent notes for the day's sessions. */
    val intentsByHabit: Map<Habit, List<String>> = emptyMap(),
)

data class HabitTrackerState(
    val today: LocalDate,
    val selected: YearMonth,
    val view: HabitMonthlyView? = null,
    /** Currently filtered habit. null means show all 5 columns. */
    val habitFilter: Habit? = null,
    /** Selected day for the drill-down panel. null = no panel. */
    val selectedDay: LocalDate? = null,
    val dayDetail: DayDetail? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HabitTrackerViewModel @Inject constructor(
    private val clock: Clock,
    observeMonth: ObserveMonthHabitsUseCase,
    private val toggleUseCase: ToggleHabitUseCase,
    private val sessions: SessionRepository,
) : ViewModel() {

    private val selected = MutableStateFlow(YearMonth.from(clock.today()))
    private val habitFilter = MutableStateFlow<Habit?>(null)
    private val selectedDay = MutableStateFlow<LocalDate?>(null)
    private val dayDetail = MutableStateFlow<DayDetail?>(null)

    val state: StateFlow<HabitTrackerState> = combine(
        selected.flatMapLatest { ym ->
            observeMonth(ym.year, ym.monthValue).map { view -> ym to view }
        },
        habitFilter,
        selectedDay,
        dayDetail,
    ) { (ym, view), filter, day, detail ->
        HabitTrackerState(
            today = clock.today(),
            selected = ym,
            view = view,
            habitFilter = filter,
            selectedDay = day,
            dayDetail = if (detail?.date == day) detail else null,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HabitTrackerState(
            today = clock.today(),
            selected = YearMonth.from(clock.today()),
        ),
    )

    fun selectMonth(yearMonth: YearMonth) {
        selected.value = yearMonth
        selectedDay.value = null
    }

    /** Long-press on a cell — flips the habit's done-flag for that day. */
    fun toggle(date: LocalDate, habit: Habit) {
        viewModelScope.launch { toggleUseCase(date, habit) }
    }

    /** Tap on a row — opens the drill-down panel for that day, or closes
     *  it if the same day is tapped again. */
    fun selectDay(date: LocalDate) {
        if (selectedDay.value == date) {
            selectedDay.value = null
            return
        }
        selectedDay.value = date
        viewModelScope.launch {
            val epoch = date.toStartOfDayEpoch(clock.zone())
            val focusList: List<FocusSession> = sessions.observeFocusForDay(epoch)
                .map { list -> list.filter { it.endedAt != null } }
                .first()
            val session = sessions.findForDate(epoch)
            val focusByHabit = focusList.groupBy { it.habit }
            dayDetail.value = DayDetail(
                date = date,
                focusByHabit = focusByHabit.mapValues { (_, list) ->
                    list.sumOf { (it.endedAt ?: 0L) - it.startedAt }
                },
                focusCountByHabit = focusByHabit.mapValues { (_, list) -> list.size },
                journal = session?.journalEntry,
                intentsByHabit = focusByHabit.mapValues { (_, list) ->
                    list.mapNotNull { it.intent?.takeIf { s -> s.isNotBlank() } }
                }.filterValues { it.isNotEmpty() },
            )
        }
    }

    fun closeDayPanel() {
        selectedDay.value = null
    }

    /** Habit filter — null shows all 5 columns; one habit makes the tracker a
     *  single-column timeline. */
    fun setHabitFilter(habit: Habit?) {
        habitFilter.value = habit
    }
}
