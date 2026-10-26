package com.itermeum.app.core.engine

import com.itermeum.app.core.database.entity.MemorizationItemEntity
import com.itermeum.app.core.database.entity.SrsStage
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Section 11.5. SM-2 over the six prayer stages. Grades: 1 = не вспомнил,
 * 3 = с трудом, 5 = легко. Stage +1 after two easy answers in a row;
 * RECALL + three easy in a row → KNOWN with interval ≥ 21 days.
 */
object SrsEngine {

    const val MIN_EASINESS = 1.3
    const val KNOWN_INTERVAL_DAYS = 21
    const val NEW_PRAYER_EVERY_DAYS = 7
    const val SESSION_MAX_MINUTES = 5

    enum class Grade(val q: Int) { FORGOT(1), HARD(3), EASY(5) }

    fun review(item: MemorizationItemEntity, grade: Grade, today: LocalDate): MemorizationItemEntity {
        val q = grade.q
        var ef = item.easiness + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02))
        if (ef < MIN_EASINESS) ef = MIN_EASINESS

        if (q < 3) {
            val stage = demote(item.stage)
            return item.copy(
                easiness = ef, repetitions = 0, intervalDays = 1, stage = stage,
                consecutiveEasy = 0, dueDate = today.plusDays(1), lastReviewed = today,
            )
        }
        val repetitions = item.repetitions + 1
        val interval = when (repetitions) {
            1 -> 1
            2 -> 6
            else -> (item.intervalDays * ef).roundToInt().coerceAtLeast(1)
        }
        val easyRun = if (grade == Grade.EASY) item.consecutiveEasy + 1 else 0
        var stage = item.stage
        var run = easyRun
        if (stage == SrsStage.RECALL && easyRun >= 3) {
            stage = SrsStage.KNOWN
            run = 0
        } else if (stage != SrsStage.RECALL && stage != SrsStage.KNOWN && easyRun >= 2) {
            stage = promote(stage)
            run = 0
        }
        val finalInterval = if (stage == SrsStage.KNOWN) maxOf(interval, KNOWN_INTERVAL_DAYS) else interval
        return item.copy(
            easiness = ef, repetitions = repetitions, intervalDays = finalInterval, stage = stage,
            consecutiveEasy = run, dueDate = today.plusDays(finalInterval.toLong()), lastReviewed = today,
        )
    }

    fun promote(stage: SrsStage): SrsStage = when (stage) {
        SrsStage.READ -> SrsStage.CLOZE_25
        SrsStage.CLOZE_25 -> SrsStage.CLOZE_50
        SrsStage.CLOZE_50 -> SrsStage.CLOZE_100
        SrsStage.CLOZE_100 -> SrsStage.RECALL
        SrsStage.RECALL -> SrsStage.KNOWN
        SrsStage.KNOWN -> SrsStage.KNOWN
    }

    /** Section 11.5: on a miss, one stage back but never below CLOZE_25 once introduced. */
    fun demote(stage: SrsStage): SrsStage = when (stage) {
        SrsStage.READ, SrsStage.CLOZE_25 -> SrsStage.CLOZE_25
        SrsStage.CLOZE_50 -> SrsStage.CLOZE_25
        SrsStage.CLOZE_100 -> SrsStage.CLOZE_50
        SrsStage.RECALL -> SrsStage.CLOZE_100
        SrsStage.KNOWN -> SrsStage.RECALL
    }

    /** May a new prayer be introduced today? At most one per 7 days. */
    fun canIntroduce(lastIntroducedOn: LocalDate?, today: LocalDate): Boolean =
        lastIntroducedOn == null || !today.isBefore(lastIntroducedOn.plusDays(NEW_PRAYER_EVERY_DAYS.toLong()))

    /** Fraction of words hidden at each stage. */
    fun clozeFraction(stage: SrsStage): Double = when (stage) {
        SrsStage.READ -> 0.0
        SrsStage.CLOZE_25 -> 0.25
        SrsStage.CLOZE_50 -> 0.5
        SrsStage.CLOZE_100, SrsStage.RECALL, SrsStage.KNOWN -> 1.0
    }

    /**
     * Deterministic cloze: words are hidden by a seeded pseudo-random
     * sequence (`seed = prayerId + stage`) so the same card always hides
     * the same words. Returns the token list with `hidden` flags.
     */
    fun cloze(text: String, stage: SrsStage, prayerId: String): List<Token> {
        val words = text.split(Regex("(?<=\\s)|(?=\\s)")).filter { it.isNotEmpty() }
        val fraction = clozeFraction(stage)
        if (fraction == 0.0) return words.map { Token(it, false) }
        val wordIdx = words.indices.filter { words[it].isNotBlank() && words[it].any(Char::isLetter) }
        val hideCount = (wordIdx.size * fraction).roundToInt()
        val seed = (prayerId + stage.name).hashCode().toLong()
        // Own LCG: kotlin.random's sequence is not guaranteed across Kotlin versions,
        // and the hidden words must stay the same for a given prayer and stage.
        var state = seed xor 0x5DEECE66DL
        fun next(bound: Int): Int { state = (state * 6364136223846793005L + 1442695040888963407L); return ((state ushr 33) % bound).toInt().let { if (it < 0) -it else it } }
        val shuffled = wordIdx.toMutableList().also { l -> for (i in l.lastIndex downTo 1) { val j = next(i + 1); val t = l[i]; l[i] = l[j]; l[j] = t } }
        val hidden = shuffled.take(hideCount).toSet()
        return words.mapIndexed { i, w -> Token(w, i in hidden) }
    }

    data class Token(val text: String, val hidden: Boolean)
}
