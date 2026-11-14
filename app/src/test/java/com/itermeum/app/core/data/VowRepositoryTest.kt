package com.itermeum.app.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.itermeum.app.core.common.Clock
import com.itermeum.app.core.content.ContentAssets
import com.itermeum.app.core.content.ContentSeeder
import com.itermeum.app.core.database.AppDatabase
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** D-67: the sobriety vow — answers, a slip, milestones, feasts. */
@RunWith(RobolectricTestRunner::class)
class VowRepositoryTest {

    private class FakeClock(var now: LocalDateTime) : Clock {
        override fun nowEpochMillis(): Long = ZonedDateTime.of(now, zone()).toInstant().toEpochMilli()
        override fun zone(): ZoneId = ZoneId.of("Europe/Moscow")
    }

    private lateinit var db: AppDatabase
    private lateinit var clock: FakeClock
    private lateinit var vows: VowRepository
    private val start = LocalDate.of(2026, 11, 6)

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        ContentSeeder(db, ContentAssets(context, Json { ignoreUnknownKeys = true })).seed()
        clock = FakeClock(start.atTime(10, 0))
        vows = VowRepository(db.vowDao(), db.streakDao(), clock)
        vows.take(VowKind.SOBRIETY, "за брата", "Отец Павел", "+7 900 000-00-00", "Спасибо, я не пью")
    }

    @After
    fun tearDown() = db.close()

    private fun dayOn(n: Long, hour: Int = 21) { clock.now = start.plusDays(n).atTime(hour, 0) }

    @Test
    fun `today is asked only from the evening, yesterday the next morning`() = runBlocking {
        assertTrue(vows.state(VowKind.SOBRIETY).pending.isEmpty())
        dayOn(0, 21)
        assertEquals(listOf(start), vows.state(VowKind.SOBRIETY).pending)
        dayOn(1, 9)
        assertEquals(listOf(start), vows.state(VowKind.SOBRIETY).pending)
        assertTrue(vows.needsAnswer(VowKind.SOBRIETY, start))
    }

    @Test
    fun `kept days count, a slip starts again and keeps the best`() = runBlocking {
        (0L..2L).forEach { dayOn(it); vows.answer(VowKind.SOBRIETY, start.plusDays(it), true) }
        dayOn(3); vows.answer(VowKind.SOBRIETY, start.plusDays(3), false)
        dayOn(4); vows.answer(VowKind.SOBRIETY, start.plusDays(4), true)
        val s = vows.state(VowKind.SOBRIETY).stats
        assertEquals(1, s.current)
        assertEquals(3, s.best)
        assertEquals(4, s.total)
        assertFalse(vows.needsAnswer(VowKind.SOBRIETY, start.plusDays(4)))
    }

    @Test
    fun `nothing is counted before the vow or in the future`() = runBlocking {
        vows.answer(VowKind.SOBRIETY, start.minusDays(1), true)
        vows.answer(VowKind.SOBRIETY, start.plusDays(1), true)
        assertEquals(0, vows.state(VowKind.SOBRIETY).stats.total)
    }

    @Test
    fun `seven kept days reach the first milestone`() = runBlocking {
        (0L..6L).forEach { dayOn(it); vows.answer(VowKind.SOBRIETY, start.plusDays(it), true) }
        assertNotNull(db.streakDao().milestone("sobriety_7")?.achievedAt)
        assertNull(db.streakDao().milestone("sobriety_30")?.achievedAt)
    }

    @Test
    fun `a feast is kept on its day and cleared`() = runBlocking {
        vows.setFeast(start, LocalTime.of(19, 30))
        assertEquals(LocalTime.of(19, 30), vows.state(VowKind.SOBRIETY).today?.let { VowRepository.feastTime(it) })
        assertEquals(1, vows.upcomingFeasts().size)
        vows.setFeast(start, null)
        assertTrue(vows.upcomingFeasts().isEmpty())
    }

    @Test
    fun `a laid down vow asks nothing, taking it again starts a new count`() = runBlocking {
        dayOn(0); vows.answer(VowKind.SOBRIETY, start, true)
        vows.release(VowKind.SOBRIETY)
        dayOn(2)
        assertFalse(vows.state(VowKind.SOBRIETY).active)
        assertTrue(vows.state(VowKind.SOBRIETY).pending.isEmpty())
        vows.take(VowKind.SOBRIETY, null, null, null, null)
        assertEquals(start.plusDays(2), vows.vow(VowKind.SOBRIETY)?.startDate)
        assertEquals(0, vows.state(VowKind.SOBRIETY).stats.total)
    }

    @Test
    fun `vows count apart — a slip in one leaves the others`() = runBlocking {
        vows.take(VowKind.PURITY, null, null, null, null)
        vows.take(VowKind.GAMES, null, null, null, null)
        dayOn(0)
        assertEquals(listOf(VowKind.SOBRIETY, VowKind.PURITY, VowKind.GAMES), vows.needingAnswer(start))
        vows.answer(VowKind.SOBRIETY, start, true)
        vows.answer(VowKind.PURITY, start, false)
        vows.answer(VowKind.GAMES, start, true)
        assertEquals(1, vows.state(VowKind.SOBRIETY).stats.current)
        assertEquals(0, vows.state(VowKind.PURITY).stats.current)
        assertEquals(1, vows.state(VowKind.PURITY).stats.slips)
        assertEquals(1, vows.state(VowKind.GAMES).stats.current)
        assertTrue(vows.needingAnswer(start).isEmpty())
        (1L..7L).forEach { dayOn(it); vows.answer(VowKind.GAMES, start.plusDays(it), true) }
        assertNotNull(db.streakDao().milestone("games_7")?.achievedAt)
        assertNull(db.streakDao().milestone("purity_7")?.achievedAt)
    }
}
