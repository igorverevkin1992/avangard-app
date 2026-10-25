package com.itermeum.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.itermeum.app.core.database.entity.LiturgicalDayCacheEntity
import com.itermeum.app.core.database.entity.RosaryProgressEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface LiturgyDao {
    @Query("SELECT * FROM liturgical_day_cache WHERE date = :date")
    suspend fun get(date: LocalDate): LiturgicalDayCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(day: LiturgicalDayCacheEntity)

    @Query("SELECT * FROM rosary_progress WHERE logicalDate = :date")
    fun observeRosary(date: LocalDate): Flow<RosaryProgressEntity?>

    @Query("SELECT * FROM rosary_progress WHERE logicalDate = :date")
    suspend fun rosary(date: LocalDate): RosaryProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRosary(progress: RosaryProgressEntity)

    @Query("SELECT COUNT(*) FROM rosary_progress WHERE decadesDone >= 5")
    suspend fun fullRosaries(): Int
}
