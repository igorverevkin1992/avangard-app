package com.avangard.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.avangard.app.core.database.entity.DailyLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyLogDao {

    @Query("SELECT * FROM daily_log WHERE date_epoch = :dateEpoch LIMIT 1")
    fun observeByDate(dateEpoch: Long): Flow<DailyLogEntity?>

    @Query("SELECT * FROM daily_log WHERE date_epoch = :dateEpoch LIMIT 1")
    suspend fun findByDate(dateEpoch: Long): DailyLogEntity?

    @Query("SELECT * FROM daily_log WHERE date_epoch BETWEEN :from AND :to ORDER BY date_epoch ASC")
    fun observeRange(from: Long, to: Long): Flow<List<DailyLogEntity>>

    @Query("SELECT * FROM daily_log ORDER BY date_epoch DESC")
    fun observeAll(): Flow<List<DailyLogEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: DailyLogEntity): Long

    @Update
    suspend fun update(entity: DailyLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DailyLogEntity): Long

    @Query("DELETE FROM daily_log")
    suspend fun deleteAll()
}
