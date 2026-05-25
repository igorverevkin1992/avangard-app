package com.avangard.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.avangard.app.core.database.entity.DailySessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class DailySessionDao {

    @Query("SELECT * FROM daily_session WHERE date_epoch = :dateEpoch LIMIT 1")
    abstract fun observeByDate(dateEpoch: Long): Flow<DailySessionEntity?>

    @Query("SELECT * FROM daily_session WHERE date_epoch = :dateEpoch LIMIT 1")
    abstract suspend fun findByDate(dateEpoch: Long): DailySessionEntity?

    @Query("SELECT * FROM daily_session WHERE date_epoch BETWEEN :from AND :to ORDER BY date_epoch ASC")
    abstract fun observeRange(from: Long, to: Long): Flow<List<DailySessionEntity>>

    @Query("SELECT * FROM daily_session ORDER BY date_epoch ASC")
    abstract fun observeAll(): Flow<List<DailySessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(entity: DailySessionEntity)

    @Query("DELETE FROM daily_session")
    abstract suspend fun deleteAll()

    @Query("SELECT * FROM daily_session ORDER BY date_epoch ASC")
    abstract suspend fun getAll(): List<DailySessionEntity>

    /**
     * Atomic find-or-insert. Without this, the repository's read+write pattern
     * had a race window where two concurrent callers could both observe the
     * row as absent and both insert — REPLACE would silently overwrite one.
     */
    @Transaction
    open suspend fun ensureRow(dateEpoch: Long): DailySessionEntity {
        findByDate(dateEpoch)?.let { return it }
        val fresh = DailySessionEntity(dateEpoch = dateEpoch)
        upsert(fresh)
        return fresh
    }
}
