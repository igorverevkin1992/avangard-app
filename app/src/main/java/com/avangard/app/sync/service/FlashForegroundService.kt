package com.avangard.app.sync.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import com.avangard.app.core.domain.repository.SessionRepository
import com.avangard.app.sync.notifications.SimpleNotificationPresenter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Ongoing-notification companion for the active focus session. Bound to the
 * lifetime of a single focus row: once `observeActiveFocus()` emits null, the
 * service tears itself down. The notification carries a chronometer so the
 * system renders the running elapsed time without per-second wake-ups.
 *
 * The row in `focus_session` is the source of truth; this service only
 * mirrors it. If Android kills the process, the row stays on disk and the
 * pulpit timer recovers correctly on next launch.
 */
@AndroidEntryPoint
class FlashForegroundService : Service() {

    @Inject lateinit var presenter: SimpleNotificationPresenter
    @Inject lateinit var sessions: SessionRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watch: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android requires startForeground inside 5s of startForegroundService.
        // Post a placeholder synchronously; the watch coroutine will replace it
        // via NotificationManagerCompat.notify on the same notification id once
        // Room emits the real focus row.
        startForeground(
            SimpleNotificationPresenter.FOCUS_NOTIFICATION_ID,
            presenter.buildFocusPlaceholder(),
        )

        watch?.cancel()
        watch = scope.launch {
            sessions.observeActiveFocus().collectLatest { focus ->
                if (focus == null) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@collectLatest
                }
                NotificationManagerCompat.from(this@FlashForegroundService).notify(
                    SimpleNotificationPresenter.FOCUS_NOTIFICATION_ID,
                    presenter.buildFocusOngoing(focus),
                )
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, FlashForegroundService::class.java)
            context.startForegroundService(intent)
        }
    }
}
