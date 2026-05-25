package com.avangard.app.core.domain.model

enum class DayClass { Extracted, Partial, Burned, Today, Future }

enum class WeekClass { Extracted, Partial, Burned, Current, Future }

data class ChronometerProgress(
    val configured: Boolean,
    val daysLived: Int,
    val daysBudget: Int,
    val daysRemaining: Int,
    val yearsRemaining: Double,
    val extractedDays: Int,
    val partialDays: Int,
    val burnedDays: Int,
    val todayClass: DayClass,
    /** Class of yesterday's day; used by the ignition notification body. */
    val yesterdayClass: DayClass,
    val weeks: List<WeekClass>,
) {
    companion object {
        val EMPTY = ChronometerProgress(
            configured = false,
            daysLived = 0,
            daysBudget = 0,
            daysRemaining = 0,
            yearsRemaining = 0.0,
            extractedDays = 0,
            partialDays = 0,
            burnedDays = 0,
            todayClass = DayClass.Today,
            yesterdayClass = DayClass.Burned,
            weeks = emptyList(),
        )
    }
}
