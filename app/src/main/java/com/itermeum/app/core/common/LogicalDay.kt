package com.itermeum.app.core.common

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Section 4, rule 1: a logical day starts at 03:00 and ends at 02:59 local
 * time, so an Examen written at 00:30 still belongs to the evening before.
 */
object LogicalDay {
    val START: LocalTime = LocalTime.of(3, 0)

    fun of(dateTime: LocalDateTime): LocalDate =
        if (dateTime.toLocalTime().isBefore(START)) dateTime.toLocalDate().minusDays(1)
        else dateTime.toLocalDate()

    /** Wall-clock start of the given logical day. */
    fun startOf(date: LocalDate): LocalDateTime = date.atTime(START)

    /** Exclusive wall-clock end of the given logical day (= start of the next). */
    fun endOf(date: LocalDate): LocalDateTime = date.plusDays(1).atTime(START)
}
