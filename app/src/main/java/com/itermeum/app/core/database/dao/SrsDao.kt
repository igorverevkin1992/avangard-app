package com.itermeum.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.itermeum.app.core.database.entity.MemorizationItemEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface SrsDao {
    @Query("SELECT * FROM memorization_item ORDER BY orderIndex")
    suspend fun all(): List<MemorizationItemEntity>

    @Query("SELECT * FROM memorization_item ORDER BY orderIndex")
    fun observeAll(): Flow<List<MemorizationItemEntity>>

    @Query("SELECT * FROM memorization_item WHERE prayerId = :id")
    suspend fun get(id: String): MemorizationItemEntity?

    /** Introduced items whose review is due on or before [date]. */
    @Query("SELECT * FROM memorization_item WHERE introducedOn IS NOT NULL AND stage != 'KNOWN' AND (dueDate IS NULL OR dueDate <= :date) ORDER BY orderIndex")
    suspend fun due(date: LocalDate): List<MemorizationItemEntity>

    @Query("SELECT COUNT(*) FROM memorization_item WHERE introducedOn IS NOT NULL AND stage != 'KNOWN' AND (dueDate IS NULL OR dueDate <= :date)")
    fun observeDueCount(date: LocalDate): Flow<Int>

    @Query("SELECT * FROM memorization_item WHERE introducedOn IS NULL ORDER BY orderIndex LIMIT 1")
    suspend fun nextToIntroduce(): MemorizationItemEntity?

    @Query("SELECT MAX(introducedOn) FROM memorization_item")
    suspend fun lastIntroducedOn(): LocalDate?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(items: List<MemorizationItemEntity>)

    @Update
    suspend fun update(item: MemorizationItemEntity)

    @Query("SELECT COUNT(*) FROM memorization_item WHERE stage = 'KNOWN'")
    suspend fun knownCount(): Int

    @Query("SELECT COUNT(*) FROM memorization_item")
    suspend fun count(): Int
}
