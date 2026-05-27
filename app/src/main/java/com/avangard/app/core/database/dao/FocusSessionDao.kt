package com.avangard.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.avangard.app.core.database.entity.FocusSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusSessionDao {

    @Query("SELECT * FROM focus_session WHERE ended_at IS NULL ORDER BY started_at DESC LIMIT 1")
    fun observeActive(): Flow<FocusSessionEntity?>

    @Query("SELECT * FROM focus_session WHERE ended_at IS NULL ORDER BY started_at DESC LIMIT 1")
    suspend fun findActive(): FocusSessionEntity?

    @Query("SELECT * FROM focus_session WHERE date_epoch = :dateEpoch ORDER BY started_at ASC")
    fun observeForDay(dateEpoch: Long): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_session WHERE date_epoch BETWEEN :from AND :to ORDER BY started_at ASC")
    fun observeRange(from: Long, to: Long): Flow<List<FocusSessionEntity>>

    @Query("""
        SELECT COALESCE(SUM(ended_at - started_at), 0) FROM focus_session
        WHERE date_epoch = :dateEpoch AND habit_code = :habitCode AND ended_at IS NOT NULL
    """)
    suspend fun sumDurationByDayAndCode(dateEpoch: Long, habitCode: String): Long

    @Insert
    suspend fun insert(entity: FocusSessionEntity): Long

    @Query("UPDATE focus_session SET ended_at = :endedAt WHERE id = :id")
    suspend fun endSession(id: Long, endedAt: Long)

    /**
     * Force-end every active row whose start date is earlier than today.
     * Run once on app start to clear orphans left by a crash or kill —
     * the partial unique index uniq_focus_active otherwise refuses every
     * subsequent startFocus until the operator wipes data.
     *
     * `ended_at = started_at` keeps the session as a zero-duration row;
     * it shows up in the daily ledger as «started and immediately closed»
     * which is preferable to dropping the start record entirely.
     */
    @Query("""
        UPDATE focus_session
        SET ended_at = started_at
        WHERE ended_at IS NULL AND date_epoch < :todayEpoch
    """)
    suspend fun closeOrphansBefore(todayEpoch: Long): Int

    @Query("DELETE FROM focus_session")
    suspend fun deleteAll()

    @Query("SELECT * FROM focus_session ORDER BY started_at ASC")
    suspend fun getAll(): List<FocusSessionEntity>
}
