package com.avangard.app.core.domain.model

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * DMN-isolation gate. Historical aggregations (Sunday audit, monthly grid,
 * journal) may be accessed strictly on Sundays — every other day the
 * operator's attention is restricted to the current production cycle.
 */
object AccessPolicy {
    fun isHistoryUnlocked(today: LocalDate): Boolean =
        today.dayOfWeek == DayOfWeek.SUNDAY
}
