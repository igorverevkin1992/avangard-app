package com.itermeum.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.itermeum.app.core.database.entity.WorkTaskEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM work_task WHERE logicalDate = :date ORDER BY slot")
    fun observeForDate(date: LocalDate): Flow<List<WorkTaskEntity>>

    @Query("SELECT * FROM work_task WHERE logicalDate = :date ORDER BY slot")
    suspend fun forDate(date: LocalDate): List<WorkTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tasks: List<WorkTaskEntity>)

    @Update
    suspend fun update(task: WorkTaskEntity)

    @Query("DELETE FROM work_task WHERE logicalDate = :date")
    suspend fun deleteForDate(date: LocalDate)

    @Query("SELECT * FROM work_task WHERE id = :id")
    suspend fun get(id: Long): WorkTaskEntity?

    @Query("SELECT * FROM work_task WHERE logicalDate BETWEEN :from AND :to")
    suspend fun between(from: LocalDate, to: LocalDate): List<WorkTaskEntity>
}
