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
 * D-67, D-79: vows (зароки) — each taken once, without a term; each day is confirmed
 * in the Examen (or the next morning). «Нет» starts that vow's series again; nothing
 * else — no other vow, no streak — depends on it.
 */
@Singleton
class VowRepository @Inject constructor(
    private val dao: VowDao,
    private val streakDao: StreakDao,
    private val clock: Clock,
) {
    data class State(
        val kind: VowKind,
        val vow: VowEntity? = null,
        val stats: VowEngine.Stats = VowEngine.Stats(),
        /** Days still to answer, oldest first (a week back at most). */
        val pending: List<LocalDate> = emptyList(),
        val today: VowDayEntity? = null,
        val tomorrow: VowDayEntity? = null,
    ) {
        val active: Boolean get() = vow?.active == true
    }

    fun observe(kind: VowKind): Flow<State> = combine(dao.observe(kind.id), dao.observeDays(kind.id)) { vow, days -> compute(kind, vow, days) }

    /** Every kind, taken or not, in the order of [VowKind]. */
    fun observeAll(): Flow<List<State>> = combine(dao.observeAll(), dao.observeAllDays()) { vows, days ->
        VowKind.entries.map { k -> compute(k, vows.firstOrNull { it.id == k.id }, days.filter { it.vowId == k.id }) }
    }

    suspend fun state(kind: VowKind): State = compute(kind, dao.get(kind.id), dao.days(kind.id))

    private fun compute(kind: VowKind, vow: VowEntity?, days: List<VowDayEntity>): State {
        if (vow == null) return State(kind)
        val today = clock.logicalToday()
        val since = days.filter { !it.logicalDate.isBefore(vow.startDate) }
        val answers = since.mapNotNull { d -> d.kept?.let { d.logicalDate to it } }.toMap()
        val pending = if (vow.active) VowEngine.pending(vow.startDate, today, answers.keys, todayOpen = eveningOpen()) else emptyList()
        return State(
            kind = kind, vow = vow, stats = VowEngine.stats(answers), pending = pending,
            today = days.firstOrNull { it.logicalDate == today }, tomorrow = days.firstOrNull { it.logicalDate == today.plusDays(1) },
        )
    }

    /** Today can be answered from the evening on (the logical day ends at 03:00). */
    fun eveningOpen(): Boolean = clock.localTime().let { !it.isBefore(EVENING) || it.isBefore(LocalTime.of(3, 0)) }

    /** Takes the vow from today; taking it again later starts a new count. */
    suspend fun take(kind: VowKind, intention: String?, helperName: String?, helperPhone: String?, declinePhrase: String?) {
        val old = dao.get(kind.id)
        dao.upsert(
            VowEntity(
                id = kind.id,
                startDate = if (old?.active == true) old.startDate else clock.logicalToday(),
                intention = intention.clean(), helperName = helperName.clean(), helperPhone = helperPhone.clean(),
                declinePhrase = declinePhrase.clean(), active = true, createdAt = old?.createdAt ?: clock.nowEpochMillis(),
            ),
        )
    }

    suspend fun updateDetails(kind: VowKind, intention: String?, helperName: String?, helperPhone: String?, declinePhrase: String?) {
        val v = dao.get(kind.id) ?: return
        dao.upsert(v.copy(intention = intention.clean(), helperName = helperName.clean(), helperPhone = helperPhone.clean(), declinePhrase = declinePhrase.clean()))
    }

    /** Lays the vow down; its days stay in the history. */
    suspend fun release(kind: VowKind) { dao.get(kind.id)?.let { dao.upsert(it.copy(active = false)) } }

    /** True when [date] still waits for an answer for this vow. */
    suspend fun needsAnswer(kind: VowKind, date: LocalDate): Boolean {
        val v = dao.get(kind.id)?.takeIf { it.active } ?: return false
        if (date.isBefore(v.startDate) || date.isAfter(clock.logicalToday())) return false
        return dao.day(kind.id, date)?.kept == null
    }

    /** The active vows that still wait for an answer about [date], in the order of [VowKind]. */
    suspend fun needingAnswer(date: LocalDate): List<VowKind> = VowKind.entries.filter { needsAnswer(it, date) }

    /** «Сохранил?» for [date]: answers count from the start of the vow to today. */
    suspend fun answer(kind: VowKind, date: LocalDate, kept: Boolean) {
        val v = dao.get(kind.id)?.takeIf { it.active } ?: return
        if (date.isBefore(v.startDate) || date.isAfter(clock.logicalToday())) return
        val day = dao.day(kind.id, date) ?: VowDayEntity(vowId = kind.id, logicalDate = date)
        dao.upsertDay(day.copy(kept = kept, answeredAt = clock.nowEpochMillis()))
        val best = state(kind).stats.best
        VowEngine.milestoneIds(best, kind.id).forEach { streakDao.achieve(it, clock.nowEpochMillis()) }
    }

    /** A feast on [date] at [time] (null clears it): the sobriety reminder comes an hour before. */
    suspend fun setFeast(date: LocalDate, time: LocalTime?) {
        val id = VowKind.SOBRIETY.id
        val day = dao.day(id, date) ?: VowDayEntity(vowId = id, logicalDate = date)
        dao.upsertDay(day.copy(feastMinute = time?.let { it.hour * 60 + it.minute }))
    }

    /** Feasts from today on, for the reminders. */
    suspend fun upcomingFeasts(): List<VowDayEntity> {
        val id = VowKind.SOBRIETY.id
        return if (dao.get(id)?.active == true) dao.feastsFrom(id, clock.logicalToday()) else emptyList()
    }

    suspend fun vow(kind: VowKind): VowEntity? = dao.get(kind.id)
    suspend fun isActive(kind: VowKind): Boolean = dao.get(kind.id)?.active == true

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    companion object {
        val EVENING: LocalTime = LocalTime.of(18, 0)
        fun feastTime(day: VowDayEntity): LocalTime? = day.feastMinute?.let { LocalTime.of(it / 60, it % 60) }
    }
}
