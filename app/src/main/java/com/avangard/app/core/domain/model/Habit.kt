package com.avangard.app.core.domain.model

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
