package com.avangard.app.core.domain

import com.avangard.app.core.domain.model.Habit
import com.avangard.app.sync.notifications.StatusNotifier

/**
 * Test double — domain use-cases that post status notifications take a
 * [StatusNotifier], so unit tests need a JVM-safe stand-in (the real impl
 * touches Android's NotificationManager).
 */
object NoopStatusNotifier : StatusNotifier {
    override fun notifyStatusFix(habit: Habit, mode: String) = Unit
}
