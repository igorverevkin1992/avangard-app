package com.avangard.app.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.avangard.app.core.database.AppDatabase
import com.avangard.app.core.domain.FakeClock
import com.avangard.app.core.domain.model.Habit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies that [RoomHabitRepository.toggle] is atomic. Without the surrounding
 * transaction added in v3.3, the exists-then-insert/delete sequence races and
 * concurrent toggles on the same (date, habit) interleave into duplicate or
 * missed rows.
 */
@RunWith(RobolectricTestRunner::class)
class RoomHabitRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: RoomHabitRepository
    private val clock = FakeClock()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomHabitRepository(
            database = database,
            dao = database.habitLogDao(),
            clock = clock,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `toggle creates a row when none exists`() = runBlocking {
        repository.toggle(clock.today(), Habit.Sport, clock.nowEpochMillis())
        val rows = database.habitLogDao().getAll()
        assertEquals(1, rows.size)
        assertEquals(Habit.Sport.code, rows.first().habitCode)
    }

    @Test
    fun `toggle removes the row on second call`() = runBlocking {
        repository.toggle(clock.today(), Habit.Sport, clock.nowEpochMillis())
        repository.toggle(clock.today(), Habit.Sport, clock.nowEpochMillis())
        assertEquals(0, database.habitLogDao().getAll().size)
    }

    @Test
    fun `concurrent toggles on same date-habit converge to at most one row`() = runBlocking {
        // Twenty concurrent toggles end with either 0 or 1 row — never 2+.
        // The pre-fix code (no withTransaction) could leave duplicates because
        // multiple coroutines pass the exists()==null check before any insert
        // commits.
        val jobs = List(20) {
            async {
                repository.toggle(clock.today(), Habit.Sport, clock.nowEpochMillis())
            }
        }
        jobs.awaitAll()
        val rows = database.habitLogDao().getAll()
        assertEquals("expected 0 or 1 row, got ${rows.size}", true, rows.size <= 1)
    }
}
