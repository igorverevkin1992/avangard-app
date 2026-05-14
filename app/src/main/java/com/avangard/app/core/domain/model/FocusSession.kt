package com.avangard.app.core.domain.model

data class FocusSession(
    val id: Long,
    val dateEpoch: Long,
    val habit: Habit,
    val startedAt: Long,
    val endedAt: Long?,
) {
    val isActive: Boolean get() = endedAt == null
    fun durationMillis(now: Long): Long = (endedAt ?: now) - startedAt
}
