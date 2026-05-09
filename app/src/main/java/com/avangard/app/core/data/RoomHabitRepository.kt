package com.avangard.app.core.data

import com.avangard.app.core.common.Clock
import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.database.dao.HabitLogDao
import com.avangard.app.core.database.entity.HabitLogEntity
import com.avangard.app.core.domain.model.Habit
import com.avangard.app.core.domain.model.HabitMonthlyView
import com.avangard.app.core.domain.repository.HabitRepository
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomHabitRepository @Inject constructor(
    private val dao: HabitLogDao,
    private val clock: Clock,
) : HabitRepository {

    override fun observeMonth(year: Int, month: Int): Flow<HabitMonthlyView> {
        val zone = clock.zone()
        val ym = YearMonth.of(year, month)
        val first = ym.atDay(1)
        val from = first.toStartOfDayEpoch(zone)
        val to = ym.atEndOfMonth().toStartOfDayEpoch(zone) + DAY_MILLIS - 1
        return dao.observeRange(from, to).map { rows ->
            val cells = buildMonthlyCells(rows, zone)
            HabitMonthlyView(
                year = year,
                month = month,
                daysInMonth = ym.lengthOfMonth(),
                cells = cells,
            )
        }
    }

    override suspend fun toggle(date: LocalDate, habit: Habit, recordedAt: Long) {
        val epoch = date.toStartOfDayEpoch(clock.zone())
        if (dao.exists(epoch, habit.code) != null) {
            dao.delete(epoch, habit.code)
        } else {
            dao.insert(
                HabitLogEntity(
                    dateEpoch = epoch,
                    habitCode = habit.code,
                    completedAt = recordedAt,
                )
            )
        }
    }

    override suspend fun wipe() {
        dao.deleteAll()
    }

    private fun buildMonthlyCells(
        rows: List<HabitLogEntity>,
        zone: ZoneId,
    ): Map<LocalDate, Set<Habit>> {
        val grouped = mutableMapOf<LocalDate, MutableSet<Habit>>()
        for (row in rows) {
            val habit = Habit.byCode(row.habitCode) ?: continue
            val date = java.time.Instant.ofEpochMilli(row.dateEpoch)
                .atZone(zone)
                .toLocalDate()
            grouped.getOrPut(date) { mutableSetOf() }.add(habit)
        }
        return grouped
    }

    companion object {
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
