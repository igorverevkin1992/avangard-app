package com.itermeum.app.core.common

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Injectable wall clock so every engine can be driven by a fake in tests. */
interface Clock {
    fun nowEpochMillis(): Long
    fun zone(): ZoneId
    fun now(): LocalDateTime = Instant.ofEpochMilli(nowEpochMillis()).atZone(zone()).toLocalDateTime()
    fun today(): LocalDate = now().toLocalDate()
    fun localTime(): LocalTime = now().toLocalTime()
    /** Section 4: the logical day runs 03:00–02:59 local time. */
    fun logicalToday(): LocalDate = LogicalDay.of(now())
}

@Singleton
class SystemClock @Inject constructor() : Clock {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}
