package com.itermeum.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.itermeum.app.core.database.entity.NormEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface NormDao {
    @Query("SELECT * FROM norm ORDER BY orderIndex")
    fun observeAll(): Flow<List<NormEntity>>

    @Query("SELECT * FROM norm ORDER BY orderIndex")
    suspend fun getAll(): List<NormEntity>

    @Query("SELECT * FROM norm WHERE id = :id")
    suspend fun get(id: String): NormEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(norms: List<NormEntity>)

    @Update
    suspend fun update(norm: NormEntity)

    @Query("UPDATE norm SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("UPDATE norm SET userTime = :minutes WHERE id = :id")
    suspend fun setUserTime(id: String, minutes: Int?)

    @Query("UPDATE norm SET pausedUntil = :until WHERE id = :id AND isCore = 0")
    suspend fun pauseUntil(id: String, until: LocalDate?)

    @Query("SELECT COUNT(*) FROM norm")
    suspend fun count(): Int

    /** D-35, once per install: the former core «frog» norm becomes optional and off (later user choices stay). */
    @Query("UPDATE norm SET enabled = 0, isCore = 0 WHERE id = 'frog' AND isCore = 1")
    suspend fun retireFrog()

    /** D-60: the norm no longer ends at 14:00 — its stored title follows. */
    @Query("UPDATE norm SET title = 'Обзвон героев' WHERE id = 'frog' AND title = 'Обзвон героев до 14:00'")
    suspend fun renameFrog()

    /** D-77: the parish edition calls Salve Regina «Радуйся, Царица». */
    @Query("UPDATE norm SET title = 'Радуйся, Царица' WHERE id = 'marian_saturday' AND title = 'Славься, Царица'")
    suspend fun renameMarian()
}
