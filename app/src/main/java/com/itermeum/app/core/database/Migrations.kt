package com.itermeum.app.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Section 8: every schema change after v1 ships a hand-written migration. */
object Migrations {

    /** v2 (D-22): `profile.massTime` + `massDay` → per-day `massTimeSaturday` / `massTimeSunday`. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(PROFILE_V2_DDL.replace("`profile`", "`profile_new`"))
            db.execSQL(
                """INSERT INTO `profile_new` (`id`,`name`,`wakeTime`,`wakeTimeWeekend`,`bedTime`,`workStart`,`workEnd`,`timezoneHome`,
                   `baptized`,`baptismDate`,`massTimeSaturday`,`massTimeSunday`,`massDay`,`contentPackVersion`,`createdAt`,`onboardingDone`,
                   `outreachTarget`,`wakeProofMethod`,`wakeQrUuid`,`alarmSound`,`alarmVolume`,`notificationTone`,`travelAuto`,
                   `confessionEveryDays`,`examenPinEnabled`)
                   SELECT `id`,`name`,`wakeTime`,`wakeTimeWeekend`,`bedTime`,`workStart`,`workEnd`,`timezoneHome`,
                   `baptized`,`baptismDate`,
                   CASE WHEN `massDay` = 6 AND `massTime` IS NOT NULL THEN `massTime` ELSE 1080 END,
                   CASE WHEN `massDay` = 7 AND `massTime` IS NOT NULL THEN `massTime` ELSE 600 END,
                   `massDay`,`contentPackVersion`,`createdAt`,`onboardingDone`,
                   `outreachTarget`,`wakeProofMethod`,`wakeQrUuid`,`alarmSound`,`alarmVolume`,`notificationTone`,`travelAuto`,
                   `confessionEveryDays`,`examenPinEnabled` FROM `profile`""",
            )
            db.execSQL("DROP TABLE `profile`")
            db.execSQL("ALTER TABLE `profile_new` RENAME TO `profile`")
        }
    }

    /** Exact DDL Room expects for `profile` at v2 (verified by MigrationsTest). */
    const val PROFILE_V2_DDL = "CREATE TABLE IF NOT EXISTS `profile` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, " +
        "`wakeTime` INTEGER NOT NULL, `wakeTimeWeekend` INTEGER NOT NULL, `bedTime` INTEGER NOT NULL, `workStart` INTEGER NOT NULL, " +
        "`workEnd` INTEGER NOT NULL, `timezoneHome` TEXT NOT NULL, `baptized` INTEGER NOT NULL, `baptismDate` TEXT, " +
        "`massTimeSaturday` INTEGER NOT NULL, `massTimeSunday` INTEGER NOT NULL, `massDay` INTEGER NOT NULL, " +
        "`contentPackVersion` INTEGER NOT NULL, `createdAt` TEXT, `onboardingDone` INTEGER NOT NULL, `outreachTarget` INTEGER NOT NULL, " +
        "`wakeProofMethod` TEXT NOT NULL, `wakeQrUuid` TEXT, `alarmSound` INTEGER NOT NULL, `alarmVolume` INTEGER NOT NULL, " +
        "`notificationTone` TEXT NOT NULL, `travelAuto` INTEGER NOT NULL, `confessionEveryDays` INTEGER NOT NULL, " +
        "`examenPinEnabled` INTEGER NOT NULL, PRIMARY KEY(`id`))"

    val ALL: Array<Migration> get() = arrayOf(MIGRATION_1_2)
}
