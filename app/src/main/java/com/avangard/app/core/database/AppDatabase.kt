package com.avangard.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.avangard.app.core.database.dao.HabitLogDao
import com.avangard.app.core.database.entity.HabitLogEntity

@Database(
    entities = [HabitLogEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitLogDao(): HabitLogDao

    companion object {
        const val DB_NAME = "avangard.db"

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE daily_log ADD COLUMN midday_status INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL("ALTER TABLE daily_log ADD COLUMN midday_action TEXT")
                db.execSQL("ALTER TABLE daily_log ADD COLUMN midday_recorded_at INTEGER")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS habit_log (
                        date_epoch INTEGER NOT NULL,
                        habit_code TEXT NOT NULL,
                        completed_at INTEGER NOT NULL,
                        PRIMARY KEY(date_epoch, habit_code)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_habit_log_habit_code ON habit_log(habit_code)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_habit_log_date_epoch ON habit_log(date_epoch)"
                )
            }
        }

        // v3.0 (Verevkin's Lab) drops the report/flag tables; habit_log survives untouched.
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS daily_log")
                db.execSQL("DROP TABLE IF EXISTS system_metrics")
            }
        }
    }
}
