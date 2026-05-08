package com.avangard.app.sync.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.avangard.app.sync.notifications.ReportNotificationPresenter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ReportAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var presenter: ReportNotificationPresenter
    @Inject lateinit var dispatcher: AlarmDispatcher

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmDispatcher.ACTION_FIRE) return
        val slotId = intent.getIntExtra(AlarmDispatcher.EXTRA_SLOT_ID, -1)
        val slot = AlarmSlot.fromId(slotId) ?: return
        presenter.present(slot)
        // One-shot exact alarms must be re-armed for the next occurrence.
        dispatcher.schedule(slot)
    }
}
