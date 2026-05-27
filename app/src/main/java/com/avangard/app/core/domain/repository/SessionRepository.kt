package com.avangard.app.core.domain.repository

import com.avangard.app.core.domain.model.Bottleneck
import com.avangard.app.core.domain.model.BottleneckFollowup
import com.avangard.app.core.domain.model.CoreMode
import com.avangard.app.core.domain.model.DailySession
import com.avangard.app.core.domain.model.DefectKind
import com.avangard.app.core.domain.model.FocusSession
import com.avangard.app.core.domain.model.Habit
import com.avangard.app.core.domain.model.InfraStatus
import com.avangard.app.core.domain.model.VirtueScores
import kotlinx.coroutines.flow.Flow

/**
 * One repository — one truth — for everything that happens during an
 * operational day in v3.0. observeToday() auto-upserts a zeroed row if absent.
 */
interface SessionRepository {

    fun observeToday(): Flow<DailySession>
    fun observeForDate(dateEpoch: Long): Flow<DailySession>
    fun observeRange(fromEpoch: Long, toEpoch: Long): Flow<List<DailySession>>
    suspend fun findForDate(dateEpoch: Long): DailySession?

    suspend fun approveCore(dateEpoch: Long, prompt: String, mode: CoreMode, approvedAt: Long)
    suspend fun setInfraStatus(dateEpoch: Long, habit: Habit, status: InfraStatus, recordedAt: Long)
    suspend fun closeEvening(
        dateEpoch: Long,
        virtues: VirtueScores,
        defectKind: DefectKind?,
        recordedAt: Long,
    )
    suspend fun setBottleneck(dateEpoch: Long, bottleneck: Bottleneck)
    suspend fun setBottleneckFollowup(dateEpoch: Long, followup: BottleneckFollowup)
    suspend fun setJournalEntry(dateEpoch: Long, entry: String?)

    fun observeActiveFocus(): Flow<FocusSession?>
    suspend fun findActiveFocus(): FocusSession?
    fun observeFocusForDay(dateEpoch: Long): Flow<List<FocusSession>>
    fun observeFocusRange(fromEpoch: Long, toEpoch: Long): Flow<List<FocusSession>>
    suspend fun sumFocusDurationFor(dateEpoch: Long, habit: Habit): Long
    suspend fun startFocus(dateEpoch: Long, habit: Habit, startedAt: Long): Long
    suspend fun endFocus(id: Long, endedAt: Long)

    suspend fun wipe()
}
