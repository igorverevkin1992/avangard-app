package com.itermeum.app.core.data

import com.itermeum.app.core.common.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One in-memory timer for the mental-prayer and reading sessions (5.6, 5.9).
 * The foreground service mirrors it in a notification; the ViewModels
 * read it. Start time is wall-clock, so process death only loses the
 * notification, not the elapsed minutes shown on return.
 */
@Singleton
class TimerController @Inject constructor(private val clock: Clock) {

    enum class Kind { MENTAL_PRAYER, READING }

    data class Session(val kind: Kind, val startedAt: Long, val durationMs: Long, val bookId: String? = null) {
        fun elapsed(now: Long) = (now - startedAt).coerceAtLeast(0)
        fun fraction(now: Long) = (elapsed(now).toDouble() / durationMs).coerceIn(0.0, 1.0)
    }

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    fun start(kind: Kind, durationMs: Long, bookId: String? = null): Session {
        val s = Session(kind, clock.nowEpochMillis(), durationMs, bookId)
        _session.value = s
        return s
    }

    /** Returns the finished session (caller decides whether ≥80 % counts). */
    fun stop(): Session? = _session.value.also { _session.value = null }

    fun current(): Session? = _session.value
}
