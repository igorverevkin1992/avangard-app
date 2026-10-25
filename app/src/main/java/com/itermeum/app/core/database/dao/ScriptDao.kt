package com.itermeum.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.itermeum.app.core.database.entity.ScriptSessionEntity
import com.itermeum.app.core.database.entity.TransitionRitualEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface ScriptDao {
    @Insert
    suspend fun insert(session: ScriptSessionEntity): Long

    @Update
    suspend fun update(session: ScriptSessionEntity)

    @Query("SELECT * FROM script_session WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeActive(): Flow<ScriptSessionEntity?>

    @Query("SELECT * FROM script_session WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun active(): ScriptSessionEntity?

    @Query("SELECT COALESCE(SUM(minutes),0) FROM script_session WHERE logicalDate BETWEEN :from AND :to")
    suspend fun minutesBetween(from: LocalDate, to: LocalDate): Int

    @Query("SELECT COUNT(*) FROM script_session WHERE logicalDate = :date")
    fun observeCountForDate(date: LocalDate): Flow<Int>

    @Insert
    suspend fun insertRitual(ritual: TransitionRitualEntity)

    @Query("SELECT COUNT(*) FROM transition_ritual WHERE logicalDate = :date")
    fun observeRitualCount(date: LocalDate): Flow<Int>
}
