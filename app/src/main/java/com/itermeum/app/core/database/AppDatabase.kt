package com.itermeum.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.itermeum.app.core.database.dao.BackupDao
import com.itermeum.app.core.database.dao.CompletionDao
import com.itermeum.app.core.database.dao.DayPlanDao
import com.itermeum.app.core.database.dao.ExamenDao
import com.itermeum.app.core.database.dao.FormationDao
import com.itermeum.app.core.database.dao.LiturgyDao
import com.itermeum.app.core.database.dao.MorningDao
import com.itermeum.app.core.database.dao.NormDao
import com.itermeum.app.core.database.dao.OutreachDao
import com.itermeum.app.core.database.dao.PrayerDao
import com.itermeum.app.core.database.dao.ProfileDao
import com.itermeum.app.core.database.dao.ProjectDao
import com.itermeum.app.core.database.dao.ReadingDao
import com.itermeum.app.core.database.dao.ReviewDao
import com.itermeum.app.core.database.dao.ScriptDao
import com.itermeum.app.core.database.dao.SrsDao
import com.itermeum.app.core.database.dao.StreakDao
import com.itermeum.app.core.database.dao.TaskDao
import com.itermeum.app.core.database.entity.AsceticItemEntity
import com.itermeum.app.core.database.entity.CatechesisNoteEntity
import com.itermeum.app.core.database.entity.DayPlanEntity
import com.itermeum.app.core.database.entity.ExamenEntity
import com.itermeum.app.core.database.entity.FormationEventEntity
import com.itermeum.app.core.database.entity.FreezeLedgerEntity
import com.itermeum.app.core.database.entity.JaculatoriaEntity
import com.itermeum.app.core.database.entity.JaculatoriaLogEntity
import com.itermeum.app.core.database.entity.LiturgicalDayCacheEntity
import com.itermeum.app.core.database.entity.MemorizationItemEntity
import com.itermeum.app.core.database.entity.MilestoneEntity
import com.itermeum.app.core.database.entity.MonthlySummaryViewEntity
import com.itermeum.app.core.database.entity.MorningStateEntity
import com.itermeum.app.core.database.entity.MortificationLogEntity
import com.itermeum.app.core.database.entity.NormCompletionEntity
import com.itermeum.app.core.database.entity.NormEntity
import com.itermeum.app.core.database.entity.OutreachLogEntity
import com.itermeum.app.core.database.entity.PrayerEntity
import com.itermeum.app.core.database.entity.ProfileEntity
import com.itermeum.app.core.database.entity.ProjectEntity
import com.itermeum.app.core.database.entity.QuarterGoalEntity
import com.itermeum.app.core.database.entity.QuarterIncomeEntity
import com.itermeum.app.core.database.entity.ReadingLogEntity
import com.itermeum.app.core.database.entity.ReadingPlanItemEntity
import com.itermeum.app.core.database.entity.RosaryProgressEntity
import com.itermeum.app.core.database.entity.ScriptSessionEntity
import com.itermeum.app.core.database.entity.StreakStateEntity
import com.itermeum.app.core.database.entity.TransitionRitualEntity
import com.itermeum.app.core.database.entity.TravelPeriodEntity
import com.itermeum.app.core.database.entity.VanityCheckEntity
import com.itermeum.app.core.database.entity.WeeklyReviewEntity
import com.itermeum.app.core.database.entity.WorkTaskEntity

/**
 * Section 8: schema version 2, exported. `fallbackToDestructiveMigration`
 * is forbidden — every later version ships a Migration (see [Migrations]).
 */
@Database(
    entities = [
        ProfileEntity::class,
        NormEntity::class, NormCompletionEntity::class,
        DayPlanEntity::class, WorkTaskEntity::class, OutreachLogEntity::class,
        ScriptSessionEntity::class, TransitionRitualEntity::class,
        ExamenEntity::class, PrayerEntity::class, MemorizationItemEntity::class,
        JaculatoriaEntity::class, JaculatoriaLogEntity::class, AsceticItemEntity::class,
        ReadingPlanItemEntity::class, ReadingLogEntity::class, LiturgicalDayCacheEntity::class,
        RosaryProgressEntity::class,
        FormationEventEntity::class, CatechesisNoteEntity::class,
        StreakStateEntity::class, FreezeLedgerEntity::class, MilestoneEntity::class,
        ProjectEntity::class, QuarterGoalEntity::class, QuarterIncomeEntity::class, VanityCheckEntity::class,
        WeeklyReviewEntity::class, MortificationLogEntity::class, TravelPeriodEntity::class,
        MorningStateEntity::class, MonthlySummaryViewEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun normDao(): NormDao
    abstract fun completionDao(): CompletionDao
    abstract fun dayPlanDao(): DayPlanDao
    abstract fun taskDao(): TaskDao
    abstract fun outreachDao(): OutreachDao
    abstract fun scriptDao(): ScriptDao
    abstract fun examenDao(): ExamenDao
    abstract fun prayerDao(): PrayerDao
    abstract fun srsDao(): SrsDao
    abstract fun liturgyDao(): LiturgyDao
    abstract fun readingDao(): ReadingDao
    abstract fun formationDao(): FormationDao
    abstract fun streakDao(): StreakDao
    abstract fun projectDao(): ProjectDao
    abstract fun reviewDao(): ReviewDao
    abstract fun morningDao(): MorningDao
    abstract fun backupDao(): BackupDao

    companion object {
        const val DB_NAME = "itermeum.db"
        /** Keep equal to `@Database(version)`; written into backups. */
        const val SCHEMA_VERSION = 2
    }
}
