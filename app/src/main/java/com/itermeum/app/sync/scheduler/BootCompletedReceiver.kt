package com.itermeum.app.sync.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.itermeum.app.core.data.DayFlags
import com.itermeum.app.sync.alarm.WakeAlarmScheduler
import com.itermeum.app.sync.isUserUnlocked
import com.itermeum.app.sync.runAsync
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Re-arms every alarm after reboot, clock/zone changes and app updates (section 10).
 * Before the first unlock (LOCKED_BOOT_COMPLETED) only the wake alarm is
 * re-armed, from device-protected storage; the rest waits for BOOT_COMPLETED.
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduler: ReminderScheduler
    @Inject lateinit var wake: WakeAlarmScheduler
    @Inject lateinit var flags: DayFlags

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                if (!context.isUserUnlocked()) wake.rearmFromDirectBoot()
            }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> {
                if (!context.isUserUnlocked()) { wake.rearmFromDirectBoot(); return }
                if (intent.action == Intent.ACTION_TIMEZONE_CHANGED) flags.recordTimeZoneChange(System.currentTimeMillis())
                runAsync("BootCompleted") { scheduler.rescheduleAll() }
            }
        }
    }
}
