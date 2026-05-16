package com.avangard.app.sync.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.avangard.app.MainActivity
import com.avangard.app.R
import com.avangard.app.core.domain.model.FocusSession
import com.avangard.app.navigation.NavRoute
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SimpleNotificationPresenter @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun ensureChannel() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Закрытие смены",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Сигнал к закрытию операционного дня"
                enableLights(false)
                enableVibration(true)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
        if (manager.getNotificationChannel(FOCUS_CHANNEL_ID) == null) {
            val focusChannel = NotificationChannel(
                FOCUS_CHANNEL_ID,
                "Фокус-сессия",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Хронометр активной фокус-сессии"
                enableLights(false)
                enableVibration(false)
                setShowBadge(false)
            }
            manager.createNotificationChannel(focusChannel)
        }
    }

    fun presentEveningClose() {
        ensureChannel()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_START_DESTINATION, NavRoute.EveningClose.route)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            EVENING_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_avangard)
            .setColor(android.graphics.Color.parseColor("#7BB661"))
            .setContentTitle(context.getString(R.string.notification_evening_title))
            .setContentText(context.getString(R.string.notification_evening_body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS denied on Android 13+. The in-app banner
            // in OperatorPulpit handles the fallback nudge; Sentry sees the
            // logged warning via its breadcrumb integration.
            Log.w(LOG_TAG, "evening close notification denied", e)
        }
    }

    /**
     * Minimal ongoing notification posted synchronously inside
     * `Service.onStartCommand` so the 5-second startForeground deadline is
     * met before the Room observer emits the real focus row. Gets replaced
     * by [buildFocusOngoing] on the first emission.
     */
    fun buildFocusPlaceholder(): Notification {
        ensureChannel()
        return NotificationCompat.Builder(context, FOCUS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_avangard)
            .setContentTitle(context.getString(R.string.focus_notification_placeholder_title))
            .setContentText(context.getString(R.string.focus_notification_body))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * Builds the ongoing focus-session notification consumed by
     * [com.avangard.app.sync.service.FlashForegroundService.startForeground].
     * Chronometer counts from `focus.startedAt`; the system handles the
     * tick rendering so the service doesn't need to push updates.
     */
    fun buildFocusOngoing(focus: FocusSession): Notification {
        ensureChannel()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            FOCUS_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, FOCUS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_avangard)
            .setContentTitle(context.getString(R.string.focus_notification_title, focus.habit.displayName))
            .setContentText(context.getString(R.string.focus_notification_body))
            .setOngoing(true)
            .setUsesChronometer(true)
            .setWhen(focus.startedAt)
            .setShowWhen(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "channel.evening_close"
        const val FOCUS_CHANNEL_ID = "channel.focus_session"
        const val FOCUS_NOTIFICATION_ID = 1004
        private const val NOTIFICATION_ID = 1003
        private const val EVENING_REQUEST_CODE = 5001
        private const val FOCUS_REQUEST_CODE = 5002
        private const val LOG_TAG = "NotifPresenter"
    }
}
