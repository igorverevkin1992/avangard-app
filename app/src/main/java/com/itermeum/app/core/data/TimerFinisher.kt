package com.itermeum.app.core.data

import com.itermeum.app.core.common.Clock
import com.itermeum.app.core.content.NormCatalog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stops the running prayer/reading timer and records it: mental prayer counts
 * at ≥80 % of its time, reading logs its minutes (≥8 count). Shared by the
 * screens and the timer card in the shade (D-50).
 */
@Singleton
class TimerFinisher @Inject constructor(
    private val timers: TimerController,
    private val norms: NormRepository,
    private val reading: ReadingRepository,
    private val clock: Clock,
) {
    suspend fun finish(): TimerController.Session? {
        val s = timers.stop() ?: return null
        val now = clock.nowEpochMillis()
        when (s.kind) {
            TimerController.Kind.MENTAL_PRAYER -> if (s.fraction(now) >= 0.8) norms.markDone(NormCatalog.MENTAL_PRAYER, clock.logicalToday())
            TimerController.Kind.READING -> reading.logReading(clock.logicalToday(), s.bookId ?: "book", (s.elapsed(now) / 60_000L).toInt())
        }
        return s
    }

    /** Milliseconds from the start after which the session counts. */
    fun countsAfterMs(s: TimerController.Session): Long = when (s.kind) {
        TimerController.Kind.MENTAL_PRAYER -> (s.durationMs * 0.8).toLong()
        TimerController.Kind.READING -> ReadingRepository.READING_MIN_MINUTES * 60_000L
    }
}
