package com.avangard.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Event log of Flash (Vspyshka) micro-sessions per direction.
 * Active session has [endedAt] = null. There can be at most one active globally —
 * the use case layer enforces it; the DAO surfaces it via observeActive().
 */
@Entity(
    tableName = "focus_session",
    indices = [
        Index("date_epoch"),
        Index("habit_code"),
    ],
)
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "date_epoch") val dateEpoch: Long,
    @ColumnInfo(name = "habit_code") val habitCode: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long? = null,
)
