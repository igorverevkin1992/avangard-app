package com.itermeum.app.core.database.entity

import androidx.room.Entity
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

@Serializable
@Entity(tableName = "day_plan")
data class DayPlanEntity(
    @PrimaryKey @Contextual val logicalDate: LocalDate,
    val intention: String? = null,
    val savedAt: Long? = null,
    val travelMode: Boolean = false,
    val sick: Boolean = false,
    val shootDay: Boolean = false,
)

enum class TaskType { OUTREACH, SHOOT, OPS, DOCS, AI_GEN, SCRIPT }

enum class TaskStatus { OPEN, DONE, DONE_WITH_DEBT, MOVED }

@Serializable
@Entity(tableName = "work_task", indices = [Index("logicalDate")])
data class WorkTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @Contextual val logicalDate: LocalDate,
    /** 1..3 */
    val slot: Int,
    val type: TaskType,
    val title: String,
    val projectId: Long? = null,
    val status: TaskStatus = TaskStatus.OPEN,
    /** JSON array of 4 booleans — the «последний камень» checklist. */
    val checklistJson: String? = null,
    val closedAt: Long? = null,
    /** SHOOT days: «кому позвонить после». */
    val followUpContact: String? = null,
    val createdAt: Long,
    /** Section 5.2: edits after 14:00 are flagged as «перенос». */
    val movedAfterCutoff: Boolean = false,
    /** D-64: the work item this slot of the day's plan carries out (its notes, checklist, contact). */
    val itemId: Long? = null,
)

/** D-64: a work item — the inbox and every task, with or without a day: the master record of a task. */
enum class ItemStatus { OPEN, DONE, DROPPED }

@Serializable
@Entity(tableName = "work_item", indices = [Index("status"), Index("dueDate")])
data class WorkItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val type: TaskType = TaskType.OPS,
    val status: ItemStatus = ItemStatus.OPEN,
    /** Null — in the inbox, no day yet. */
    @Contextual val dueDate: LocalDate? = null,
    val projectId: Long? = null,
    val notes: String? = null,
    val contactName: String? = null,
    val contactPhone: String? = null,
    val link: String? = null,
    /** JSON array of {"t": text, "d": done}. */
    val checklistJson: String? = null,
    /** Time spent in focus on this item, all days together. */
    val spentMs: Long = 0,
    val createdAt: Long,
    val doneAt: Long? = null,
)

/** D-64: one focus session on an item — for the day's and the week's totals. */
@Serializable
@Entity(tableName = "focus_log", indices = [Index("logicalDate")])
data class FocusLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @Contextual val logicalDate: LocalDate,
    val itemId: Long,
    val ms: Long,
)

enum class OutreachKind { CALL, MESSAGE }

/** CALLBACK — «вернуться»: the hero asked to call back later (D-36). Appended last: stored by name. */
enum class OutreachOutcome { REACHED, NO_ANSWER, AGREEMENT, REJECTION, CALLBACK }

@Serializable
@Entity(tableName = "outreach_log", indices = [Index("logicalDate")])
data class OutreachLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @Contextual val logicalDate: LocalDate,
    val at: Long,
    val personLabel: String,
    val role: String? = null,
    val projectId: Long? = null,
    val kind: OutreachKind,
    val outcome: OutreachOutcome,
    @Contextual val agreementDate: LocalDate? = null,
    /** True when logged before the 14:00 cutoff (counts toward the norm). */
    val beforeCutoff: Boolean,
)

@Serializable
@Entity(tableName = "script_session", indices = [Index("logicalDate")])
data class ScriptSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @Contextual val logicalDate: LocalDate,
    val startedAt: Long,
    val endedAt: Long? = null,
    val minutes: Int = 0,
    val note: String? = null,
)

@Serializable
@Entity(tableName = "transition_ritual")
data class TransitionRitualEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    @Contextual val logicalDate: LocalDate,
)
