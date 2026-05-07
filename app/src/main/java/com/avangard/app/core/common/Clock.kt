package com.avangard.app.core.common

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

interface Clock {
    fun nowEpochMillis(): Long
    fun today(): LocalDate
    fun localTime(): LocalTime
    fun zone(): ZoneId
}

@Singleton
class SystemClock @Inject constructor() : Clock {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()
    override fun today(): LocalDate = LocalDate.now()
    override fun localTime(): LocalTime = LocalTime.now()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}

fun LocalDate.toStartOfDayEpoch(zone: ZoneId): Long =
    atStartOfDay(zone).toEpochSecond() * 1000L
