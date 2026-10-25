package com.itermeum.app.core.database

import androidx.room.TypeConverter
import java.time.LocalDate
import java.time.LocalTime

/**
 * LocalDate ↔ ISO-8601 text (sorts correctly in SQL), LocalTime ↔ minutes
 * since midnight. Enums are handled by Room natively (stored by name).
 */
class Converters {
    @TypeConverter fun dateToString(value: LocalDate?): String? = value?.toString()
    @TypeConverter fun stringToDate(value: String?): LocalDate? = value?.let(LocalDate::parse)
    @TypeConverter fun timeToMinutes(value: LocalTime?): Int? = value?.let { it.hour * 60 + it.minute }
    @TypeConverter fun minutesToTime(value: Int?): LocalTime? = value?.let { LocalTime.of(it / 60, it % 60) }
}
