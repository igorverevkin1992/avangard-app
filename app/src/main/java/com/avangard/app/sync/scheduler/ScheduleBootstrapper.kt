package com.avangard.app.sync.scheduler

import com.avangard.app.sync.notifications.ReportNotificationPresenter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single entry point used by [com.avangard.app.AvangardApplication] on cold
 * start. Re-arms regulatory alarms idempotently so we converge on the
 * intended schedule even if the device was rebooted while the app was
 * uninstalled or alarms were dropped by the system.
 */
@Singleton
class ScheduleBootstrapper @Inject constructor(
    private val dispatcher: AlarmDispatcher,
    private val presenter: ReportNotificationPresenter,
) {
    fun bootstrap() {
        presenter.ensureChannel()
        dispatcher.scheduleAll()
    }
}
