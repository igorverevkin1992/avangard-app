package com.itermeum.app.core.engine

import com.itermeum.app.core.content.NormCatalog
import com.itermeum.app.core.content.NormCatalog.includes
import com.itermeum.app.core.database.entity.NormCategory
import com.itermeum.app.core.database.entity.NormEntity
import com.itermeum.app.core.database.entity.NormFrequency
import com.itermeum.app.core.database.entity.ProfileEntity
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/**
 * Section 11.3. Pure function from (profile, norms, calendar facts, events)
 * to the list of scheduled items for one logical day. Morning norms shift
 * with the day's wake time; work norms are anchored to `workStart`;
 * everything else keeps its default (or user) time.
 */
object DayScheduleEngine {

    val DEFAULT_WAKE: LocalTime = LocalTime.of(6, 30)
    val LOGICAL_END: LocalTime = LocalTime.of(2, 59)
    const val QUIET_BEFORE_MIN = 90L
    const val QUIET_AFTER_MIN = 90L
    val FROG_CUTOFF: LocalTime = LocalTime.of(14, 0)
    private val WORK_ANCHOR: LocalTime = LocalTime.of(12, 0)

    data class Input(
        val date: LocalDate,
        val profile: ProfileEntity,
        val norms: List<NormEntity>,
        val reginaCaeli: Boolean,
        val travel: Boolean,
        val sick: Boolean,
        val shootDay: Boolean,
        /** Formation events (catechesis, Mass) on this date as wall-clock intervals. */
        val events: List<Pair<LocalDateTime, LocalDateTime>>,
        /** Last Sunday of the quarter → `quarter_check` is active. */
        val lastSundayOfQuarter: Boolean = isLastSundayOfQuarter(date),
        /** Date of a Mass already marked this weekend (Sat/Sun pair), if any. */
        val massDoneThisWeekend: LocalDate? = null,
        /** Last confession (for the monthly due date once baptised). */
        val lastConfession: LocalDate? = null,
    )

    data class ScheduledItem(
        val normId: String,
        /** Reminder time (null for «в течение дня» norms). */
        val time: LocalTime?,
        val windowStart: LocalDateTime,
        /** Exclusive end of the window in which completion counts. */
        val windowEnd: LocalDateTime,
        /** Outreach target / rosary decades after travel coefficients. */
        val target: Int,
        /** Reminder falls inside a ±90 min quiet window around Mass/catechesis. */
        val muted: Boolean,
        val isCore: Boolean,
    )

    data class Quiet(val start: LocalDateTime, val end: LocalDateTime)

