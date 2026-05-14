package com.avangard.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.avangard.app.core.database.dao.DailySessionDao
import com.avangard.app.core.database.dao.FocusSessionDao
import com.avangard.app.core.database.dao.HabitLogDao
import com.avangard.app.core.database.entity.DailySessionEntity
import com.avangard.app.core.database.entity.FocusSessionEntity
import com.avangard.app.core.database.entity.HabitLogEntity

@Database(
    entities = [
        HabitLogEntity::class,
        DailySessionEntity::class,
        FocusSessionEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitLogDao(): HabitLogDao
    abstract fun dailySessionDao(): DailySessionDao
    abstract fun focusSessionDao(): FocusSessionDao

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

        // Adds the daily_session ledger and focus_session event log.
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS daily_session (
                        date_epoch INTEGER NOT NULL PRIMARY KEY,
                        mvd_active INTEGER NOT NULL DEFAULT 0,
                        core_status INTEGER NOT NULL DEFAULT 0,
                        core_prompt TEXT,
                        core_authorized_at INTEGER,
                        core_defect_kind INTEGER,
                        infra_02_status INTEGER NOT NULL DEFAULT 0,
                        infra_03_status INTEGER NOT NULL DEFAULT 0,
                        infra_04_status INTEGER NOT NULL DEFAULT 0,
                        infra_05_status INTEGER NOT NULL DEFAULT 0,
                        evening_closed INTEGER NOT NULL DEFAULT 0,
                        evening_closed_at INTEGER,
                        virt_rationality INTEGER,
                        virt_independence INTEGER,
                        virt_honesty INTEGER,
                        virt_justice INTEGER,
                        bottleneck_for_next_week TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS focus_session (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        date_epoch INTEGER NOT NULL,
                        habit_code TEXT NOT NULL,
                        started_at INTEGER NOT NULL,
                        ended_at INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_focus_session_date_epoch ON focus_session(date_epoch)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_focus_session_habit_code ON focus_session(habit_code)"
                )
            }
        }
    }
}
