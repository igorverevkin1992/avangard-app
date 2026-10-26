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
import com.itermeum.app.core.content.ContentAssets
import com.itermeum.app.core.data.PathRepository
import com.itermeum.app.core.data.ReadingRepository
import com.itermeum.app.core.database.entity.MortificationLogEntity
import com.itermeum.app.core.database.entity.WorkTaskEntity
import com.itermeum.app.sync.scheduler.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.mapLatest
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
    private val path: PathRepository,
    private val reading: ReadingRepository,
    private val assets: ContentAssets,
    private val reminders: ReminderScheduler,
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

    /** Section 6.6: one or two non-core norms may be paused for 7 days. */
    private val pausedNow = MutableStateFlow(0)
    fun pauseNorm(id: String) = viewModelScope.launch {
        norms.pauseForWeek(id, date)
        pausedNow.value += 1
        if (pausedNow.value >= MAX_PAUSED) lowerBar.value = null
        reminders.rescheduleAll()
    }
    fun dismissLowerBar() { lowerBar.value = null }

    /** Section 9: the three tasks of the day, shown once work has started. */
    val tasks: StateFlow<List<WorkTaskEntity>> = work.observeTasks(date).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Section 5.9: the focus phrase chosen in the Gospel of the day. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val focusPhrase: StateFlow<String?> = reading.observeLogs(date).mapLatest { reading.focusPhrase(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Section 3.4/7.8: Friday — choose a small mortification in the morning, mark it in the evening. */
    val mortification: StateFlow<MortificationLogEntity?> = path.observeMortification(date).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val mortificationOptions: List<String> get() = assets.mortifications.items
    val isFriday: Boolean get() = date.dayOfWeek == java.time.DayOfWeek.FRIDAY
    fun chooseMortification(item: String) = viewModelScope.launch { path.chooseMortification(date, item) }
    fun completeMortification() = viewModelScope.launch { path.completeMortification(date) }

    /** Work has started (for the tasks card); re-read every minute by the screen. */
    fun workStarted(profile: ProfileEntity): Boolean = !clock.localTime().isBefore(profile.workStart)

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
            norms.observeInput(date),
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
    /**
     * Section 4: a sick day is marked before 06:00 or the evening before. Today can be
     * switched on only before 06:00 (switching off is always allowed); otherwise it is
     * set for tomorrow. Alarms are re-armed (sick wake = 08:00 without proof).
     */
    fun canSetSickToday(): Boolean = clock.localTime().let { !it.isBefore(java.time.LocalTime.of(3, 0)) && it.isBefore(java.time.LocalTime.of(6, 0)) }
    fun setSick(sick: Boolean) = viewModelScope.launch { morning.setSick(date, sick); reminders.rescheduleAll() }
    fun setSickTomorrow(sick: Boolean) = viewModelScope.launch { morning.setSick(date.plusDays(1), sick); reminders.rescheduleAll() }
    val sickTomorrow: StateFlow<Boolean> = work.observePlan(date.plusDays(1)).map { it?.sick == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Simple norms that are completed by a tap on the Today list (phase 4). */
    fun isTapCompletable(id: String): Boolean =
        id !in setOf(NormCatalog.WAKE, NormCatalog.SERVIAM, NormCatalog.QUARANTINE, NormCatalog.GYM, NormCatalog.MORTIFICATION)

    /** Non-core norms that may be paused (section 6.6). */
    fun pausable(items: List<DayScheduleEngine.ScheduledItem>): List<DayScheduleEngine.ScheduledItem> =
        items.filter { !it.isCore && it.normId !in setOf(NormCatalog.SERVIAM, NormCatalog.QUARANTINE, NormCatalog.MASS, NormCatalog.CATECHESIS, NormCatalog.CONFESSION) }

    companion object {
        const val MAX_PAUSED = 2
    }
}
