package com.itermeum.app.core.engine

/**
 * Section 11.4 / 5.12: a propósito is 5–120 characters and must be
 * measurable — it contains a time («до 13:00», «утром», «до обеда»), a
 * number, or an action verb from the dictionary.
 */
object ExamenValidator {

    private val TIME = Regex("""\b\d{1,2}[:.]\d{2}\b""")
    private val NUMBER = Regex("""\d+""")
    private val TIME_WORDS = listOf("утром", "до обеда", "после обеда", "вечером", "в полдень", "перед сном", "к полудню", "до 12", "в первый час")
    private val ACTION_VERBS = listOf(
        "позвон", "напиш", "отправ", "договор", "закро", "законч", "сдела", "встан", "начн", "прочита", "помол",
        "выключ", "откро", "запиш", "отправлю", "назнач", "извин", "поблагодар", "попрош", "отвеч", "дойд", "схож",
        "сниму", "смонтир", "напечата", "подпиш", "оплач", "выплач", "собер", "уберу", "разбер", "подготов", "проверю",
        "не открою", "не буду", "откажусь", "промолч", "выслуша", "дозвон",
        "приду", "скажу", "спрошу", "лягу", "куплю", "пойду", "выйду", "поеду", "заплачу", "выучу", "повторю", "навещу",
    )

    sealed interface Result {
        data object Ok : Result
        data class Rejected(val reason: Reason) : Result
    }

    enum class Reason { TOO_SHORT, TOO_LONG, NOT_MEASURABLE }

    fun validateProposito(text: String): Result {
        val t = text.trim()
        if (t.length < 5) return Result.Rejected(Reason.TOO_SHORT)
        if (t.length > 120) return Result.Rejected(Reason.TOO_LONG)
        val lower = t.lowercase()
        val measurable = TIME.containsMatchIn(lower) || NUMBER.containsMatchIn(lower) ||
            TIME_WORDS.any { lower.contains(it) } || ACTION_VERBS.any { lower.contains(it) }
        return if (measurable) Result.Ok else Result.Rejected(Reason.NOT_MEASURABLE)
    }

    const val TEMPLATE = "Завтра до [время] я [действие]"
}
