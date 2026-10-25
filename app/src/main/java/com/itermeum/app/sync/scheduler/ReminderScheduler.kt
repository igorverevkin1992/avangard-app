package com.itermeum.app.sync.scheduler

import android.content.Context
import android.content.Intent
import com.itermeum.app.core.common.Clock
import com.itermeum.app.core.content.ContentSeeder
import com.itermeum.app.core.data.ProfileRepository
import com.itermeum.app.sync.alarm.AlarmHealthReceiver
import com.itermeum.app.sync.alarm.ExactAlarms
import com.itermeum.app.sync.alarm.WakeAlarmScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalTime
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Section 10 entry point. Called on app start, BOOT_COMPLETED, TIME_SET,
 * TIMEZONE_CHANGED, MY_PACKAGE_REPLACED, settings changes, «Я крещён» and at
 * 03:00. Phase 4: wake alarm + 23:15 health check. Norm reminders and the
 * 03:00 day close arrive in phase 8.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val seeder: ContentSeeder,
    private val profiles: ProfileRepository,
    private val wake: WakeAlarmScheduler,
    private val clock: Clock,
) {
    private val alarms = ExactAlarms(context)

    suspend fun rescheduleAll() {
        seeder.seedIfNeeded()
        val profile = profiles.get()
        if (!profile.onboardingDone) return
        wake.scheduleNext()
        scheduleHealthCheck()
    }

    private fun scheduleHealthCheck() {
        val now = clock.now()
        var at = now.toLocalDate().atTime(HEALTH_CHECK)
        if (!at.isAfter(now)) at = at.plusDays(1)
        val epoch = ZonedDateTime.of(at, clock.zone()).toInstant().toEpochMilli()
        val op = alarms.broadcast(ExactAlarms.RC_HEALTH, Intent(context, AlarmHealthReceiver::class.java).setAction(AlarmHealthReceiver.ACTION))
        alarms.setExact(epoch, op)
    }

    companion object {
        val HEALTH_CHECK: LocalTime = LocalTime.of(23, 15)
    }
}
