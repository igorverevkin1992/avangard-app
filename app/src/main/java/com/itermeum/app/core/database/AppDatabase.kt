package com.itermeum.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.itermeum.app.core.database.dao.ProfileDao
import com.itermeum.app.core.database.entity.ProfileEntity

/**
 * Section 8: schema version 1, exported. `fallbackToDestructiveMigration`
 * is forbidden after the v1 release — every later version ships a Migration.
 */
@Database(
    entities = [ProfileEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao

    companion object {
        const val DB_NAME = "itermeum.db"
    }
}
