package com.avangard.app.sync.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Intentionally minimal. The receiver exists for one reason: re-arm the
 * single 21:00 evening-close alarm after device reboot, when the system
 * forgets all previously scheduled AlarmManager entries. Any other startup
 * logic belongs in [com.avangard.app.AvangardApplication.onCreate].
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduler: EveningCloseScheduler

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            -> scheduler.ensureScheduled()
        }
    }
}
