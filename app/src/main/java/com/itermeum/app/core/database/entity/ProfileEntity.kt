package com.itermeum.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalTime

/** Section 8 `Profile`. Single row (id = 1). */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String = "",
    val wakeTime: LocalTime = LocalTime.of(6, 30),
    val wakeTimeWeekend: LocalTime = LocalTime.of(7, 30),
    val bedTime: LocalTime = LocalTime.of(23, 0),
    val workStart: LocalTime = LocalTime.of(12, 0),
    val workEnd: LocalTime = LocalTime.of(20, 0),
    val timezoneHome: String = "Europe/Moscow",
    val baptized: Boolean = false,
    val baptismDate: LocalDate? = null,
    val massTime: LocalTime? = null,
    /** Day of week for the usual Mass: 6 = Saturday evening, 7 = Sunday morning. */
    val massDay: Int = 6,
    val contentPackVersion: Int = 0,
    val createdAt: LocalDate? = null,
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
)
