package com.avangard.app.sync.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Intentionally minimal. The receiver exists for one reason: re-arm the
 * single 21:00 evening-close alarm after device reboot, when the system
 * forgets all previously scheduled AlarmManager entries. Any other startup
 * logic belongs in [com.avangard.app.AvangardApplication.onCreate].
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduler: EveningCloseScheduler
    @Inject lateinit var ignitionScheduler: IgnitionScheduler

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            -> {
                val pending = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        scheduler.ensureScheduled()
                        ignitionScheduler.ensureScheduled()
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
