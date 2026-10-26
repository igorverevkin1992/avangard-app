package com.itermeum.app.sync.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.itermeum.app.MainActivity
import com.itermeum.app.R
import com.itermeum.app.sync.alarm.ExactAlarms
import com.itermeum.app.sync.alarm.MorningEventReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Posts section 10 notifications. Texts come from the content pack or strings. */
@Singleton
class NotificationPresenter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val channels: NotificationChannels,
) {
    fun post(
        id: Int, channel: String, title: String, text: String, deepLink: String?,
        priority: Int = NotificationCompat.PRIORITY_DEFAULT, ongoing: Boolean = false,
        actions: List<NotificationCompat.Action> = emptyList(),
    ) {
        channels.ensureCreated()
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_itermeum)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(priority)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .setContentIntent(deepLinkIntent(deepLink, id))
        actions.forEach { builder.addAction(it) }
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (e: SecurityException) {
            Log.w(TAG, "notification denied", e)
        }
    }

    fun cancel(id: Int) = NotificationManagerCompat.from(context).cancel(id)

    fun deepLinkIntent(deepLink: String?, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (deepLink != null) { action = Intent.ACTION_VIEW; data = Uri.parse(deepLink) }
        }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Section 5.1: «Карантин сохранён? Да/Нет» at +35 min. */
    fun quarantineQuestion(date: LocalDate) {
        val alarms = ExactAlarms(context)
        val yes = NotificationCompat.Action(0, context.getString(R.string.yes),
            alarms.broadcast(ExactAlarms.RC_QUARANTINE_YES, MorningEventReceiver.intent(context, MorningEventReceiver.ACTION_QUARANTINE_YES, date)))
        val no = NotificationCompat.Action(0, context.getString(R.string.no),
            alarms.broadcast(ExactAlarms.RC_QUARANTINE_NO, MorningEventReceiver.intent(context, MorningEventReceiver.ACTION_QUARANTINE_NO, date)))
        post(ID_QUARANTINE, NotificationChannels.NORM_REMINDERS, context.getString(R.string.quarantine_question_title),
            context.getString(R.string.quarantine_question_text), "itermeum://today", actions = listOf(yes, no))
    }

    companion object {
        private const val TAG = "Notifications"
        const val ID_QUARANTINE = 3101
        const val ID_HEALTH = 3102
        const val ID_NORM_BASE = 3200
        const val ID_TEST_ALARM = 3103
    }
}
