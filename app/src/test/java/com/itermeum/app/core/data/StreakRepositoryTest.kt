package com.itermeum.app.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.itermeum.app.core.common.Clock
import com.itermeum.app.core.content.ContentAssets
import com.itermeum.app.core.content.ContentSeeder
import com.itermeum.app.core.content.NormCatalog
import com.itermeum.app.core.database.AppDatabase
import com.itermeum.app.core.database.entity.CompletionQuality
import com.itermeum.app.core.database.entity.DayPlanEntity
import com.itermeum.app.core.database.entity.NormCompletionEntity
import com.itermeum.app.core.database.entity.StreakType
import com.itermeum.app.core.engine.StreakEngine
import com.itermeum.app.core.liturgy.LectionaryTables
import com.itermeum.app.core.liturgy.LiturgicalCalendar
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Section 11.6 persistence tests: freezes on the 1st and 2nd miss, the 3rd breaks, sick skips, 1st of month resets. */
@RunWith(RobolectricTestRunner::class)
class StreakRepositoryTest {

    private class FakeClock(var now: LocalDateTime, val zoneId: ZoneId = ZoneId.of("Europe/Moscow")) : Clock {
        override fun nowEpochMillis(): Long = ZonedDateTime.of(now, zoneId).toInstant().toEpochMilli()
        override fun zone(): ZoneId = zoneId
    }

    private lateinit var db: AppDatabase
    private lateinit var clock: FakeClock
    private lateinit var repo: StreakRepository
    private lateinit var norms: NormRepository
    private val start = LocalDate.of(2026, 11, 2) // Monday

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val json = Json { ignoreUnknownKeys = true }
        val assets = ContentAssets(context, json)
        clock = FakeClock(start.atTime(12, 0))
        val profiles = ProfileRepository(db.profileDao(), clock)
        ContentSeeder(db, assets).seedIfNeeded()
        profiles.update { it.copy(createdAt = start, onboardingDone = true) }
        val calendar = LiturgicalCalendar(LectionaryTables(context, json))
        norms = NormRepository(db.normDao(), db.completionDao(), db.dayPlanDao(), db.reviewDao(), db.formationDao(), profiles, calendar, clock)
        repo = StreakRepository(db, db.streakDao(), db.completionDao(), db.morningDao(), db.outreachDao(), db.dayPlanDao(), db.srsDao(), db.liturgyDao(), norms, profiles, assets, clock)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun goodDay(date: LocalDate) {
        val active = repo.facts(date).activeNorms
        val ids = norms.scheduleFor(date).map { it.normId }
        // Complete enough norms for 60 % including wake.
        val needed = kotlin.math.ceil(active * 0.6).toInt()
        (listOf(NormCatalog.WAKE) + ids.filter { it != NormCatalog.WAKE }).take(needed).forEach { id ->
            db.completionDao().upsert(NormCompletionEntity(normId = id, logicalDate = date, completedAt = 0L, quality = CompletionQuality.FULL))
        }
    }

    @Test
    fun `first and second miss are frozen, third breaks, best survives`() = runBlocking {
        goodDay(start); goodDay(start.plusDays(1)); goodDay(start.plusDays(2))
        repo.closeDaysUpTo(start.plusDays(2))
        assertEquals(3, db.streakDao().get(StreakType.FIDELITY)!!.current)
        // three empty days
        val r = repo.closeDaysUpTo(start.plusDays(5))
        val outcomes = r.map { it.outcomes[StreakType.FIDELITY] }
        assertTrue(outcomes[0] is StreakEngine.Outcome.Frozen)
        assertTrue(outcomes[1] is StreakEngine.Outcome.Frozen)
        assertTrue(outcomes[2] is StreakEngine.Outcome.Broken)
        val st = db.streakDao().get(StreakType.FIDELITY)!!
        assertEquals(0, st.current)
        assertEquals(3, st.best)
        assertEquals(3, st.totalDays)
        assertEquals(2, db.streakDao().freezesUsed("2026-11"))
    }

    @Test
    fun `sick day neither grows nor burns nor spends a freeze`() = runBlocking {
        goodDay(start)
        db.dayPlanDao().upsert(DayPlanEntity(logicalDate = start.plusDays(1), sick = true))
        repo.closeDaysUpTo(start.plusDays(1))
        val st = db.streakDao().get(StreakType.FIDELITY)!!
        assertEquals(1, st.current)
        assertEquals(0, db.streakDao().freezesUsed("2026-11"))
        assertEquals(start.plusDays(1), st.lastCountedDate)
    }

    @Test
    fun `closing is idempotent per day`() = runBlocking {
        goodDay(start)
        repo.closeDaysUpTo(start)
        repo.closeDaysUpTo(start)
        assertEquals(1, db.streakDao().get(StreakType.FIDELITY)!!.current)
    }

    @Test
    fun `freeze allowance resets on the first of the month`() = runBlocking {
        val nov28 = LocalDate.of(2026, 11, 28)
        var d = start
        while (d < nov28) { goodDay(d); d = d.plusDays(1) }
        repo.closeDaysUpTo(nov28.minusDays(1))
        // Nov 28, 29 missed → two November freezes; Dec 1 missed → December freeze, streak intact.
        goodDay(LocalDate.of(2026, 11, 30))
        val results = repo.closeDaysUpTo(LocalDate.of(2026, 12, 1))
        assertEquals(2, db.streakDao().freezesUsed("2026-11"))
        assertEquals(1, db.streakDao().freezesUsed("2026-12"))
        assertTrue(results.last().outcomes[StreakType.FIDELITY] is StreakEngine.Outcome.Frozen)
        assertTrue(db.streakDao().get(StreakType.FIDELITY)!!.current > 0)
    }

    @Test
    fun `frog streak counts only weekdays`() = runBlocking {
        val sat = LocalDate.of(2026, 11, 7)
        goodDay(sat)
        val r = repo.closeDaysUpTo(sat)
        assertTrue(r.last().outcomes[StreakType.FROG] is StreakEngine.Outcome.Skipped)
    }
}
