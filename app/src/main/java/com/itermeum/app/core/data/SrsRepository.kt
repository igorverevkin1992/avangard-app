package com.itermeum.app.core.data

import com.itermeum.app.core.common.Clock
import com.itermeum.app.core.content.NormCatalog
import com.itermeum.app.core.database.dao.SrsDao
import com.itermeum.app.core.database.entity.MemorizationItemEntity
import com.itermeum.app.core.database.entity.SrsStage
import com.itermeum.app.core.engine.SrsEngine
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Section 5.13: due cards for today, one new prayer per week, norm `srs` when the queue is empty or ≥5 min spent. */
@Singleton
class SrsRepository @Inject constructor(
    private val dao: SrsDao,
    private val norms: NormRepository,
    private val clock: Clock,
) {
    fun observeAll(): Flow<List<MemorizationItemEntity>> = dao.observeAll()
    fun observeDueCount(date: LocalDate): Flow<Int> = dao.observeDueCount(date)

    /** Introduces the next prayer if a week has passed, then returns today's queue. */
    suspend fun queue(date: LocalDate = clock.logicalToday()): List<MemorizationItemEntity> {
        val last = dao.lastIntroducedOn()
        if (SrsEngine.canIntroduce(last, date)) {
            dao.nextToIntroduce()?.let { dao.update(it.copy(introducedOn = date, dueDate = date)) }
        }
        return dao.due(date)
    }

    suspend fun review(item: MemorizationItemEntity, grade: SrsEngine.Grade): MemorizationItemEntity {
        val updated = SrsEngine.review(item, grade, clock.logicalToday())
        dao.update(updated)
        return updated
    }

    suspend fun markSessionDone(date: LocalDate = clock.logicalToday()) = norms.markDone(NormCatalog.SRS, date)

    suspend fun knownCount() = dao.knownCount()
    suspend fun count() = dao.count()
    suspend fun isKnown(prayerId: String) = dao.get(prayerId)?.stage == SrsStage.KNOWN
}
