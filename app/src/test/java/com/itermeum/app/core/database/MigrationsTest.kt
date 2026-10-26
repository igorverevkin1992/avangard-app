package com.itermeum.app.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Migrations are checked against the committed Room schemas
 * (`app/schemas/.../N.json`): the real v1 the phone already has, not a
 * hand-written approximation. `runMigrationsAndValidate` fails on any
 * column, type, index or nullability drift.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationsTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun `1 to 2 keeps the Saturday Mass time and defaults the other day`() {
        helper.createDatabase(DB, 1).apply {
            execSQL(
                "INSERT INTO profile (id,name,wakeTime,wakeTimeWeekend,bedTime,workStart,workEnd,timezoneHome,baptized,massTime,massDay," +
                    "contentPackVersion,onboardingDone,outreachTarget,wakeProofMethod,alarmSound,alarmVolume,notificationTone,travelAuto," +
                    "confessionEveryDays,examenPinEnabled) VALUES (1,'Игорь',390,450,1380,720,1200,'Europe/Moscow',0,1140,6,1,1,5,'QR',1,100,'FIRM',1,30,0)",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(DB, 2, true, Migrations.MIGRATION_1_2)
        db.query("SELECT massTimeSaturday, massTimeSunday, massDay, name FROM profile").use { c ->
            c.moveToFirst()
            assertEquals(1140, c.getInt(0))
            assertEquals(600, c.getInt(1))
            assertEquals(6, c.getInt(2))
            assertEquals("Игорь", c.getString(3))
        }
    }

    @Test
    fun `1 to 2 with a Sunday Mass and no profile time`() {
        helper.createDatabase(DB, 1).apply {
            execSQL(
                "INSERT INTO profile (id,name,wakeTime,wakeTimeWeekend,bedTime,workStart,workEnd,timezoneHome,baptized,massTime,massDay," +
                    "contentPackVersion,onboardingDone,outreachTarget,wakeProofMethod,alarmSound,alarmVolume,notificationTone,travelAuto," +
                    "confessionEveryDays,examenPinEnabled) VALUES (1,'',390,450,1380,720,1200,'Europe/Moscow',0,NULL,7,1,0,5,'QR',1,100,'FIRM',1,30,0)",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(DB, 2, true, Migrations.MIGRATION_1_2)
        db.query("SELECT massTimeSaturday, massTimeSunday FROM profile").use { c ->
            c.moveToFirst()
            assertEquals(1080, c.getInt(0))
            assertEquals(600, c.getInt(1))
        }
    }

    private companion object { const val DB = "migration-test" }
}
