package com.itermeum.app.core.liturgy

import java.time.LocalDate

enum class Season { ADVENT, CHRISTMAS, ORDINARY_1, LENT, TRIDUUM, EASTER, ORDINARY_2 }

enum class Rank { SOLEMNITY, FEAST, SUNDAY, MEMORIAL, WEEKDAY }

/** Section 12 liturgical accent names; hex values live in the design system. */
enum class LiturgicalColor { GREEN, VIOLET, ROSE, WHITE, RED }

data class LiturgicalDay(
    val date: LocalDate,
    val season: Season,
    val rank: Rank,
    val color: LiturgicalColor,
    val celebrationId: String?,
    val title: String,
    val sundayCycle: SundayCycle,
    val weekdayCycle: WeekdayCycle,
    val gospelRef: String?,
    /** Section 5.7: «Царица Небесная» from the Easter Vigil through Pentecost. */
    val reginaCaeli: Boolean,
    /** Section 3.4: Lenten Friday — abstinence reminder (voluntary wording). */
    val lentFriday: Boolean,
    /** Week number inside the season (Advent/Lent/Easter/Ordinary), 0 for Ash-Wednesday days. */
    val weekOfSeason: Int,
)
