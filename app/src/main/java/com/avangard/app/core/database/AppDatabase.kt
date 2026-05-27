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
    version = 7,
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

        /**
         * Adds:
         *   * partial UNIQUE index on focus_session.ended_at WHERE ended_at IS NULL —
         *     enforces "at most one active focus session" at the SQLite layer, so
         *     rapid taps on FlashButton can no longer race past the check;
         *   * index on focus_session.started_at — observeActive ORDER BY started_at
         *     DESC LIMIT 1 was a full scan in v4.
         *
         * Before creating the partial unique index we end every stray active row
         * but the most recent one — if a v4 build broke the invariant, the
         * migration would otherwise fail with a UNIQUE constraint violation.
         */
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Quiesce any stray active sessions before the unique index is built.
                db.execSQL(
                    """
                    UPDATE focus_session
                    SET ended_at = started_at
                    WHERE ended_at IS NULL
                      AND id NOT IN (
                        SELECT id FROM focus_session
                        WHERE ended_at IS NULL
                        ORDER BY started_at DESC
                        LIMIT 1
                      )
                    """.trimIndent()
                )
                db.execSQL(
                    // Index a constant for every active row: SQLite treats NULLs as
                    // distinct, so a plain `UNIQUE(ended_at) WHERE ended_at IS NULL`
                    // would not deduplicate active sessions. The literal-1 expression
                    // collapses all qualifying rows to the same indexed value, so
                    // a second active insert hits the UNIQUE constraint.
                    "CREATE UNIQUE INDEX IF NOT EXISTS uniq_focus_active " +
                        "ON focus_session((1)) WHERE ended_at IS NULL"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_focus_session_started_at " +
                        "ON focus_session(started_at)"
                )
            }
        }

        /**
         * v6: adds the per-day journal entry. Capped to 500 chars in the
         * use-case layer; the column itself is plain TEXT so old rows just
         * carry NULL until the operator writes one.
         */
        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE daily_session ADD COLUMN journal_entry TEXT")
            }
        }

        /**
         * v7: replaces the global per-day MVD flag with a per-Core mode.
         * Adds `core_mode TEXT` ("Standard" / "Mvd"); back-fills from legacy
         * `mvd_active` so existing Approved days keep their classification:
         *   * mvd_active = 1 → core_mode = "Mvd"
         *   * mvd_active = 0 AND core_status = 1 (Approved) → core_mode = "Standard"
         * Idle/Failed days stay NULL. `mvd_active` column survives for
         * BackupBundle v2 compatibility but is no longer written to.
         */
        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE daily_session ADD COLUMN core_mode TEXT")
                db.execSQL(
                    """
                    UPDATE daily_session
                    SET core_mode = CASE
                        WHEN mvd_active = 1 AND core_status = 1 THEN 'Mvd'
                        WHEN mvd_active = 0 AND core_status = 1 THEN 'Standard'
                        ELSE NULL
                    END
                    """.trimIndent()
                )
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
