package com.avangard.app.sync.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.avangard.app.core.common.Clock
import com.avangard.app.core.common.toStartOfDayEpoch
import com.avangard.app.core.domain.model.CoreStatus
import com.avangard.app.core.domain.repository.SessionRepository
import com.avangard.app.sync.notifications.SimpleNotificationPresenter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MiddayCheckReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduler: MiddayCheckScheduler
    @Inject lateinit var presenter: SimpleNotificationPresenter
    @Inject lateinit var sessions: SessionRepository
    @Inject lateinit var clock: Clock

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != MiddayCheckScheduler.ACTION_FIRE) return
        val result = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val today = clock.today().toStartOfDayEpoch(clock.zone())
                val session = sessions.findForDate(today)
                if (session?.coreStatus !is CoreStatus.Approved) {
                    presenter.presentMiddayCheck()
                }
                scheduler.scheduleNextAfterFire()
            } finally {
                result.finish()
            }
        }
    }
}
