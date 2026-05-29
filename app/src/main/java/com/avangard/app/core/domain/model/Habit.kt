package com.avangard.app.core.domain.model

/**
 * Five-direction tracker. All habits are start-able at any point in the day;
 * the operator owns sequencing. Generations (Core) is structurally singled
 * out — it's the only habit whose Approval is the day-classification source
 * for the chronometer and the only one with a dedicated «АНАЛИЗ ГЕНЕРАЦИИ»
 * panel on the pulpit. UI also surfaces a passive reminder while Core is
 * still Idle so the operator never forgets it's the day's central act.
 */
enum class Habit(
    val code: String,
    val displayName: String,
    val shortLabel: String,
) {
    Generations(code = "01", displayName = "ГЕНЕРАЦИИ", shortLabel = "ГЕН"),
    Spanish(code = "02", displayName = "ИСПАНСКИЙ", shortLabel = "ИСП"),
    Sport(code = "03", displayName = "СПОРТ", shortLabel = "СПОРТ"),
    Watching(code = "04", displayName = "НАСМОТРЕННОСТЬ", shortLabel = "НАСМ"),
    Reading(code = "05", displayName = "ЧТЕНИЕ", shortLabel = "ЧТЕН");

    companion object {
        fun byCode(code: String): Habit? = entries.firstOrNull { it.code == code }
    }
}
