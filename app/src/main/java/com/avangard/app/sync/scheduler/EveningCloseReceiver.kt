package com.avangard.app.sync.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.avangard.app.sync.notifications.SimpleNotificationPresenter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class EveningCloseReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduler: EveningCloseScheduler
    @Inject lateinit var presenter: SimpleNotificationPresenter

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != EveningCloseScheduler.ACTION_FIRE) return
        // Re-arm first so any presenter failure can't break the schedule.
        scheduler.ensureScheduled()
        presenter.presentEveningClose()
    }
}
