package com.itermeum.app.feature.path

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itermeum.app.core.common.Clock
import com.itermeum.app.core.content.ContentAssets
import com.itermeum.app.core.data.NormRepository
import com.itermeum.app.core.data.PathRepository
import com.itermeum.app.core.data.ProfileRepository
import com.itermeum.app.core.data.ProjectRepository
import com.itermeum.app.core.data.SrsRepository
import com.itermeum.app.core.data.StreakRepository
import com.itermeum.app.core.data.WorkRepository
import com.itermeum.app.core.database.dao.CompletionDao
import com.itermeum.app.core.database.dao.OutreachDao
import com.itermeum.app.core.database.entity.CatechesisNoteEntity
import com.itermeum.app.core.database.entity.GoalStatus
import com.itermeum.app.core.database.entity.MilestoneEntity
import com.itermeum.app.core.database.entity.OutreachOutcome
import com.itermeum.app.core.database.entity.ProfileEntity
import com.itermeum.app.core.database.entity.QuarterGoalEntity
import com.itermeum.app.core.database.entity.StreakStateEntity
import com.itermeum.app.core.database.entity.WeeklyReviewEntity
import com.itermeum.app.sync.scheduler.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WeekStats(val calls: Int = 0, val agreements: Int = 0, val fidelityDays: Int = 0)
data class MonthStats(val fidelityDays: Int = 0, val calls: Int = 0, val agreements: Int = 0, val prayersKnown: Int = 0, val scriptMinutes: Int = 0, val catechesis: Int = 0)

@HiltViewModel
class PathViewModel @Inject constructor(
    private val path: PathRepository,
    private val streaks: StreakRepository,
    private val projects: ProjectRepository,
    private val outreachDao: OutreachDao,
    private val completionDao: CompletionDao,
    private val work: WorkRepository,
    private val srs: SrsRepository,
    private val assets: ContentAssets,
    private val norms: NormRepository,
    private val profiles: ProfileRepository,
    private val reminders: ReminderScheduler,
    private val clock: Clock,
) : ViewModel() {
    val today: LocalDate = clock.logicalToday()
    val nextClass: LocalDate = path.nextCatechesisDate()
    val weekStart: LocalDate = path.weekStart(today)
    val quarter: String = ProjectRepository.quarterOf(today)
    val yearMonth: String = "%04d-%02d".format(today.year, today.monthValue)

    val profile: StateFlow<ProfileEntity> = profiles.flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileEntity())
    val notes: StateFlow<List<CatechesisNoteEntity>> = path.observeNotes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val attended: StateFlow<Int> = path.observeAttended().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val openHomework: StateFlow<CatechesisNoteEntity?> = path.observeOpenHomework().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val weekly: StateFlow<WeeklyReviewEntity?> = path.observeWeekly(weekStart).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val streakStates: StateFlow<List<StreakStateEntity>> = streaks.observeStreaks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val milestones: StateFlow<List<MilestoneEntity>> = streaks.observeMilestones().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val goals: StateFlow<List<QuarterGoalEntity>> = projects.observeGoals(quarter).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val done: StateFlow<Set<String>> = norms.observeDoneIds(today).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _week = MutableStateFlow(WeekStats())
    val week: StateFlow<WeekStats> = _week.asStateFlow()
    private val _month = MutableStateFlow(MonthStats())
    val month: StateFlow<MonthStats> = _month.asStateFlow()
    val virtues: List<String> = assets.virtues.items

    init {
        viewModelScope.launch {
            val weekEnd = weekStart.plusDays(6)
            val outreach = outreachDao.between(weekStart, minOf(weekEnd, today))
            _week.value = WeekStats(outreach.size, outreach.count { it.outcome == OutreachOutcome.AGREEMENT }, streaks.fidelityDaysBetween(weekStart, minOf(weekEnd, today.minusDays(1))))
            val ym = YearMonth.from(today.minusMonths(if (today.dayOfMonth == 1) 1 else 0))
            val from = ym.atDay(1); val to = minOf(ym.atEndOfMonth(), today)
            val mo = outreachDao.between(from, to)
            _month.value = MonthStats(
                fidelityDays = streaks.fidelityDaysBetween(from, minOf(to, today.minusDays(1))),
                calls = mo.size, agreements = mo.count { it.outcome == OutreachOutcome.AGREEMENT },
                prayersKnown = srs.knownCount(), scriptMinutes = work.scriptMinutes(from, to),
                catechesis = completionDao.between(from, to).count { it.normId == com.itermeum.app.core.content.NormCatalog.CATECHESIS },
            )
        }
    }

    fun saveNote(date: LocalDate, topic: String, theses: String, question: String, homework: String, existingId: Long?, onDone: () -> Unit) =
        viewModelScope.launch { path.saveNote(date, topic, theses, question, homework, existingId); onDone() }
    suspend fun note(id: Long) = path.note(id)
    fun homeworkDone(n: CatechesisNoteEntity) = viewModelScope.launch { path.markHomeworkDone(n) }
    fun missedForTravel(date: LocalDate) = viewModelScope.launch { path.markCatechesisMissed(date, travel = true) }

    fun saveWeekly(plan: List<String>, virtue: String, onDone: () -> Unit) = viewModelScope.launch {
        val w = _week.value
        path.saveWeekly(weekStart, plan, virtue, w.calls, w.agreements, w.fidelityDays); onDone()
    }
    fun decryptPlan(r: WeeklyReviewEntity) = path.decryptPlan(r)

    fun markMonthViewed() = viewModelScope.launch { path.markMonthViewed(yearMonth) }

    fun setGoalStatus(g: QuarterGoalEntity, s: GoalStatus) = viewModelScope.launch { projects.setGoalStatus(g, s) }
    suspend fun vanity(): List<String>? = path.vanity(quarter)
    fun saveQuarter(answers: List<String>, income: Long?, onDone: () -> Unit) = viewModelScope.launch {
        income?.let { projects.setIncome(quarter, it) }
        path.saveQuarter(quarter, answers); onDone()
    }

    fun milestoneMeta(id: String) = streaks.milestoneMeta(id)

    /** Section 3.7: the user types the date and the word «КРЕЩЁН». Nothing switches automatically. */
    fun markBaptized(date: LocalDate, confirmation: String, onDone: () -> Unit) = viewModelScope.launch {
        if (confirmation.trim().uppercase() != "КРЕЩЁН" && confirmation.trim().uppercase() != "КРЕЩЕН") return@launch
        path.markBaptized(date)
        streaks.checkMilestones()
        reminders.rescheduleAll()
        onDone()
    }
}
