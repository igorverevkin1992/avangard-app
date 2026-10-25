package com.itermeum.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.itermeum.app.core.database.entity.ExamenEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface ExamenDao {
    @Query("SELECT * FROM examen WHERE logicalDate = :date")
    fun observe(date: LocalDate): Flow<ExamenEntity?>

    @Query("SELECT * FROM examen WHERE logicalDate = :date")
    suspend fun get(date: LocalDate): ExamenEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(examen: ExamenEntity)

    @Query("SELECT * FROM examen WHERE completedAt IS NOT NULL AND logicalDate < :before ORDER BY logicalDate DESC LIMIT 1")
    suspend fun latestCompletedBefore(before: LocalDate): ExamenEntity?

    @Query("SELECT * FROM examen WHERE logicalDate BETWEEN :from AND :to")
    suspend fun between(from: LocalDate, to: LocalDate): List<ExamenEntity>
}
