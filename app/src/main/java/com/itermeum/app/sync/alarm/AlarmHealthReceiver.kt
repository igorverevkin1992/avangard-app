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
import com.itermeum.app.sync.runAsync

/** Section 13: 23:15 daily — `nextAlarmClock` must be our wake alarm, else re-arm and warn. */
@AndroidEntryPoint
class AlarmHealthReceiver : BroadcastReceiver() {

    @Inject lateinit var wake: WakeAlarmScheduler
    @Inject lateinit var morning: MorningRepository
    @Inject lateinit var presenter: NotificationPresenter
    @Inject lateinit var budget: com.itermeum.app.sync.notifications.NotificationBudget

    override fun onReceive(context: Context, intent: Intent) {
        runAsync(TAG) {
            val expected = wake.scheduleNext()
            // Another app's earlier alarm hides ours from nextAlarmClock; then only exactness matters.
            val ours = wake.ourNextAlarmClock()
            val healthy = (ours == null || kotlin.math.abs(ours - expected) < 60_000L) && wake.canScheduleExact()
            if (!healthy) {
                presenter.post(
                    NotificationPresenter.ID_HEALTH, NotificationChannels.SYSTEM_HEALTH,
                    context.getString(R.string.alarm_health_title), context.getString(R.string.alarm_health_text),
                    "itermeum://settings", NotificationCompat.PRIORITY_HIGH,
                )
                budget.record(1)
            }
        }
    }

    companion object {
        const val ACTION = "com.itermeum.app.alarm.HEALTH"
        private const val TAG = "AlarmHealth"
    }
}
