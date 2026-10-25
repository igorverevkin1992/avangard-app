package com.itermeum.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.itermeum.app.core.database.entity.FreezeLedgerEntity
import com.itermeum.app.core.database.entity.MilestoneEntity
import com.itermeum.app.core.database.entity.StreakStateEntity
import com.itermeum.app.core.database.entity.StreakType
import kotlinx.coroutines.flow.Flow

@Dao
interface StreakDao {
    @Query("SELECT * FROM streak_state")
    fun observeAll(): Flow<List<StreakStateEntity>>

    @Query("SELECT * FROM streak_state")
    suspend fun all(): List<StreakStateEntity>

    @Query("SELECT * FROM streak_state WHERE type = :type")
    suspend fun get(type: StreakType): StreakStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: StreakStateEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(states: List<StreakStateEntity>)

    @Query("SELECT COUNT(*) FROM freeze_ledger WHERE yearMonth = :yearMonth AND streakType = :type")
    suspend fun freezesUsed(yearMonth: String, type: StreakType): Int

    @Query("SELECT COUNT(*) FROM freeze_ledger WHERE yearMonth = :yearMonth")
    suspend fun freezesUsedTotal(yearMonth: String): Int

    @Query("SELECT * FROM freeze_ledger WHERE yearMonth = :yearMonth")
    fun observeFreezes(yearMonth: String): Flow<List<FreezeLedgerEntity>>

    @Insert
    suspend fun insertFreeze(freeze: FreezeLedgerEntity)

    @Query("SELECT * FROM milestone")
    fun observeMilestones(): Flow<List<MilestoneEntity>>

    @Query("SELECT COUNT(*) FROM milestone")
    suspend fun milestoneCount(): Int

    @Query("SELECT * FROM milestone WHERE id = :id")
    suspend fun milestone(id: String): MilestoneEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMilestonesIgnore(items: List<MilestoneEntity>)

    @Query("UPDATE milestone SET achievedAt = :at WHERE id = :id AND achievedAt IS NULL")
    suspend fun achieve(id: String, at: Long): Int

    @Query("UPDATE milestone SET seen = 1 WHERE id = :id")
    suspend fun markSeen(id: String)

    @Query("SELECT * FROM milestone WHERE achievedAt IS NOT NULL AND seen = 0 ORDER BY achievedAt LIMIT 1")
    fun observeUnseen(): Flow<MilestoneEntity?>
}
