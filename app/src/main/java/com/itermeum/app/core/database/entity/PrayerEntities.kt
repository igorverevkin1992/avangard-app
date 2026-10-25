package com.itermeum.app.core.database.entity

import androidx.room.Entity
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

enum class FollowUpAnswer { KEPT, NOT_KEPT }

/** Section 5.12. `*Enc` fields hold AES-GCM ciphertext (base64). */
@Serializable
@Entity(tableName = "examen")
data class ExamenEntity(
    @PrimaryKey @Contextual val logicalDate: LocalDate,
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

@Serializable
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

@Serializable
@Entity(tableName = "memorization_item")
data class MemorizationItemEntity(
    @PrimaryKey val prayerId: String,
    val stage: SrsStage = SrsStage.READ,
    val easiness: Double = 2.5,
    val intervalDays: Int = 0,
    val repetitions: Int = 0,
    @Contextual val dueDate: LocalDate? = null,
    @Contextual val lastReviewed: LocalDate? = null,
    val orderIndex: Int,
    /** Easy answers in a row at the current stage (stage +1 at 2, KNOWN at 3 in RECALL). */
    val consecutiveEasy: Int = 0,
    /** Set when the item was introduced (section 5.13: ≤1 new prayer a week). */
    @Contextual val introducedOn: LocalDate? = null,
)

@Serializable
@Entity(tableName = "jaculatoria")
data class JaculatoriaEntity(
    @PrimaryKey val id: String,
    val situation: String,
    val text: String,
    val source: String,
    val legal: String,
    val verify: Boolean,
)

@Serializable
@Entity(tableName = "jaculatoria_log", indices = [Index("logicalDate")])
data class JaculatoriaLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jaculatoriaId: String?,
    val situation: String,
    val at: Long,
    @Contextual val logicalDate: LocalDate,
)

@Serializable
@Entity(tableName = "ascetic_item")
data class AsceticItemEntity(
    @PrimaryKey val id: String,
    val book: String,
    val number: String,
    val theme: String,
    val microCommitment: String,
)

@Serializable
@Entity(tableName = "reading_plan_item")
data class ReadingPlanItemEntity(
    @PrimaryKey val id: String,
    val month: Int,
    val bookId: String,
    val title: String,
    val sectionLabel: String,
    val url: String? = null,
)

@Serializable
@Entity(tableName = "reading_log", indices = [Index("logicalDate")])
data class ReadingLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @Contextual val logicalDate: LocalDate,
    val bookId: String,
    val minutes: Int,
    /** «одна фраза — фокус дня», encrypted. */
    val phraseEnc: String? = null,
)

@Serializable
@Entity(tableName = "liturgical_day_cache")
data class LiturgicalDayCacheEntity(
    @PrimaryKey @Contextual val date: LocalDate,
    val season: String,
    val rank: String,
    val color: String,
    val celebrationId: String? = null,
    val sundayCycle: String,
    val weekdayCycle: String,
    val gospelRef: String?,
)

@Serializable
@Entity(tableName = "rosary_progress")
data class RosaryProgressEntity(
    @PrimaryKey @Contextual val logicalDate: LocalDate,
    /** 0..5 decades completed. */
    val decadesDone: Int = 0,
    val mysterySet: String,
    val updatedAt: Long,
)
