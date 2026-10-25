package com.itermeum.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.itermeum.app.core.database.entity.NormEntity
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

    @Query("SELECT COUNT(*) FROM norm")
    suspend fun count(): Int
}
