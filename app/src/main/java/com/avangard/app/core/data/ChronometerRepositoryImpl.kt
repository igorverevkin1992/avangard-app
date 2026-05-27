package com.avangard.app.core.data

import com.avangard.app.core.common.Clock
import com.avangard.app.core.database.dao.DailySessionDao
import com.avangard.app.core.database.entity.DailySessionEntity
import com.avangard.app.core.domain.model.ChronometerProgress
import com.avangard.app.core.domain.model.DayClass
import com.avangard.app.core.domain.model.WeekClass
import com.avangard.app.core.domain.repository.ChronometerRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

@Singleton
class ChronometerRepositoryImpl @Inject constructor(
    private val preferences: UserPreferencesRepository,
    private val dailyDao: DailySessionDao,
    private val clock: Clock,
) : ChronometerRepository {

    override fun observeProgress(): Flow<ChronometerProgress> = combine(
        preferences.flow,
        dailyDao.observeAll(),
        todayTicker(),
    ) { prefs, entities, today ->
        compute(prefs.birthdayEpochDay, prefs.lifeExpectancyYears, entities, today, clock.zone())
    }.distinctUntilChanged()

    private fun todayTicker(): Flow<LocalDate> = flow {
        while (true) {
            emit(clock.today())
            delay(TICK_INTERVAL_MS)
        }
    }.distinctUntilChanged()

    companion object {
        private const val TICK_INTERVAL_MS = 60_000L
        private const val WEEKS_PER_YEAR = 52

        fun compute(
            birthdayEpochDay: Long?,
            lifeExpectancyYears: Int,
            entities: List<DailySessionEntity>,
            today: LocalDate,
            zone: ZoneId,
        ): ChronometerProgress {
            if (birthdayEpochDay == null) return ChronometerProgress.EMPTY

            val todayEpochDay = today.toEpochDay()
            val daysLived = max(0, (todayEpochDay - birthdayEpochDay).toInt())
            val daysBudget = ceil(lifeExpectancyYears * DAYS_PER_YEAR).toInt()
            val daysRemaining = max(0, daysBudget - daysLived)
            val yearsRemaining = daysRemaining / DAYS_PER_YEAR

            val classByEpochDay = HashMap<Long, DayClass>(entities.size)
            for (entity in entities) {
                val epochDay = epochMillisToEpochDay(entity.dateEpoch, zone)
                classByEpochDay[epochDay] = classifyDay(entity, epochDay, todayEpochDay)
            }

            var extracted = 0
            var partial = 0
            var burned = 0
            for (epochDay in birthdayEpochDay until todayEpochDay) {
                when (classByEpochDay[epochDay]) {
                    DayClass.Extracted -> extracted++
                    DayClass.Partial -> partial++
                    else -> burned++
                }
            }

            val todayClass = classByEpochDay[todayEpochDay] ?: DayClass.Today
            val yesterdayEpochDay = todayEpochDay - 1
            val yesterdayClass = when {
                yesterdayEpochDay < birthdayEpochDay -> DayClass.Future
                else -> classByEpochDay[yesterdayEpochDay] ?: DayClass.Burned
            }

            val totalWeeks = max(1, ceil(lifeExpectancyYears.toDouble()).toInt()) * WEEKS_PER_YEAR
            val weeks = ArrayList<WeekClass>(totalWeeks)
            for (weekIndex in 0 until totalWeeks) {
                val weekStart = birthdayEpochDay + weekIndex * 7L
                val weekEnd = weekStart + 7L
                weeks += classifyWeek(weekStart, weekEnd, todayEpochDay, classByEpochDay)
            }

            return ChronometerProgress(
                configured = true,
                daysLived = daysLived,
                daysBudget = daysBudget,
                daysRemaining = daysRemaining,
                yearsRemaining = yearsRemaining,
                extractedDays = extracted,
                partialDays = partial,
                burnedDays = burned,
                todayClass = todayClass,
                yesterdayClass = yesterdayClass,
                weeks = weeks,
            )
        }

        private fun classifyDay(
            entity: DailySessionEntity,
            epochDay: Long,
            todayEpochDay: Long,
        ): DayClass {
            val approved = entity.coreStatus == 1
            // Post-MIGRATION_6_7: classification is driven by per-Core mode
            // rather than the legacy day-wide mvd_active flag. Missing/NULL
            // core_mode on an Approved row is treated as Standard (the most
            // common backfill case from the migration).
            return when {
                approved && entity.coreMode == "Mvd" -> DayClass.Partial
                approved -> DayClass.Extracted
                epochDay == todayEpochDay -> DayClass.Today
                epochDay > todayEpochDay -> DayClass.Future
                else -> DayClass.Burned
            }
        }

        private fun classifyWeek(
            weekStart: Long,
            weekEnd: Long,
            todayEpochDay: Long,
            classByEpochDay: Map<Long, DayClass>,
        ): WeekClass {
            if (todayEpochDay in weekStart until weekEnd) return WeekClass.Current
            if (weekStart > todayEpochDay) return WeekClass.Future

            var best: WeekClass = WeekClass.Burned
            var sawAny = false
            for (epochDay in weekStart until min(weekEnd, todayEpochDay)) {
                sawAny = true
                when (classByEpochDay[epochDay]) {
                    DayClass.Extracted -> return WeekClass.Extracted
                    DayClass.Partial -> if (best != WeekClass.Extracted) best = WeekClass.Partial
                    else -> {}
                }
            }
            return if (sawAny) best else WeekClass.Future
        }

        private fun epochMillisToEpochDay(epochMillis: Long, zone: ZoneId): Long =
            Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().toEpochDay()

        private const val DAYS_PER_YEAR = 365.2425
    }
}
