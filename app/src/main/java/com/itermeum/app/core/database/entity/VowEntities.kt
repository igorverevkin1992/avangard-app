package com.itermeum.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * D-67: a standing vow (зарок) — today only total abstinence from alcohol. Not a daily
 * norm: it is taken once, without a term, and each day is confirmed in the Examen.
 */
@Serializable
@Entity(tableName = "vow")
data class VowEntity(
    @PrimaryKey val id: String = SOBRIETY,
    @Contextual val startDate: LocalDate,
    /** For whom or for what the abstinence is offered — shown in hard moments and on milestones. */
    val intention: String? = null,
    /** Who to call when it is hard. */
    val helperName: String? = null,
    val helperPhone: String? = null,
    /** The ready answer at a feast. */
    val declinePhrase: String? = null,
    val active: Boolean = true,
    val createdAt: Long,
) {
    companion object { const val SOBRIETY = "sobriety" }
}

/** One day of a vow: [kept] null — not answered yet; [feastMinute] — a feast that day (minutes after midnight). */
@Serializable
@Entity(tableName = "vow_day", primaryKeys = ["vowId", "logicalDate"])
data class VowDayEntity(
    val vowId: String,
    @Contextual val logicalDate: LocalDate,
    val kept: Boolean? = null,
    val answeredAt: Long? = null,
    val feastMinute: Int? = null,
)
