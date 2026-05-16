package com.avangard.app.sync.notifications

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
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
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

    companion object {
        const val CHANNEL_ID = "channel.evening_close"
        private const val NOTIFICATION_ID = 1003
        private const val EVENING_REQUEST_CODE = 5001
        private const val LOG_TAG = "NotifPresenter"
    }
}
