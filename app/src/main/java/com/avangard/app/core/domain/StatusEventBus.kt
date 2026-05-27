package com.avangard.app.core.domain

import com.avangard.app.core.domain.model.Habit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * What just got fixed: which habit, which mode (СТАНДАРТ / МИНИМУМ).
 * Source-of-truth for both the in-app banner on the pulpit and the
 * accompanying low-priority system notification — both fire from the
 * same use-case path.
 */
data class StatusFixedEvent(val habit: Habit, val mode: String)

/**
 * Process-wide bus carrying status-fix events from use-cases to UI. Singleton
 * so the bus survives ViewModel lifecycle — the pulpit can be off-screen
 * when the user marks an Infra habit (e.g. via a future widget) and still
 * pick the banner up when it next becomes active.
 */
@Singleton
class StatusEventBus @Inject constructor() {
    private val _events = MutableSharedFlow<StatusFixedEvent>(
        replay = 0,
        extraBufferCapacity = 4,
    )
    val events: SharedFlow<StatusFixedEvent> = _events.asSharedFlow()

    fun tryEmit(event: StatusFixedEvent): Boolean = _events.tryEmit(event)
}
