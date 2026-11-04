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
    private val items: WorkItemRepository,
    private val work: dagger.Lazy<WorkRepository>,
) {
    /** [complete] — focus «Готово»: the item is closed too (its slot of today's plan with it). */
    suspend fun finish(complete: Boolean = false): TimerController.Session? {
        val s = timers.stop() ?: return null
        val now = clock.nowEpochMillis()
        when (s.kind) {
            TimerController.Kind.MENTAL_PRAYER -> if (s.fraction(now) >= 0.8) norms.markDone(NormCatalog.MENTAL_PRAYER, clock.logicalToday())
            TimerController.Kind.READING -> {
                // D-59: the session continued the day's clock — log only what it added.
                val total = s.elapsed(now)
                val date = clock.logicalToday()
                readingCarry = date to total
                // Whole minutes of the day minus whole minutes before: no seconds lost between sittings.
                reading.logReading(date, s.bookId ?: "book", (total / 60_000L - s.priorMs / 60_000L).toInt(), (total / 60_000L).toInt())
            }
            // D-64: the session's own time goes to the item and the day's focus log.
            TimerController.Kind.FOCUS -> s.refId?.let { id ->
                items.addFocus(id, s.elapsed(now) - s.priorMs)
                if (complete) work.get().completeItem(id)
            }
        }
        return s
    }

    /** The day's reading so far, to the second while the app runs (minutes from the log after a restart). */
    @Volatile private var readingCarry: Pair<java.time.LocalDate, Long>? = null

    suspend fun readingPriorMs(): Long {
        val date = clock.logicalToday()
        val fromLog = reading.minutesOn(date) * 60_000L
        val carried = readingCarry?.takeIf { it.first == date }?.second ?: 0L
        return maxOf(fromLog, carried)
    }

    /** Milliseconds from the start after which the session counts. */
    fun countsAfterMs(s: TimerController.Session): Long = when (s.kind) {
        TimerController.Kind.MENTAL_PRAYER -> (s.durationMs * 0.8).toLong()
        TimerController.Kind.READING -> ReadingRepository.READING_MIN_MINUTES * 60_000L
        TimerController.Kind.FOCUS -> 0L
    }
}
