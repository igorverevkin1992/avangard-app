package com.itermeum.app.feature.examen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itermeum.app.core.common.Clock
import com.itermeum.app.R
import com.itermeum.app.core.content.ContentAssets
import com.itermeum.app.core.content.ExamenQuestionJson
import com.itermeum.app.core.content.NormCatalog
import com.itermeum.app.core.data.DayFlags
import com.itermeum.app.core.data.ExamenRepository
import com.itermeum.app.core.data.NormRepository
import com.itermeum.app.core.data.PrayerRepository
import com.itermeum.app.core.data.ProfileRepository
import com.itermeum.app.core.data.WorkRepository
import com.itermeum.app.core.database.dao.OutreachDao
import com.itermeum.app.core.database.dao.StreakDao
import com.itermeum.app.core.database.dao.TaskDao
import com.itermeum.app.core.database.entity.FollowUpAnswer
import com.itermeum.app.core.database.entity.StreakType
import com.itermeum.app.core.database.entity.TaskStatus
import com.itermeum.app.core.database.entity.TaskType
import com.itermeum.app.core.database.entity.OutreachOutcome
import com.itermeum.app.core.database.entity.OutreachKind
import com.itermeum.app.core.engine.ExamenValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ExamenStep { CLOSED, CATCH_UP, FOLLOW_UP, PRESENCE, GRATITUDE, AUDIT, CONTRITION, PROPOSITO, PLAN_TOMORROW, AVE, WHAT_BROKE, DONE }

data class ExamenUi(
    val date: LocalDate? = null,
    val step: ExamenStep = ExamenStep.PRESENCE,
    val presenceText: String = "",
    val presenceSecondsLeft: Int = 30,
    val gratitude: List<String> = listOf("", "", ""),
    val questions: List<ExamenQuestionJson> = emptyList(),
    val auditIndex: Int = 0,
    val audit: List<ExamenRepository.Audit> = emptyList(),
    val hints: Map<Int, String> = emptyMap(),
    val contritionText: String = "",
    val contritionSaid: Boolean = false,
    val previous: Pair<LocalDate, String>? = null,
    val previousAnswer: FollowUpAnswer? = null,
    val proposito: String = "",
    val propositoError: ExamenValidator.Reason? = null,
    val aveText: String = "",
    val aveCount: Int = 0,
    val needsWhatBroke: Boolean = false,
    val whatBroke: String = "",
    val alreadyDone: Boolean = false,
    /** Settings: the Examen opens only after the phone's PIN / biometrics. */
    val pinRequired: Boolean = false,
    /** D-34 evening catch-up: what really happened today (minimums = what was logged). */
    val heroesLogged: Int = 0,
    val agreementsLogged: Int = 0,
    val tasksDoneLogged: Int = 0,
    val tasksTotal: Int = 0,
    val heroes: Int = 0,
    val agreements: Int = 0,
    val tasksDone: Int = 0,
    /** D-34: tomorrow is a working day without a plan yet. */
    val planTomorrow: Boolean = false,
    val tomorrowTitles: List<String> = listOf("", "", ""),
)

