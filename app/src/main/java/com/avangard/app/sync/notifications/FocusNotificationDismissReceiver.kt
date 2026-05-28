package com.avangard.app.sync.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.avangard.app.core.domain.repository.SessionRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Triggers when the operator swipes the ongoing focus-session notification
 * away. Inspects the persisted active focus row and, if a session is still
 * running, immediately re-posts the same notification — the user experience
 * is "swiping it away doesn't work while focus is on", which is the
 * intended behaviour for a Pomodoro-style sticky timer.
 *
 * The foreground-service notification is technically uncancellable, but
 * MIUI and some Samsung skins still allow swipe-dismiss for low-importance
 * channels. This receiver papers over that platform divergence.
 */
@AndroidEntryPoint
class FocusNotificationDismissReceiver : BroadcastReceiver() {

    @Inject lateinit var presenter: SimpleNotificationPresenter
    @Inject lateinit var sessions: SessionRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DISMISSED) return
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val focus = sessions.findActiveFocus() ?: return@launch
                NotificationManagerCompat.from(context).notify(
                    SimpleNotificationPresenter.FOCUS_NOTIFICATION_ID,
                    presenter.buildFocusOngoing(focus),
                )
            } finally {
                result.finish()
            }
        }
    }

    companion object {
        const val ACTION_DISMISSED = "com.avangard.app.notification.FOCUS_DISMISSED"
    }
}
