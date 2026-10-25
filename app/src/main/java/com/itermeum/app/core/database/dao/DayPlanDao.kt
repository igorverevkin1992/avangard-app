package com.itermeum.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.itermeum.app.core.database.entity.DayPlanEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface DayPlanDao {
    @Query("SELECT * FROM day_plan WHERE logicalDate = :date")
    fun observe(date: LocalDate): Flow<DayPlanEntity?>

    @Query("SELECT * FROM day_plan WHERE logicalDate = :date")
    suspend fun get(date: LocalDate): DayPlanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(plan: DayPlanEntity)

    @Query("SELECT * FROM day_plan WHERE logicalDate BETWEEN :from AND :to")
    suspend fun between(from: LocalDate, to: LocalDate): List<DayPlanEntity>
}
