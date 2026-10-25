package com.itermeum.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.itermeum.app.core.database.entity.MonthlySummaryViewEntity
import com.itermeum.app.core.database.entity.MortificationLogEntity
import com.itermeum.app.core.database.entity.TravelPeriodEntity
import com.itermeum.app.core.database.entity.WeeklyReviewEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface ReviewDao {
    @Query("SELECT * FROM weekly_review WHERE weekStart = :weekStart")
    fun observeWeekly(weekStart: LocalDate): Flow<WeeklyReviewEntity?>

    @Query("SELECT * FROM weekly_review WHERE weekStart = :weekStart")
    suspend fun weekly(weekStart: LocalDate): WeeklyReviewEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWeekly(review: WeeklyReviewEntity)

    @Query("SELECT * FROM mortification_log WHERE logicalDate = :date")
    fun observeMortification(date: LocalDate): Flow<MortificationLogEntity?>

    @Query("SELECT * FROM mortification_log WHERE logicalDate = :date")
    suspend fun mortification(date: LocalDate): MortificationLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMortification(log: MortificationLogEntity)

    @Query("SELECT * FROM travel_period WHERE endDate IS NULL ORDER BY startDate DESC LIMIT 1")
    fun observeActiveTravel(): Flow<TravelPeriodEntity?>

    @Query("SELECT * FROM travel_period WHERE endDate IS NULL ORDER BY startDate DESC LIMIT 1")
    suspend fun activeTravel(): TravelPeriodEntity?

    @Query("SELECT * FROM travel_period WHERE startDate <= :date AND (endDate IS NULL OR endDate >= :date) LIMIT 1")
    suspend fun travelOn(date: LocalDate): TravelPeriodEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTravel(period: TravelPeriodEntity): Long

    @Query("UPDATE travel_period SET endDate = :endDate WHERE id = :id")
    suspend fun endTravel(id: Long, endDate: LocalDate)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markMonthViewed(view: MonthlySummaryViewEntity)

    @Query("SELECT * FROM monthly_summary_view WHERE yearMonth = :yearMonth")
    suspend fun monthViewed(yearMonth: String): MonthlySummaryViewEntity?
}
