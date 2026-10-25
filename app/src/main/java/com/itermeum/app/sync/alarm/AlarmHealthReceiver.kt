package com.itermeum.app.sync.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.itermeum.app.R
import com.itermeum.app.core.data.MorningRepository
import com.itermeum.app.sync.notifications.NotificationChannels
import com.itermeum.app.sync.notifications.NotificationPresenter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Section 13: 23:15 daily — `nextAlarmClock` must be our wake alarm, else re-arm and warn. */
@AndroidEntryPoint
class AlarmHealthReceiver : BroadcastReceiver() {

    @Inject lateinit var wake: WakeAlarmScheduler
    @Inject lateinit var morning: MorningRepository
    @Inject lateinit var presenter: NotificationPresenter

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val expected = wake.scheduleNext()
                val actual = wake.nextAlarmClock()
                val healthy = actual != null && kotlin.math.abs(actual - expected) < 60_000L
                if (!healthy || !wake.canScheduleExact()) {
                    presenter.post(
                        NotificationPresenter.ID_HEALTH, NotificationChannels.SYSTEM_HEALTH,
                        context.getString(R.string.alarm_health_title), context.getString(R.string.alarm_health_text),
                        "itermeum://settings", NotificationCompat.PRIORITY_HIGH,
                    )
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "com.itermeum.app.alarm.HEALTH"
    }
}
