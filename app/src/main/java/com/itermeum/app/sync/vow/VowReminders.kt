package com.itermeum.app.sync.vow

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.itermeum.app.R
import com.itermeum.app.core.common.Clock
import com.itermeum.app.core.data.VowRepository
import com.itermeum.app.sync.alarm.ExactAlarms
import com.itermeum.app.sync.notifications.NotificationChannels
import com.itermeum.app.sync.notifications.NotificationPresenter
import com.itermeum.app.sync.runAsync
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * D-67: an hour before a feast marked on the vow screen — the intention and the
 * ready answer. One alarm at a time: the nearest feast; it re-arms when it fires.
 */
@Singleton
class VowReminders @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vows: VowRepository,
    private val presenter: NotificationPresenter,
    private val channels: NotificationChannels,
    private val clock: Clock,
) {
    private val alarms = ExactAlarms(context)

    private fun pending(): PendingIntent =
        alarms.broadcast(ExactAlarms.RC_VOW_FEAST, Intent(context, VowReceiver::class.java).setAction(VowReceiver.ACTION_FEAST))

    /** Arms the reminder of the nearest feast still ahead (or clears it). */
    suspend fun schedule() {
        val now = clock.nowEpochMillis()
        val next = vows.upcomingFeasts().mapNotNull { d ->
            VowRepository.feastTime(d)?.let { t -> ZonedDateTime.of(d.logicalDate.atTime(t), clock.zone()).toInstant().toEpochMilli() }
        }.filter { it > now }.minOrNull()
        if (next == null) { alarms.cancel(pending()); return }
        // An hour before; a feast set less than an hour ahead is reminded right away.
        alarms.setExact(maxOf(next - REMIND_BEFORE_MS, now + 5_000L), pending())
    }

    /** Posts the reminder for the feast that is now within the hour, then arms the next one. */
    suspend fun fire() {
        val now = clock.nowEpochMillis()
        val vow = vows.vow()?.takeIf { it.active }
        val feast = vows.upcomingFeasts().mapNotNull { d -> VowRepository.feastTime(d)?.let { d.logicalDate.atTime(it) } }
            .map { it to ZonedDateTime.of(it, clock.zone()).toInstant().toEpochMilli() }
            .filter { (_, at) -> at > now && at - now <= REMIND_BEFORE_MS + 60_000L }
            .minByOrNull { it.second }
        if (vow != null && feast != null) {
            val time = feast.first.toLocalTime().toString()
            val text = buildList {
                add(context.getString(R.string.vow_feast_text, time))
                vow.intention?.let { add(context.getString(R.string.vow_feast_intention, it)) }
                vow.declinePhrase?.let { add(context.getString(R.string.vow_feast_phrase, it)) }
            }.joinToString("\n")
            channels.ensureCreated()
            // On the lock screen only «Iter Meum · напоминание»: the vow is nobody else's business.
            val public = NotificationCompat.Builder(context, NotificationChannels.NORM_REMINDERS)
                .setSmallIcon(R.drawable.ic_stat_itermeum).setContentTitle(context.getString(R.string.app_name))
                .setContentText(context.getString(R.string.vow_public_text)).build()
            val n = NotificationCompat.Builder(context, NotificationChannels.NORM_REMINDERS)
                .setSmallIcon(R.drawable.ic_stat_itermeum)
                .setContentTitle(context.getString(R.string.vow_title))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(public)
                .setAutoCancel(true)
                .setContentIntent(presenter.deepLinkIntent("itermeum://vow", NOTIFICATION_ID))
                .build()
            try { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n) } catch (e: SecurityException) { /* not granted */ }
        }
        schedule()
    }

    companion object {
        const val NOTIFICATION_ID = 3400
        private const val REMIND_BEFORE_MS = 60 * 60_000L
    }
}

@AndroidEntryPoint
class VowReceiver : BroadcastReceiver() {
    @Inject lateinit var reminders: VowReminders

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FEAST) return
        runAsync("VowFeast") { reminders.fire() }
    }

    companion object { const val ACTION_FEAST = "com.itermeum.app.vow.FEAST" }
}
