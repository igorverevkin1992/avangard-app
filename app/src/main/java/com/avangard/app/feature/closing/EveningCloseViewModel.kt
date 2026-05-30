package com.avangard.app.feature.closing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.avangard.app.core.common.Clock
import com.avangard.app.core.common.DAY_MILLIS
import com.avangard.app.core.common.DomainResult
import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.domain.model.Bottleneck
import com.avangard.app.core.domain.model.CoreMode
import com.avangard.app.core.domain.model.CoreStatus
import com.avangard.app.core.domain.model.DailySession
import com.avangard.app.core.domain.model.DefectKind
import com.avangard.app.core.domain.model.FocusSession
import com.avangard.app.core.domain.model.SessionError
import com.avangard.app.core.domain.model.VirtueScores
import com.avangard.app.core.domain.repository.SessionRepository
import com.avangard.app.core.domain.usecase.CloseEveningUseCase
import com.avangard.app.core.domain.usecase.ObserveDailySessionUseCase
import com.avangard.app.core.domain.usecase.SetJournalUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

enum class Virtue { Rationality, Independence, Honesty, Justice }

data class EveningCloseState(
    val coreApproved: Boolean = false,
    val coreFailed: Boolean = false,
    val rationality: Int = 0,
    val independence: Int = 0,
    val honesty: Int = 0,
    val justice: Int = 0,
    val defectKind: DefectKind? = null,
    val journalDraft: String = "",
    val journalLoaded: Boolean = false,
    val submitting: Boolean = false,
    val error: SessionError? = null,
    /** Most recent bottleneck set in past audits — surfaced above the journal
     *  field as a passive reminder of the operator's current focus theme. */
    val priorBottleneck: Bottleneck? = null,
    /** Virtues the day's metrics suggest as +1; the picker still requires an
     *  explicit tap. Empty means no clear signal one way or the other. */
    val suggestedVirtuesUp: Set<Virtue> = emptySet(),
    /** Virtues suggested as −1 (e.g. integrity if a session was force-ended). */
    val suggestedVirtuesDown: Set<Virtue> = emptySet(),
) {
    /** Idle core demands operator to pick Defect or Waste before closing. */
    val needsDefectKind: Boolean get() = !coreApproved && !coreFailed
    val productivityOk: Boolean get() = coreApproved
    val prideOk: Boolean get() = coreApproved
    val integrityOk: Boolean get() = !coreFailed

    val journalCharCount: Int get() = journalDraft.length
    val journalLimit: Int get() = DailySession.JOURNAL_MAX_CHARS
    val journalOverLimit: Boolean get() = journalCharCount > journalLimit

    val canSubmit: Boolean
        get() = !submitting &&
            !journalOverLimit &&
            (!needsDefectKind || defectKind != null)
}

sealed interface EveningCloseEffect {
    data object Closed : EveningCloseEffect
}

@HiltViewModel
class EveningCloseViewModel @Inject constructor(
    observeSession: ObserveDailySessionUseCase,
    private val closeEvening: CloseEveningUseCase,
    private val setJournal: SetJournalUseCase,
    sessions: SessionRepository,
    clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(EveningCloseState())
    val state: StateFlow<EveningCloseState> = _state.asStateFlow()

    private val _effects = Channel<EveningCloseEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        viewModelScope.launch {
            combine(
                observeSession(),
                // Two-week look-back to find the most recent populated bottleneck
                // — defends against weeks the operator skipped the Sunday audit.
                sessions.observeRange(today - 13 * DAY_MILLIS, today - 1),
                sessions.observeFocusForDay(today),
            ) { session, prior, focus ->
                Triple(session, prior, focus)
            }.collect { (session, priorRows, focusList) ->
                val current = _state.value
                val priorBottleneck = priorRows.asReversed()
                    .firstOrNull { it.bottleneckForNextWeek != null }
                    ?.bottleneckForNextWeek
                val suggestedUp = suggestVirtuesUp(session, focusList)
                val suggestedDown = suggestVirtuesDown(focusList)
                _state.value = current.copy(
                    coreApproved = session.coreStatus is CoreStatus.Approved,
                    coreFailed = session.coreStatus is CoreStatus.Failed,
                    journalDraft = if (!current.journalLoaded) {
                        session.journalEntry.orEmpty()
                    } else current.journalDraft,
                    journalLoaded = true,
                    priorBottleneck = priorBottleneck,
                    suggestedVirtuesUp = suggestedUp,
                    suggestedVirtuesDown = suggestedDown,
                )
            }
        }
    }

    private fun suggestVirtuesUp(
        session: DailySession,
        focusList: List<FocusSession>,
    ): Set<Virtue> {
        val out = mutableSetOf<Virtue>()
        val completed = focusList.count { it.endedAt != null }
        val core = session.coreStatus
        if (completed >= 2 && core is CoreStatus.Approved && session.dayMode == CoreMode.Standard) {
            out += Virtue.Rationality
        }
        // ≥3 completed sessions across any habits with Core not failed signals
        // sustained independence (no external prod to stop).
        if (completed >= 3 && core !is CoreStatus.Failed) {
            out += Virtue.Independence
        }
        return out
    }

    private fun suggestVirtuesDown(focusList: List<FocusSession>): Set<Virtue> {
        // A zero-duration row (start = end) marks a session that was force-ended
        // by either the orphan cleanup or the Core-approve handler — both
        // signal a broken integrity chain on the day. One is noise; ≥2 hints
        // at a pattern.
        val zeroDuration = focusList.count {
            it.endedAt != null && (it.endedAt!! - it.startedAt) <= 1
        }
        return if (zeroDuration >= 2) setOf(Virtue.Honesty) else emptySet()
    }

    fun onVirtueChange(virtue: Virtue, value: Int) {
        val clamped = value.coerceIn(-1, 1)
        _state.value = when (virtue) {
            Virtue.Rationality -> _state.value.copy(rationality = clamped)
            Virtue.Independence -> _state.value.copy(independence = clamped)
            Virtue.Honesty -> _state.value.copy(honesty = clamped)
            Virtue.Justice -> _state.value.copy(justice = clamped)
        }
    }

    fun onDefectKindChange(kind: DefectKind?) {
        _state.value = _state.value.copy(defectKind = kind, error = null)
    }

    fun onJournalChange(text: String) {
        // Hard-clip at the limit so the textarea can't accept the 501st
        // character. SetJournalUseCase still re-checks defensively.
        val clipped = if (text.length > DailySession.JOURNAL_MAX_CHARS) {
            text.substring(0, DailySession.JOURNAL_MAX_CHARS)
        } else text
        _state.value = _state.value.copy(journalDraft = clipped)
    }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.value = current.copy(submitting = true, error = null)
        viewModelScope.launch {
            val virtues = VirtueScores(
                rationality = current.rationality,
                independence = current.independence,
                honesty = current.honesty,
                justice = current.justice,
            )
            val defect = if (current.needsDefectKind) current.defectKind else null
            // Journal first — it's recoverable on its own, doesn't gate the
            // close. The clipped draft is already at-or-below the limit, so
            // SetJournalUseCase will succeed.
            setJournal(current.journalDraft)
            when (val result = closeEvening(virtues = virtues, defectKindWhenIdle = defect)) {
                is DomainResult.Ok -> {
                    _state.value = current.copy(submitting = false)
                    _effects.send(EveningCloseEffect.Closed)
                }
                is DomainResult.Err -> {
                    _state.value = current.copy(submitting = false, error = result.error)
                }
            }
        }
    }
}
