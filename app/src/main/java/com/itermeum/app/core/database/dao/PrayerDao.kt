package com.itermeum.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.itermeum.app.core.database.entity.AsceticItemEntity
import com.itermeum.app.core.database.entity.JaculatoriaEntity
import com.itermeum.app.core.database.entity.JaculatoriaLogEntity
import com.itermeum.app.core.database.entity.PrayerEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface PrayerDao {
    @Query("SELECT * FROM prayer ORDER BY orderIndex")
    fun observeAll(): Flow<List<PrayerEntity>>

    @Query("SELECT * FROM prayer ORDER BY orderIndex")
    suspend fun getAll(): List<PrayerEntity>

    @Query("SELECT * FROM prayer WHERE id = :id")
    fun observe(id: String): Flow<PrayerEntity?>

    @Query("SELECT * FROM prayer WHERE id = :id")
    suspend fun get(id: String): PrayerEntity?

    /** Seeding: keeps the user's `userEditedText` by only inserting missing rows… */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(prayers: List<PrayerEntity>)

    /** …and refreshing the canonical columns of existing ones. */
    @Query("UPDATE prayer SET title = :title, text = :text, source = :source, status = :status, legal = :legal, verify = :verify, orderIndex = :orderIndex WHERE id = :id")
    suspend fun refreshCanonical(id: String, title: String, text: String, source: String, status: String, legal: String, verify: Boolean, orderIndex: Int)

    @Query("UPDATE prayer SET userEditedText = :text WHERE id = :id")
    suspend fun setUserText(id: String, text: String?)

    @Query("SELECT COUNT(*) FROM prayer")
    suspend fun count(): Int

    @Query("SELECT * FROM jaculatoria")
    suspend fun allJaculatoria(): List<JaculatoriaEntity>

    @Query("SELECT * FROM jaculatoria WHERE situation = :situation")
    fun observeJaculatoria(situation: String): Flow<List<JaculatoriaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertJaculatoria(items: List<JaculatoriaEntity>)

    @Insert
    suspend fun logJaculatoria(log: JaculatoriaLogEntity)

    @Query("SELECT COUNT(*) FROM jaculatoria_log WHERE logicalDate = :date")
    fun observeJaculatoriaCount(date: LocalDate): Flow<Int>

    @Query("SELECT COUNT(*) FROM jaculatoria_log WHERE logicalDate = :date")
    suspend fun jaculatoriaCount(date: LocalDate): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAscetic(items: List<AsceticItemEntity>)

    @Query("SELECT * FROM ascetic_item")
    fun observeAscetic(): Flow<List<AsceticItemEntity>>

    @Query("SELECT COUNT(*) FROM ascetic_item")
    suspend fun asceticCount(): Int
}
