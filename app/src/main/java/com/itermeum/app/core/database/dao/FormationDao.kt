package com.itermeum.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.itermeum.app.core.database.entity.CatechesisNoteEntity
import com.itermeum.app.core.database.entity.FormationEventEntity
import com.itermeum.app.core.database.entity.FormationType
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface FormationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvent(event: FormationEventEntity): Long

    @Update
    suspend fun updateEvent(event: FormationEventEntity)

    @Query("SELECT * FROM formation_event WHERE logicalDate = :date AND type = :type LIMIT 1")
    suspend fun eventFor(date: LocalDate, type: FormationType): FormationEventEntity?

    @Query("SELECT * FROM formation_event WHERE logicalDate BETWEEN :from AND :to ORDER BY startAt")
    suspend fun eventsBetween(from: LocalDate, to: LocalDate): List<FormationEventEntity>

    @Query("SELECT COUNT(*) FROM formation_event WHERE type = 'CATECHESIS' AND attended = 1")
    fun observeCatechesisAttended(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNote(note: CatechesisNoteEntity): Long

    @Query("SELECT * FROM catechesis_note ORDER BY logicalDate DESC")
    fun observeNotes(): Flow<List<CatechesisNoteEntity>>

    @Query("SELECT * FROM catechesis_note WHERE id = :id")
    suspend fun note(id: Long): CatechesisNoteEntity?

    @Query("SELECT * FROM catechesis_note WHERE logicalDate = :date LIMIT 1")
    suspend fun noteFor(date: LocalDate): CatechesisNoteEntity?

    @Query("SELECT * FROM catechesis_note WHERE homeworkDone = 0 ORDER BY homeworkDue LIMIT 1")
    fun observeOpenHomework(): Flow<CatechesisNoteEntity?>
}
