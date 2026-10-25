package com.itermeum.app.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

enum class FormationType { CATECHESIS, MASS }

@Entity(tableName = "formation_event", indices = [Index("logicalDate")])
data class FormationEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: FormationType,
    val logicalDate: LocalDate,
    val startAt: Long,
    val endAt: Long,
    val attended: Boolean? = null,
    /** Section 3.5: «пропуск по командировке». */
    val missedForTravel: Boolean = false,
)

@Entity(tableName = "catechesis_note")
data class CatechesisNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: Long?,
    val logicalDate: LocalDate,
    val topic: String,
    val theses: String,
    val question: String,
    val homework: String,
    val homeworkDue: LocalDate?,
    val homeworkDone: Boolean = false,
    val savedAt: Long,
)

enum class StreakType { FIDELITY, WAKE, FROG }

@Entity(tableName = "streak_state")
data class StreakStateEntity(
    @PrimaryKey val type: StreakType,
    val current: Int = 0,
    val best: Int = 0,
    val lastCountedDate: LocalDate? = null,
    val totalDays: Int = 0,
)

@Entity(tableName = "freeze_ledger", indices = [Index("yearMonth")])
data class FreezeLedgerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** "2026-11" */
    val yearMonth: String,
    val usedOn: LocalDate,
    val streakType: StreakType,
)

@Entity(tableName = "milestone")
data class MilestoneEntity(
    @PrimaryKey val id: String,
    val achievedAt: Long? = null,
    val seen: Boolean = false,
)

enum class Market { RU, EN, LATAM }

enum class ProjectStage { CONCEPT, SCRIPT, PREPRODUCTION, SHOOTING, POST, RELEASED, ARCHIVED }

@Entity(tableName = "project")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val market: Market,
    val format: String,
    val stage: ProjectStage,
    val dedication: String,
    /** JSON array of 4 booleans (Pius XII criteria). */
    val ethicsJson: String = "[false,false,false,false]",
    val reflectionEnc: String? = null,
    val createdAt: Long,
)

enum class GoalStatus { DONE, PARTIAL, NOT_DONE, OPEN }

@Entity(tableName = "quarter_goal")
data class QuarterGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** "2026-Q4" */
    val quarter: String,
    val text: String,
    val status: GoalStatus = GoalStatus.OPEN,
)

@Entity(tableName = "quarter_income")
data class QuarterIncomeEntity(
    @PrimaryKey val quarter: String,
    val amountRub: Long,
)

@Entity(tableName = "vanity_check")
data class VanityCheckEntity(
    @PrimaryKey val quarter: String,
    val answersEnc: String,
    val completedAt: Long,
)

@Entity(tableName = "weekly_review")
data class WeeklyReviewEntity(
    @PrimaryKey val weekStart: LocalDate,
    val planEnc: String? = null,
    val virtue: String? = null,
    val completedAt: Long? = null,
    val calls: Int = 0,
    val agreements: Int = 0,
    val fidelityDays: Int = 0,
)

@Entity(tableName = "mortification_log")
data class MortificationLogEntity(
    @PrimaryKey val logicalDate: LocalDate,
    val item: String,
    val done: Boolean = false,
)

@Entity(tableName = "travel_period")
data class TravelPeriodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val timezone: String,
    val manual: Boolean,
)

enum class MorningStage { SCHEDULED, RINGING, PROOF_PENDING, PROVEN, HEROIC_DONE, QUARANTINE, DONE, MISSED }

/**
 * Section 5.1 state machine, one row per logical day. Not in the section 8
 * list — recorded in docs/DECISIONS.md (D-07).
 */
@Entity(tableName = "morning_state")
data class MorningStateEntity(
    @PrimaryKey val logicalDate: LocalDate,
    val alarmAt: Long,
    val stage: MorningStage = MorningStage.SCHEDULED,
    val rangAt: Long? = null,
    val provenAt: Long? = null,
    val proofMethod: String? = null,
    val heroicDoneAt: Long? = null,
    val quarantineAnswer: Boolean? = null,
    val quarantineAnsweredAt: Long? = null,
    val gymAt: Long? = null,
    val sick: Boolean = false,
)

@Entity(tableName = "monthly_summary_view")
data class MonthlySummaryViewEntity(
    @PrimaryKey val yearMonth: String,
    val viewedAt: Long,
)
