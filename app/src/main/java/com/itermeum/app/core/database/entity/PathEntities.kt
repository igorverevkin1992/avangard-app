package com.itermeum.app.core.database.entity

import androidx.room.Entity
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

enum class FormationType { CATECHESIS, MASS }

@Serializable
@Entity(tableName = "formation_event", indices = [Index("logicalDate")])
data class FormationEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: FormationType,
    @Contextual val logicalDate: LocalDate,
    val startAt: Long,
    val endAt: Long,
    val attended: Boolean? = null,
    /** Section 3.5: «пропуск по командировке». */
    val missedForTravel: Boolean = false,
)

@Serializable
@Entity(tableName = "catechesis_note")
data class CatechesisNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: Long?,
    @Contextual val logicalDate: LocalDate,
    val topic: String,
    val theses: String,
    val question: String,
    val homework: String,
    @Contextual val homeworkDue: LocalDate?,
    val homeworkDone: Boolean = false,
    val savedAt: Long,
)

enum class StreakType { FIDELITY, WAKE, FROG }

@Serializable
@Entity(tableName = "streak_state")
data class StreakStateEntity(
    @PrimaryKey val type: StreakType,
    val current: Int = 0,
    val best: Int = 0,
    @Contextual val lastCountedDate: LocalDate? = null,
    val totalDays: Int = 0,
)

@Serializable
@Entity(tableName = "freeze_ledger", indices = [Index("yearMonth")])
data class FreezeLedgerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** "2026-11" */
    val yearMonth: String,
    @Contextual val usedOn: LocalDate,
    val streakType: StreakType,
)

@Serializable
@Entity(tableName = "milestone")
data class MilestoneEntity(
    @PrimaryKey val id: String,
    val achievedAt: Long? = null,
    val seen: Boolean = false,
)

enum class Market { RU, EN, LATAM }

enum class ProjectStage { CONCEPT, SCRIPT, PREPRODUCTION, SHOOTING, POST, RELEASED, ARCHIVED }

@Serializable
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

@Serializable
@Entity(tableName = "quarter_goal")
data class QuarterGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** "2026-Q4" */
    val quarter: String,
    val text: String,
    val status: GoalStatus = GoalStatus.OPEN,
)

@Serializable
@Entity(tableName = "quarter_income")
data class QuarterIncomeEntity(
    @PrimaryKey val quarter: String,
    val amountRub: Long,
)

@Serializable
@Entity(tableName = "vanity_check")
data class VanityCheckEntity(
    @PrimaryKey val quarter: String,
    val answersEnc: String,
    val completedAt: Long,
)

@Serializable
@Entity(tableName = "weekly_review")
data class WeeklyReviewEntity(
    @PrimaryKey @Contextual val weekStart: LocalDate,
    val planEnc: String? = null,
    val virtue: String? = null,
    val completedAt: Long? = null,
    val calls: Int = 0,
    val agreements: Int = 0,
    val fidelityDays: Int = 0,
)

@Serializable
@Entity(tableName = "mortification_log")
data class MortificationLogEntity(
    @PrimaryKey @Contextual val logicalDate: LocalDate,
    val item: String,
    val done: Boolean = false,
)

@Serializable
@Entity(tableName = "travel_period")
data class TravelPeriodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @Contextual val startDate: LocalDate,
    @Contextual val endDate: LocalDate? = null,
    val timezone: String,
    val manual: Boolean,
)

enum class MorningStage { SCHEDULED, RINGING, PROOF_PENDING, PROVEN, HEROIC_DONE, QUARANTINE, DONE, MISSED }

/**
 * Section 5.1 state machine, one row per logical day. Not in the section 8
 * list — recorded in docs/DECISIONS.md (D-07).
 */
@Serializable
@Entity(tableName = "morning_state")
data class MorningStateEntity(
    @PrimaryKey @Contextual val logicalDate: LocalDate,
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

@Serializable
@Entity(tableName = "monthly_summary_view")
data class MonthlySummaryViewEntity(
    @PrimaryKey val yearMonth: String,
    val viewedAt: Long,
)
