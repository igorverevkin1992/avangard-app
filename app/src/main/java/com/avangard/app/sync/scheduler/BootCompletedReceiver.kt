package com.avangard.app.sync.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduler: EveningCloseScheduler

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            -> scheduler.ensureScheduled()
        }
    }
}
