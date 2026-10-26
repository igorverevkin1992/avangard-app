package com.itermeum.app.core.database.entity

import androidx.room.Entity
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import androidx.room.PrimaryKey
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Section 8 `Profile`. Single row (id = 1). */
@Serializable
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String = "",
    @Contextual val wakeTime: LocalTime = LocalTime.of(6, 30),
    @Contextual val wakeTimeWeekend: LocalTime = LocalTime.of(7, 30),
    @Contextual val bedTime: LocalTime = LocalTime.of(23, 0),
    @Contextual val workStart: LocalTime = LocalTime.of(12, 0),
    @Contextual val workEnd: LocalTime = LocalTime.of(20, 0),
    val timezoneHome: String = "Europe/Moscow",
    val baptized: Boolean = false,
    @Contextual val baptismDate: LocalDate? = null,
    /** Mass times per day (D-22): the Saturday evening Mass and the Sunday morning one. */
    @Contextual val massTimeSaturday: LocalTime = LocalTime.of(18, 0),
    @Contextual val massTimeSunday: LocalTime = LocalTime.of(10, 0),
    /** Day of week for the usual Mass: 6 = Saturday evening, 7 = Sunday morning. */
    val massDay: Int = 6,
    val contentPackVersion: Int = 0,
    @Contextual val createdAt: LocalDate? = null,
    val onboardingDone: Boolean = false,
    val outreachTarget: Int = 5,
    val wakeProofMethod: String = "QR",
    val wakeQrUuid: String? = null,
    val alarmSound: Int = 1,
    val alarmVolume: Int = 100,
    val notificationTone: String = "FIRM",
    val travelAuto: Boolean = true,
    val confessionEveryDays: Int = 30,
    val examenPinEnabled: Boolean = false,
) {
    fun massTimeFor(day: DayOfWeek): LocalTime = if (day == DayOfWeek.SATURDAY) massTimeSaturday else massTimeSunday
}
