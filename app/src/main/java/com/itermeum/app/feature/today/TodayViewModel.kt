package com.itermeum.app.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itermeum.app.core.common.Clock
import com.itermeum.app.core.content.NormCatalog
import com.itermeum.app.core.data.MorningRepository
import com.itermeum.app.core.data.NormRepository
import com.itermeum.app.core.data.ProfileRepository
import com.itermeum.app.core.data.StreakRepository
import com.itermeum.app.core.data.WorkRepository
import com.itermeum.app.core.database.entity.MilestoneEntity
import com.itermeum.app.core.database.entity.StreakStateEntity
import com.itermeum.app.sync.notifications.NotificationBudget
import com.itermeum.app.sync.scheduler.DayCloseRunner
import com.itermeum.app.core.database.entity.ScriptSessionEntity
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
import kotlinx.coroutines.flow.first
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
    private val work: WorkRepository,
    private val streaks: StreakRepository,
    private val dayClose: DayCloseRunner,
    private val budget: NotificationBudget,
    private val clock: Clock,
) : ViewModel() {

    private val date: LocalDate = clock.logicalToday()

    val streakStates: StateFlow<List<StreakStateEntity>> = streaks.observeStreaks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val unseenMilestone: StateFlow<MilestoneEntity?> = streaks.observeUnseenMilestone().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val calls: StateFlow<Int> = work.observeOutreachCount(date).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val agreements: StateFlow<Int> = work.observeAgreements(date).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val banner = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val lowerBar = kotlinx.coroutines.flow.MutableStateFlow<LowerBar?>(null)

    /** Section 6.6: 3 failures in 7 days → offer to pause 1–2 non-core norms; 7 days without a counted day → one calm screen. */
    data class LowerBar(val offerPause: Boolean, val calmRestart: Boolean)

    init {
        viewModelScope.launch {
            dayClose.run()
            banner.value = budget.takeBanner()
            val fidelity = streaks.observeStreaks().first().firstOrNull { it.type == com.itermeum.app.core.database.entity.StreakType.FIDELITY }
            val recent = (1..7).map { date.minusDays(it.toLong()) }.filter { it >= (profiles.get().createdAt ?: date) }
            val failures = recent.count { !com.itermeum.app.core.engine.StreakEngine.fidelityMet(streaks.facts(it)) }
            val noneCounted = recent.size >= 7 && failures >= 7
            lowerBar.value = when {
                noneCounted -> LowerBar(offerPause = false, calmRestart = true)
                failures >= 3 && fidelity != null -> LowerBar(offerPause = true, calmRestart = false)
                else -> null
            }
        }
    }

    fun markMilestoneSeen(id: String) = viewModelScope.launch { streaks.markMilestoneSeen(id) }
    fun pauseNorm(id: String) = viewModelScope.launch { norms.pauseForWeek(id, date); lowerBar.value = null }
    fun dismissLowerBar() { lowerBar.value = null }

    val activeScript: StateFlow<ScriptSessionEntity?> = work.observeActiveScript().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val scriptSessionsToday: StateFlow<Int> = work.observeScriptCount(date).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val beforeWork: Boolean get() = clock.localTime().isBefore(java.time.LocalTime.of(12, 0))

    fun startScript() = viewModelScope.launch { work.startScript() }
    fun stopScript() = viewModelScope.launch { work.stopScript(null) }

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
