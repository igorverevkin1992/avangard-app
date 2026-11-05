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
 * the lock screen like the work card: running time, «засчитается в HH:mm», «Закончить»;
 * one short buzz when the session starts to count. D-65: posted once, updated only at
 * that moment — every re-post wakes the lock screen on HyperOS.
 */
@AndroidEntryPoint
class SessionTimerService : Service() {

    @Inject lateinit var timers: TimerController
    @Inject lateinit var finisher: TimerFinisher
    @Inject lateinit var presenter: NotificationPresenter
    @Inject lateinit var channels: NotificationChannels
    @Inject lateinit var clock: Clock
    @Inject lateinit var workCard: com.itermeum.app.sync.work.WorkCard

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ticker: Job? = null
    private var watcher: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_FINISH || intent?.action == ACTION_DONE) {
            // The session lives in memory: after a process restart there is nothing to finish.
            if (timers.current() == null) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
            else scope.launch {
                // The session flow turns null → the card goes. A finished focus may change the work card's step.
                val s = finisher.finish(complete = intent?.action == ACTION_DONE)
                if (s?.kind == TimerController.Kind.FOCUS) runCatching { workCard.refresh() }
            }
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
        // D-65: the card is never re-posted to tick — HyperOS lights the lock screen on every update.
        // The system chronometer counts by itself; the one update is the moment the session starts
        // to count (with its buzz). Focus has no such moment: posted once, left alone.
        val countsAt = session.startedAt + finisher.countsAfterMs(session)
        if (session.kind != TimerController.Kind.FOCUS && clock.nowEpochMillis() < countsAt) {
            ticker = scope.launch {
                delay(countsAt - clock.nowEpochMillis())
                val s = timers.current()?.takeIf { it.startedAt == session.startedAt && it.kind == session.kind } ?: return@launch
                notify(build(s, alert = true))
            }
        }
        watcher?.cancel()
        watcher = scope.launch {
            timers.session.collectLatest { if (it == null) { ticker?.cancel(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() } }
        }
        return START_NOT_STICKY
    }

    private fun build(s: TimerController.Session, alert: Boolean): Notification {
        if (s.kind == TimerController.Kind.FOCUS) return buildFocus(s)
        val mental = s.kind == TimerController.Kind.MENTAL_PRAYER
        // A fixed time, not a countdown: the text stays true without updates.
        val countsAt = s.startedAt + finisher.countsAfterMs(s)
        val text = if (clock.nowEpochMillis() < countsAt) {
            getString(R.string.timer_counts_at, java.time.Instant.ofEpochMilli(countsAt).atZone(clock.zone()).toLocalTime().format(HH_MM))
        } else getString(R.string.timer_counted)
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
            .setOngoing(true)
            .setOnlyAlertOnce(!alert)
            .setSilent(!alert)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(presenter.deepLinkIntent(if (mental) "itermeum://mental" else "itermeum://reading", NOTIFICATION_ID))
            .addAction(0, getString(R.string.timer_finish), finish)
            .build()
    }

    /** D-64: focus on one item — the clock shows all the time spent on it; «Пауза» keeps it open, «Готово» closes it. */
    private fun buildFocus(s: TimerController.Session): Notification {
        fun svc(code: Int, action: String) = PendingIntent.getService(
            this, code, Intent(this, SessionTimerService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, NotificationChannels.TIMER_CARD)
            .setSmallIcon(R.drawable.ic_stat_itermeum)
            .setContentTitle(getString(R.string.focus_card_title, s.label.orEmpty()))
            .setContentText(getString(R.string.focus_card_text))
            .setUsesChronometer(true)
            .setWhen(s.startedAt)
            .setShowWhen(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(presenter.deepLinkIntent(s.refId?.let { "itermeum://item/$it" } ?: "itermeum://work", NOTIFICATION_ID))
            .addAction(0, getString(R.string.focus_pause), svc(1, ACTION_FINISH))
            .addAction(0, getString(R.string.focus_done), svc(2, ACTION_DONE))
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
        private const val ACTION_DONE = "com.itermeum.app.timer.DONE"
        private val HH_MM = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        fun start(context: Context) = context.startForegroundService(Intent(context, SessionTimerService::class.java))
    }
}
