package com.itermeum.app.core.data

import com.itermeum.app.core.common.Clock
import com.itermeum.app.core.database.dao.StreakDao
import com.itermeum.app.core.database.dao.VowDao
import com.itermeum.app.core.database.entity.VowDayEntity
import com.itermeum.app.core.database.entity.VowEntity
import com.itermeum.app.core.engine.VowEngine
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * D-67: the sobriety vow (зарок) — taken once, without a term; each day is confirmed
 * in the Examen (or the next morning). «Нет» starts the series again; nothing else
 * of the day's streaks depends on it.
 */
@Singleton
class VowRepository @Inject constructor(
    private val dao: VowDao,
    private val streakDao: StreakDao,
    private val clock: Clock,
) {
    data class State(
        val vow: VowEntity? = null,
        val stats: VowEngine.Stats = VowEngine.Stats(),
        /** Days still to answer, oldest first (a week back at most). */
        val pending: List<LocalDate> = emptyList(),
        val today: VowDayEntity? = null,
        val tomorrow: VowDayEntity? = null,
    ) {
        val active: Boolean get() = vow?.active == true
    }

    fun observe(): Flow<State> = combine(dao.observe(ID), dao.observeDays(ID)) { vow, days -> compute(vow, days) }

    suspend fun state(): State = compute(dao.get(ID), dao.days(ID))

    private fun compute(vow: VowEntity?, days: List<VowDayEntity>): State {
        if (vow == null) return State()
        val today = clock.logicalToday()
        val since = days.filter { !it.logicalDate.isBefore(vow.startDate) }
        val answers = since.mapNotNull { d -> d.kept?.let { d.logicalDate to it } }.toMap()
        val pending = if (vow.active) VowEngine.pending(vow.startDate, today, answers.keys, todayOpen = eveningOpen()) else emptyList()
        return State(
            vow = vow, stats = VowEngine.stats(answers), pending = pending,
            today = days.firstOrNull { it.logicalDate == today }, tomorrow = days.firstOrNull { it.logicalDate == today.plusDays(1) },
        )
    }

    /** Today can be answered from the evening on (the logical day ends at 03:00). */
    fun eveningOpen(): Boolean = clock.localTime().let { !it.isBefore(EVENING) || it.isBefore(LocalTime.of(3, 0)) }

    /** Takes the vow from today; taking it again later starts a new count. */
    suspend fun take(intention: String?, helperName: String?, helperPhone: String?, declinePhrase: String?) {
        val old = dao.get(ID)
        dao.upsert(
            VowEntity(
                startDate = if (old?.active == true) old.startDate else clock.logicalToday(),
                intention = intention.clean(), helperName = helperName.clean(), helperPhone = helperPhone.clean(),
                declinePhrase = declinePhrase.clean(), active = true, createdAt = old?.createdAt ?: clock.nowEpochMillis(),
            ),
        )
    }

    suspend fun updateDetails(intention: String?, helperName: String?, helperPhone: String?, declinePhrase: String?) {
        val v = dao.get(ID) ?: return
        dao.upsert(v.copy(intention = intention.clean(), helperName = helperName.clean(), helperPhone = helperPhone.clean(), declinePhrase = declinePhrase.clean()))
    }

    /** Lays the vow down; its days stay in the history. */
    suspend fun release() { dao.get(ID)?.let { dao.upsert(it.copy(active = false)) } }

    /** True when [date] still waits for an answer. */
    suspend fun needsAnswer(date: LocalDate): Boolean {
        val v = dao.get(ID)?.takeIf { it.active } ?: return false
        if (date.isBefore(v.startDate) || date.isAfter(clock.logicalToday())) return false
        return dao.day(ID, date)?.kept == null
    }

    /** «Сохранил трезвость?» for [date]: answers count from the start of the vow to today. */
    suspend fun answer(date: LocalDate, kept: Boolean) {
        val v = dao.get(ID)?.takeIf { it.active } ?: return
        if (date.isBefore(v.startDate) || date.isAfter(clock.logicalToday())) return
        val day = dao.day(ID, date) ?: VowDayEntity(vowId = ID, logicalDate = date)
        dao.upsertDay(day.copy(kept = kept, answeredAt = clock.nowEpochMillis()))
        val best = state().stats.best
        VowEngine.milestoneIds(best).forEach { streakDao.achieve(it, clock.nowEpochMillis()) }
    }

    /** A feast on [date] at [time] (null clears it): the reminder comes an hour before. */
    suspend fun setFeast(date: LocalDate, time: LocalTime?) {
        val day = dao.day(ID, date) ?: VowDayEntity(vowId = ID, logicalDate = date)
        dao.upsertDay(day.copy(feastMinute = time?.let { it.hour * 60 + it.minute }))
    }

    /** Feasts from today on, for the reminders. */
    suspend fun upcomingFeasts(): List<VowDayEntity> =
        if (dao.get(ID)?.active == true) dao.feastsFrom(ID, clock.logicalToday()) else emptyList()

    suspend fun vow(): VowEntity? = dao.get(ID)
    suspend fun isActive(): Boolean = dao.get(ID)?.active == true
    fun observeVow(): Flow<VowEntity?> = dao.observe(ID)

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    companion object {
        const val ID = VowEntity.SOBRIETY
        val EVENING: LocalTime = LocalTime.of(18, 0)
        fun feastTime(day: VowDayEntity): LocalTime? = day.feastMinute?.let { LocalTime.of(it / 60, it % 60) }
    }
}
