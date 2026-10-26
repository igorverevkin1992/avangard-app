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
import com.itermeum.app.core.database.entity.MorningStage
import com.itermeum.app.core.engine.StreakEngine
import com.itermeum.app.core.liturgy.LectionaryTables
import com.itermeum.app.core.liturgy.LiturgicalCalendar
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Section 5.1 state machine edges found in the audit. */
@RunWith(RobolectricTestRunner::class)
class MorningRepositoryTest {

    private class FakeClock(var now: LocalDateTime) : Clock {
        override fun nowEpochMillis(): Long = ZonedDateTime.of(now, zone()).toInstant().toEpochMilli()
        override fun zone(): ZoneId = ZoneId.of("Europe/Moscow")
    }

    private lateinit var db: AppDatabase
    private lateinit var clock: FakeClock
    private lateinit var repo: MorningRepository
    private val monday = LocalDate.of(2026, 11, 2)

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val json = Json { ignoreUnknownKeys = true }
        clock = FakeClock(monday.minusDays(1).atTime(21, 0))
        val profiles = ProfileRepository(db.profileDao(), clock)
        ContentSeeder(db, ContentAssets(context, json)).seedIfNeeded()
        profiles.update { it.copy(createdAt = monday, onboardingDone = true, wakeTime = LocalTime.of(6, 30)) }
        val norms = NormRepository(db.normDao(), db.completionDao(), db.dayPlanDao(), db.reviewDao(), db.formationDao(), profiles,
            LiturgicalCalendar(LectionaryTables(context, json)), clock)
        repo = MorningRepository(db.morningDao(), db.dayPlanDao(), profiles, norms, clock)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `proof the evening before the alarm does not count`() = runBlocking {
        repo.schedule(monday)
        assertNull(repo.markProven(monday, "QR"))
        assertNull(db.completionDao().find(NormCatalog.WAKE, monday))
        assertEquals(MorningStage.SCHEDULED, repo.get(monday)!!.stage)
    }

    @Test
    fun `missing state row is measured against the planned alarm`() = runBlocking {
        clock.now = monday.atTime(11, 0)
        assertEquals(StreakEngine.WakeVerdict.MISSED, repo.markProven(monday, "QR"))
        assertNull(db.completionDao().find(NormCatalog.WAKE, monday))
    }

    @Test
    fun `late proof is LATE and ringing never resets a proven morning`() = runBlocking {
        repo.schedule(monday)
        clock.now = monday.atTime(6, 30)
        repo.markRinging(monday)
        clock.now = monday.atTime(6, 55)
        assertEquals(StreakEngine.WakeVerdict.LATE, repo.markProven(monday, "QR"))
        assertEquals(CompletionQuality.LATE, db.completionDao().find(NormCatalog.WAKE, monday)!!.quality)
        repo.markRinging(monday)
        assertEquals(MorningStage.PROVEN, repo.get(monday)!!.stage)
    }

    @Test
    fun `serviam without a proof is not credited`() = runBlocking {
        repo.schedule(monday)
        clock.now = monday.atTime(6, 31)
        repo.markHeroicDone(monday)
        assertNull(db.completionDao().find(NormCatalog.SERVIAM, monday))
    }
}
