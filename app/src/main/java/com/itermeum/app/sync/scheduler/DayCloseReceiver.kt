package com.itermeum.app.sync.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.itermeum.app.sync.runAsync

/** 03:00: close yesterday for the streaks, refresh freezes, re-arm everything (section 6.1). */
@AndroidEntryPoint
class DayCloseReceiver : BroadcastReceiver() {

    @Inject lateinit var dayClose: DayCloseRunner
    @Inject lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        runAsync("DayClose") {
            try { dayClose.run() } finally { scheduler.rescheduleAll() }
        }
    }

    companion object { const val ACTION = "com.itermeum.app.DAY_CLOSE" }
}
