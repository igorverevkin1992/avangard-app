package com.itermeum.app.core.engine

import java.time.LocalDate

/**
 * D-67: counting the days of a vow. Only answered days count: «да» adds a day,
 * «нет» ends the series (it starts again from zero, the best series and the total
 * stay). A day left unanswered neither counts nor breaks the series.
 */
object VowEngine {

    data class Stats(val current: Int = 0, val best: Int = 0, val total: Int = 0, val slips: Int = 0, val lastSlip: LocalDate? = null)

    /** [answers] — answered days from the start of the vow on, in any order. */
    fun stats(answers: Map<LocalDate, Boolean>): Stats {
        var current = 0; var best = 0; var total = 0; var slips = 0; var lastSlip: LocalDate? = null
        answers.toSortedMap().forEach { (date, kept) ->
            if (kept) { current++; total++; best = maxOf(best, current) } else { current = 0; slips++; lastSlip = date }
        }
        return Stats(current, best, total, slips, lastSlip)
    }

    /**
     * Days still to answer, oldest first: from the start (at most [window] days back) to yesterday,
     * and today too once the evening has come ([todayOpen]).
     */
    fun pending(start: LocalDate, today: LocalDate, answered: Set<LocalDate>, todayOpen: Boolean, window: Int = 7): List<LocalDate> {
        val from = maxOf(start, today.minusDays(window.toLong()))
        val to = if (todayOpen) today else today.minusDays(1)
        if (from.isAfter(to)) return emptyList()
        return generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.filter { it !in answered }.toList()
    }

    /** Milestones of the best series (D-67): 7, 30, 40, 100, 365 days. */
    val MILESTONES = listOf(7, 30, 40, 100, 365)

    /** Milestone ids of a vow ([vow] is its id: sobriety, purity, games — D-79). */
    fun milestoneIds(best: Int, vow: String = "sobriety"): List<String> = MILESTONES.filter { best >= it }.map { "${vow}_$it" }
}
