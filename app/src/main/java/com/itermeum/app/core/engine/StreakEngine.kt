package com.itermeum.app.core.engine

import com.itermeum.app.core.database.entity.StreakType

/**
 * Section 11.6. Pure evaluation of one logical day for the three streaks.
 * Persistence (StreakState, FreezeLedger) is applied by the caller.
 */
object StreakEngine {

    const val THRESHOLD_HOME = 0.60
    const val THRESHOLD_TRAVEL = 0.50
    const val FREEZES_PER_MONTH = 2
    const val WAKE_WINDOW_MIN = 20L
    const val WAKE_LATE_MIN = 60L

    data class DayFacts(
        val sick: Boolean,
        val travel: Boolean,
        val activeNorms: Int,
        val completedNorms: Int,
        val wakeMet: Boolean,
        val examenDone: Boolean,
        val weekday: Boolean,
        val outreachBeforeCutoff: Int,
        val outreachTarget: Int,
    )

    enum class Verdict { MET, NOT_MET, SKIP }

    data class State(val current: Int, val best: Int, val totalDays: Int)

    sealed interface Outcome {
        val state: State
        data class Advanced(override val state: State) : Outcome
        data class Frozen(override val state: State) : Outcome
        data class Broken(override val state: State) : Outcome
        data class Skipped(override val state: State) : Outcome
    }

    fun fidelityMet(f: DayFacts): Boolean {
        if (f.activeNorms == 0) return false
        val threshold = if (f.travel) THRESHOLD_TRAVEL else THRESHOLD_HOME
        val ratio = f.completedNorms.toDouble() / f.activeNorms
        return ratio >= threshold - 1e-9 && (f.wakeMet || f.examenDone)
    }

    fun verdict(type: StreakType, f: DayFacts): Verdict {
        if (f.sick) return Verdict.SKIP
        return when (type) {
            StreakType.FIDELITY -> if (fidelityMet(f)) Verdict.MET else Verdict.NOT_MET
            StreakType.WAKE -> if (f.wakeMet) Verdict.MET else Verdict.NOT_MET
            StreakType.FROG -> if (!f.weekday) Verdict.SKIP else if (f.outreachBeforeCutoff >= f.outreachTarget) Verdict.MET else Verdict.NOT_MET
        }
    }

    /** Applies a verdict; [freezesLeft] is the remaining monthly allowance for this day's month. */
    fun apply(state: State, verdict: Verdict, freezesLeft: Int): Outcome = when (verdict) {
        Verdict.SKIP -> Outcome.Skipped(state)
        Verdict.MET -> {
            val current = state.current + 1
            Outcome.Advanced(State(current, maxOf(current, state.best), state.totalDays + 1))
        }
        Verdict.NOT_MET -> if (freezesLeft > 0) Outcome.Frozen(state) else Outcome.Broken(state.copy(current = 0))
    }

    /** Section 5.1: proof within [alarm, alarm+20m] counts; up to +60m is «late»; after that MISSED. */
    fun wakeVerdict(alarmAt: Long, provenAt: Long?): WakeVerdict {
        if (provenAt == null) return WakeVerdict.MISSED
        val delta = (provenAt - alarmAt) / 60_000L
        return when {
            delta < 0 -> WakeVerdict.ON_TIME
            delta <= WAKE_WINDOW_MIN -> WakeVerdict.ON_TIME
            delta <= WAKE_LATE_MIN -> WakeVerdict.LATE
            else -> WakeVerdict.MISSED
        }
    }

    enum class WakeVerdict { ON_TIME, LATE, MISSED }
}
