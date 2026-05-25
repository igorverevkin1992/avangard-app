package com.avangard.app.sync.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.avangard.app.core.domain.repository.ChronometerRepository
import com.avangard.app.sync.notifications.SimpleNotificationPresenter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class IgnitionReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduler: IgnitionScheduler
    @Inject lateinit var presenter: SimpleNotificationPresenter
    @Inject lateinit var chronometer: ChronometerRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != IgnitionScheduler.ACTION_FIRE) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                scheduler.scheduleNextAfterFire()
                val progress = chronometer.observeProgress().first()
                presenter.presentIgnition(progress)
            } finally {
                pending.finish()
            }
        }
    }
}
