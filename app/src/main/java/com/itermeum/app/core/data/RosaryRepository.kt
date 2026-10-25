package com.itermeum.app.core.data

import com.itermeum.app.core.common.Clock
import com.itermeum.app.core.content.ContentAssets
import com.itermeum.app.core.content.NormCatalog
import com.itermeum.app.core.database.dao.LiturgyDao
import com.itermeum.app.core.database.entity.RosaryProgressEntity
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Section 5.10: mysteries by weekday (7.6), progress kept until the end of the logical day. */
@Singleton
class RosaryRepository @Inject constructor(
    private val dao: LiturgyDao,
    private val assets: ContentAssets,
    private val norms: NormRepository,
    private val clock: Clock,
) {
    fun setFor(date: LocalDate): String {
        val dow = date.dayOfWeek.value
        return assets.rosary.sets.entries.firstOrNull { dow in it.value.days }?.key ?: "JOYFUL"
    }

    fun mysteries(set: String) = assets.rosary.sets[set]
    fun contemplations(set: String) = assets.rosary.contemplations[set] ?: emptyList()
    val verify: Boolean get() = assets.rosary.verify

    fun observe(date: LocalDate): Flow<RosaryProgressEntity?> = dao.observeRosary(date)

    suspend fun setDecades(date: LocalDate, decades: Int) {
        val set = dao.rosary(date)?.mysterySet ?: setFor(date)
        dao.upsertRosary(RosaryProgressEntity(date, decades.coerceIn(0, 5), set, clock.nowEpochMillis()))
        val target = if (norms.inputFor(date).travel) 1 else 5
        if (decades >= target && !norms.isDone(NormCatalog.ROSARY, date)) norms.markDone(NormCatalog.ROSARY, date)
    }

    suspend fun fullRosaries() = dao.fullRosaries()
}
