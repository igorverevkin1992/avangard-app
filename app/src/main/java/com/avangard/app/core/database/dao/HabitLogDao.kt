package com.avangard.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.avangard.app.core.database.entity.HabitLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitLogDao {

    @Query("SELECT * FROM habit_log WHERE date_epoch BETWEEN :from AND :to")
    fun observeRange(from: Long, to: Long): Flow<List<HabitLogEntity>>

    @Query(
        "SELECT 1 FROM habit_log WHERE date_epoch = :dateEpoch AND habit_code = :code LIMIT 1"
    )
    suspend fun exists(dateEpoch: Long, code: String): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: HabitLogEntity)

    @Query("DELETE FROM habit_log WHERE date_epoch = :dateEpoch AND habit_code = :code")
    suspend fun delete(dateEpoch: Long, code: String)

    @Query("DELETE FROM habit_log")
    suspend fun deleteAll()

    @Query("SELECT * FROM habit_log ORDER BY date_epoch ASC")
    suspend fun getAll(): List<HabitLogEntity>
}
