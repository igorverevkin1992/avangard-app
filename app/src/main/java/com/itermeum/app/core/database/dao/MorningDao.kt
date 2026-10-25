package com.itermeum.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.itermeum.app.core.database.entity.MorningStateEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface MorningDao {
    @Query("SELECT * FROM morning_state WHERE logicalDate = :date")
    fun observe(date: LocalDate): Flow<MorningStateEntity?>

    @Query("SELECT * FROM morning_state WHERE logicalDate = :date")
    suspend fun get(date: LocalDate): MorningStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: MorningStateEntity)

    @Query("SELECT * FROM morning_state ORDER BY logicalDate DESC LIMIT 1")
    suspend fun latest(): MorningStateEntity?

    @Query("SELECT * FROM morning_state WHERE logicalDate BETWEEN :from AND :to")
    suspend fun between(from: LocalDate, to: LocalDate): List<MorningStateEntity>
}
