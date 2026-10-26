package com.itermeum.app.feature.examen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itermeum.app.core.common.Clock
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

enum class ExamenStep { CLOSED, FOLLOW_UP, PRESENCE, GRATITUDE, AUDIT, CONTRITION, PROPOSITO, AVE, WHAT_BROKE, DONE }

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
)

/** Section 5.12 wizard: [FOLLOW_UP of yesterday's resolution] → PRESENCE → GRATITUDE → AUDIT(1..5) → CONTRITION → PROPOSITO → AVE(3) → [WHAT_BROKE] → DONE. */
@HiltViewModel
class ExamenViewModel @Inject constructor(
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
            _ui.update {
                it.copy(
                    date = date, questions = questions, presenceText = presence, contritionText = contrition, aveText = ave, previous = previous,
                    hints = hintsFor(date), needsWhatBroke = needsWhatBroke, alreadyDone = existing?.completedAt != null,
                    audit = questions.map { ExamenRepository.Audit(ExamenRepository.Answer.PARTLY, "") },
                    // Yesterday's resolution is asked first (section 5.12: «Исполнил? Да/Нет»).
                    step = when {
                        existing?.completedAt != null -> ExamenStep.DONE
                        previous != null -> ExamenStep.FOLLOW_UP
                        else -> ExamenStep.PRESENCE
                    },
                    pinRequired = profiles.get().examenPinEnabled,
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
            if (tasks.isEmpty()) add("план дня не сохранён")
            if (open > 0) add("открытых задач: $open")
            if (debt > 0) add("закрыто с долгом: $debt")
            if (moved > 0) add("перенесено: $moved")
            if (attempts < target) add("звонков $attempts из $target")
        }
        if (work1.isNotEmpty()) m[1] = work1.joinToString("; ")
        val unity = buildList {
            if (NormCatalog.ANGELUS !in done) add("Ангел Господень не отмечен")
            if (NormCatalog.JACULATORIA !in done) add("молитв-стрел меньше трёх")
            if (NormCatalog.MENTAL_PRAYER !in done) add("ментальной молитвы не было")
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
            is ExamenValidator.Result.Ok -> { _ui.update { it.copy(step = ExamenStep.AVE, propositoError = null) }; persistDraft() }
            is ExamenValidator.Result.Rejected -> _ui.update { it.copy(propositoError = r.reason) }
        }
    }

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
