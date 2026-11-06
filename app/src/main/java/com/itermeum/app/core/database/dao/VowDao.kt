package com.itermeum.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.itermeum.app.core.database.entity.VowDayEntity
import com.itermeum.app.core.database.entity.VowEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** D-67: the vow and its days. */
@Dao
interface VowDao {
    @Query("SELECT * FROM vow WHERE id = :id") suspend fun get(id: String): VowEntity?
    @Query("SELECT * FROM vow WHERE id = :id") fun observe(id: String): Flow<VowEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(vow: VowEntity)

    @Query("SELECT * FROM vow_day WHERE vowId = :id ORDER BY logicalDate") fun observeDays(id: String): Flow<List<VowDayEntity>>
    @Query("SELECT * FROM vow_day WHERE vowId = :id ORDER BY logicalDate") suspend fun days(id: String): List<VowDayEntity>
    @Query("SELECT * FROM vow_day WHERE vowId = :id AND logicalDate = :date") suspend fun day(id: String, date: LocalDate): VowDayEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertDay(day: VowDayEntity)
    @Query("SELECT * FROM vow_day WHERE vowId = :id AND feastMinute IS NOT NULL AND logicalDate >= :from") suspend fun feastsFrom(id: String, from: LocalDate): List<VowDayEntity>
}
