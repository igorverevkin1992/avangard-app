package com.itermeum.app.core.liturgy

import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Section 11.1 mandatory fixtures plus transfer rules. */
@RunWith(RobolectricTestRunner::class)
class LiturgicalCalendarTest {

    private lateinit var cal: LiturgicalCalendar

    @Before
    fun setUp() {
        cal = LiturgicalCalendar(LectionaryTables(ApplicationProvider.getApplicationContext(), Json { ignoreUnknownKeys = true }))
    }

    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun `easter dates`() {
        assertEquals(d("2026-04-05"), Easter.of(2026))
        assertEquals(d("2027-03-28"), Easter.of(2027))
        assertEquals(d("2028-04-16"), Easter.of(2028))
        assertEquals(d("2029-04-01"), Easter.of(2029))
    }

    @Test
    fun `2026-11-29 is Advent 1 of year B`() {
        val day = cal.day(d("2026-11-29"))
        assertEquals(Season.ADVENT, day.season)
        assertEquals("ADV1", day.celebrationId)
        assertEquals(SundayCycle.B, day.sundayCycle)
        assertEquals("Mk 13:33-37", day.gospelRef)
        assertEquals(LiturgicalColor.VIOLET, day.color)
    }

    @Test
    fun `2027-02-10 is Ash Wednesday`() {
        val day = cal.day(d("2027-02-10"))
        assertEquals(Season.LENT, day.season)
        assertEquals("ASH_WEDNESDAY", day.celebrationId)
        assertEquals("Mt 6:1-6,16-18", day.gospelRef)
        assertEquals(0, day.weekOfSeason)
    }

    @Test
    fun `2027-03-28 is Easter and Regina Caeli starts the evening before`() {
        val easter = cal.day(d("2027-03-28"))
        assertEquals("EASTER", easter.celebrationId)
        assertTrue(easter.reginaCaeli)
        assertTrue(cal.day(d("2027-03-27")).reginaCaeli)
        assertFalse(cal.day(d("2027-03-26")).reginaCaeli)
        assertEquals("EASTER_VIGIL", cal.day(d("2027-03-27")).celebrationId)
    }

    @Test
    fun `2027-05-16 is Pentecost, 17-05 is back to Angelus`() {
        val p = cal.day(d("2027-05-16"))
        assertEquals("PENTECOST", p.celebrationId)
        assertTrue(p.reginaCaeli)
        assertEquals(LiturgicalColor.RED, p.color)
        val next = cal.day(d("2027-05-17"))
        assertFalse(next.reginaCaeli)
        assertEquals(Season.ORDINARY_2, next.season)
        assertNotNull(next.gospelRef)
    }

    @Test
    fun `2027-11-28 is Advent 1 of year C`() {
        val day = cal.day(d("2027-11-28"))
        assertEquals("ADV1", day.celebrationId)
        assertEquals(SundayCycle.C, day.sundayCycle)
        assertEquals("Lk 21:25-28,34-36", day.gospelRef)
    }

    @Test
    fun `2028 Lent fixtures`() {
        assertEquals("ASH_WEDNESDAY", cal.day(d("2028-03-01")).celebrationId)
        val lent1 = cal.day(d("2028-03-05"))
        assertEquals("LENT1", lent1.celebrationId)
        assertEquals("Lk 4:1-13", lent1.gospelRef)
        // St Joseph falls on Lent 3 Sunday → Monday 20 March.
        assertEquals("LENT3", cal.day(d("2028-03-19")).celebrationId)
        val joseph = cal.day(d("2028-03-20"))
        assertEquals("ST_JOSEPH", joseph.celebrationId)
        assertEquals(Rank.SOLEMNITY, joseph.rank)
        assertEquals("Mt 1:16,18-21,24", joseph.gospelRef)
    }

    @Test
    fun `2028 Easter fixtures`() {
        assertEquals("EASTER_VIGIL", cal.day(d("2028-04-15")).celebrationId)
        val easter = cal.day(d("2028-04-16"))
        assertEquals("EASTER", easter.celebrationId)
        assertEquals(SundayCycle.C, easter.sundayCycle)
        assertEquals(WeekdayCycle.II, easter.weekdayCycle)
        assertEquals("PENTECOST", cal.day(d("2028-06-04")).celebrationId)
    }

    @Test
    fun `annunciation 2027 falls on Holy Thursday and moves after the octave`() {
        assertEquals("HOLY_THURSDAY", cal.day(d("2027-03-25")).celebrationId)
        assertEquals("ANNUNCIATION", cal.day(d("2027-04-05")).celebrationId)
        assertEquals("EASTER_OCTAVE_1", cal.day(d("2027-03-29")).celebrationId)
    }

    @Test
    fun `christmas season 2026-27`() {
        assertEquals("CHRISTMAS_DAY", cal.day(d("2026-12-25")).celebrationId)
        assertEquals("HOLY_FAMILY", cal.day(d("2026-12-27")).celebrationId)
        assertEquals("ST_STEPHEN", cal.day(d("2026-12-26")).celebrationId)
        assertEquals("MARY_MOTHER_OF_GOD", cal.day(d("2027-01-01")).celebrationId)
        assertEquals("CHRISTMAS2", cal.day(d("2027-01-03")).celebrationId)
        assertEquals("Jn 1:1-18", cal.day(d("2027-01-03")).gospelRef)
        assertEquals("EPIPHANY", cal.day(d("2027-01-06")).celebrationId)
        assertEquals("BAPTISM", cal.day(d("2027-01-10")).celebrationId)
        val mondayOt1 = cal.day(d("2027-01-11"))
        assertEquals(Season.ORDINARY_1, mondayOt1.season)
        assertEquals(1, mondayOt1.weekOfSeason)
        assertEquals("Mk 1:14-20", mondayOt1.gospelRef)
    }

    @Test
    fun `ordinary time after pentecost ends with Christ the King`() {
        val ctk = cal.day(d("2027-11-21"))
        assertEquals("OT34", ctk.celebrationId)
        assertEquals(34, ctk.weekOfSeason)
        assertEquals("Jn 18:33-37", ctk.gospelRef) // 2027 = year B
        val sat = cal.day(d("2027-11-27"))
        assertEquals("Lk 21:34-36", sat.gospelRef)
    }

    @Test
    fun `russian transfers of Ascension and Corpus Christi to Sunday`() {
        assertEquals("ASCENSION", cal.day(d("2027-05-09")).celebrationId)
        assertEquals("CORPUS_CHRISTI", cal.day(d("2027-05-30")).celebrationId)
        assertEquals("TRINITY", cal.day(d("2027-05-23")).celebrationId)
        assertEquals("SACRED_HEART", cal.day(d("2027-06-04")).celebrationId)
    }

    @Test
    fun `advent december 17 and lent friday`() {
        assertEquals("Mt 1:1-17", cal.day(d("2026-12-17")).gospelRef)
        assertEquals("Lk 1:67-79", cal.day(d("2026-12-24")).gospelRef)
        assertTrue(cal.day(d("2028-03-10")).lentFriday)
        assertFalse(cal.day(d("2028-05-05")).lentFriday)
        assertEquals(LiturgicalColor.ROSE, cal.day(d("2026-12-13")).color)
    }

    @Test
    fun `every day of two years has a gospel`() {
        var day = d("2026-10-01")
        while (day < d("2028-12-31")) {
            val l = cal.day(day)
            assertNotNull("no gospel on $day (${l.title})", l.gospelRef)
            day = day.plusDays(1)
        }
    }
}
