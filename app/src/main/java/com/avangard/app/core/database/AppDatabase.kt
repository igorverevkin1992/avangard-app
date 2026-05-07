package com.avangard.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.avangard.app.core.database.dao.DailyLogDao
import com.avangard.app.core.database.dao.SystemMetricDao
import com.avangard.app.core.database.entity.DailyLogEntity
import com.avangard.app.core.database.entity.SystemMetricEntity

@Database(
    entities = [DailyLogEntity::class, SystemMetricEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dailyLogDao(): DailyLogDao
    abstract fun systemMetricDao(): SystemMetricDao

    companion object {
        const val DB_NAME = "avangard.db"
    }
}
