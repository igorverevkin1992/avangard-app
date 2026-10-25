package com.itermeum.app.sync.scheduler

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Section 10 entry point. Called on app start, BOOT_COMPLETED, TIME_SET,
 * TIMEZONE_CHANGED, MY_PACKAGE_REPLACED, settings changes, «Я крещён» and at
 * 03:00. The skeleton has nothing to schedule yet; phases 4 and 8 fill it in.
 */
@Singleton
class ReminderScheduler @Inject constructor() {
    suspend fun rescheduleAll() {
        // Intentionally empty in phase 1.
    }
}
