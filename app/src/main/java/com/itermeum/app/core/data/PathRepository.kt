package com.itermeum.app.core.data

import com.itermeum.app.core.common.Clock
import com.itermeum.app.core.engine.DayScheduleEngine
import com.itermeum.app.core.content.NormCatalog
import com.itermeum.app.core.crypto.FieldCipher
import com.itermeum.app.core.database.dao.FormationDao
import com.itermeum.app.core.database.dao.ProjectDao
import com.itermeum.app.core.database.dao.ReviewDao
import com.itermeum.app.core.database.entity.CatechesisNoteEntity
import com.itermeum.app.core.database.entity.FormationEventEntity
import com.itermeum.app.core.database.entity.FormationType
import com.itermeum.app.core.database.entity.MonthlySummaryViewEntity
import com.itermeum.app.core.database.entity.MortificationLogEntity
import com.itermeum.app.core.database.entity.TravelPeriodEntity
import com.itermeum.app.core.database.entity.VanityCheckEntity
import com.itermeum.app.core.database.entity.WeeklyReviewEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Sections 5.14–5.15, 3.5–3.7: catechesis, reviews, mortification, travel, baptism. */
@Singleton
class PathRepository @Inject constructor(
    private val formationDao: FormationDao,
    private val reviewDao: ReviewDao,
    private val projectDao: ProjectDao,
    private val profiles: ProfileRepository,
    private val norms: NormRepository,
    private val cipher: FieldCipher,
    private val clock: Clock,
) {
    // ---- catechesis (Saturday 16:00–17:30 at the cathedral)

    fun nextCatechesisDate(from: LocalDate = clock.logicalToday()): LocalDate =
        if (from.dayOfWeek == DayOfWeek.SATURDAY && clock.localTime().isBefore(CATECHESIS_END)) from
        else from.with(TemporalAdjusters.next(DayOfWeek.SATURDAY))

    /** Ensures the Saturday class exists as a FormationEvent (used by the schedule's quiet window). */
    suspend fun ensureCatechesisEvent(date: LocalDate): FormationEventEntity {
        formationDao.eventFor(date, FormationType.CATECHESIS)?.let { return it }
        val zone = clock.zone()
        val start = ZonedDateTime.of(date.atTime(CATECHESIS_START), zone).toInstant().toEpochMilli()
        val end = ZonedDateTime.of(date.atTime(CATECHESIS_END), zone).toInstant().toEpochMilli()
        val e = FormationEventEntity(type = FormationType.CATECHESIS, logicalDate = date, startAt = start, endAt = end)
        return e.copy(id = formationDao.upsertEvent(e))
    }

    suspend fun ensureMassEvent(date: LocalDate) {
        val p = profiles.get()
        val time = p.massTimeFor(date.dayOfWeek)
        val isMassDay = (p.massDay == 6 && date.dayOfWeek == DayOfWeek.SATURDAY) || (p.massDay == 7 && date.dayOfWeek == DayOfWeek.SUNDAY)
        if (!isMassDay || formationDao.eventFor(date, FormationType.MASS) != null) return
        val zone = clock.zone()
        val start = ZonedDateTime.of(date.atTime(time), zone).toInstant().toEpochMilli()
        formationDao.upsertEvent(FormationEventEntity(type = FormationType.MASS, logicalDate = date, startAt = start, endAt = start + 60 * 60_000L))
    }

    fun observeNotes(): Flow<List<CatechesisNoteEntity>> = formationDao.observeNotes()
    fun observeAttended(): Flow<Int> = formationDao.observeCatechesisAttended()
    fun observeOpenHomework(): Flow<CatechesisNoteEntity?> = formationDao.observeOpenHomework()
    suspend fun note(id: Long) = formationDao.note(id)

    /** Saving the note marks attendance and completes `catechesis` for that Saturday (section 4). */
    suspend fun saveNote(date: LocalDate, topic: String, theses: String, question: String, homework: String, existingId: Long?) {
        val event = ensureCatechesisEvent(date)
        formationDao.updateEvent(event.copy(attended = true))
        formationDao.upsertNote(
            CatechesisNoteEntity(
                id = existingId ?: 0, eventId = event.id, logicalDate = date, topic = topic.trim(), theses = theses.trim(), question = question.trim(),
                homework = homework.trim(), homeworkDue = date.with(TemporalAdjusters.next(DayOfWeek.SATURDAY)), savedAt = clock.nowEpochMillis(),
            ),
        )
        norms.markDone(NormCatalog.CATECHESIS, date)
    }

    suspend fun markHomeworkDone(note: CatechesisNoteEntity) = formationDao.upsertNote(note.copy(homeworkDone = true))

    suspend fun markCatechesisMissed(date: LocalDate, travel: Boolean) {
        val e = ensureCatechesisEvent(date)
        formationDao.updateEvent(e.copy(attended = false, missedForTravel = travel))
    }

    // ---- weekly review (Sunday, 20 min)

    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    fun observeWeekly(weekStart: LocalDate): Flow<WeeklyReviewEntity?> = reviewDao.observeWeekly(weekStart)

    suspend fun saveWeekly(weekStart: LocalDate, plan: List<String>, virtue: String, calls: Int, agreements: Int, fidelityDays: Int) {
        reviewDao.upsertWeekly(
            WeeklyReviewEntity(weekStart, cipher.encrypt(json.encodeToString(ListSerializer(String.serializer()), plan)), virtue, clock.nowEpochMillis(), calls, agreements, fidelityDays),
        )
        // The review belongs to its Sunday even when written on Monday.
        norms.markDone(NormCatalog.WEEKLY_REVIEW, clock.logicalToday().with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)))
    }

    fun decryptPlan(r: WeeklyReviewEntity): List<String> =
        r.planEnc?.let { runCatching { json.decodeFromString(ListSerializer(String.serializer()), cipher.decrypt(it)) }.getOrNull() } ?: emptyList()

    // ---- monthly summary / quarter

    suspend fun markMonthViewed(yearMonth: String) {
        reviewDao.markMonthViewed(MonthlySummaryViewEntity(yearMonth, clock.nowEpochMillis()))
        if (clock.logicalToday().dayOfMonth == 1) norms.markDone(NormCatalog.MONTHLY_SUMMARY)
    }

    suspend fun vanity(quarter: String): List<String>? =
        projectDao.vanity(quarter)?.let { runCatching { json.decodeFromString(ListSerializer(String.serializer()), cipher.decrypt(it.answersEnc)) }.getOrNull() }

    suspend fun saveQuarter(quarter: String, vanityAnswers: List<String>) {
        projectDao.upsertVanity(VanityCheckEntity(quarter, cipher.encrypt(json.encodeToString(ListSerializer(String.serializer()), vanityAnswers)), clock.nowEpochMillis()))
        // Marked on the quarter's last Sunday if it was within the last week, else today.
        val today = clock.logicalToday()
        val due = (0L..6L).map { today.minusDays(it) }.firstOrNull { DayScheduleEngine.isLastSundayOfQuarter(it) } ?: today
        norms.markDone(NormCatalog.QUARTER_CHECK, due)
    }

    // ---- Friday mortification

    fun observeMortification(date: LocalDate): Flow<MortificationLogEntity?> = reviewDao.observeMortification(date)
    suspend fun chooseMortification(date: LocalDate, item: String) = reviewDao.upsertMortification(MortificationLogEntity(date, item, false))
    suspend fun completeMortification(date: LocalDate) {
        val m = reviewDao.mortification(date) ?: return
        reviewDao.upsertMortification(m.copy(done = true))
        norms.markDone(NormCatalog.MORTIFICATION, date)
    }

    // ---- travel (section 3.5)

    fun observeActiveTravel(): Flow<TravelPeriodEntity?> = reviewDao.observeActiveTravel()
    suspend fun startTravel(manual: Boolean) {
        if (reviewDao.activeTravel() != null) return
        reviewDao.upsertTravel(TravelPeriodEntity(startDate = clock.logicalToday(), timezone = clock.zone().id, manual = manual))
    }
    suspend fun endTravel() { reviewDao.activeTravel()?.let { reviewDao.endTravel(it.id, clock.logicalToday()) } }

    /** Auto mode: travel when the device zone is not the home zone. */
    suspend fun syncAutoTravel() {
        val p = profiles.get()
        if (!p.travelAuto) return
        val away = clock.zone().id != p.timezoneHome
        val active = reviewDao.activeTravel()
        if (away && active == null) startTravel(manual = false)
        if (!away && active != null && !active.manual) endTravel()
    }

    // ---- baptism (section 3.7)

    suspend fun markBaptized(date: LocalDate) = profiles.update { it.copy(baptized = true, baptismDate = date) }

    companion object {
        val CATECHESIS_START: LocalTime = LocalTime.of(16, 0)
        val CATECHESIS_END: LocalTime = LocalTime.of(17, 30)
        private val json = Json
    }
}
