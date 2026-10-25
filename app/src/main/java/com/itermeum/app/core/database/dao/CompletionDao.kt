package com.itermeum.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.itermeum.app.core.database.entity.NormCompletionEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface CompletionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(completion: NormCompletionEntity): Long

    @Query("DELETE FROM norm_completion WHERE normId = :normId AND logicalDate = :date")
    suspend fun delete(normId: String, date: LocalDate)

    @Query("SELECT * FROM norm_completion WHERE logicalDate = :date")
    fun observeForDate(date: LocalDate): Flow<List<NormCompletionEntity>>

    @Query("SELECT * FROM norm_completion WHERE logicalDate = :date")
    suspend fun forDate(date: LocalDate): List<NormCompletionEntity>

    @Query("SELECT * FROM norm_completion WHERE logicalDate BETWEEN :from AND :to ORDER BY logicalDate")
    suspend fun between(from: LocalDate, to: LocalDate): List<NormCompletionEntity>

    @Query("SELECT * FROM norm_completion WHERE normId = :normId AND logicalDate = :date LIMIT 1")
    suspend fun find(normId: String, date: LocalDate): NormCompletionEntity?

    @Query("SELECT COUNT(*) FROM norm_completion WHERE normId = :normId")
    suspend fun countFor(normId: String): Int

    @Query("SELECT * FROM norm_completion WHERE normId = :normId ORDER BY logicalDate DESC LIMIT 1")
    suspend fun latestFor(normId: String): NormCompletionEntity?
}
