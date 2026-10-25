package com.itermeum.app.core.data

import com.itermeum.app.core.common.Clock
import com.itermeum.app.core.content.NormCatalog
import com.itermeum.app.core.database.dao.CompletionDao
import com.itermeum.app.core.database.dao.DayPlanDao
import com.itermeum.app.core.database.dao.FormationDao
import com.itermeum.app.core.database.dao.NormDao
import com.itermeum.app.core.database.dao.ReviewDao
import com.itermeum.app.core.database.entity.CompletionQuality
import com.itermeum.app.core.database.entity.NormCompletionEntity
import com.itermeum.app.core.database.entity.NormEntity
import com.itermeum.app.core.engine.DayScheduleEngine
import com.itermeum.app.core.liturgy.LiturgicalCalendar
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Norms, their schedule for a date and completion marks. */
@Singleton
class NormRepository @Inject constructor(
    private val normDao: NormDao,
    private val completionDao: CompletionDao,
    private val dayPlanDao: DayPlanDao,
    private val reviewDao: ReviewDao,
    private val formationDao: FormationDao,
    private val profiles: ProfileRepository,
    private val calendar: LiturgicalCalendar,
    private val clock: Clock,
) {
    fun observeNorms(): Flow<List<NormEntity>> = normDao.observeAll()

    fun observeCompletions(date: LocalDate): Flow<List<NormCompletionEntity>> = completionDao.observeForDate(date)

    suspend fun scheduleFor(date: LocalDate): List<DayScheduleEngine.ScheduledItem> =
        DayScheduleEngine.schedule(inputFor(date))

    suspend fun inputFor(date: LocalDate): DayScheduleEngine.Input {
        val profile = profiles.get()
        val plan = dayPlanDao.get(date)
        val travel = plan?.travelMode == true || reviewDao.travelOn(date) != null
        val events = formationDao.eventsBetween(date, date).map { e ->
            val zone = clock.zone()
            Instant.ofEpochMilli(e.startAt).atZone(zone).toLocalDateTime() to Instant.ofEpochMilli(e.endAt).atZone(zone).toLocalDateTime()
        }
        return DayScheduleEngine.Input(
            date = date,
            profile = profile,
            norms = normDao.getAll(),
            reginaCaeli = calendar.day(date).reginaCaeli,
            travel = travel,
            sick = plan?.sick == true,
            shootDay = plan?.shootDay == true,
            events = events,
        )
    }

    /** Observe-friendly: list of norm ids done on [date]. */
    fun observeDoneIds(date: LocalDate): Flow<Set<String>> =
        completionDao.observeForDate(date).let { f -> combine(f, normDao.observeAll()) { c, _ -> c.map { it.normId }.toSet() } }

    suspend fun isDone(normId: String, date: LocalDate): Boolean = completionDao.find(normId, date) != null

    suspend fun markDone(normId: String, date: LocalDate = clock.logicalToday(), quality: CompletionQuality = CompletionQuality.FULL, note: String? = null) {
        completionDao.upsert(
            NormCompletionEntity(normId = normId, logicalDate = date, completedAt = clock.nowEpochMillis(), quality = quality, note = note),
        )
    }

    suspend fun unmark(normId: String, date: LocalDate = clock.logicalToday()) = completionDao.delete(normId, date)

    suspend fun setEnabled(id: String, enabled: Boolean) = normDao.setEnabled(id, enabled)
    suspend fun setUserTime(id: String, minutes: Int?) = normDao.setUserTime(id, minutes)

    suspend fun norm(id: String): NormEntity? = normDao.get(id)

    companion object {
        val CORE = setOf(NormCatalog.WAKE, NormCatalog.FROG, NormCatalog.EXAMEN)
    }
}
