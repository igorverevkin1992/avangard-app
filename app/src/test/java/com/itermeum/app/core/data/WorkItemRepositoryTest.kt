package com.itermeum.app.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.itermeum.app.core.common.Clock
import com.itermeum.app.core.content.ContentAssets
import com.itermeum.app.core.content.ContentSeeder
import com.itermeum.app.core.database.AppDatabase
import com.itermeum.app.core.database.entity.ItemStatus
import com.itermeum.app.core.database.entity.TaskStatus
import com.itermeum.app.core.database.entity.TaskType
import com.itermeum.app.core.liturgy.LectionaryTables
import com.itermeum.app.core.liturgy.LiturgicalCalendar
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** D-64: the inbox, the day's plan and «Перенести» work together. */
@RunWith(RobolectricTestRunner::class)
class WorkItemRepositoryTest {

    private class FakeClock(var now: LocalDateTime) : Clock {
        override fun nowEpochMillis(): Long = ZonedDateTime.of(now, zone()).toInstant().toEpochMilli()
        override fun zone(): ZoneId = ZoneId.of("Europe/Moscow")
    }

    private lateinit var db: AppDatabase
    private lateinit var clock: FakeClock
    private lateinit var items: WorkItemRepository
    private lateinit var work: WorkRepository
    private val monday = LocalDate.of(2026, 11, 2)

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val json = Json { ignoreUnknownKeys = true }
        clock = FakeClock(monday.atTime(10, 0))
        val profiles = ProfileRepository(db.profileDao(), clock)
        ContentSeeder(db, ContentAssets(context, json)).seedIfNeeded()
        profiles.update { it.copy(createdAt = monday, onboardingDone = true) }
        val norms = NormRepository(db.normDao(), db.completionDao(), db.dayPlanDao(), db.reviewDao(), db.formationDao(), profiles,
            LiturgicalCalendar(LectionaryTables(context, json)), clock)
        items = WorkItemRepository(db, db.workItemDao(), db.taskDao(), clock, json)
        work = WorkRepository(db, db.dayPlanDao(), db.taskDao(), db.outreachDao(), db.scriptDao(), norms, profiles, clock, items)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `a task for today joins the plan, the inbox does not`() = runBlocking {
        val today = items.capture("Смета", WorkItemRepository.Due.TODAY)
        val later = items.capture("Идея")
        val plan = work.tasksFor(monday)
        assertEquals(listOf(today), plan.mapNotNull { it.itemId })
        assertNull(items.get(later)!!.dueDate)
        items.planToday(later)
        items.planToday(later) // once
        assertEquals(listOf(today, later), work.tasksFor(monday).sortedBy { it.slot }.mapNotNull { it.itemId })
    }

    @Test
    fun `moved at the last stone the task is in tomorrow's plan by itself`() = runBlocking {
        val id = items.capture("Договор", WorkItemRepository.Due.TODAY)
        work.moveTask(work.tasksFor(monday).single())
        assertEquals(monday.plusDays(1), items.get(id)!!.dueDate)
        clock.now = monday.plusDays(1).atTime(9, 0)
        items.ensureDuePlanned()
        items.ensureDuePlanned() // not twice
        val tuesday = work.tasksFor(monday.plusDays(1))
        assertEquals(listOf(id), tuesday.mapNotNull { it.itemId })
        assertEquals("Договор", tuesday.single().title)
    }

    @Test
    fun `saving the plan gives every slot a card and sends removed ones back to the inbox`() = runBlocking {
        val first = items.capture("Первое", WorkItemRepository.Due.TODAY)
        val second = items.capture("Второе", WorkItemRepository.Due.TODAY)
        work.savePlan(
            monday,
            listOf(WorkRepository.TaskDraft(1, TaskType.OPS, "Второе", itemId = second), WorkRepository.TaskDraft(2, TaskType.DOCS, "Третье")),
            null, false,
        )
        val plan = work.tasksFor(monday).sortedBy { it.slot }
        assertEquals(second, plan[0].itemId)
        val third = plan[1].itemId!!
        assertTrue(third != first && third != second)
        assertEquals("Третье", items.get(third)!!.title)
        assertEquals(TaskType.DOCS, items.get(third)!!.type)
        // The removed one is back in the inbox and does not come back today.
        assertNull(items.get(first)!!.dueDate)
        items.ensureDuePlanned()
        assertEquals(2, work.tasksFor(monday).size)
    }

    @Test
    fun `done on the card closes its slot, back to work reopens it`() = runBlocking {
        val id = items.capture("Монтаж", WorkItemRepository.Due.TODAY)
        work.completeItem(id)
        assertEquals(ItemStatus.DONE, items.get(id)!!.status)
        assertEquals(TaskStatus.DONE, work.tasksFor(monday).single().status)
        work.reopenItem(id)
        assertEquals(ItemStatus.OPEN, items.get(id)!!.status)
        assertEquals(TaskStatus.OPEN, work.tasksFor(monday).single().status)
    }

    @Test
    fun `focus time adds up on the item and in the day's log`() = runBlocking {
        val id = items.capture("Сценарий")
        items.addFocus(id, 25 * 60_000L)
        items.addFocus(id, 5 * 60_000L)
        assertEquals(30 * 60_000L, items.get(id)!!.spentMs)
        assertEquals(30 * 60_000L, items.observeFocusMs(monday, monday).first())
    }

    @Test
    fun `checklist round-trips`() {
        val list = listOf(WorkItemRepository.Check("позвонить"), WorkItemRepository.Check("отправить", d = true))
        val item = com.itermeum.app.core.database.entity.WorkItemEntity(title = "x", createdAt = 0, checklistJson = items.checklistJson(list))
        assertEquals(list, items.checklist(item))
        assertNull(items.checklistJson(emptyList()))
    }
}
