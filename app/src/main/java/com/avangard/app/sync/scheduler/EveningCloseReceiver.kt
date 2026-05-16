package com.avangard.app.sync.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.avangard.app.sync.notifications.SimpleNotificationPresenter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@AndroidEntryPoint
class EveningCloseReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduler: EveningCloseScheduler
    @Inject lateinit var presenter: SimpleNotificationPresenter

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != EveningCloseScheduler.ACTION_FIRE) return
        // goAsync because scheduler.ensureScheduled is now suspend (reads
        // UserPreferences DataStore). 10s broadcast budget is plenty.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // Re-arm for tomorrow first so any presenter failure can't
                // break the schedule. Using scheduleNextAfterFire (not
                // ensureScheduled) breaks the loop where the fire-immediately
                // heuristic would re-trigger us in ~5s while the shift is
                // still unclosed.
                scheduler.scheduleNextAfterFire()
                presenter.presentEveningClose()
            } finally {
                pending.finish()
            }
        }
    }
}
