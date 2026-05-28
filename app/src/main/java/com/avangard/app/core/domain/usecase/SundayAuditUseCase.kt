package com.avangard.app.core.domain.usecase

import com.avangard.app.core.common.Clock
import com.avangard.app.core.common.DAY_MILLIS
import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.domain.model.CoreStatus
import com.avangard.app.core.domain.model.DailySession
import com.avangard.app.core.domain.model.DefectKind
import com.avangard.app.core.domain.model.FocusSession
import com.avangard.app.core.domain.model.Habit
import com.avangard.app.core.domain.model.InfraStatus
import com.avangard.app.core.domain.repository.SessionRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Aggregate of the previous 7 days (today + 6 prior) computed reactively from
 * both the daily ledger and the focus event log. A new focus session anywhere
 * in the window re-emits the view — the previous flow{} variant only sampled
 * focus_session once and went stale.
 *
 * [previous] holds the same metrics for the prior 7-day window (-14..-7
 * days). Audit screen renders delta annotations against it so the operator
 * sees whether the current week trended up or down vs the previous one.
 */
data class SundayAuditView(
    val coreHoursMillis: Long,
    val daysApproved: Int,
    val defectCount: Int,
    val wasteCount: Int,
    val mvdDays: Int,
    val infraBreakdown: Map<Habit, InfraBreakdown>,
    val virtueSums: VirtueSums,
    val previous: PreviousWeek? = null,
) {
    data class InfraBreakdown(
        val done: Int,
        val notDone: Int,
    )

    data class VirtueSums(
        val rationality: Int,
        val independence: Int,
        val honesty: Int,
        val justice: Int,
    )

    /** Snapshot of the previous week — same metrics, fed to the delta renderer. */
    data class PreviousWeek(
        val coreHoursMillis: Long,
        val daysApproved: Int,
        val defectCount: Int,
        val wasteCount: Int,
        val mvdDays: Int,
        val virtueSum: Int,
        /** True iff the previous-week window had ≥1 closed shift; gates whether
         *  deltas render at all (no comparison against an empty week). */
        val populated: Boolean,
    )

    fun isEmpty(): Boolean =
        coreHoursMillis == 0L &&
            daysApproved == 0 &&
            defectCount == 0 &&
            wasteCount == 0 &&
            mvdDays == 0 &&
            virtueSums.rationality == 0 &&
            virtueSums.independence == 0 &&
            virtueSums.honesty == 0 &&
            virtueSums.justice == 0 &&
            infraBreakdown.values.all { it.standard == 0 && it.mvd == 0 }
}

class SundayAuditUseCase @Inject constructor(
    private val repository: SessionRepository,
    private val clock: Clock,
) {
    operator fun invoke(): Flow<SundayAuditView> {
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        val weekStart = today - 6 * DAY_MILLIS
        val weekEnd = today + DAY_MILLIS - 1
        val prevWeekStart = today - 13 * DAY_MILLIS
        val prevWeekEnd = today - 7 * DAY_MILLIS + DAY_MILLIS - 1
        return combine(
            repository.observeRange(weekStart, weekEnd),
            repository.observeFocusRange(weekStart, weekEnd),
            repository.observeRange(prevWeekStart, prevWeekEnd),
            repository.observeFocusRange(prevWeekStart, prevWeekEnd),
        ) { sessions, focus, prevSessions, prevFocus ->
            buildView(sessions, focus).copy(previous = previousFor(prevSessions, prevFocus))
        }
    }

    private fun previousFor(
        sessions: List<DailySession>,
        focus: List<FocusSession>,
    ): SundayAuditView.PreviousWeek {
        val coreHours = focus
            .filter { it.habit == Habit.Generations && it.endedAt != null }
            .sumOf { (it.endedAt!! - it.startedAt) }
        val approved = sessions.count { it.coreStatus is CoreStatus.Approved }
        val defects = sessions.count {
            val cs = it.coreStatus
            cs is CoreStatus.Failed && cs.kind == DefectKind.Defect
        }
        val wastes = sessions.count {
            val cs = it.coreStatus
            cs is CoreStatus.Failed && cs.kind == DefectKind.Waste
        }
        val mvds = sessions.count {
            val cs = it.coreStatus
            cs is CoreStatus.Approved && cs.mode == com.avangard.app.core.domain.model.CoreMode.Mvd
        }
        val virtueSum = sessions.sumOf {
            val v = it.virtues
            (v?.rationality ?: 0) + (v?.independence ?: 0) +
                (v?.honesty ?: 0) + (v?.justice ?: 0)
        }
        val populated = sessions.any { it.eveningClosed } || coreHours > 0
        return SundayAuditView.PreviousWeek(
            coreHoursMillis = coreHours,
            daysApproved = approved,
            defectCount = defects,
            wasteCount = wastes,
            mvdDays = mvds,
            virtueSum = virtueSum,
            populated = populated,
        )
    }

    private fun buildView(
        weekSessions: List<DailySession>,
        focus: List<FocusSession>,
    ): SundayAuditView {
        val coreHoursMillis = focus
            .filter { it.habit == Habit.Generations && it.endedAt != null }
            .sumOf { (it.endedAt!! - it.startedAt) }

        val daysApproved = weekSessions.count { it.coreStatus is CoreStatus.Approved }
        val defectCount = weekSessions.count {
            val cs = it.coreStatus
            cs is CoreStatus.Failed && cs.kind == DefectKind.Defect
        }
        val wasteCount = weekSessions.count {
            val cs = it.coreStatus
            cs is CoreStatus.Failed && cs.kind == DefectKind.Waste
        }
        val mvdDays = weekSessions.count {
            val cs = it.coreStatus
            cs is CoreStatus.Approved && cs.mode == com.avangard.app.core.domain.model.CoreMode.Mvd
        }

        val infraBreakdown = listOf(Habit.Spanish, Habit.Sport, Habit.Watching, Habit.Reading)
            .associateWith { habit ->
                val statuses = weekSessions.map { it.infraStatus(habit) }
                SundayAuditView.InfraBreakdown(
                    done = statuses.count { it == InfraStatus.Done },
                    notDone = statuses.count { it == InfraStatus.NotDone },
                )
            }
        val virtueSums = SundayAuditView.VirtueSums(
            rationality = weekSessions.sumOf { it.virtues?.rationality ?: 0 },
            independence = weekSessions.sumOf { it.virtues?.independence ?: 0 },
            honesty = weekSessions.sumOf { it.virtues?.honesty ?: 0 },
            justice = weekSessions.sumOf { it.virtues?.justice ?: 0 },
        )
        return SundayAuditView(
            coreHoursMillis = coreHoursMillis,
            daysApproved = daysApproved,
            defectCount = defectCount,
            wasteCount = wasteCount,
            mvdDays = mvdDays,
            infraBreakdown = infraBreakdown,
            virtueSums = virtueSums,
        )
    }
}
