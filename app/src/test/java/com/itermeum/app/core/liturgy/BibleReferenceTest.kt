package com.itermeum.app.core.liturgy

import org.junit.Assert.assertEquals
import org.junit.Test

class BibleReferenceTest {

    @Test
    fun `simple range`() {
        val r = BibleReference.parse("Mt 5:1-12")
        assertEquals("Mt", r.book)
        assertEquals(listOf(BibleReference.Segment(5, 1, 5, 12)), r.segments)
    }

    @Test
    fun `discontinuous verses in one chapter`() {
        val r = BibleReference.parse("Mt 6:1-6,16-18")
        assertEquals(listOf(BibleReference.Segment(6, 1, 6, 6), BibleReference.Segment(6, 16, 6, 18)), r.segments)
    }

    @Test
    fun `cross-chapter range`() {
        val r = BibleReference.parse("Mt 26:14-27:66")
        assertEquals(listOf(BibleReference.Segment(26, 14, 27, 66)), r.segments)
    }

    @Test
    fun `comma segment with its own chapter`() {
        val r = BibleReference.parse("Jn 15:26-27,16:12-15")
        assertEquals(listOf(BibleReference.Segment(15, 26, 15, 27), BibleReference.Segment(16, 12, 16, 15)), r.segments)
    }

    @Test
    fun `single verse`() {
        val r = BibleReference.parse("Mt 1:16,18-21,24")
        assertEquals(3, r.segments.size)
        assertEquals(BibleReference.Segment(1, 24, 1, 24), r.segments[2])
    }

    @Test
    fun `display uses russian short name and en dash`() {
        assertEquals("Мф 6:1–6,16–18", BibleReference.parse("Mt 6:1-6,16-18").display("Мф"))
        assertEquals("Мф 26:14–27:66", BibleReference.parse("Mt 26:14-27:66").display("Мф"))
        assertEquals("Ин 15:26–27,16:12–15", BibleReference.parse("Jn 15:26-27,16:12-15").display("Ин"))
    }
}
