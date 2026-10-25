package com.itermeum.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Section 8 `Profile`. Single row (id = 1). Times are stored as minutes
 * since midnight so the schema stays SQLite-native; `baptismDate` is an
 * ISO-8601 string or null until the «Я крещён» action.
 */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String = "",
    val wakeTimeMin: Int = 6 * 60 + 30,
    val wakeTimeWeekendMin: Int = 7 * 60 + 30,
    val bedTimeMin: Int = 23 * 60,
    val workStartMin: Int = 12 * 60,
    val workEndMin: Int = 20 * 60,
    val timezoneHome: String = "Europe/Moscow",
    val baptized: Boolean = false,
    val baptismDate: String? = null,
    val massTimeMin: Int? = null,
    val contentPackVersion: Int = 0,
    val createdAt: String? = null,
)
