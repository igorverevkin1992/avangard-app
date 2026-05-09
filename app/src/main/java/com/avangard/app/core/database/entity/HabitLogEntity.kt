package com.avangard.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "habit_log",
    primaryKeys = ["date_epoch", "habit_code"],
    indices = [
        Index("habit_code"),
        Index("date_epoch"),
    ],
)
data class HabitLogEntity(
    @ColumnInfo(name = "date_epoch") val dateEpoch: Long,
    @ColumnInfo(name = "habit_code") val habitCode: String,
    @ColumnInfo(name = "completed_at") val completedAt: Long,
)
