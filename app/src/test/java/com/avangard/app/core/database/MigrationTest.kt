package com.avangard.app.core.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Migration tests that do not depend on `app/schemas/` JSON snapshots. Each
 * test materialises the pre-migration schema by hand through execSQL on a
 * fresh in-memory(-ish) SupportSQLiteOpenHelper, seeds fixture rows, runs the
 * migration callback directly, then asserts post-conditions.
 *
 * Once app/schemas/ is committed, these can be re-platformed onto Room's
 * MigrationTestHelper for full schema-equivalence validation. Until then this
 * lighter setup covers the behavioural assertions that matter — column adds,
 * table drops, partial unique index handling.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private val openHelpers = mutableListOf<SupportSQLiteOpenHelper>()

    @After
    fun tearDown() {
        openHelpers.forEach { runCatching { it.close() } }
        openHelpers.clear()
    }

    @Test
    fun migration_1_to_2_addsMiddayColumnsAndHabitLog() {
        val (db, helper) = createV1WithFixture()
        AppDatabase.MIGRATION_1_2.migrate(db)

        db.query("SELECT midday_status, midday_action, midday_recorded_at FROM daily_log").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))           // DEFAULT 0
            assertTrue(c.isNull(1))                // nullable, no default
            assertTrue(c.isNull(2))
        }
        db.query(
            "SELECT count(*) FROM sqlite_master WHERE type='table' AND name='habit_log'"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
        helper.close()
    }

    @Test
    fun migration_2_to_3_dropsLegacyTablesPreservesHabitLog() {
        val (db, helper) = createV2WithFixture()
        AppDatabase.MIGRATION_2_3.migrate(db)

        db.query(
            "SELECT count(*) FROM sqlite_master WHERE type='table' AND name IN ('daily_log','system_metrics')"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        // habit_log row from the fixture must survive.
        db.query("SELECT habit_code FROM habit_log").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("03", c.getString(0))
        }
        helper.close()
    }

    @Test
    fun migration_3_to_4_addsSessionTables() {
        val (db, helper) = createV3()
        AppDatabase.MIGRATION_3_4.migrate(db)

        db.query(
            "SELECT count(*) FROM sqlite_master WHERE type='table' AND name IN ('daily_session','focus_session')"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(2, c.getInt(0))
        }
        // Indices exist.
        db.query(
            "SELECT count(*) FROM sqlite_master WHERE type='index' " +
                "AND name IN ('index_focus_session_date_epoch','index_focus_session_habit_code')"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(2, c.getInt(0))
        }
        helper.close()
    }

    @Test
    fun migration_4_to_5_quiescesStrayActivesThenAddsUniqueIndex() {
        val (db, helper) = createV4()
        // Seed two active focus rows — broken invariant possible in v4.
        db.execSQL(
            "INSERT INTO focus_session(date_epoch, habit_code, started_at, ended_at) " +
                "VALUES(1700000000000, '01', 1000, NULL)"
        )
        db.execSQL(
            "INSERT INTO focus_session(date_epoch, habit_code, started_at, ended_at) " +
                "VALUES(1700000000000, '01', 2000, NULL)"
        )

        AppDatabase.MIGRATION_4_5.migrate(db)

        // Exactly one active row survives — the most recent.
        db.query("SELECT count(*), max(started_at) FROM focus_session WHERE ended_at IS NULL").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
            assertEquals(2000L, c.getLong(1))
        }
        // Partial unique index now refuses a second active insert.
        db.execSQL(
            "INSERT INTO focus_session(date_epoch, habit_code, started_at, ended_at) " +
                "VALUES(1700000000000, '02', 3000, 3500)" // ended — fine
        )
        var threw = false
        try {
            db.execSQL(
                "INSERT INTO focus_session(date_epoch, habit_code, started_at, ended_at) " +
                    "VALUES(1700000000000, '02', 4000, NULL)"
            )
        } catch (_: Exception) {
            threw = true
        }
        assertTrue("partial unique index should have rejected a second active row", threw)
        helper.close()
    }

    @Test
    fun migration_5_to_6_addsJournalEntryColumn() {
        val (db, helper) = createV5()
        // Seed a v5 daily row — column doesn't exist yet.
        db.execSQL(
            "INSERT INTO daily_session(date_epoch, mvd_active, core_status) " +
                "VALUES(1700000000000, 1, 0)"
        )

        AppDatabase.MIGRATION_5_6.migrate(db)

        // Column exists and pre-existing row reads NULL.
        db.query("SELECT mvd_active, journal_entry FROM daily_session WHERE date_epoch = 1700000000000").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
            assertTrue("journal_entry must default to NULL for pre-v6 rows", c.isNull(1))
        }

        // Subsequent insert / update can write the field.
        db.execSQL(
            "UPDATE daily_session SET journal_entry = 'итог дня' " +
                "WHERE date_epoch = 1700000000000"
        )
        db.query("SELECT journal_entry FROM daily_session WHERE date_epoch = 1700000000000").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("итог дня", c.getString(0))
        }
        helper.close()
    }

    // --- Schema builders ----------------------------------------------------

    private fun createV1WithFixture(): Pair<SupportSQLiteDatabase, SupportSQLiteOpenHelper> {
        val helper = openV1()
        val db = helper.writableDatabase
        db.execSQL(
            """
            INSERT INTO daily_log(date_epoch, target_artifact, is_completed, eliminated_waste)
            VALUES(1700000000000, 'тест', 1, 3)
            """.trimIndent()
        )
        return db to helper
    }

    private fun createV2WithFixture(): Pair<SupportSQLiteDatabase, SupportSQLiteOpenHelper> {
        // v2 = v1 + midday cols + habit_log (the post-state of MIGRATION_1_2).
        val helper = openWithCallback(2) { db ->
            createV1Schema(db)
            applyV1ToV2(db)
        }
        val db = helper.writableDatabase
        db.execSQL(
            "INSERT INTO habit_log(date_epoch, habit_code, completed_at) " +
                "VALUES(1700000000000, '03', 1500)"
        )
        db.execSQL(
            "INSERT INTO system_metrics(`key`, value) VALUES('focus_mode','1')"
        )
        return db to helper
    }

    private fun createV3(): Pair<SupportSQLiteDatabase, SupportSQLiteOpenHelper> {
        // v3 = v2 + drop(daily_log, system_metrics).
        val helper = openWithCallback(3) { db ->
            createV1Schema(db)
            applyV1ToV2(db)
            applyV2ToV3(db)
        }
        return helper.writableDatabase to helper
    }

    private fun createV4(): Pair<SupportSQLiteDatabase, SupportSQLiteOpenHelper> {
        // v4 = v3 + create(daily_session, focus_session).
        val helper = openWithCallback(4) { db ->
            createV1Schema(db)
            applyV1ToV2(db)
            applyV2ToV3(db)
            applyV3ToV4(db)
        }
        return helper.writableDatabase to helper
    }

    private fun createV5(): Pair<SupportSQLiteDatabase, SupportSQLiteOpenHelper> {
        val helper = openWithCallback(5) { db ->
            createV1Schema(db)
            applyV1ToV2(db)
            applyV2ToV3(db)
            applyV3ToV4(db)
        }
        val db = helper.writableDatabase
        AppDatabase.MIGRATION_4_5.migrate(db)
        return db to helper
    }

    private fun openV1(): SupportSQLiteOpenHelper =
        openWithCallback(1) { db -> createV1Schema(db) }

    private fun openWithCallback(
        version: Int,
        onCreate: (SupportSQLiteDatabase) -> Unit,
    ): SupportSQLiteOpenHelper {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Random db name per call so consecutive tests do not collide on the same file.
        val name = "migration-test-${System.nanoTime()}"
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) = onCreate(db)
                override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) = Unit
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        // Trigger onCreate by opening the writable db.
        helper.writableDatabase
        openHelpers += helper
        return helper
    }

    // --- Raw schema fragments (mirror the Room-generated SQL) ---------------

    private fun createV1Schema(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE daily_log (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                date_epoch INTEGER NOT NULL,
                target_artifact TEXT NOT NULL,
                is_completed INTEGER NOT NULL DEFAULT 0,
                eliminated_waste INTEGER NOT NULL DEFAULT 0,
                failure_cause TEXT,
                corrective_action TEXT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX index_daily_log_date_epoch ON daily_log(date_epoch)")
        db.execSQL(
            """
            CREATE TABLE system_metrics (
                `key` TEXT NOT NULL PRIMARY KEY,
                value TEXT
            )
            """.trimIndent()
        )
    }

    private fun applyV1ToV2(db: SupportSQLiteDatabase) =
        AppDatabase.MIGRATION_1_2.migrate(db)

    private fun applyV2ToV3(db: SupportSQLiteDatabase) =
        AppDatabase.MIGRATION_2_3.migrate(db)

    private fun applyV3ToV4(db: SupportSQLiteDatabase) =
        AppDatabase.MIGRATION_3_4.migrate(db)
}
