package com.avangard.app.core.data

import com.avangard.app.core.database.entity.DailySessionEntity
import com.avangard.app.core.domain.model.DayClass
import com.avangard.app.core.domain.model.WeekClass
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChronometerRepositoryImplTest {

    private val zone: ZoneId = ZoneId.of("UTC")

    private fun day(date: LocalDate, mvd: Int = 0, status: Int = 0): DailySessionEntity {
        // Mirror MIGRATION_6_7: an Approved row with mvd=1 maps to core_mode "Mvd",
        // mvd=0 + Approved maps to "Standard"; Idle/Failed stays NULL.
        val mode = when {
            status == 1 && mvd == 1 -> "Mvd"
            status == 1 -> "Standard"
            else -> null
        }
        return DailySessionEntity(
            dateEpoch = date.atStartOfDay(zone).toEpochSecond() * 1000L,
            mvdActive = mvd,
            coreStatus = status,
            coreMode = mode,
        )
    }

    @Test
    fun `null birthday yields unconfigured progress`() {
        val today = LocalDate.of(2026, 5, 25)
        val out = ChronometerRepositoryImpl.compute(
            birthdayEpochDay = null,
            lifeExpectancyYears = 80,
            entities = emptyList(),
            today = today,
            zone = zone,
        )
        assertFalse(out.configured)
        assertEquals(0, out.weeks.size)
    }

    @Test
    fun `extracted and partial are distinguished by mvd flag`() {
        val birthday = LocalDate.of(2020, 1, 1)
        val today = LocalDate.of(2020, 1, 8)
        val approvedStandard = day(LocalDate.of(2020, 1, 2), mvd = 0, status = 1)
        val approvedMvd = day(LocalDate.of(2020, 1, 3), mvd = 1, status = 1)
        val idle = day(LocalDate.of(2020, 1, 4), mvd = 0, status = 0)

        val out = ChronometerRepositoryImpl.compute(
            birthdayEpochDay = birthday.toEpochDay(),
            lifeExpectancyYears = 80,
            entities = listOf(approvedStandard, approvedMvd, idle),
            today = today,
            zone = zone,
        )

        assertTrue(out.configured)
        assertEquals(7, out.daysLived)
        assertEquals(1, out.extractedDays)
        assertEquals(1, out.partialDays)
        // Five burned: 2020-01-01, 04, 05, 06, 07 (today is excluded from the past-days tally).
        assertEquals(5, out.burnedDays)
    }

    @Test
    fun `current week classification overrides past achievements`() {
        val birthday = LocalDate.of(2026, 5, 18) // Monday — index-0 of week 0.
        val today = LocalDate.of(2026, 5, 25)    // Following Monday — week 1.
        val out = ChronometerRepositoryImpl.compute(
            birthdayEpochDay = birthday.toEpochDay(),
            lifeExpectancyYears = 80,
            entities = emptyList(),
            today = today,
            zone = zone,
        )
        // Week 0 has 7 burned days; week 1 contains today.
        assertEquals(WeekClass.Burned, out.weeks[0])
        assertEquals(WeekClass.Current, out.weeks[1])
        assertEquals(WeekClass.Future, out.weeks[2])
    }

    @Test
    fun `extracted day promotes its week`() {
        val birthday = LocalDate.of(2026, 5, 18)
        val today = LocalDate.of(2026, 5, 28) // a few days into week 1
        val entities = listOf(
            day(LocalDate.of(2026, 5, 19), mvd = 0, status = 1), // Extracted day in week 0
            day(LocalDate.of(2026, 5, 20), mvd = 1, status = 1), // Partial in week 0
        )
        val out = ChronometerRepositoryImpl.compute(
            birthdayEpochDay = birthday.toEpochDay(),
            lifeExpectancyYears = 80,
            entities = entities,
            today = today,
            zone = zone,
        )
        assertEquals(WeekClass.Extracted, out.weeks[0])
        assertEquals(WeekClass.Current, out.weeks[1])
    }

    @Test
    fun `today class reflects today's row`() {
        val birthday = LocalDate.of(2020, 1, 1)
        val today = LocalDate.of(2020, 1, 10)
        val out = ChronometerRepositoryImpl.compute(
            birthdayEpochDay = birthday.toEpochDay(),
            lifeExpectancyYears = 80,
            entities = listOf(day(today, mvd = 0, status = 1)),
            today = today,
            zone = zone,
        )
        assertEquals(DayClass.Extracted, out.todayClass)
    }

    @Test
    fun `weeks list size equals 52 times expectancy years`() {
        val birthday = LocalDate.of(2020, 1, 1)
        val today = LocalDate.of(2026, 5, 25)
        val out = ChronometerRepositoryImpl.compute(
            birthdayEpochDay = birthday.toEpochDay(),
            lifeExpectancyYears = 80,
            entities = emptyList(),
            today = today,
            zone = zone,
        )
        assertEquals(80 * 52, out.weeks.size)
    }
}
