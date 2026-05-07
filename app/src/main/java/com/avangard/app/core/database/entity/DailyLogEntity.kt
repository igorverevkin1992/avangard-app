package com.avangard.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "daily_log",
    indices = [Index(value = ["date_epoch"], unique = true)],
)
data class DailyLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "date_epoch")
    val dateEpoch: Long,

    @ColumnInfo(name = "target_artifact")
    val targetArtifact: String,

    @ColumnInfo(name = "is_completed")
    val isCompleted: Int = 0,

    @ColumnInfo(name = "eliminated_waste")
    val eliminatedWaste: Int = 0,

    @ColumnInfo(name = "failure_cause")
    val failureCause: String? = null,

    @ColumnInfo(name = "corrective_action")
    val correctiveAction: String? = null,
)
