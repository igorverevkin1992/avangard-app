package com.itermeum.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.itermeum.app.core.database.entity.FocusLogEntity
import com.itermeum.app.core.database.entity.WorkItemEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** D-64: the inbox and every work item; focus sessions. */
@Dao
interface WorkItemDao {
    @Insert suspend fun insert(item: WorkItemEntity): Long
    @Update suspend fun update(item: WorkItemEntity)

    @Query("SELECT * FROM work_item WHERE id = :id") suspend fun get(id: Long): WorkItemEntity?
    @Query("SELECT * FROM work_item WHERE id = :id") fun observe(id: Long): Flow<WorkItemEntity?>
    @Query("DELETE FROM work_item WHERE id = :id") suspend fun delete(id: Long)

    /** Open items, dated first by date, then the undated inbox, newest first. */
    @Query("SELECT * FROM work_item WHERE status = 'OPEN' ORDER BY dueDate IS NULL, dueDate, createdAt DESC")
    fun observeOpen(): Flow<List<WorkItemEntity>>

    @Query("SELECT * FROM work_item WHERE status = 'OPEN' AND dueDate IS NOT NULL AND dueDate <= :date ORDER BY dueDate, createdAt")
    suspend fun dueBy(date: LocalDate): List<WorkItemEntity>

    @Query("SELECT * FROM work_item WHERE projectId = :projectId ORDER BY status, dueDate IS NULL, dueDate, createdAt DESC")
    fun observeForProject(projectId: Long): Flow<List<WorkItemEntity>>

    @Query(
        "SELECT * FROM work_item WHERE title LIKE '%' || :q || '%' OR notes LIKE '%' || :q || '%' " +
            "OR contactName LIKE '%' || :q || '%' OR contactPhone LIKE '%' || :q || '%' ORDER BY status, createdAt DESC LIMIT 100",
    )
    fun search(q: String): Flow<List<WorkItemEntity>>

    @Insert suspend fun insertFocus(log: FocusLogEntity)
    @Query("SELECT COALESCE(SUM(ms), 0) FROM focus_log WHERE logicalDate BETWEEN :from AND :to")
    fun observeFocusMs(from: LocalDate, to: LocalDate): Flow<Long>
}
