package com.avangard.app.sync.notifications

import com.avangard.app.core.domain.model.Habit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Abstraction over the system-channel posting of «СТАНДАРТ / МИНИМУМ
 * зафиксирован» notifications. Lives in `sync.notifications` next to its
 * only impl so the domain use-cases depend on an interface, not on the
 * Android NotificationManager.
 */
interface StatusNotifier {
    fun notifyStatusFix(habit: Habit, mode: String)
}

@Singleton
class StatusNotifierImpl @Inject constructor(
    private val presenter: SimpleNotificationPresenter,
) : StatusNotifier {
    override fun notifyStatusFix(habit: Habit, mode: String) {
        presenter.presentStatusFix(habit, mode)
    }
}
