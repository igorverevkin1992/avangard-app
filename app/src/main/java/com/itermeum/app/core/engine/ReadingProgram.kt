package com.itermeum.app.core.engine

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Section 11.9: `monthIndex = monthsSince(profile.createdAt) + 1`, clamped to the last programme month; no reading debt. */
object ReadingProgram {
    fun monthIndex(createdAt: LocalDate, date: LocalDate, maxMonth: Int): Int {
        val months = ChronoUnit.MONTHS.between(createdAt.withDayOfMonth(1), date.withDayOfMonth(1)).toInt()
        return (months + 1).coerceIn(1, maxMonth.coerceAtLeast(1))
    }
}
