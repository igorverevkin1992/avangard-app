package com.avangard.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.avangard.app.core.database.entity.DailySessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailySessionDao {

    @Query("SELECT * FROM daily_session WHERE date_epoch = :dateEpoch LIMIT 1")
    fun observeByDate(dateEpoch: Long): Flow<DailySessionEntity?>

    @Query("SELECT * FROM daily_session WHERE date_epoch = :dateEpoch LIMIT 1")
    suspend fun findByDate(dateEpoch: Long): DailySessionEntity?

    @Query("SELECT * FROM daily_session WHERE date_epoch BETWEEN :from AND :to ORDER BY date_epoch ASC")
    fun observeRange(from: Long, to: Long): Flow<List<DailySessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DailySessionEntity)

    @Query("DELETE FROM daily_session")
    suspend fun deleteAll()
}
