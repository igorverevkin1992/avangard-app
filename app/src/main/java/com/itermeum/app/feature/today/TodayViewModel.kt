package com.itermeum.app.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itermeum.app.core.common.Clock
import com.itermeum.app.core.content.NormCatalog
import com.itermeum.app.core.data.MorningRepository
import com.itermeum.app.core.data.NormRepository
import com.itermeum.app.core.data.ProfileRepository
import com.itermeum.app.core.database.entity.MorningStateEntity
import com.itermeum.app.core.database.entity.NormEntity
import com.itermeum.app.core.database.entity.ProfileEntity
import com.itermeum.app.core.engine.DayScheduleEngine
import com.itermeum.app.core.liturgy.LiturgicalCalendar
import com.itermeum.app.core.liturgy.LiturgicalDay
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TodayUi(
    val date: LocalDate,
    val liturgical: LiturgicalDay? = null,
    val profile: ProfileEntity = ProfileEntity(),
    val morning: MorningStateEntity? = null,
    val items: List<DayScheduleEngine.ScheduledItem> = emptyList(),
    val norms: Map<String, NormEntity> = emptyMap(),
    val done: Set<String> = emptySet(),
    val next: DayScheduleEngine.ScheduledItem? = null,
    val sick: Boolean = false,
    val travel: Boolean = false,
    val loaded: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val norms: NormRepository,
    private val morning: MorningRepository,
    private val profiles: ProfileRepository,
    private val calendar: LiturgicalCalendar,
    private val clock: Clock,
) : ViewModel() {

    private val date: LocalDate = clock.logicalToday()

    val ui: StateFlow<TodayUi> = profiles.flow.flatMapLatest { profile ->
        combine(
            norms.observeNorms(),
            norms.observeDoneIds(date),
            morning.observe(date),
            flow { emit(norms.inputFor(date)) },
        ) { normList, done, morningState, input ->
            val items = DayScheduleEngine.schedule(input.copy(profile = profile, norms = normList))
            TodayUi(
                date = date,
                liturgical = calendar.day(date),
                profile = profile,
                morning = morningState,
                items = items,
                norms = normList.associateBy { it.id },
                done = done,
                next = DayScheduleEngine.nextItem(items, clock.now(), done),
                sick = input.sick,
                travel = input.travel,
                loaded = true,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUi(date))

    fun toggle(normId: String) {
        viewModelScope.launch {
            if (norms.isDone(normId, date)) norms.unmark(normId, date) else norms.markDone(normId, date)
        }
    }

    fun answerQuarantine(kept: Boolean) = viewModelScope.launch { morning.answerQuarantine(date, kept) }
    fun markGym(home: Boolean) = viewModelScope.launch { morning.markGym(date, home) }
    fun setSick(sick: Boolean) = viewModelScope.launch { morning.setSick(date, sick) }

    /** Simple norms that are completed by a tap on the Today list (phase 4). */
    fun isTapCompletable(id: String): Boolean = id !in setOf(NormCatalog.WAKE, NormCatalog.SERVIAM, NormCatalog.QUARANTINE, NormCatalog.GYM)
}
