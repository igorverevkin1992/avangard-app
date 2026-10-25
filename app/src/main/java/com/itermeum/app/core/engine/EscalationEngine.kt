package com.itermeum.app.core.engine

import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Section 11.7. Decides the escalation level of a norm reminder and whether
 * the daily budget / quiet hours allow it. Level 1 at the norm's time;
 * level 2 at +90 min when the norm is core and still open; level 3 once per
 * day, two hours before bed, when fidelity is below threshold.
 */
object EscalationEngine {

    const val MAX_PER_DAY = 8
    const val MAX_LEVEL3_PER_DAY = 1
    const val LEVEL2_AFTER_MIN = 90L
    const val LEVEL3_BEFORE_BED_HOURS = 2L
    val QUIET_START: LocalTime = LocalTime.of(23, 30)
    val QUIET_END: LocalTime = LocalTime.of(6, 0)

    enum class Priority { ALARM, HEALTH, CORE, OTHER }

    data class Budget(val sentToday: Int, val level3SentToday: Int)

    data class Candidate(val normId: String, val level: Int, val at: LocalDateTime, val priority: Priority, val muted: Boolean)

    fun level(normTime: LocalDateTime, isCore: Boolean, done: Boolean, now: LocalDateTime): Int? {
        if (done || now.isBefore(normTime)) return null
        return if (isCore && !now.isBefore(normTime.plusMinutes(LEVEL2_AFTER_MIN))) 2 else 1
    }

    fun threatDue(now: LocalDateTime, bedTime: LocalTime, fidelityMet: Boolean, level3SentToday: Int): Boolean {
        if (fidelityMet || level3SentToday >= MAX_LEVEL3_PER_DAY) return false
        val threat = now.toLocalDate().atTime(bedTime).minusHours(LEVEL3_BEFORE_BED_HOURS)
        return !now.isBefore(threat)
    }

    fun inQuietHours(t: LocalTime): Boolean = !t.isBefore(QUIET_START) || t.isBefore(QUIET_END)

    /** Alarms always pass; everything else respects quiet hours, Mass/catechesis silence and the daily cap. */
    fun allow(c: Candidate, budget: Budget): Boolean {
        if (c.priority == Priority.ALARM) return true
        if (inQuietHours(c.at.toLocalTime())) return false
        if (c.muted) return false
        if (budget.sentToday >= MAX_PER_DAY) return false
        if (c.level == 3 && budget.level3SentToday >= MAX_LEVEL3_PER_DAY) return false
        return true
    }

    /** When over budget, keep the most important candidates: alarm > health > core > other. */
    fun prioritize(candidates: List<Candidate>, budget: Budget): List<Candidate> {
        val room = (MAX_PER_DAY - budget.sentToday).coerceAtLeast(0)
        val (alarms, rest) = candidates.partition { it.priority == Priority.ALARM }
        return alarms + rest.filter { allow(it, budget) }.sortedBy { it.priority.ordinal }.take(room)
    }
}
