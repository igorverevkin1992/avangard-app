package com.itermeum.app.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

enum class FollowUpAnswer { KEPT, NOT_KEPT }

/** Section 5.12. `*Enc` fields hold AES-GCM ciphertext (base64). */
@Entity(tableName = "examen")
data class ExamenEntity(
    @PrimaryKey val logicalDate: LocalDate,
    val gratitudeEnc: String? = null,
    val answersEnc: String? = null,
    val contritionSaid: Boolean = false,
    val propositoEnc: String? = null,
    val previousFollowUp: FollowUpAnswer? = null,
    val aveCount: Int = 0,
    val completedAt: Long? = null,
    /** Section 6.2: required step after a freeze or broken streak. */
    val whatBrokeEnc: String? = null,
    val startedAt: Long? = null,
)

@Entity(tableName = "prayer")
data class PrayerEntity(
    @PrimaryKey val id: String,
    val title: String,
    val text: String,
    val source: String,
    val status: String,
    val legal: String,
    val verify: Boolean,
    val userEditedText: String? = null,
    val orderIndex: Int,
)

enum class SrsStage { READ, CLOZE_25, CLOZE_50, CLOZE_100, RECALL, KNOWN }

@Entity(tableName = "memorization_item")
data class MemorizationItemEntity(
    @PrimaryKey val prayerId: String,
    val stage: SrsStage = SrsStage.READ,
    val easiness: Double = 2.5,
    val intervalDays: Int = 0,
    val repetitions: Int = 0,
    val dueDate: LocalDate? = null,
    val lastReviewed: LocalDate? = null,
    val orderIndex: Int,
    /** Easy answers in a row at the current stage (stage +1 at 2, KNOWN at 3 in RECALL). */
    val consecutiveEasy: Int = 0,
    /** Set when the item was introduced (section 5.13: ≤1 new prayer a week). */
    val introducedOn: LocalDate? = null,
)

@Entity(tableName = "jaculatoria")
data class JaculatoriaEntity(
    @PrimaryKey val id: String,
    val situation: String,
    val text: String,
    val source: String,
    val legal: String,
    val verify: Boolean,
)

@Entity(tableName = "jaculatoria_log", indices = [Index("logicalDate")])
data class JaculatoriaLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jaculatoriaId: String?,
    val situation: String,
    val at: Long,
    val logicalDate: LocalDate,
)

@Entity(tableName = "ascetic_item")
data class AsceticItemEntity(
    @PrimaryKey val id: String,
    val book: String,
    val number: String,
    val theme: String,
    val microCommitment: String,
)

@Entity(tableName = "reading_plan_item")
data class ReadingPlanItemEntity(
    @PrimaryKey val id: String,
    val month: Int,
    val bookId: String,
    val title: String,
    val sectionLabel: String,
    val url: String? = null,
)

@Entity(tableName = "reading_log", indices = [Index("logicalDate")])
data class ReadingLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val logicalDate: LocalDate,
    val bookId: String,
    val minutes: Int,
    /** «одна фраза — фокус дня», encrypted. */
    val phraseEnc: String? = null,
)

@Entity(tableName = "liturgical_day_cache")
data class LiturgicalDayCacheEntity(
    @PrimaryKey val date: LocalDate,
    val season: String,
    val rank: String,
    val color: String,
    val celebrationId: String? = null,
    val sundayCycle: String,
    val weekdayCycle: String,
    val gospelRef: String?,
)

@Entity(tableName = "rosary_progress")
data class RosaryProgressEntity(
    @PrimaryKey val logicalDate: LocalDate,
    /** 0..5 decades completed. */
    val decadesDone: Int = 0,
    val mysterySet: String,
    val updatedAt: Long,
)
