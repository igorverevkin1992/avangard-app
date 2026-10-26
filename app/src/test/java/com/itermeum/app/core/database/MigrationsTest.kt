package com.itermeum.app.core.database

import android.content.Context
import android.database.Cursor
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The hand-written v2 `profile` DDL must match what Room generates for the
 * entity, column by column (name, affinity, NOT NULL, primary key), and the
 * data copy must keep the old Mass time on its day.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationsTest {

    private fun Cursor.columns(): List<String> = use { c ->
        generateSequence { if (c.moveToNext()) c.getString(1) + ":" + c.getString(2) + ":" + c.getInt(3) + ":" + c.getInt(5) else null }.toList()
    }

    private fun openV1(context: Context, afterCreate: (SupportSQLiteDatabase) -> Unit): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context).name(null).callback(object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) = afterCreate(db)
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }).build()
        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    @Test
    fun `v2 profile DDL matches the Room entity`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val room = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val expected = room.openHelper.writableDatabase.query("PRAGMA table_info(`profile`)").columns()
        room.close()

        val raw = openV1(context) { it.execSQL(Migrations.PROFILE_V2_DDL) }
        assertEquals(expected, raw.query("PRAGMA table_info(`profile`)").columns())
        raw.close()
    }

    @Test
    fun `migration keeps the Saturday Mass time and defaults the other day`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val v1Ddl = Migrations.PROFILE_V2_DDL.replace("`massTimeSaturday` INTEGER NOT NULL, `massTimeSunday` INTEGER NOT NULL, ", "`massTime` INTEGER, ")
        val db = openV1(context) {
            it.execSQL(v1Ddl)
            it.execSQL(
                "INSERT INTO profile (id,name,wakeTime,wakeTimeWeekend,bedTime,workStart,workEnd,timezoneHome,baptized,massTime,massDay," +
                    "contentPackVersion,onboardingDone,outreachTarget,wakeProofMethod,alarmSound,alarmVolume,notificationTone,travelAuto," +
                    "confessionEveryDays,examenPinEnabled) VALUES (1,'Игорь',390,450,1380,720,1200,'Europe/Moscow',0,1140,6,1,1,5,'QR',1,100,'FIRM',1,30,0)",
            )
        }
        Migrations.MIGRATION_1_2.migrate(db)
        db.query("SELECT massTimeSaturday, massTimeSunday, massDay, name FROM profile").use { c ->
            c.moveToFirst()
            assertEquals(1140, c.getInt(0))
            assertEquals(600, c.getInt(1))
            assertEquals(6, c.getInt(2))
            assertEquals("Игорь", c.getString(3))
        }
        db.close()
    }
}
