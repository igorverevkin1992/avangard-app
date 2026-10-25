package com.itermeum.app.sync.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 03:00: close yesterday for the streaks, refresh freezes, re-arm everything (section 6.1). */
@AndroidEntryPoint
class DayCloseReceiver : BroadcastReceiver() {

    @Inject lateinit var dayClose: DayCloseRunner
    @Inject lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                dayClose.run()
                scheduler.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }

    companion object { const val ACTION = "com.itermeum.app.DAY_CLOSE" }
}
