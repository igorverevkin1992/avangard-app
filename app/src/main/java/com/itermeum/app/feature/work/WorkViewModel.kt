package com.itermeum.app.feature.work

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val work: WorkRepository,
    private val norms: NormRepository,
    private val prayers: PrayerRepository,
    private val clock: Clock,
) : ViewModel() {

    val date: LocalDate = clock.logicalToday()
    private val target = MutableStateFlow(5)

    init { viewModelScope.launch { target.value = work.targetFor(date) } }

    val ui: StateFlow<WorkUi> = combine(
        work.observePlan(date), work.observeTasks(date), work.observeOutreach(date), work.observeRitualCount(date), norms.observeDoneIds(date),
    ) { plan, tasks, outreach, rituals, done ->
        WorkUi(
            date = date, plan = plan, tasks = tasks, outreach = outreach,
            attempts = outreach.size, agreements = outreach.count { it.outcome == OutreachOutcome.AGREEMENT },
            target = target.value, rituals = rituals, done = done,
            beforeCutoff = clock.localTime().isBefore(WorkRepository.CUTOFF),
            workStarted = !clock.localTime().isBefore(java.time.LocalTime.of(12, 0)),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkUi(date))

    fun planIsValid(drafts: List<WorkRepository.TaskDraft>, shootDay: Boolean) = work.planIsValid(drafts, shootDay)

    fun savePlan(drafts: List<WorkRepository.TaskDraft>, intention: String?, shootDay: Boolean, onDone: () -> Unit) {
        viewModelScope.launch { work.savePlan(date, drafts, intention, shootDay); onDone() }
    }

    fun logOutreach(person: String, role: String?, kind: OutreachKind, outcome: OutreachOutcome, agreementDate: LocalDate?) {
        viewModelScope.launch { work.logOutreach(date, person, role, null, kind, outcome, agreementDate) }
    }

    suspend fun recentPeople() = work.recentPeople()
    suspend fun beforeCallPrayer(): String = prayers.pickJaculatoria("BEFORE_CALL")?.text ?: "Господи, дай мне смелости и любви к этому человеку"
    suspend fun afterRejectionPrayer(): String = prayers.pickJaculatoria("AFTER_REJECTION")?.text ?: "Omnia in bonum"
    suspend fun ritualPrayer(): String = "Господи, благодарю за прошедшую встречу; благослови людей, к которым я сейчас иду"

    fun closeTask(task: WorkTaskEntity, checklist: List<Boolean>) = viewModelScope.launch { work.closeTask(task, checklist) }
    fun moveTask(task: WorkTaskEntity) = viewModelScope.launch { work.moveTask(task) }
    fun reopenTask(task: WorkTaskEntity) = viewModelScope.launch { work.reopenTask(task) }
    fun logRitual() = viewModelScope.launch { work.logRitual() }

    fun frogDone() = NormCatalog.FROG in ui.value.done

    companion object {
        val TYPES = TaskType.entries
    }
}
