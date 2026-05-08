package com.avangard.app.sync.scheduler

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmTriggerTest {

    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 5, 7)

    @Test
    fun `slot scheduled today when trigger is later`() {
        val now = LocalTime.of(5, 0)
        val target = AlarmSlot.MorningInitialization.triggerTime
        val expected = LocalDateTime.of(today, target).atZone(zone).toEpochSecond() * 1000L
        assertEquals(expected, nextTriggerEpochMillis(today, now, target, zone))
    }

    @Test
    fun `slot rolls to tomorrow when trigger already passed`() {
        val now = LocalTime.of(21, 30)
        val target = AlarmSlot.EveningReport.triggerTime
        val expected = LocalDateTime.of(today.plusDays(1), target).atZone(zone).toEpochSecond() * 1000L
        assertEquals(expected, nextTriggerEpochMillis(today, now, target, zone))
    }

    @Test
    fun `equal trigger time rolls to tomorrow`() {
        val target = AlarmSlot.MidDayCheckpoint.triggerTime
        val now = target
        val expected = LocalDateTime.of(today.plusDays(1), target).atZone(zone).toEpochSecond() * 1000L
        assertEquals(expected, nextTriggerEpochMillis(today, now, target, zone))
    }

    @Test
    fun `slot ids are unique and resolvable from id`() {
        val ids = AlarmSlot.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        AlarmSlot.entries.forEach { slot ->
            assertEquals(slot, AlarmSlot.fromId(slot.id))
        }
    }
}
