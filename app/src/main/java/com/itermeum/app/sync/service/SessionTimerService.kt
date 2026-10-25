package com.itermeum.app.sync.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.itermeum.app.R
import com.itermeum.app.core.data.TimerController
import com.itermeum.app.sync.notifications.NotificationChannels
import com.itermeum.app.sync.notifications.NotificationPresenter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Section 5.6/5.9: ongoing chronometer notification for an active prayer/reading timer. */
@AndroidEntryPoint
class SessionTimerService : Service() {

    @Inject lateinit var timers: TimerController
    @Inject lateinit var presenter: NotificationPresenter

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val session = timers.current()
        if (session == null) { stopSelf(); return START_NOT_STICKY }
        val title = if (session.kind == TimerController.Kind.MENTAL_PRAYER) getString(R.string.timer_mental_prayer) else getString(R.string.timer_reading)
        val deepLink = if (session.kind == TimerController.Kind.MENTAL_PRAYER) "itermeum://mental" else "itermeum://reading"
        val notification = NotificationCompat.Builder(this, NotificationChannels.TIMERS)
            .setSmallIcon(R.drawable.ic_stat_itermeum)
            .setContentTitle(title)
            .setUsesChronometer(true)
            .setWhen(session.startedAt)
            .setShowWhen(true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(presenter.deepLinkIntent(deepLink, NOTIFICATION_ID))
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        scope.launch {
            timers.session.collectLatest { if (it == null) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() } }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    companion object {
        const val NOTIFICATION_ID = 3002
        fun start(context: Context) = context.startForegroundService(Intent(context, SessionTimerService::class.java))
    }
}
