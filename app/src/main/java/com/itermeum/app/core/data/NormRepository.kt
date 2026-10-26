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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest

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

    /**
     * The schedule input, re-read whenever something that shapes the day changes:
     * the day plan (sick, travel, shoot day), completions (weekly Mass, confession),
     * norms, the profile and an active travel period.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeInput(date: LocalDate): Flow<DayScheduleEngine.Input> =
        combine(dayPlanDao.observe(date), completionDao.observeForDate(date), normDao.observeAll(), profiles.flow, reviewDao.observeActiveTravel()) { _, _, _, _, _ -> Unit }
            .mapLatest { inputFor(date) }

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
            massDoneThisWeekend = massDoneThisWeekend(date),
            lastConfession = completionDao.latestFor(NormCatalog.CONFESSION)?.logicalDate,
        )
    }

    /** The Saturday/Sunday pair is one Mass week (section 4). */
    private suspend fun massDoneThisWeekend(date: LocalDate): LocalDate? {
        val saturday = when (date.dayOfWeek) {
            java.time.DayOfWeek.SATURDAY -> date
            java.time.DayOfWeek.SUNDAY -> date.minusDays(1)
            else -> return null
        }
        return listOf(saturday, saturday.plusDays(1)).firstOrNull { completionDao.find(NormCatalog.MASS, it) != null }
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
    suspend fun pauseForWeek(id: String, from: LocalDate) = normDao.pauseUntil(id, from.plusDays(6))
    suspend fun unpause(id: String) = normDao.pauseUntil(id, null)

    companion object {
        val CORE = setOf(NormCatalog.WAKE, NormCatalog.FROG, NormCatalog.EXAMEN)
    }
}
