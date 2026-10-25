package com.itermeum.app.core.engine

import com.itermeum.app.core.content.NormCatalog
import com.itermeum.app.core.content.NormCatalog.includes
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
        return when (norm.frequency) {
            NormFrequency.DAILY, NormFrequency.WEEKLY -> true
            NormFrequency.MONTHLY -> if (norm.id == NormCatalog.MONTHLY_SUMMARY) input.date.dayOfMonth == 1 else true
            NormFrequency.QUARTERLY -> input.lastSundayOfQuarter
        }
    }

    fun schedule(input: Input): List<ScheduledItem> {
        val p = input.profile
        val date = input.date
        val wake = wakeTimeFor(date, p)
        val shift = Duration.between(DEFAULT_WAKE, wake)
        val quiet = quietWindows(input.events)
        val dayStart = date.atTime(3, 0)
        val dayEnd = date.plusDays(1).atTime(3, 0)

        return input.norms.filter { isActive(it, input) }.map { norm ->
            val base = norm.userTime ?: norm.defaultTime?.let { if (norm.followsWakeTime && norm.userTime == null) it.plus(shift) else it }
            val time: LocalTime? = when (norm.id) {
                NormCatalog.MASS -> p.massTime
                else -> base
            }
            val at = time?.let { date.atTime(it) }
            val (ws, we) = when (norm.id) {
                NormCatalog.WAKE -> at!! to at.plusMinutes(StreakEngine.WAKE_WINDOW_MIN)
                NormCatalog.SERVIAM -> at!! to at.plusMinutes(5)
                NormCatalog.QUARANTINE -> at!! to at.plusMinutes(35)
                NormCatalog.ANGELUS -> date.atTime(11, 30) to date.atTime(14, 0)
                NormCatalog.DAY_PLAN -> dayStart to date.atTime(p.workStart).plusMinutes(30)
                NormCatalog.FROG -> date.atTime(p.workStart) to date.atTime(14, 0)
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
                muted = at != null && isQuiet(at, quiet),
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
        val live = open.filter { val at = it.windowStart.toLocalDate().atTime(it.time); !now.isBefore(at) && now.isBefore(it.windowEnd) && it.windowEnd.isBefore(dayEnd) }
        live.minByOrNull { it.windowEnd }?.let { return it }
        return open.filter { !it.windowStart.toLocalDate().atTime(it.time).isBefore(now) }.minByOrNull { it.time!! }
    }

    fun isLastSundayOfQuarter(date: LocalDate): Boolean {
        if (date.dayOfWeek != DayOfWeek.SUNDAY) return false
        val quarterEndMonth = ((date.monthValue - 1) / 3) * 3 + 3
        val lastDay = LocalDate.of(date.year, quarterEndMonth, 1).with(TemporalAdjusters.lastDayOfMonth())
        return date == lastDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    }
}
