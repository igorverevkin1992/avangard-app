package com.itermeum.app.core.data

import com.itermeum.app.R

/**
 * D-79: the vows a person can take — each on its own, with its own count of days,
 * question in the Examen, milestones and help in a hard moment. The id is the
 * `vow.id` / `vow_day.vowId` in the database.
 */
enum class VowKind(
    val id: String,
    /** Full name: «Зарок трезвости». */
    val title: Int,
    /** Discreet name for lines and rows: «Трезвость». */
    val short: Int,
    val intro: Int,
    val counts: Int,
    /** The «I want to…» button and screen title. */
    val sos: Int,
    val sosHint: Int,
    /** Jaculatory situation for the hard moment. */
    val situation: String,
    /** A prayer shown in the hard moment, if any, with a line about how to pray it. */
    val prayerId: String?,
    val prayerNote: Int?,
    /** A feast reminder and a ready answer belong to this vow. */
    val feasts: Boolean,
    /** The doctor's warning about stopping at once. */
    val health: Boolean,
) {
    SOBRIETY(
        "sobriety", R.string.vow_sobriety_title, R.string.vow_sobriety_short, R.string.vow_intro, R.string.vow_counts,
        R.string.vow_sos, R.string.vow_sos_hint, "TEMPTATION", "st_matthias", null, feasts = true, health = true,
    ),
    PURITY(
        "purity", R.string.vow_purity_title, R.string.vow_purity_short, R.string.vow_purity_intro, R.string.vow_purity_counts,
        R.string.vow_purity_sos, R.string.vow_purity_sos_hint, "PURITY", "hail_mary", R.string.vow_purity_prayer_note, feasts = false, health = false,
    ),
    GAMES(
        "games", R.string.vow_games_title, R.string.vow_games_short, R.string.vow_games_intro, R.string.vow_games_counts,
        R.string.vow_games_sos, R.string.vow_games_sos_hint, "TEMPTATION", null, null, feasts = false, health = false,
    ),
    ;

    companion object {
        fun of(id: String?): VowKind? = entries.firstOrNull { it.id == id }
    }
}
