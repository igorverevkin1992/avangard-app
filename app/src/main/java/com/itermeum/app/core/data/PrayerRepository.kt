package com.itermeum.app.core.data

import com.itermeum.app.core.common.Clock
import com.itermeum.app.core.database.dao.PrayerDao
import com.itermeum.app.core.database.entity.JaculatoriaEntity
import com.itermeum.app.core.database.entity.JaculatoriaLogEntity
import com.itermeum.app.core.database.entity.PrayerEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class PrayerRepository @Inject constructor(
    private val dao: PrayerDao,
    private val clock: Clock,
) {
    fun observeAll(): Flow<List<PrayerEntity>> = dao.observeAll()
    fun observe(id: String): Flow<PrayerEntity?> = dao.observe(id)
    suspend fun get(id: String): PrayerEntity? = dao.get(id)
    suspend fun setUserText(id: String, text: String?) = dao.setUserText(id, text?.takeIf { it.isNotBlank() })

    /** The text the user prays: their own verified edit, else the pack text. */
    fun displayText(p: PrayerEntity): String = p.userEditedText ?: p.text

    fun observeJaculatoria(situation: String): Flow<List<JaculatoriaEntity>> = dao.observeJaculatoria(situation)
    suspend fun allJaculatoria(): List<JaculatoriaEntity> = dao.allJaculatoria()

    /** A rotating pick for a situation: deterministic per hour so the overlay does not flicker. */
    suspend fun pickJaculatoria(situation: String): JaculatoriaEntity? {
        val list = dao.allJaculatoria().filter { it.situation == situation }
        if (list.isEmpty()) return null
        val slot = (clock.nowEpochMillis() / 3_600_000L).toInt()
        return list[Math.floorMod(slot, list.size)]
    }

    suspend fun logJaculatoria(item: JaculatoriaEntity?, situation: String) {
        dao.logJaculatoria(JaculatoriaLogEntity(jaculatoriaId = item?.id, situation = situation, at = clock.nowEpochMillis(), logicalDate = clock.logicalToday()))
    }

    fun observeJaculatoriaCount(date: java.time.LocalDate): Flow<Int> = dao.observeJaculatoriaCount(date)
    suspend fun jaculatoriaCount(date: java.time.LocalDate): Int = dao.jaculatoriaCount(date)
}
