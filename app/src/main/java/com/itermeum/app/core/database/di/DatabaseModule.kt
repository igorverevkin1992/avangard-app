package com.itermeum.app.core.database.di

import android.content.Context
import androidx.room.Room
import com.itermeum.app.core.database.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DB_NAME).build()

    @Provides fun profileDao(db: AppDatabase) = db.profileDao()
    @Provides fun normDao(db: AppDatabase) = db.normDao()
    @Provides fun completionDao(db: AppDatabase) = db.completionDao()
    @Provides fun dayPlanDao(db: AppDatabase) = db.dayPlanDao()
    @Provides fun taskDao(db: AppDatabase) = db.taskDao()
    @Provides fun outreachDao(db: AppDatabase) = db.outreachDao()
    @Provides fun scriptDao(db: AppDatabase) = db.scriptDao()
    @Provides fun examenDao(db: AppDatabase) = db.examenDao()
    @Provides fun prayerDao(db: AppDatabase) = db.prayerDao()
    @Provides fun srsDao(db: AppDatabase) = db.srsDao()
    @Provides fun liturgyDao(db: AppDatabase) = db.liturgyDao()
    @Provides fun readingDao(db: AppDatabase) = db.readingDao()
    @Provides fun formationDao(db: AppDatabase) = db.formationDao()
    @Provides fun streakDao(db: AppDatabase) = db.streakDao()
    @Provides fun projectDao(db: AppDatabase) = db.projectDao()
    @Provides fun reviewDao(db: AppDatabase) = db.reviewDao()
    @Provides fun morningDao(db: AppDatabase) = db.morningDao()
    @Provides fun backupDao(db: AppDatabase) = db.backupDao()
}
