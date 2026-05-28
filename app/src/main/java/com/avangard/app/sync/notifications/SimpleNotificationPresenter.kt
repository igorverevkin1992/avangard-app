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
import com.avangard.app.core.domain.model.ChronometerProgress
import com.avangard.app.core.domain.model.DayClass
import com.avangard.app.core.domain.model.FocusSession
import com.avangard.app.core.domain.model.Habit
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
        if (manager.getNotificationChannel(IGNITION_CHANNEL_ID) == null) {
            val ignitionChannel = NotificationChannel(
                IGNITION_CHANNEL_ID,
                "Хронометр · утренний сигнал",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Утренний сигнал хронометра"
                enableLights(false)
                enableVibration(true)
                setShowBadge(false)
            }
            manager.createNotificationChannel(ignitionChannel)
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
        if (manager.getNotificationChannel(STATUS_CHANNEL_ID) == null) {
            val statusChannel = NotificationChannel(
                STATUS_CHANNEL_ID,
                "Фиксация статуса",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Уведомление о фиксации СТАНДАРТ или МИНИМУМ"
                enableLights(false)
                enableVibration(false)
                setShowBadge(false)
            }
            manager.createNotificationChannel(statusChannel)
        }
        if (manager.getNotificationChannel(MIDDAY_CHANNEL_ID) == null) {
            val midday = NotificationChannel(
                MIDDAY_CHANNEL_ID,
                "Полуденная проверка",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Напоминание в середине дня, если Ядро ещё не закрыто"
                enableLights(false)
                enableVibration(false)
                setShowBadge(false)
            }
            manager.createNotificationChannel(midday)
        }
    }

    fun presentMiddayCheck() {
        ensureChannel()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_START_DESTINATION, NavRoute.OperatorPulpit.route)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            MIDDAY_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, MIDDAY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_avangard)
            .setColor(android.graphics.Color.parseColor("#C8A800"))
            .setContentTitle(context.getString(R.string.notification_midday_title))
            .setContentText(context.getString(R.string.notification_midday_body))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(MIDDAY_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.w(LOG_TAG, "midday notification denied", e)
        }
    }

    /**
     * Posts a one-shot, low-importance confirmation that an Infra or Core
     * habit just landed in Standard / Mvd. Channel is muted (no vibration, no
     * badge) — the signal goes in the shade for cases when the operator
     * acted from the pulpit but the phone is in their pocket. Auto-cancels
     * on tap.
     */
    fun presentStatusFix(habit: Habit, mode: String) {
        ensureChannel()
        val title = context.getString(R.string.notification_status_fix_title, habit.code, habit.displayName)
        val body = context.getString(R.string.notification_status_fix_body, mode)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_START_DESTINATION, NavRoute.OperatorPulpit.route)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            STATUS_REQUEST_CODE_BASE + habit.code.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, STATUS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_avangard)
            .setColor(android.graphics.Color.parseColor("#7BB661"))
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setTimeoutAfter(STATUS_NOTIFICATION_TIMEOUT_MS)
            .setContentIntent(pendingIntent)
            .build()
        try {
            // Per-habit id keeps the most recent status replacement, not stacks.
            NotificationManagerCompat.from(context).notify(
                STATUS_NOTIFICATION_ID_BASE + habit.code.hashCode(),
                notification,
            )
        } catch (e: SecurityException) {
            Log.w(LOG_TAG, "status notification denied", e)
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

    fun presentIgnition(progress: ChronometerProgress) {
        ensureChannel()
        if (!progress.configured) return
        val dayNumber = progress.daysLived + 1
        val bodyRes = if (progress.yesterdayClass == DayClass.Extracted ||
            progress.yesterdayClass == DayClass.Partial
        ) {
            R.string.notification_ignition_body_extracted
        } else {
            R.string.notification_ignition_body_loss
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_START_DESTINATION, NavRoute.Chronometer.route)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            IGNITION_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, IGNITION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_avangard)
            .setColor(android.graphics.Color.parseColor("#C0C5CA"))
            .setContentTitle(context.getString(R.string.notification_ignition_title, dayNumber, progress.daysRemaining))
            .setContentText(context.getString(bodyRes))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(IGNITION_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.w(LOG_TAG, "ignition notification denied", e)
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
        const val IGNITION_CHANNEL_ID = "channel.ignition"
        const val STATUS_CHANNEL_ID = "channel.status"
        const val MIDDAY_CHANNEL_ID = "channel.midday"
        const val FOCUS_NOTIFICATION_ID = 1004
        private const val NOTIFICATION_ID = 1003
        private const val IGNITION_NOTIFICATION_ID = 1005
        private const val MIDDAY_NOTIFICATION_ID = 1006
        private const val STATUS_NOTIFICATION_ID_BASE = 1100
        private const val EVENING_REQUEST_CODE = 5001
        private const val FOCUS_REQUEST_CODE = 5002
        private const val IGNITION_REQUEST_CODE = 5003
        private const val MIDDAY_REQUEST_CODE = 5004
        private const val STATUS_REQUEST_CODE_BASE = 5100
        // 90 seconds is long enough for the operator to glance at the shade
        // after putting the phone down, short enough to keep notifications
        // from piling up across the day.
        private const val STATUS_NOTIFICATION_TIMEOUT_MS = 90_000L
        private const val LOG_TAG = "NotifPresenter"
    }
}
