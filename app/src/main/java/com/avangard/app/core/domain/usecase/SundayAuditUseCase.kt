package com.avangard.app.core.domain.usecase

import com.avangard.app.core.common.Clock
import com.avangard.app.core.common.DAY_MILLIS
import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.domain.model.CoreStatus
import com.avangard.app.core.domain.model.DailySession
import com.avangard.app.core.domain.model.DefectKind
import com.avangard.app.core.domain.model.Habit
import com.avangard.app.core.domain.model.InfraStatus
import com.avangard.app.core.domain.repository.SessionRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Aggregate of the previous 7 days (Sun ↔ Sat) including today, computed when
 * the operator opens the Sunday audit.
 */
data class SundayAuditView(
    val coreHoursMillis: Long,
    val daysApproved: Int,
    val defectCount: Int,
    val wasteCount: Int,
    val mvdDays: Int,
    val infraBreakdown: Map<Habit, InfraBreakdown>,
    val virtueSums: VirtueSums,
) {
    data class InfraBreakdown(
        val standard: Int,
        val mvd: Int,
        val notDone: Int,
    )

    data class VirtueSums(
        val rationality: Int,
        val independence: Int,
        val honesty: Int,
        val justice: Int,
    )
}

class SundayAuditUseCase @Inject constructor(
    private val repository: SessionRepository,
    private val clock: Clock,
) {
    operator fun invoke(): Flow<SundayAuditView> {
        val today = clock.today().toStartOfDayEpoch(clock.zone())
        val weekStart = today - 6 * DAY_MILLIS
        val sessions = repository.observeRange(weekStart, today + DAY_MILLIS - 1)
        val perHabitFlows: List<Flow<Pair<Habit, Long>>> = Habit.entries
            .filter { it == Habit.Generations }
            .map { habit ->
                kotlinx.coroutines.flow.flow {
                    var total = 0L
                    var cursor = weekStart
                    while (cursor <= today) {
                        total += repository.sumFocusDurationFor(cursor, habit)
                        cursor += DAY_MILLIS
                    }
                    emit(habit to total)
                }
            }
        return combine(sessions, perHabitFlows.first()) { weekSessions, (_, coreMillis) ->
            buildView(weekSessions, coreMillis)
        }
    }

    private fun buildView(weekSessions: List<DailySession>, coreMillis: Long): SundayAuditView {
        val daysApproved = weekSessions.count { it.coreStatus is CoreStatus.Approved }
        val defectCount = weekSessions.count {
            val cs = it.coreStatus
            cs is CoreStatus.Failed && cs.kind == DefectKind.Defect
        }
        val wasteCount = weekSessions.count {
            val cs = it.coreStatus
            cs is CoreStatus.Failed && cs.kind == DefectKind.Waste
        }
        val mvdDays = weekSessions.count { it.mvdActive }

        val infraBreakdown = listOf(Habit.Spanish, Habit.Sport, Habit.Watching, Habit.Reading)
            .associateWith { habit ->
                val statuses = weekSessions.map { it.infraStatus(habit) }
                SundayAuditView.InfraBreakdown(
                    standard = statuses.count { it == InfraStatus.Standard },
                    mvd = statuses.count { it == InfraStatus.Mvd },
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
            coreHoursMillis = coreMillis,
            daysApproved = daysApproved,
            defectCount = defectCount,
            wasteCount = wasteCount,
            mvdDays = mvdDays,
            infraBreakdown = infraBreakdown,
            virtueSums = virtueSums,
        )
    }
}
