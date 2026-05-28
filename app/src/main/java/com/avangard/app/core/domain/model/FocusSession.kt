package com.avangard.app.core.domain.model

/**
 * A single Flash (Vspyshka) session belongs to its **start day**: [dateEpoch]
 * is the day the session was started, even if it ran across midnight. The UI
 * timer keeps counting upward through the boundary (visible "26:14:32"), but
 * the day-of-start invariant means [sumFocusDurationFor] for the *next* day
 * will not include the portion that bled past midnight. This is documented
 * trade-off — automatic split-at-midnight is a future enhancement.
 */
data class FocusSession(
    val id: Long,
    val dateEpoch: Long,
    val habit: Habit,
    val startedAt: Long,
    val endedAt: Long?,
    /** Operator-typed pre-start note ("что именно сделаю"); null when blank. */
    val intent: String? = null,
) {
    val isActive: Boolean get() = endedAt == null
    fun durationMillis(now: Long): Long = (endedAt ?: now) - startedAt
}
