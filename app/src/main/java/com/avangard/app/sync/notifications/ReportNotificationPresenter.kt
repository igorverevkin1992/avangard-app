package com.avangard.app.sync.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.avangard.app.MainActivity
import com.avangard.app.R
import com.avangard.app.sync.scheduler.AlarmSlot
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReportNotificationPresenter @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun ensureChannel() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Регламент",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Срочные регламентные сигналы"
            enableLights(false)
            enableVibration(true)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun present(slot: AlarmSlot) {
        ensureChannel()
        val titleRes = when (slot) {
            AlarmSlot.MorningInitialization -> R.string.notification_artifact_capture
            AlarmSlot.MidDayCheckpoint -> R.string.notification_deadline_breach
            AlarmSlot.EveningReport -> R.string.notification_evening_due
        }
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            slot.id,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(titleRes))
            .setContentText(context.getString(R.string.app_name))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setContentIntent(pendingIntent)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(slot.id, notification)
        }
    }

    companion object {
        const val CHANNEL_ID = "channel.reports"
    }
}
