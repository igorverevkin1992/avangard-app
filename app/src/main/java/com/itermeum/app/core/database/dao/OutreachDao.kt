package com.itermeum.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.itermeum.app.core.database.entity.OutreachLogEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface OutreachDao {
    @Insert
    suspend fun insert(log: OutreachLogEntity): Long

    @Query("SELECT * FROM outreach_log WHERE logicalDate = :date ORDER BY at")
    fun observeForDate(date: LocalDate): Flow<List<OutreachLogEntity>>

    @Query("SELECT COUNT(*) FROM outreach_log WHERE logicalDate = :date AND (:beforeCutoff = 0 OR beforeCutoff = 1)")
    suspend fun countFor(date: LocalDate, beforeCutoff: Boolean): Int

    @Query("SELECT COUNT(*) FROM outreach_log WHERE logicalDate = :date")
    fun observeCount(date: LocalDate): Flow<Int>

    @Query("SELECT COUNT(*) FROM outreach_log WHERE logicalDate = :date AND outcome = 'AGREEMENT'")
    fun observeAgreements(date: LocalDate): Flow<Int>

    @Query("SELECT COUNT(*) FROM outreach_log")
    suspend fun totalAttempts(): Int

    @Query("SELECT COUNT(*) FROM outreach_log WHERE outcome = 'AGREEMENT'")
    suspend fun totalAgreements(): Int

    @Query("SELECT * FROM outreach_log WHERE logicalDate BETWEEN :from AND :to ORDER BY at")
    suspend fun between(from: LocalDate, to: LocalDate): List<OutreachLogEntity>

    @Query("SELECT DISTINCT personLabel FROM outreach_log ORDER BY at DESC LIMIT 30")
    suspend fun recentPeople(): List<String>
}