    fun wakeTimeFor(date: LocalDate, profile: ProfileEntity): LocalTime =
        if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) profile.wakeTimeWeekend else profile.wakeTime

    fun quietWindows(events: List<Pair<LocalDateTime, LocalDateTime>>): List<Quiet> =
        events.map { (s, e) -> Quiet(s.minusMinutes(QUIET_BEFORE_MIN), e.plusMinutes(QUIET_AFTER_MIN)) }

    fun isQuiet(at: LocalDateTime, quiet: List<Quiet>): Boolean = quiet.any { !at.isBefore(it.start) && at.isBefore(it.end) }

    fun isActive(norm: NormEntity, input: Input): Boolean {
        if (!norm.enabled) return false
        if (norm.pausedUntil != null && !input.date.isAfter(norm.pausedUntil)) return false
        if (norm.requiresBaptism && !input.profile.baptized) return false
        if (!norm.daysOfWeek.includes(input.date.dayOfWeek)) return false
        if (input.sick && norm.id != NormCatalog.EXAMEN) return false
        return when (norm.id) {
            // Mass counts once a week (section 4): on the usual day, unless already
            // heard on the other day of the weekend; the other day only if heard there.
            NormCatalog.MASS -> {
                val usual = input.date.dayOfWeek.value == input.profile.massDay
                val doneElsewhere = input.massDoneThisWeekend != null && input.massDoneThisWeekend != input.date
                if (usual) !doneElsewhere else input.massDoneThisWeekend == input.date
            }
            // Confession only when due (every N days), or on the day it was made.
            NormCatalog.CONFESSION -> {
                val last = input.lastConfession
                last == null || last == input.date || !input.date.isBefore(last.plusDays(input.profile.confessionEveryDays.toLong()))
            }
            else -> when (norm.frequency) {
                NormFrequency.DAILY, NormFrequency.WEEKLY -> true
                NormFrequency.MONTHLY -> if (norm.id == NormCatalog.MONTHLY_SUMMARY) input.date.dayOfMonth == 1 else true
                NormFrequency.QUARTERLY -> input.lastSundayOfQuarter
            }
        }
    }

    /**
     * Outreach cutoff: 14:00 per section 5.3, but never less than two working
     * hours after `workStart` (otherwise a late start makes the frog unreachable).
     */
    fun frogCutoff(profile: ProfileEntity): LocalTime =
        maxOf(FROG_CUTOFF, profile.workStart.plusHours(2).takeIf { it.isAfter(profile.workStart) } ?: LocalTime.of(23, 59))

    /** Work norms keep their offset from noon relative to `workStart` (section 11.3). */
    fun workAnchored(defaultTime: LocalTime, profile: ProfileEntity): LocalTime =
        profile.workStart.plusMinutes(Duration.between(WORK_ANCHOR, defaultTime).toMinutes())

    /** Norms whose own event creates the quiet window must still remind about that event. */
    fun selfQuieted(normId: String): Boolean = normId == NormCatalog.MASS || normId == NormCatalog.CATECHESIS

    fun schedule(input: Input): List<ScheduledItem> {
        val p = input.profile
        val date = input.date
        val wake = wakeTimeFor(date, p)
        val shift = Duration.between(DEFAULT_WAKE, wake)
        val quiet = quietWindows(input.events)
        val dayStart = date.atTime(3, 0)
        val dayEnd = date.plusDays(1).atTime(3, 0)

        return input.norms.filter { isActive(it, input) }.map { norm ->
            val base = norm.userTime ?: norm.defaultTime?.let {
                when {
                    norm.followsWakeTime -> it.plus(shift)
                    norm.category == NormCategory.WORK -> workAnchored(it, p)
                    else -> it
                }
            }
            val time: LocalTime? = when (norm.id) {
                NormCatalog.MASS -> p.massTimeFor(date.dayOfWeek)
                else -> base
            }
            val at = time?.let { date.atTime(it) }
            val (ws, we) = when (norm.id) {
                NormCatalog.WAKE -> at!! to at.plusMinutes(StreakEngine.WAKE_WINDOW_MIN)
                NormCatalog.SERVIAM -> at!! to at.plusMinutes(5)
                NormCatalog.QUARANTINE -> at!! to at.plusMinutes(35)
                NormCatalog.ANGELUS -> date.atTime(11, 30) to date.atTime(14, 0)
                NormCatalog.DAY_PLAN -> dayStart to date.atTime(p.workStart).plusMinutes(30)
                NormCatalog.FROG -> date.atTime(p.workStart) to date.atTime(frogCutoff(p))
                NormCatalog.LAST_STONE -> date.atTime(p.workStart) to date.atTime(21, 0)
                NormCatalog.EXAMEN -> date.atTime(20, 0) to dayEnd
                NormCatalog.CATECHESIS -> date.atTime(16, 0) to dayEnd
                NormCatalog.MASS -> dayStart to dayEnd
                else -> (at ?: dayStart) to dayEnd
            }
            val target = when (norm.id) {
                NormCatalog.FROG -> if (input.travel || input.shootDay) 2 else p.outreachTarget
                NormCatalog.ROSARY -> if (input.travel) 1 else 5
                else -> 1
            }
            ScheduledItem(
                normId = norm.id,
                time = time,
                windowStart = ws,
                windowEnd = we,
                target = target,
                muted = at != null && !selfQuieted(norm.id) && isQuiet(at, quiet),
                isCore = norm.isCore,
            )
        }.sortedWith(compareBy(nullsLast()) { it.time })
    }

    /**
     * «Следующая норма» for the Today screen: an open item whose deadline is
     * live (time ≤ now < windowEnd, windowEnd before the end of the logical
     * day) wins by earliest deadline; otherwise the earliest upcoming item.
     */
    fun nextItem(items: List<ScheduledItem>, now: LocalDateTime, done: Set<String>): ScheduledItem? {
        val dayEnd = now.toLocalDate().let { d -> if (now.toLocalTime().isBefore(LocalTime.of(3, 0))) d.atTime(3, 0) else d.plusDays(1).atTime(3, 0) }
        val open = items.filter { it.normId !in done && it.time != null }
        fun startOf(i: ScheduledItem) = i.windowStart.toLocalDate().atTime(i.time)
        val live = open.filter { !now.isBefore(startOf(it)) && now.isBefore(it.windowEnd) }
        live.filter { it.windowEnd.isBefore(dayEnd) }.minByOrNull { it.windowEnd }?.let { return it }
        open.filter { !startOf(it).isBefore(now) }.minByOrNull { startOf(it) }?.let { return it }
        // Late evening, nothing upcoming: the most recently due open norm (e.g. Examen at 23:00).
        return live.maxByOrNull { startOf(it) }
    }

    fun isLastSundayOfQuarter(date: LocalDate): Boolean {
        if (date.dayOfWeek != DayOfWeek.SUNDAY) return false
        val quarterEndMonth = ((date.monthValue - 1) / 3) * 3 + 3
        val lastDay = LocalDate.of(date.year, quarterEndMonth, 1).with(TemporalAdjusters.lastDayOfMonth())
        return date == lastDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    }
}
