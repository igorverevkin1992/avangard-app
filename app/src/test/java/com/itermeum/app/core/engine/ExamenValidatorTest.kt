package com.itermeum.app.core.engine

import com.itermeum.app.core.engine.ExamenValidator.Reason
import com.itermeum.app.core.engine.ExamenValidator.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Section 11.4: 15 accepted, 15 rejected. */
class ExamenValidatorTest {

    private val accepted = listOf(
        "Завтра до 13:00 я позвоню трём героям",
        "Утром не открою телефон до конца молитвы",
        "Завтра на худсовете выслушаю всех до конца, ни разу не перебив",
        "Напишу продюсеру до обеда",
        "Закрою смету до 19:30",
        "Встану по первому будильнику",
        "Сделаю пять звонков до 14:00",
        "Отправлю сценарий вечером",
        "Прочитаю 10 пунктов «Пути»",
        "Договорюсь о съёмке с Ириной",
        "Выключу мессенджеры на 90 минут",
        "Поблагодарю оператора лично",
        "Не буду повышать голос на монтаже",
        "Помолюсь Розарий по дороге домой",
        "Извинюсь перед ассистентом до 12.00",
    )

    private val rejected = listOf(
        "быть лучше" to Reason.NOT_MEASURABLE,
        "стараться" to Reason.NOT_MEASURABLE,
        "меньше нервничать" to Reason.NOT_MEASURABLE,
        "быть добрее к людям" to Reason.NOT_MEASURABLE,
        "жить по-христиански" to Reason.NOT_MEASURABLE,
        "терпение" to Reason.NOT_MEASURABLE,
        "больше молитвы" to Reason.NOT_MEASURABLE,
        "дисциплина" to Reason.NOT_MEASURABLE,
        "работать усерднее" to Reason.NOT_MEASURABLE,
        "верить сильнее" to Reason.NOT_MEASURABLE,
        "да" to Reason.TOO_SHORT,
        "ок" to Reason.TOO_SHORT,
        "" to Reason.TOO_SHORT,
        "    " to Reason.TOO_SHORT,
        ("Завтра до 13:00 я " + "позвоню ".repeat(20)) to Reason.TOO_LONG,
    )

    @Test
    fun `accepts measurable resolutions`() {
        assertEquals(15, accepted.size)
        accepted.forEach { assertEquals(it, Result.Ok, ExamenValidator.validateProposito(it)) }
    }

    @Test
    fun `rejects vague, short and long resolutions`() {
        assertEquals(15, rejected.size)
        rejected.forEach { (text, reason) ->
            val r = ExamenValidator.validateProposito(text)
            assertTrue(text, r is Result.Rejected)
            assertEquals(text, reason, (r as Result.Rejected).reason)
        }
    }
}
