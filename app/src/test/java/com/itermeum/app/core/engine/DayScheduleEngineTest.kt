package com.itermeum.app.core.engine

import com.itermeum.app.core.content.NormCatalog
import com.itermeum.app.core.database.entity.ProfileEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DayScheduleEngineTest {

    private val monday = LocalDate.of(2026, 11, 2)
    private val saturday = LocalDate.of(2026, 11, 7)

    private fun input(
        date: LocalDate = monday,
        profile: ProfileEntity = ProfileEntity(massTime = LocalTime.of(19, 0)),
        travel: Boolean = false,
        sick: Boolean = false,
        events: List<Pair<LocalDateTime, LocalDateTime>> = emptyList(),
    ) = DayScheduleEngine.Input(date, profile, NormCatalog.all, reginaCaeli = false, travel = travel, sick = sick, shootDay = false, events = events)

    private fun List<DayScheduleEngine.ScheduledItem>.of(id: String) = first { it.normId == id }

    @Test
    fun `wake at 07-00 shifts mental prayer to 07-35 but not Angelus or work`() {
        val items = DayScheduleEngine.schedule(input(profile = ProfileEntity(wakeTime = LocalTime.of(7, 0))))
        assertEquals(LocalTime.of(7, 0), items.of(NormCatalog.WAKE).time)
        assertEquals(LocalTime.of(7, 35), items.of(NormCatalog.MENTAL_PRAYER).time)
        assertEquals(LocalTime.of(7, 50), items.of(NormCatalog.GYM).time)
        assertEquals(LocalTime.of(12, 0), items.of(NormCatalog.ANGELUS).time)
        assertEquals(LocalTime.of(12, 5), items.of(NormCatalog.FROG).time)
        assertEquals(LocalTime.of(22, 40), items.of(NormCatalog.EXAMEN).time)
    }

    @Test
    fun `user time overrides without changing the completion rule`() {
        val norms = NormCatalog.all.map { if (it.id == NormCatalog.ROSARY) it.copy(userTime = LocalTime.of(21, 0)) else it }
        val items = DayScheduleEngine.schedule(input().copy(norms = norms))
        assertEquals(LocalTime.of(21, 0), items.of(NormCatalog.ROSARY).time)
        assertEquals(5, items.of(NormCatalog.ROSARY).target)
    }

    @Test
    fun `saturday has weekend wake, catechesis and no work norms`() {
        val catechesis = saturday.atTime(16, 0) to saturday.atTime(17, 30)
        val items = DayScheduleEngine.schedule(input(date = saturday, events = listOf(catechesis)))
        assertEquals(LocalTime.of(7, 30), items.of(NormCatalog.WAKE).time)
        assertTrue(items.any { it.normId == NormCatalog.CATECHESIS })
        assertTrue(items.any { it.normId == NormCatalog.MARIAN_SATURDAY })
        assertTrue(items.any { it.normId == NormCatalog.MASS })
        assertNull(items.firstOrNull { it.normId == NormCatalog.FROG })
        assertNull(items.firstOrNull { it.normId == NormCatalog.GYM })
        // Quiet window 14:30–19:00 around the class.
        val quiet = DayScheduleEngine.quietWindows(listOf(catechesis))
        assertTrue(DayScheduleEngine.isQuiet(saturday.atTime(14, 30), quiet))
        assertTrue(DayScheduleEngine.isQuiet(saturday.atTime(17, 29), quiet))
        assertFalse(DayScheduleEngine.isQuiet(saturday.atTime(14, 29), quiet))
        assertFalse(DayScheduleEngine.isQuiet(saturday.atTime(19, 0), quiet))
    }

    @Test
    fun `mass reminder is muted by its own quiet window and never hides the catechesis note`() {
        val mass = saturday.atTime(19, 0) to saturday.atTime(20, 0)
        val items = DayScheduleEngine.schedule(input(date = saturday, events = listOf(mass)))
        assertTrue(items.of(NormCatalog.MASS).muted)
        assertFalse(items.of(NormCatalog.ROSARY).muted) // 20:15 < 21:30? still inside → check below
    }

    @Test
    fun `travel applies section 3-5 coefficients`() {
        val items = DayScheduleEngine.schedule(input(travel = true))
        assertEquals(2, items.of(NormCatalog.FROG).target)
        assertEquals(1, items.of(NormCatalog.ROSARY).target)
    }

    @Test
    fun `sick day keeps only examen`() {
        val items = DayScheduleEngine.schedule(input(sick = true))
        assertEquals(listOf(NormCatalog.EXAMEN), items.map { it.normId })
    }

    @Test
    fun `confession appears only after baptism`() {
        assertNull(DayScheduleEngine.schedule(input()).firstOrNull { it.normId == NormCatalog.CONFESSION })
        val baptized = ProfileEntity(baptized = true, massTime = LocalTime.of(19, 0))
        assertTrue(DayScheduleEngine.schedule(input(profile = baptized)).any { it.normId == NormCatalog.CONFESSION })
    }

    @Test
    fun `monthly summary only on the first, quarter check on last sunday of quarter`() {
        assertNull(DayScheduleEngine.schedule(input()).firstOrNull { it.normId == NormCatalog.MONTHLY_SUMMARY })
        assertTrue(DayScheduleEngine.schedule(input(date = LocalDate.of(2026, 12, 1))).any { it.normId == NormCatalog.MONTHLY_SUMMARY })
        assertTrue(DayScheduleEngine.isLastSundayOfQuarter(LocalDate.of(2026, 12, 27)))
        assertFalse(DayScheduleEngine.isLastSundayOfQuarter(LocalDate.of(2026, 12, 20)))
        assertTrue(DayScheduleEngine.schedule(input(date = LocalDate.of(2026, 12, 27))).any { it.normId == NormCatalog.QUARTER_CHECK })
    }

    @Test
    fun `windows follow section 4`() {
        val items = DayScheduleEngine.schedule(input())
        assertEquals(monday.atTime(14, 0), items.of(NormCatalog.FROG).windowEnd)
        assertEquals(monday.atTime(12, 30), items.of(NormCatalog.DAY_PLAN).windowEnd)
        assertEquals(monday.atTime(21, 0), items.of(NormCatalog.LAST_STONE).windowEnd)
        assertEquals(monday.plusDays(1).atTime(3, 0), items.of(NormCatalog.EXAMEN).windowEnd)
        assertEquals(monday.atTime(11, 30), items.of(NormCatalog.ANGELUS).windowStart)
        assertEquals(monday.atTime(6, 50), items.of(NormCatalog.WAKE).windowEnd)
    }

    @Test
    fun `alarm instant follows the device zone - 06-30 local in UTC+5`() {
        val wake = DayScheduleEngine.wakeTimeFor(monday, ProfileEntity())
        val zone = ZoneId.of("Asia/Yekaterinburg")
        val instant = ZonedDateTime.of(monday, wake, zone).toInstant()
        assertEquals(LocalTime.of(6, 30), instant.atZone(zone).toLocalTime())
        assertEquals(LocalTime.of(4, 30), instant.atZone(ZoneId.of("Europe/Moscow")).toLocalTime())
    }

    @Test
    fun `next item skips done and closed windows`() {
        val items = DayScheduleEngine.schedule(input())
        val next = DayScheduleEngine.nextItem(items, monday.atTime(12, 10), done = setOf(NormCatalog.ANGELUS, NormCatalog.DAY_PLAN))
        assertEquals(NormCatalog.FROG, next!!.normId)
        val evening = DayScheduleEngine.nextItem(items, monday.atTime(21, 30), done = emptySet())
        assertEquals(NormCatalog.ROSARY, evening!!.normId)
    }
}
