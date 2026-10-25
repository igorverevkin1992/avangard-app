package com.itermeum.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.itermeum.app.core.database.entity.ReadingLogEntity
import com.itermeum.app.core.database.entity.ReadingPlanItemEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlan(items: List<ReadingPlanItemEntity>)

    @Query("SELECT * FROM reading_plan_item WHERE month = :month")
    suspend fun planForMonth(month: Int): List<ReadingPlanItemEntity>

    @Query("SELECT MAX(month) FROM reading_plan_item")
    suspend fun maxMonth(): Int?

    @Query("SELECT COUNT(*) FROM reading_plan_item")
    suspend fun planCount(): Int

    @Insert
    suspend fun insertLog(log: ReadingLogEntity): Long

    @Query("SELECT * FROM reading_log WHERE logicalDate = :date")
    fun observeForDate(date: LocalDate): Flow<List<ReadingLogEntity>>

    @Query("SELECT * FROM reading_log WHERE logicalDate = :date")
    suspend fun forDate(date: LocalDate): List<ReadingLogEntity>

    @Query("SELECT COALESCE(SUM(minutes),0) FROM reading_log WHERE logicalDate BETWEEN :from AND :to")
    suspend fun minutesBetween(from: LocalDate, to: LocalDate): Int
}