/** Section 5.12 wizard: [FOLLOW_UP of yesterday's resolution] → PRESENCE → GRATITUDE → AUDIT(1..5) → CONTRITION → PROPOSITO → AVE(3) → [WHAT_BROKE] → DONE. */
@HiltViewModel
class ExamenViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val examen: ExamenRepository,
    private val prayers: PrayerRepository,
    private val assets: ContentAssets,
    private val norms: NormRepository,
    private val taskDao: TaskDao,
    private val outreachDao: OutreachDao,
    private val streakDao: StreakDao,
    private val work: WorkRepository,
    private val flags: DayFlags,
    private val profiles: ProfileRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _ui = MutableStateFlow(ExamenUi())
    val ui: StateFlow<ExamenUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val date = examen.targetDate()
            if (date == null) { _ui.update { it.copy(step = ExamenStep.CLOSED) }; return@launch }
            val existing = examen.get(date)
            val questions = assets.examenQuestions.items
            val presence = prayers.get("presence")?.let { prayers.displayText(it) } ?: ""
            val contrition = prayers.get("contrition_short")?.let { prayers.displayText(it) } ?: ""
            val ave = prayers.get("hail_mary")?.let { prayers.displayText(it) } ?: ""
            val previous = examen.previousProposito(date)
            // Set by the day close when yesterday's fidelity was frozen or broken.
            val needsWhatBroke = flags.whatBroke(date.minusDays(1))
            val workDay = norms.scheduleFor(date).any { it.normId == NormCatalog.FROG }
            val tasks = taskDao.forDate(date)
            val heroes0 = outreachDao.countFor(date)
            val agreements0 = outreachDao.observeAgreements(date).first()
            val tasksDone0 = tasks.count { it.status != TaskStatus.OPEN }
            val tomorrow = date.plusDays(1)
            val planTomorrow = norms.scheduleFor(tomorrow).any { it.normId == NormCatalog.DAY_PLAN } && work.tasksFor(tomorrow).isEmpty()
            val target = work.targetFor(tomorrow)
            _ui.update {
                it.copy(
                    date = date, questions = questions, presenceText = presence, contritionText = contrition, aveText = ave, previous = previous,
                    hints = hintsFor(date), needsWhatBroke = needsWhatBroke, alreadyDone = existing?.completedAt != null,
                    audit = questions.map { ExamenRepository.Audit(ExamenRepository.Answer.PARTLY, "") },
                    // Yesterday's resolution is asked first (section 5.12: «Исполнил? Да/Нет»).
                    step = when {
                        existing?.completedAt != null -> ExamenStep.DONE
                        // A working day: first a one-minute catch-up of what really happened (D-34).
                        workDay -> ExamenStep.CATCH_UP
                        previous != null -> ExamenStep.FOLLOW_UP
                        else -> ExamenStep.PRESENCE
                    },
                    pinRequired = profiles.get().examenPinEnabled,
                    heroesLogged = heroes0, agreementsLogged = agreements0, tasksDoneLogged = tasksDone0, tasksTotal = tasks.size,
                    heroes = heroes0, agreements = agreements0, tasksDone = tasksDone0,
                    planTomorrow = planTomorrow,
                    tomorrowTitles = listOf(context.getString(R.string.examen_tomorrow_heroes, target), "", ""),
                )
            }
            if (_ui.value.step == ExamenStep.PRESENCE) startPresenceCountdown()
        }
    }

    /** Auto-hints from the day's data: pure functions of what the day recorded (section 11.4). */
    private suspend fun hintsFor(date: LocalDate): Map<Int, String> {
        val tasks = taskDao.forDate(date)
        val debt = tasks.count { it.status == TaskStatus.DONE_WITH_DEBT }
        val moved = tasks.count { it.status == TaskStatus.MOVED || it.movedAfterCutoff }
        val open = tasks.count { it.status == TaskStatus.OPEN }
        val attempts = outreachDao.countFor(date)
        val target = work.targetFor(date)
        val done = norms.observeDoneIds(date).first()
        val m = mutableMapOf<Int, String>()
        val work1 = buildList {
            if (tasks.isEmpty()) add(context.getString(R.string.examen_hint_no_plan))
            if (open > 0) add(context.getString(R.string.examen_hint_open, open))
            if (debt > 0) add(context.getString(R.string.examen_hint_debt, debt))
            if (moved > 0) add(context.getString(R.string.examen_hint_moved, moved))
            if (attempts < target) add(context.getString(R.string.examen_hint_calls, attempts, target))
        }
        if (work1.isNotEmpty()) m[1] = work1.joinToString("; ")
        val unity = buildList {
            if (NormCatalog.ANGELUS !in done) add(context.getString(R.string.examen_hint_angelus))
            if (NormCatalog.JACULATORIA !in done) add("молитв-стрел меньше трёх")
            if (NormCatalog.MENTAL_PRAYER !in done) add(context.getString(R.string.examen_hint_mental))
        }
        if (unity.isNotEmpty()) m[5] = unity.joinToString("; ")
        return m
    }

    private fun startPresenceCountdown() = viewModelScope.launch {
        while (_ui.value.step == ExamenStep.PRESENCE && _ui.value.presenceSecondsLeft > 0) {
            kotlinx.coroutines.delay(1_000)
            _ui.update { it.copy(presenceSecondsLeft = it.presenceSecondsLeft - 1) }
        }
    }

    fun next() = _ui.update { s ->
        when (s.step) {
            ExamenStep.FOLLOW_UP -> if (s.previousAnswer != null) s.copy(step = ExamenStep.PRESENCE) else s
            ExamenStep.PRESENCE -> s.copy(step = ExamenStep.GRATITUDE)
            ExamenStep.GRATITUDE -> s.copy(step = ExamenStep.AUDIT, auditIndex = 0)
            ExamenStep.AUDIT -> if (s.auditIndex + 1 < s.questions.size) s.copy(auditIndex = s.auditIndex + 1) else s.copy(step = ExamenStep.CONTRITION)
            ExamenStep.CONTRITION -> s.copy(step = ExamenStep.PROPOSITO, contritionSaid = true)
            ExamenStep.PROPOSITO -> s
            ExamenStep.AVE -> s
            ExamenStep.WHAT_BROKE -> s
            else -> s
        }
    }.also {
        persistDraft()
        if (_ui.value.step == ExamenStep.PRESENCE && _ui.value.presenceSecondsLeft == 30) startPresenceCountdown()
    }

    fun setGratitude(i: Int, v: String) = _ui.update { it.copy(gratitude = it.gratitude.toMutableList().also { l -> l[i] = v }) }
    fun setAudit(answer: ExamenRepository.Answer, note: String) = _ui.update { s ->
        s.copy(audit = s.audit.toMutableList().also { it[s.auditIndex] = ExamenRepository.Audit(answer, note) })
    }
    fun setPreviousAnswer(a: FollowUpAnswer) = _ui.update { it.copy(previousAnswer = a) }
    fun setProposito(v: String) = _ui.update { it.copy(proposito = v, propositoError = null) }

    fun submitProposito() {
        val s = _ui.value
        when (val r = ExamenValidator.validateProposito(s.proposito)) {
            is ExamenValidator.Result.Ok -> {
                _ui.update { it.copy(step = if (it.planTomorrow) ExamenStep.PLAN_TOMORROW else ExamenStep.AVE, propositoError = null) }
                persistDraft()
            }
            is ExamenValidator.Result.Rejected -> _ui.update { it.copy(propositoError = r.reason) }
        }
    }

    // ---- D-34 evening catch-up

    fun setHeroes(n: Int) = _ui.update { it.copy(heroes = n.coerceAtLeast(it.heroesLogged), agreements = it.agreements.coerceAtMost(n.coerceAtLeast(it.heroesLogged))) }
    fun setAgreements(n: Int) = _ui.update { it.copy(agreements = n.coerceIn(it.agreementsLogged, it.heroes)) }
    fun setTasksDone(n: Int) = _ui.update { it.copy(tasksDone = n.coerceIn(it.tasksDoneLogged, it.tasksTotal)) }

    /**
     * Records the difference as after-cutoff attempts and late-closed tasks: it goes
     * into the statistics and the audit hints, never into the streaks.
     */
    fun saveCatchUp() = viewModelScope.launch {
        val s = _ui.value
        val date = s.date ?: return@launch
        val extraAgreements = (s.agreements - s.agreementsLogged).coerceAtLeast(0)
        val extraOther = (s.heroes - s.heroesLogged - extraAgreements).coerceAtLeast(0)
        val label = context.getString(R.string.examen_catchup_label)
        repeat(extraAgreements) { work.logOutreach(date, label, null, null, OutreachKind.CALL, OutreachOutcome.AGREEMENT, null) }
        repeat(extraOther) { work.logOutreach(date, label, null, null, OutreachKind.CALL, OutreachOutcome.REACHED, null) }
        work.markTasksDoneLate(date, (s.tasksDone - s.tasksDoneLogged).coerceAtLeast(0))
        _ui.update { it.copy(hints = hintsFor(date)) }
        leaveCatchUp()
    }

    fun skipCatchUp() = leaveCatchUp()

    private fun leaveCatchUp() {
        _ui.update { it.copy(step = if (it.previous != null) ExamenStep.FOLLOW_UP else ExamenStep.PRESENCE) }
        if (_ui.value.step == ExamenStep.PRESENCE) startPresenceCountdown()
    }

    // ---- D-34 tomorrow's plan, made the evening before

    fun setTomorrowTitle(i: Int, v: String) = _ui.update { it.copy(tomorrowTitles = it.tomorrowTitles.toMutableList().also { l -> l[i] = v }) }

    fun saveTomorrowPlan() = viewModelScope.launch {
        val s = _ui.value
        val tomorrow = s.date?.plusDays(1) ?: return@launch
        val drafts = s.tomorrowTitles.mapIndexedNotNull { i, t ->
            t.trim().takeIf { it.isNotEmpty() }?.let { WorkRepository.TaskDraft(i + 1, if (i == 0) TaskType.OUTREACH else TaskType.OPS, it) }
        }
        if (work.planIsValid(drafts, shootDay = false)) work.savePlan(tomorrow, drafts, null, false)
        _ui.update { it.copy(step = ExamenStep.AVE, planTomorrow = false) }
    }

    fun skipTomorrowPlan() = _ui.update { it.copy(step = ExamenStep.AVE) }

    fun tapAve() {
        val s = _ui.value
        val n = (s.aveCount + 1).coerceAtMost(3)
        _ui.update { it.copy(aveCount = n) }
        if (n == 3) {
            if (s.needsWhatBroke) _ui.update { it.copy(step = ExamenStep.WHAT_BROKE) } else finish()
        }
    }

    fun setWhatBroke(v: String) = _ui.update { it.copy(whatBroke = v) }

    fun finishWhatBroke() { if (_ui.value.whatBroke.isNotBlank()) finish() }

    private fun finish() = viewModelScope.launch {
        val s = _ui.value
        val date = s.date ?: return@launch
        examen.saveDraft(date, s.gratitude.filter { it.isNotBlank() }, s.audit, true, s.proposito.trim(), s.previousAnswer, 3, s.whatBroke)
        examen.complete(date)
        if (s.needsWhatBroke) flags.clearWhatBroke()
        _ui.update { it.copy(step = ExamenStep.DONE, alreadyDone = true) }
    }

    private fun persistDraft() = viewModelScope.launch {
        val s = _ui.value
        val date = s.date ?: return@launch
        examen.saveDraft(date, s.gratitude.filter { it.isNotBlank() }, s.audit, s.contritionSaid, s.proposito.takeIf { it.isNotBlank() }, s.previousAnswer, s.aveCount, s.whatBroke)
    }
}
