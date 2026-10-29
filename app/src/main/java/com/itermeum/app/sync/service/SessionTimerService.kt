package com.itermeum.app.sync.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.itermeum.app.R
import com.itermeum.app.core.common.Clock
import com.itermeum.app.core.data.TimerController
import com.itermeum.app.core.data.TimerFinisher
import com.itermeum.app.sync.notifications.NotificationChannels
import com.itermeum.app.sync.notifications.NotificationPresenter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Section 5.6/5.9 + D-50: the prayer/reading timer card — in the shade and on
 * the lock screen like the work card: running time, a bar to the end, «засчитается
 * через N мин», «Закончить»; one short buzz when the session starts to count.
 */
@AndroidEntryPoint
class SessionTimerService : Service() {

    @Inject lateinit var timers: TimerController
    @Inject lateinit var finisher: TimerFinisher
    @Inject lateinit var presenter: NotificationPresenter
    @Inject lateinit var channels: NotificationChannels
    @Inject lateinit var clock: Clock

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ticker: Job? = null
    private var watcher: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_FINISH) {
            // The session lives in memory: after a process restart there is nothing to finish.
            if (timers.current() == null) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
            else scope.launch { finisher.finish() } // the session flow turns null → the card goes
            return START_NOT_STICKY
        }
        val session = timers.current()
        if (session == null) { stopSelf(); return START_NOT_STICKY }
        channels.ensureCreated()
        val first = build(session, alert = false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, first)
        }
        ticker?.cancel()
        ticker = scope.launch {
            var counted = clock.nowEpochMillis() - session.startedAt >= finisher.countsAfterMs(session)
            while (true) {
                delay(TICK_MS)
                val s = timers.current() ?: break
                val nowCounts = clock.nowEpochMillis() - s.startedAt >= finisher.countsAfterMs(s)
                // Buzz once, at the moment the session starts to count.
                notify(build(s, alert = nowCounts && !counted))
                counted = nowCounts
            }
        }
        watcher?.cancel()
        watcher = scope.launch {
            timers.session.collectLatest { if (it == null) { ticker?.cancel(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() } }
        }
        return START_NOT_STICKY
    }

    private fun build(s: TimerController.Session, alert: Boolean): Notification {
        val mental = s.kind == TimerController.Kind.MENTAL_PRAYER
        val elapsed = clock.nowEpochMillis() - s.startedAt
        val left = finisher.countsAfterMs(s) - elapsed
        val text = if (left > 0) getString(R.string.timer_counts_in, ((left + 59_999) / 60_000).toInt()) else getString(R.string.timer_counted)
        val finish = PendingIntent.getService(
            this, 1, Intent(this, SessionTimerService::class.java).setAction(ACTION_FINISH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, NotificationChannels.TIMER_CARD)
            .setSmallIcon(R.drawable.ic_stat_itermeum)
            .setContentTitle(getString(if (mental) R.string.timer_mental_prayer else R.string.timer_reading))
            .setContentText(text)
            .setUsesChronometer(true)
            .setWhen(s.startedAt)
            .setShowWhen(true)
            .setProgress(s.durationMs.toInt(), elapsed.coerceIn(0, s.durationMs).toInt(), false)
            .setOngoing(true)
            .setOnlyAlertOnce(!alert)
            .setSilent(!alert)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(presenter.deepLinkIntent(if (mental) "itermeum://mental" else "itermeum://reading", NOTIFICATION_ID))
            .addAction(0, getString(R.string.timer_finish), finish)
            .build()
    }

    private fun notify(n: Notification) {
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, n)
        } catch (e: SecurityException) {
            // Notifications not granted: the screen still shows the timer.
        }
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    companion object {
        const val NOTIFICATION_ID = 3002
        private const val ACTION_FINISH = "com.itermeum.app.timer.FINISH"
        private const val TICK_MS = 15_000L
        fun start(context: Context) = context.startForegroundService(Intent(context, SessionTimerService::class.java))
    }
}
