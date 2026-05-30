package com.avangard.app.core.domain.model

/**
 * Seed values for the «РЕЖИМ» tab — what the operator should default to
 * if the standards are still blank. The Generations bar is intentionally
 * higher than the rest: it's the day's central act, and «СТАНДАРТ» on
 * Core means a real generative session, not a token gesture.
 *
 * These are starting points, not enforced rules; the operator overwrites
 * them in the Mode screen as the practice matures.
 */
object HabitStandardDefaults {
    private val table: Map<Habit, HabitStandard> = mapOf(
        Habit.Generations to HabitStandard(
            standard = "Полная сессия генерации (≥ 90 мин): постановка задачи, " +
                "проработка, фиксация результата. Главное дело дня.",
            mvd = "Сокращённая сессия (≥ 30 мин) с фиксацией хотя бы одного " +
                "конкретного шага.",
        ),
        Habit.Spanish to HabitStandard(
            standard = "Полный урок (≥ 30 мин): новый материал + повторение.",
            mvd = "Короткое повторение (≥ 10 мин) без нового материала.",
        ),
        Habit.Sport to HabitStandard(
            standard = "45 минут занятий.",
            mvd = "Минимальная разминка / зарядка (≥ 10 мин).",
        ),
        Habit.Watching to HabitStandard(
            standard = "Осмысленный просмотр (≥ 30 мин) с конспектом или " +
                "выписанной мыслью.",
            mvd = "Короткий ролик (≥ 10 мин) без конспекта.",
        ),
        Habit.Reading to HabitStandard(
            standard = "Чтение (≥ 30 мин) с выписанной мыслью или цитатой.",
            mvd = "Короткое чтение (≥ 10 мин) без записи.",
        ),
    )

    fun defaultFor(habit: Habit): HabitStandard =
        table[habit] ?: HabitStandard()

    fun fullMap(): Map<String, HabitStandard> =
        table.mapKeys { (h, _) -> h.code }
}
