package com.itermeum.app.sync.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.itermeum.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Section 10 channels. Created idempotently on every app start. */
@Singleton
class NotificationChannels @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun ensureCreated() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val specs = listOf(
            Spec(ALARM_WAKE, R.string.channel_alarm_wake, NotificationManager.IMPORTANCE_HIGH, vibrate = true),
            Spec(NORM_REMINDERS, R.string.channel_norm_reminders, NotificationManager.IMPORTANCE_DEFAULT, vibrate = false),
            Spec(NORM_ESCALATION, R.string.channel_norm_escalation, NotificationManager.IMPORTANCE_HIGH, vibrate = true),
            Spec(TIMERS, R.string.channel_timers, NotificationManager.IMPORTANCE_LOW, vibrate = false),
            Spec(SYSTEM_HEALTH, R.string.channel_system_health, NotificationManager.IMPORTANCE_HIGH, vibrate = true),
            Spec(BACKUP, R.string.channel_backup, NotificationManager.IMPORTANCE_MIN, vibrate = false),
        )
        specs.forEach { spec ->
            if (manager.getNotificationChannel(spec.id) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(spec.id, context.getString(spec.nameRes), spec.importance).apply {
                        enableLights(false)
                        enableVibration(spec.vibrate)
                        setShowBadge(false)
                    },
                )
            }
        }
    }

    private data class Spec(val id: String, val nameRes: Int, val importance: Int, val vibrate: Boolean)

    companion object {
        const val ALARM_WAKE = "alarm_wake"
        const val NORM_REMINDERS = "norm_reminders"
        const val NORM_ESCALATION = "norm_escalation"
        const val TIMERS = "timers"
        const val SYSTEM_HEALTH = "system_health"
        const val BACKUP = "backup"
    }
}
