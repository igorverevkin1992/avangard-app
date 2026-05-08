package com.avangard.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.avangard.app.core.database.entity.SystemMetricEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SystemMetricDao {

    @Query("SELECT value FROM system_metrics WHERE `key` = :key LIMIT 1")
    fun observe(key: String): Flow<String?>

    @Query("SELECT value FROM system_metrics WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: SystemMetricEntity)

    @Query("DELETE FROM system_metrics WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM system_metrics")
    suspend fun deleteAll()
}
