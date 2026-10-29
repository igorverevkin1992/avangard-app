package com.itermeum.app.core.data

import androidx.room.withTransaction
import com.itermeum.app.core.common.Clock
import com.itermeum.app.core.engine.DayScheduleEngine
import com.itermeum.app.core.content.NormCatalog
import com.itermeum.app.core.database.AppDatabase
import com.itermeum.app.core.database.dao.DayPlanDao
import com.itermeum.app.core.database.dao.OutreachDao
import com.itermeum.app.core.database.dao.ScriptDao
import com.itermeum.app.core.database.dao.TaskDao
import com.itermeum.app.core.database.entity.DayPlanEntity
import com.itermeum.app.core.database.entity.OutreachKind
import com.itermeum.app.core.database.entity.OutreachLogEntity
import com.itermeum.app.core.database.entity.OutreachOutcome
import com.itermeum.app.core.database.entity.ScriptSessionEntity
import com.itermeum.app.core.database.entity.TaskStatus
import com.itermeum.app.core.database.entity.TaskType
import com.itermeum.app.core.database.entity.TransitionRitualEntity
import com.itermeum.app.core.database.entity.WorkTaskEntity
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Sections 5.2–5.5: plan of the day, outreach, last stone, transition ritual, script sessions. */
@Singleton
class WorkRepository @Inject constructor(
    private val db: AppDatabase,
    private val dayPlanDao: DayPlanDao,
    private val taskDao: TaskDao,
    private val outreachDao: OutreachDao,
    private val scriptDao: ScriptDao,
    private val norms: NormRepository,
    private val profiles: ProfileRepository,
    private val clock: Clock,
) {
    data class TaskDraft(val slot: Int, val type: TaskType, val title: String, val projectId: Long? = null, val followUpContact: String? = null)

    fun observePlan(date: LocalDate): Flow<DayPlanEntity?> = dayPlanDao.observe(date)
    fun observeTasks(date: LocalDate): Flow<List<WorkTaskEntity>> = taskDao.observeForDate(date)
    suspend fun tasksFor(date: LocalDate): List<WorkTaskEntity> = taskDao.forDate(date)
    suspend fun attemptsBeforeCutoff(date: LocalDate): Int = outreachDao.countBeforeCutoff(date)

    /**
     * Evening catch-up (D-34): tasks done during a chaotic day are recorded as
     * done for the statistics and the Examen, without crediting «последний камень».
     */
    suspend fun markTasksDoneLate(date: LocalDate, count: Int) {
        taskDao.forDate(date).filter { it.status == TaskStatus.OPEN }.sortedBy { it.slot }.take(count).forEach {
            taskDao.update(it.copy(status = TaskStatus.DONE, closedAt = clock.nowEpochMillis()))
        }
    }
    fun observeOutreach(date: LocalDate): Flow<List<OutreachLogEntity>> = outreachDao.observeForDate(date)
    fun observeOutreachCount(date: LocalDate): Flow<Int> = outreachDao.observeCount(date)
    fun observeAgreements(date: LocalDate): Flow<Int> = outreachDao.observeAgreements(date)
    fun observeRitualCount(date: LocalDate): Flow<Int> = scriptDao.observeRitualCount(date)
    fun observeActiveScript(): Flow<ScriptSessionEntity?> = scriptDao.observeActive()
    fun observeScriptCount(date: LocalDate): Flow<Int> = scriptDao.observeCountForDate(date)

    /**
     * Section 5.2, relaxed by D-51: a plan is at least one named task. Hero calls
     * are recommended (the screen says so) but no longer block saving — on a trip
     * or a day without calls the plan must still be savable.
     */
    @Suppress("UNUSED_PARAMETER")
    fun planIsValid(drafts: List<TaskDraft>, shootDay: Boolean): Boolean =
        drafts.isNotEmpty() && drafts.none { it.title.isBlank() }

    suspend fun savePlan(date: LocalDate, drafts: List<TaskDraft>, intention: String?, shootDay: Boolean) {
        require(planIsValid(drafts, shootDay))
        val now = clock.nowEpochMillis()
        val afterCutoff = !clock.now().isBefore(date.atTime(cutoffFor()))
        db.withTransaction {
            val before = taskDao.forDate(date)
            val existing = before.associateBy { it.slot }
            // A removed slot shifts the ones after it: keep each task's status by its title first.
            val byTitle = before.associateBy { it.title }
            val plan = dayPlanDao.get(date) ?: DayPlanEntity(logicalDate = date)
            dayPlanDao.upsert(plan.copy(intention = intention, savedAt = plan.savedAt ?: now, shootDay = shootDay))
            taskDao.deleteForDate(date)
            taskDao.upsertAll(
                drafts.map { d ->
                    val old = existing[d.slot]?.takeIf { it.title == d.title.trim() } ?: byTitle[d.title.trim()] ?: existing[d.slot]
                    WorkTaskEntity(
                        id = old?.id ?: 0, logicalDate = date, slot = d.slot, type = d.type, title = d.title.trim(),
                        projectId = d.projectId, status = old?.status ?: TaskStatus.OPEN, checklistJson = old?.checklistJson,
                        closedAt = old?.closedAt, followUpContact = d.followUpContact?.trim(), createdAt = old?.createdAt ?: now,
                        movedAfterCutoff = (old?.movedAfterCutoff ?: false) || (afterCutoff && old != null && old.title != d.title.trim()),
                    )
                },
            )
        }
        // Counted when saved before 12:30 (section 4).
        val plannedBy = profiles.get().workStart.plusMinutes(30)
        // Counted for its day when saved before workStart + 30 — including the evening before (D-34).
        if (clock.now().isBefore(date.atTime(plannedBy))) norms.markDone(NormCatalog.DAY_PLAN, date)
    }

    /** Section 5.3: an attempt = call or message with a concrete request; rejection counts too. */
    suspend fun logOutreach(date: LocalDate, person: String, role: String?, projectId: Long?, kind: OutreachKind, outcome: OutreachOutcome, agreementDate: LocalDate?) {
        // Measured on the logical day: at 00:30 the calendar date is already tomorrow.
        val before = clock.logicalToday() == date && clock.now().isBefore(date.atTime(cutoffFor()))
        outreachDao.insert(
            OutreachLogEntity(logicalDate = date, at = clock.nowEpochMillis(), personLabel = person.trim(), role = role?.trim(),
                projectId = projectId, kind = kind, outcome = outcome, agreementDate = agreementDate, beforeCutoff = before),
        )
        val target = targetFor(date)
        if (outreachDao.countBeforeCutoff(date) >= target && !norms.isDone(NormCatalog.FROG, date)) norms.markDone(NormCatalog.FROG, date)
    }

    /** Outreach cutoff for the current profile (14:00, later for a late `workStart`). */
    /** The plan counts for its day when saved before this (workStart + 30, section 4). */
    suspend fun planDeadline(): LocalTime = profiles.get().workStart.plusMinutes(30)

    suspend fun cutoffFor(): LocalTime = DayScheduleEngine.frogCutoff(profiles.get())

    suspend fun targetFor(date: LocalDate): Int {
        val input = norms.inputFor(date)
        return if (input.travel || input.shootDay) 2 else input.profile.outreachTarget
    }

    suspend fun recentPeople(): List<String> = outreachDao.recentPeople()

    /** Section 5.4: 4-point checklist; closing without 4/4 = «закрыто с долгом». */
    suspend fun closeTask(task: WorkTaskEntity, checklist: List<Boolean>) {
        val status = if (checklist.size == 4 && checklist.all { it }) TaskStatus.DONE else TaskStatus.DONE_WITH_DEBT
        taskDao.update(task.copy(status = status, checklistJson = checklist.joinToString(",", "[", "]"), closedAt = clock.nowEpochMillis()))
        checkLastStone(task.logicalDate)
    }

    suspend fun moveTask(task: WorkTaskEntity) {
        taskDao.update(task.copy(status = TaskStatus.MOVED, closedAt = clock.nowEpochMillis()))
        checkLastStone(task.logicalDate)
    }

    /** «Сделать следующей» (D-44): the task moves just before the first open one; slots are renumbered. */
    suspend fun makeNext(task: WorkTaskEntity) = db.withTransaction {
        val tasks = taskDao.forDate(task.logicalDate).sortedBy { it.slot }.toMutableList()
        val target = tasks.firstOrNull { it.id == task.id } ?: return@withTransaction
        tasks.remove(target)
        val at = tasks.indexOfFirst { it.status == TaskStatus.OPEN }.let { if (it < 0) tasks.size else it }
        tasks.add(at, target)
        tasks.forEachIndexed { i, t -> if (t.slot != i + 1) taskDao.update(t.copy(slot = i + 1)) }
    }

    suspend fun reopenTask(task: WorkTaskEntity) = taskDao.update(task.copy(status = TaskStatus.OPEN, closedAt = null, checklistJson = null))

    private suspend fun checkLastStone(date: LocalDate) {
        val tasks = taskDao.forDate(date)
        // Any number of tasks (D-41) — also a two-task plan saved from the evening Examen.
        val allClosed = tasks.isNotEmpty() && tasks.none { it.status == TaskStatus.OPEN }
        val inTime = clock.now().toLocalDate() == date && clock.localTime().isBefore(LAST_STONE_DEADLINE)
        if (allClosed && inTime && !norms.isDone(NormCatalog.LAST_STONE, date)) norms.markDone(NormCatalog.LAST_STONE, date)
    }

    suspend fun logRitual() = scriptDao.insertRitual(TransitionRitualEntity(at = clock.nowEpochMillis(), logicalDate = clock.logicalToday()))

    suspend fun startScript(): Long {
        scriptDao.active()?.let { return it.id }
        return scriptDao.insert(ScriptSessionEntity(logicalDate = clock.logicalToday(), startedAt = clock.nowEpochMillis()))
    }

    suspend fun stopScript(note: String?) {
        val active = scriptDao.active() ?: return
        val end = clock.nowEpochMillis()
        scriptDao.update(active.copy(endedAt = end, minutes = ((end - active.startedAt) / 60_000L).toInt(), note = note))
    }

    suspend fun scriptMinutes(from: LocalDate, to: LocalDate) = scriptDao.minutesBetween(from, to)

    companion object {
        val CUTOFF: LocalTime = LocalTime.of(14, 0)
        val LAST_STONE_DEADLINE: LocalTime = LocalTime.of(21, 0)
        val CHECKLIST_ITEMS = 4
    }
}
