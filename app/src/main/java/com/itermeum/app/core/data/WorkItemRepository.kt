package com.itermeum.app.core.data

import androidx.room.withTransaction
import com.itermeum.app.core.common.Clock
import com.itermeum.app.core.database.AppDatabase
import com.itermeum.app.core.database.dao.TaskDao
import com.itermeum.app.core.database.dao.WorkItemDao
import com.itermeum.app.core.database.entity.FocusLogEntity
import com.itermeum.app.core.database.entity.ItemStatus
import com.itermeum.app.core.database.entity.TaskStatus
import com.itermeum.app.core.database.entity.TaskType
import com.itermeum.app.core.database.entity.WorkItemEntity
import com.itermeum.app.core.database.entity.WorkTaskEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * D-64: work items — the inbox and the details of every task. A slot of the
 * day's plan (`work_task`) carries out an item; closing the slot closes the
 * item, moving it at the last stone sends the item to tomorrow, and items due
 * today join today's plan by themselves.
 */
@Singleton
class WorkItemRepository @Inject constructor(
    private val db: AppDatabase,
    private val items: WorkItemDao,
    private val tasks: TaskDao,
    private val clock: Clock,
    private val json: Json,
) {
    @Serializable data class Check(val t: String, val d: Boolean = false)

    enum class Due { TODAY, TOMORROW, THIS_WEEK, NONE }

    fun observeOpen(): Flow<List<WorkItemEntity>> = items.observeOpen()
    fun observe(id: Long): Flow<WorkItemEntity?> = items.observe(id)
    fun search(q: String): Flow<List<WorkItemEntity>> = items.search(q.trim())
    fun observeForProject(projectId: Long): Flow<List<WorkItemEntity>> = items.observeForProject(projectId)
    fun observeFocusMs(from: LocalDate, to: LocalDate): Flow<Long> = items.observeFocusMs(from, to)
    suspend fun get(id: Long): WorkItemEntity? = items.get(id)

    fun dueDate(due: Due, today: LocalDate = clock.logicalToday()): LocalDate? = when (due) {
        Due.TODAY -> today
        Due.TOMORROW -> today.plusDays(1)
        // «На неделе» — this Friday, or next Friday from the weekend.
        Due.THIS_WEEK -> today.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
        Due.NONE -> null
    }

    /** Quick capture: one line into the inbox (or a day); a task due today joins today's plan. */
    suspend fun capture(title: String, due: Due = Due.NONE, projectId: Long? = null): Long {
        val t = title.trim()
        require(t.isNotEmpty())
        val id = items.insert(WorkItemEntity(title = t, dueDate = dueDate(due), projectId = projectId, createdAt = clock.nowEpochMillis()))
        if (due == Due.TODAY) planToday(id)
        return id
    }

    suspend fun update(item: WorkItemEntity) = db.withTransaction {
        items.update(item)
        // The plan's slot shows the same title and type.
        val today = clock.logicalToday()
        tasks.forDate(today).filter { it.itemId == item.id }.forEach { tasks.update(it.copy(title = item.title, type = item.type)) }
    }

    suspend fun setDue(id: Long, due: Due) {
        val item = items.get(id) ?: return
        items.update(item.copy(dueDate = dueDate(due)))
        if (due == Due.TODAY) planToday(id)
    }

    suspend fun delete(id: Long) = db.withTransaction {
        val today = clock.logicalToday()
        tasks.forDate(today).filter { it.itemId == id && it.status == TaskStatus.OPEN }.forEach { t ->
            tasks.update(t.copy(status = TaskStatus.MOVED, itemId = null, closedAt = clock.nowEpochMillis()))
        }
        items.delete(id)
    }

    /** Puts the item into today's plan as the last slot (once). */
    suspend fun planToday(id: Long) = db.withTransaction {
        val today = clock.logicalToday()
        val day = tasks.forDate(today)
        if (day.any { it.itemId == id }) return@withTransaction
        val item = items.get(id) ?: return@withTransaction
        if (item.dueDate != today) items.update(item.copy(dueDate = today))
        tasks.insert(
            WorkTaskEntity(
                logicalDate = today, slot = (day.maxOfOrNull { it.slot } ?: 0) + 1, type = item.type, title = item.title,
                projectId = item.projectId, createdAt = clock.nowEpochMillis(), itemId = id,
            ),
        )
    }

    /**
     * Items due today or earlier that are not in today's plan yet join it — what was moved
     * yesterday, or written «на завтра», shows up without retyping. Only for today.
     */
    suspend fun ensureDuePlanned(date: LocalDate = clock.logicalToday()) {
        if (date != clock.logicalToday()) return
        db.withTransaction {
            val day = tasks.forDate(date)
            val planned = day.mapNotNull { it.itemId }.toSet()
            var slot = day.maxOfOrNull { it.slot } ?: 0
            items.dueBy(date).filter { it.id !in planned }.forEach { item ->
                slot++
                if (item.dueDate != date) items.update(item.copy(dueDate = date))
                tasks.insert(
                    WorkTaskEntity(logicalDate = date, slot = slot, type = item.type, title = item.title, projectId = item.projectId,
                        createdAt = clock.nowEpochMillis(), itemId = item.id),
                )
            }
        }
    }

    /** A slot of the plan with no item yet (typed into the plan) gets one, so it has a card. */
    suspend fun itemFor(task: WorkTaskEntity): Long {
        task.itemId?.let { return it }
        val id = items.insert(WorkItemEntity(title = task.title, type = task.type, dueDate = task.logicalDate, projectId = task.projectId, createdAt = clock.nowEpochMillis()))
        tasks.update(task.copy(itemId = id))
        return id
    }

    // ── following the plan's slots ─────────────────────────────────────────────

    suspend fun onTaskClosed(task: WorkTaskEntity) {
        val id = task.itemId ?: return
        items.get(id)?.let { items.update(it.copy(status = ItemStatus.DONE, doneAt = clock.nowEpochMillis())) }
    }

    /** «Перенести» at the last stone: the item goes to the next day instead of being lost. */
    suspend fun onTaskMoved(task: WorkTaskEntity) {
        val id = task.itemId ?: return
        items.get(id)?.let { items.update(it.copy(dueDate = task.logicalDate.plusDays(1), status = ItemStatus.OPEN)) }
    }

    suspend fun onTaskReopened(task: WorkTaskEntity) {
        val id = task.itemId ?: return
        items.get(id)?.let { items.update(it.copy(status = ItemStatus.OPEN, doneAt = null, dueDate = task.logicalDate)) }
    }

    /** Slots removed from the plan: their items go back to the inbox, not lost and not re-added today. */
    suspend fun onRemovedFromPlan(itemIds: Collection<Long>) {
        itemIds.forEach { id -> items.get(id)?.takeIf { it.status == ItemStatus.OPEN }?.let { items.update(it.copy(dueDate = null)) } }
    }

    suspend fun complete(id: Long) = db.withTransaction {
        val now = clock.nowEpochMillis()
        items.get(id)?.let { items.update(it.copy(status = ItemStatus.DONE, doneAt = now)) }
        tasks.forDate(clock.logicalToday()).filter { it.itemId == id && it.status == TaskStatus.OPEN }.forEach {
            tasks.update(it.copy(status = TaskStatus.DONE, checklistJson = "[true,true,true,true]", closedAt = now))
        }
    }

    suspend fun reopen(id: Long) {
        items.get(id)?.let { items.update(it.copy(status = ItemStatus.OPEN, doneAt = null)) }
    }

    // ── checklist ──────────────────────────────────────────────────────────────

    private val checkSerializer = ListSerializer(Check.serializer())
    fun checklist(item: WorkItemEntity): List<Check> =
        item.checklistJson?.let { runCatching { json.decodeFromString(checkSerializer, it) }.getOrNull() } ?: emptyList()
    fun checklistJson(list: List<Check>): String? = if (list.isEmpty()) null else json.encodeToString(checkSerializer, list)

    // ── focus ──────────────────────────────────────────────────────────────────

    /** Adds a focus session's time to the item and to the day's log. */
    suspend fun addFocus(id: Long, ms: Long) {
        if (ms <= 0) return
        items.get(id)?.let { items.update(it.copy(spentMs = it.spentMs + ms)) }
        items.insertFocus(FocusLogEntity(logicalDate = clock.logicalToday(), itemId = id, ms = ms))
    }
}
