package com.itermeum.app.core.content

import com.itermeum.app.core.database.entity.NormCategory
import com.itermeum.app.core.database.entity.NormEntity
import com.itermeum.app.core.database.entity.NormFrequency
import java.time.DayOfWeek
import java.time.LocalTime

/** Section 4: the full norm list with default times and day masks. */
object NormCatalog {
    const val WAKE = "wake"
    const val SERVIAM = "serviam"
    const val QUARANTINE = "quarantine"
    const val MENTAL_PRAYER = "mental_prayer"
    const val GYM = "gym"
    const val GOSPEL = "gospel"
    const val READING = "reading"
    const val SRS = "srs"
    const val ANGELUS = "angelus"
    const val DAY_PLAN = "day_plan"
    const val FROG = "frog"
    const val JACULATORIA = "jaculatoria"
    const val LAST_STONE = "last_stone"
    const val ROSARY = "rosary"
    const val EXAMEN = "examen"
    const val MORTIFICATION = "mortification"
    const val MARIAN_SATURDAY = "marian_saturday"
    const val CATECHESIS = "catechesis"
    const val MASS = "mass"
    const val WEEKLY_REVIEW = "weekly_review"
    const val MONTHLY_SUMMARY = "monthly_summary"
    const val QUARTER_CHECK = "quarter_check"
    const val CONFESSION = "confession"

    const val ALL_DAYS = 0b1111111
    const val WEEKDAYS = 0b0011111
    val SAT = bit(DayOfWeek.SATURDAY)
    val SUN = bit(DayOfWeek.SUNDAY)
    val FRI = bit(DayOfWeek.FRIDAY)

    fun bit(day: DayOfWeek): Int = 1 shl (day.value - 1)
    fun Int.includes(day: DayOfWeek): Boolean = this and bit(day) != 0

    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)

    val all: List<NormEntity> = listOf(
        norm(WAKE, "Подъём", NormCategory.MORNING, t(6, 30), 1, core = true, days = ALL_DAYS, follows = true, order = 1),
        norm(SERVIAM, "Serviam + приношение", NormCategory.MORNING, t(6, 33), 2, days = ALL_DAYS, follows = true, order = 2),
        norm(QUARANTINE, "30 минут без мессенджеров", NormCategory.MORNING, t(6, 33), 30, days = ALL_DAYS, follows = true, order = 3),
        norm(MENTAL_PRAYER, "Ментальная молитва", NormCategory.PRAYER, t(7, 5), 10, days = ALL_DAYS, follows = true, order = 4),
        norm(GYM, "Зал", NormCategory.MORNING, t(7, 20), 60, days = WEEKDAYS, follows = true, order = 5),
        norm(GOSPEL, "Евангелие дня", NormCategory.PRAYER, t(8, 30), 5, days = ALL_DAYS, follows = true, order = 6),
        norm(READING, "Духовное чтение", NormCategory.PRAYER, t(8, 30), 10, days = ALL_DAYS, follows = true, order = 7),
        norm(SRS, "Повторение молитв", NormCategory.PRAYER, t(8, 45), 5, days = ALL_DAYS, follows = true, order = 8),
        norm(ANGELUS, "Ангел Господень", NormCategory.PRAYER, t(12, 0), 2, days = ALL_DAYS, follows = false, order = 9),
        norm(DAY_PLAN, "План дня: три задачи", NormCategory.WORK, t(12, 0), 5, days = WEEKDAYS, follows = false, order = 10),
        norm(FROG, "Обзвон героев", NormCategory.WORK, t(12, 5), 30, core = true, days = WEEKDAYS, follows = false, order = 11),
        norm(JACULATORIA, "Молитвы-стрелы", NormCategory.PRAYER, null, 0, days = ALL_DAYS, follows = false, order = 12),
        norm(LAST_STONE, "Последний камень", NormCategory.WORK, t(19, 30), 15, days = WEEKDAYS, follows = false, order = 13),
        norm(ROSARY, "Розарий", NormCategory.PRAYER, t(20, 15), 20, days = ALL_DAYS, follows = false, order = 14),
        norm(EXAMEN, "Испытание совести", NormCategory.PRAYER, t(22, 40), 5, core = true, days = ALL_DAYS, follows = false, order = 15),
        norm(MORTIFICATION, "Малое умерщвление", NormCategory.PATH, null, 0, days = FRI, follows = false, order = 16),
        norm(MARIAN_SATURDAY, "Славься, Царица", NormCategory.PRAYER, null, 2, days = SAT, follows = false, order = 17),
        norm(CATECHESIS, "Катехизация + заметка", NormCategory.PATH, t(16, 0), 90, days = SAT, follows = false, order = 18),
        norm(MASS, "Месса + духовное причастие", NormCategory.PRAYER, null, 60, days = SAT or SUN, follows = false, order = 19, freq = NormFrequency.WEEKLY),
        norm(WEEKLY_REVIEW, "Обзор недели", NormCategory.PATH, t(18, 0), 20, days = SUN, follows = false, order = 20, freq = NormFrequency.WEEKLY),
        norm(MONTHLY_SUMMARY, "Итог месяца", NormCategory.PATH, null, 5, days = ALL_DAYS, follows = false, order = 21, freq = NormFrequency.MONTHLY),
        norm(QUARTER_CHECK, "Вехи к студии + 4 вопроса", NormCategory.PATH, null, 20, days = SUN, follows = false, order = 22, freq = NormFrequency.QUARTERLY),
        norm(CONFESSION, "Исповедь", NormCategory.PRAYER, null, 30, days = ALL_DAYS, follows = false, order = 23, freq = NormFrequency.MONTHLY, baptism = true),
    )

    private fun norm(
        id: String, title: String, category: NormCategory, time: LocalTime?, duration: Int,
        core: Boolean = false, days: Int, follows: Boolean, order: Int,
        freq: NormFrequency = NormFrequency.DAILY, baptism: Boolean = false,
    ) = NormEntity(
        id = id, title = title, category = category, defaultTime = time, durationMin = duration,
        requiresBaptism = baptism, isCore = core, frequency = freq, daysOfWeek = days,
        followsWakeTime = follows, orderIndex = order,
    )
}
