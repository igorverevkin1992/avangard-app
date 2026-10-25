package com.itermeum.app.core.database.entity

import androidx.room.Entity
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalTime

enum class NormFrequency { DAILY, WEEKLY, MONTHLY, QUARTERLY }

enum class NormCategory { MORNING, WORK, PRAYER, PATH }

/**
 * Section 4 norm. `daysOfWeek` is a bitmask Mon=1 … Sun=64. `defaultTime`
 * is the spec default; `userTime` overrides it (section 4, rule 3).
 */
@Serializable
@Entity(tableName = "norm")
data class NormEntity(
    @PrimaryKey val id: String,
    val title: String,
    val category: NormCategory,
    @Contextual val defaultTime: LocalTime?,
    val durationMin: Int,
    val requiresBaptism: Boolean,
    val isCore: Boolean,
    val frequency: NormFrequency,
    val daysOfWeek: Int,
    val enabled: Boolean = true,
    @Contextual val userTime: LocalTime? = null,
    /** True for norms whose default time shifts with wakeTime (section 4, rule 2). */
    val followsWakeTime: Boolean,
    val orderIndex: Int,
    /** Section 6.6: a non-core norm paused for a week after three failures. */
    @Contextual val pausedUntil: LocalDate? = null,
)

enum class CompletionQuality { FULL, PARTIAL, LATE }

@Serializable
@Entity(
    tableName = "norm_completion",
    indices = [Index(value = ["normId", "logicalDate"], unique = true), Index("logicalDate")],
)
data class NormCompletionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val normId: String,
    @Contextual val logicalDate: LocalDate,
    val completedAt: Long,
    val quality: CompletionQuality = CompletionQuality.FULL,
    val note: String? = null,
)
