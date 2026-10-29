package com.itermeum.app.feature.work

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itermeum.app.R
import com.itermeum.app.core.common.Clock
import com.itermeum.app.core.content.NormCatalog
import com.itermeum.app.core.data.NormRepository
import com.itermeum.app.core.data.PrayerRepository
import com.itermeum.app.core.data.WorkRepository
import com.itermeum.app.core.database.entity.DayPlanEntity
import com.itermeum.app.core.database.entity.OutreachKind
import com.itermeum.app.core.database.entity.OutreachLogEntity
import com.itermeum.app.core.database.entity.OutreachOutcome
import com.itermeum.app.core.database.entity.TaskType
import com.itermeum.app.core.database.entity.WorkTaskEntity
import com.itermeum.app.core.engine.DayScheduleEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WorkUi(
    val date: LocalDate,
    val plan: DayPlanEntity? = null,
    val tasks: List<WorkTaskEntity> = emptyList(),
    val outreach: List<OutreachLogEntity> = emptyList(),
    val attempts: Int = 0,
    val agreements: Int = 0,
    val target: Int = 5,
    val rituals: Int = 0,
    val done: Set<String> = emptySet(),
    val beforeCutoff: Boolean = true,
    val workStarted: Boolean = false,
)

@HiltViewModel
class WorkViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val work: WorkRepository,
    private val norms: NormRepository,
    private val prayers: PrayerRepository,
    private val widget: com.itermeum.app.sync.widget.WidgetUpdater,
    private val clock: Clock,
    private val assets: com.itermeum.app.core.content.ContentAssets,
) : ViewModel() {

    val date: LocalDate = clock.logicalToday()
    private val target = MutableStateFlow(5)

    private val cutoff = MutableStateFlow(DayScheduleEngine.FROG_CUTOFF)

    /** Null until loaded; the plan norm counts only when saved before it. */
    val planDeadline = MutableStateFlow<java.time.LocalTime?>(null)

    init { viewModelScope.launch { target.value = work.targetFor(date); cutoff.value = work.cutoffFor(); planDeadline.value = work.planDeadline() } }

    fun planTooLate(): Boolean = planDeadline.value?.let { !clock.now().isBefore(date.atTime(it)) } ?: false

    val ui: StateFlow<WorkUi> = combine(
        work.observePlan(date), work.observeTasks(date), work.observeOutreach(date), work.observeRitualCount(date), norms.observeDoneIds(date),
    ) { plan, tasks, outreach, rituals, done ->
        WorkUi(
            date = date, plan = plan, tasks = tasks, outreach = outreach,
            attempts = outreach.size, agreements = outreach.count { it.outcome == OutreachOutcome.AGREEMENT },
            target = target.value, rituals = rituals, done = done,
            beforeCutoff = clock.now().isBefore(date.atTime(cutoff.value)),
            workStarted = !clock.localTime().isBefore(java.time.LocalTime.of(12, 0)),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkUi(date))

    /** Section 5.5: «Поработал над сценарием» before work (moved here from Today, D-33). */
    val activeScript: StateFlow<com.itermeum.app.core.database.entity.ScriptSessionEntity?> =
        work.observeActiveScript().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val scriptSessionsToday: StateFlow<Int> = work.observeScriptCount(date).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    fun beforeWork(): Boolean = clock.localTime().isBefore(java.time.LocalTime.of(12, 0))
    fun startScript() = viewModelScope.launch { work.startScript() }
    fun stopScript() = viewModelScope.launch { work.stopScript(null) }

    fun planIsValid(drafts: List<WorkRepository.TaskDraft>, shootDay: Boolean) = work.planIsValid(drafts, shootDay)

    fun makeNext(task: com.itermeum.app.core.database.entity.WorkTaskEntity) {
        viewModelScope.launch { work.makeNext(task); widget.requestUpdate() }
    }

    fun savePlan(drafts: List<WorkRepository.TaskDraft>, intention: String?, shootDay: Boolean, onDone: () -> Unit) {
        viewModelScope.launch { work.savePlan(date, drafts, intention, shootDay); widget.requestUpdate(); onDone() }
    }

    fun logOutreach(person: String, role: String?, kind: OutreachKind, outcome: OutreachOutcome, agreementDate: LocalDate?) {
        viewModelScope.launch { work.logOutreach(date, person, role, null, kind, outcome, agreementDate); widget.requestUpdate() }
    }

    suspend fun recentPeople() = work.recentPeople()
    suspend fun beforeCallPrayer(): String = prayers.pickJaculatoria("BEFORE_CALL")?.text ?: context.getString(R.string.jaculatoria_fallback_call)
    /** Section 5.4 ritual prayer, kept in resources (content texts carry their own sources). */
    /**
     * D-52: a random Opus Dei jaculatory — Latin, then Russian — never the one
     * shown last time.
     */
    fun ritualPrayer(): Pair<String, String> {
        val list = assets.jaculatoria.opusDei
        if (list.isEmpty()) return context.getString(R.string.ritual_prayer) to ""
        val prefs = context.getSharedPreferences("ritual", android.content.Context.MODE_PRIVATE)
        val last = prefs.getInt("last", -1)
        val pick = (list.indices - last).random()
        prefs.edit().putInt("last", pick).apply()
        return list[pick].latin to list[pick].text
    }
    suspend fun afterRejectionPrayer(): String = prayers.pickJaculatoria("AFTER_REJECTION")?.text ?: "Omnia in bonum"

    fun closeTask(task: WorkTaskEntity, checklist: List<Boolean>) = viewModelScope.launch { work.closeTask(task, checklist); widget.requestUpdate() }
    fun moveTask(task: WorkTaskEntity) = viewModelScope.launch { work.moveTask(task); widget.requestUpdate() }
    fun reopenTask(task: WorkTaskEntity) = viewModelScope.launch { work.reopenTask(task); widget.requestUpdate() }
    fun logRitual() = viewModelScope.launch { work.logRitual() }

    fun frogDone() = NormCatalog.FROG in ui.value.done

    companion object {
        val TYPES = TaskType.entries
    }
}
