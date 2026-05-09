package com.avangard.app.core.data

import com.avangard.app.core.database.entity.HabitLogEntity
import com.avangard.app.core.domain.model.Habit
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthlyCellsTest {

    private val moscow: ZoneId = ZoneId.of("Europe/Moscow")

    private fun startOfDay(year: Int, month: Int, day: Int, zone: ZoneId): Long =
        ZonedDateTime.of(LocalDate.of(year, month, day).atStartOfDay(), zone)
            .toInstant().toEpochMilli()

    @Test
    fun `unknown habit codes are skipped`() {
        val rows = listOf(
            HabitLogEntity(startOfDay(2026, 5, 7, moscow), "01", 1L),
            HabitLogEntity(startOfDay(2026, 5, 7, moscow), "ZZ", 1L),
        )
        val cells = buildMonthlyCells(rows, moscow)
        assertEquals(setOf(Habit.Generations), cells[LocalDate.of(2026, 5, 7)])
    }

    @Test
    fun `multiple habits on the same day group into one set`() {
        val rows = listOf(
            HabitLogEntity(startOfDay(2026, 5, 7, moscow), "01", 1L),
            HabitLogEntity(startOfDay(2026, 5, 7, moscow), "03", 1L),
            HabitLogEntity(startOfDay(2026, 5, 7, moscow), "05", 1L),
        )
        val cells = buildMonthlyCells(rows, moscow)
        assertEquals(
            setOf(Habit.Generations, Habit.Sport, Habit.Reading),
            cells[LocalDate.of(2026, 5, 7)],
        )
    }

    @Test
    fun `epoch is interpreted in the supplied zone`() {
        val moscowEpoch = startOfDay(2026, 5, 7, moscow)
        val cells = buildMonthlyCells(
            listOf(HabitLogEntity(moscowEpoch, "01", 1L)),
            moscow,
        )
        assertTrue(cells.containsKey(LocalDate.of(2026, 5, 7)))
    }

    @Test
    fun `empty input yields empty map`() {
        assertTrue(buildMonthlyCells(emptyList(), moscow).isEmpty())
    }
}
