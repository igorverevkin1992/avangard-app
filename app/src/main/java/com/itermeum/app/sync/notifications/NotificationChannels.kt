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
            // D-34: the work card buzzes once at each block change, never rings.
            Spec(WORK_CARD, R.string.channel_work_card, NotificationManager.IMPORTANCE_DEFAULT, vibrate = true, silent = true),
            // Non-work reminders during working hours: shown, but without sound or pop-up.
            // D-50: the prayer/reading timer card — shown like the work card, one buzz when it counts.
            Spec(TIMER_CARD, R.string.channel_timer_card, NotificationManager.IMPORTANCE_DEFAULT, vibrate = true, silent = true),
            Spec(QUIET_REMINDERS, R.string.channel_quiet_reminders, NotificationManager.IMPORTANCE_LOW, vibrate = false),
        )
        specs.forEach { spec ->
            if (manager.getNotificationChannel(spec.id) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(spec.id, context.getString(spec.nameRes), spec.importance).apply {
                        enableLights(false)
                        enableVibration(spec.vibrate)
                        if (spec.vibrate && spec.silent) vibrationPattern = longArrayOf(0, 180, 120, 180)
                        if (spec.silent) setSound(null, null)
                        setShowBadge(false)
                    },
                )
            }
        }
    }

    private data class Spec(val id: String, val nameRes: Int, val importance: Int, val vibrate: Boolean, val silent: Boolean = false)

    companion object {
        const val ALARM_WAKE = "alarm_wake"
        const val NORM_REMINDERS = "norm_reminders"
        const val NORM_ESCALATION = "norm_escalation"
        const val TIMERS = "timers"
        const val TIMER_CARD = "timer_card"
        const val SYSTEM_HEALTH = "system_health"
        const val BACKUP = "backup"
        const val WORK_CARD = "work_card"
        const val QUIET_REMINDERS = "quiet_reminders"
    }
}
