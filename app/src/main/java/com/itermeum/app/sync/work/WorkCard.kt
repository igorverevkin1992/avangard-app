package com.itermeum.app.sync.work

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.itermeum.app.MainActivity
import com.itermeum.app.R
import com.itermeum.app.core.common.Clock
import com.itermeum.app.core.content.NormCatalog
import com.itermeum.app.core.data.NormRepository
import com.itermeum.app.core.data.WorkRepository
import com.itermeum.app.core.database.entity.OutreachOutcome
import com.itermeum.app.core.database.entity.TaskStatus
import com.itermeum.app.core.database.entity.TaskType
import com.itermeum.app.core.engine.DayScheduleEngine
import com.itermeum.app.sync.alarm.ExactAlarms
import com.itermeum.app.sync.notifications.NotificationChannels
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * D-34: one ongoing «рабочая карточка» in the shade and on the lock screen
 * during working hours. It always shows the one current step of the day —
 * plan → heroes until the cutoff → tasks → last stone — with one-tap actions,
 * so the order is visible without opening the app. It vibrates once when the
 * step changes and is silent otherwise.
 */
@Singleton
class WorkCard @Inject constructor(
    @ApplicationContext private val context: Context,
    private val work: WorkRepository,
    private val norms: NormRepository,
    private val channels: NotificationChannels,
    private val clock: Clock,
) {
    enum class Step { PLAN, HEROES, TASKS, LAST_STONE }

    data class State(
        val step: Step, val title: String, val text: String, val deepLink: String,
        val progress: Pair<Int, Int>? = null, val openTaskId: Long? = null,
        /** The current task is the hero calls: show «+1 герой» / «Договорился» too. */
        val heroTask: Boolean = false,
    )

    private val prefs = context.getSharedPreferences("work_card", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val alarms = ExactAlarms(context)

    /** The current step, or null outside working hours / on non-working days / once the last stone is laid. */
    suspend fun state(): State? {
        val date = clock.logicalToday()
        val now = clock.now()
        val input = norms.inputFor(date)
        val items = DayScheduleEngine.schedule(input).map { it.normId }.toSet()
        if (input.sick || NormCatalog.DAY_PLAN !in items) return null
        val p = input.profile
        val start = date.atTime(p.workStart)
        val end = date.atTime(p.workEnd).plusHours(1)
        if (now.isBefore(start) || !now.isBefore(end)) return null
        val done = norms.observeDoneIds(date).first()
        if (NormCatalog.LAST_STONE in done) return null
        val tasks = work.tasksFor(date)
        val cutoff = date.atTime(DayScheduleEngine.frogCutoff(p))
        val target = work.targetFor(date)
        val attempts = work.attemptsBeforeCutoff(date)
        val open = tasks.filter { it.status == TaskStatus.OPEN }.sortedBy { it.slot }
        val outreach = work.observeOutreach(date).first()
        fun count(o: OutreachOutcome) = outreach.count { it.outcome == o }
        val tally = s(R.string.work_card_tally, count(OutreachOutcome.AGREEMENT), count(OutreachOutcome.CALLBACK), count(OutreachOutcome.REJECTION))
        return when {
            tasks.isEmpty() -> State(Step.PLAN, s(R.string.work_card_plan_title), s(R.string.work_card_plan_text), "itermeum://work")
            // Only when the optional hero-calls norm is on (D-35).
            NormCatalog.FROG in items && NormCatalog.FROG !in done && now.isBefore(cutoff) -> State(
                Step.HEROES, s(R.string.work_card_heroes_title, attempts, target),
                s(R.string.work_card_heroes_text, cutoff.toLocalTime().toString()) + "\n" + tally, "itermeum://outreach", attempts to target,
            )
            open.isEmpty() || !now.isBefore(date.atTime(LAST_STONE_AT)) -> State(Step.LAST_STONE, s(R.string.work_card_stone_title), s(R.string.work_card_stone_text), "itermeum://laststone")
            open.first().type == TaskType.OUTREACH -> State(
                Step.TASKS, s(R.string.work_card_task_title, open.first().slot, tasks.size),
                open.first().title + "\n" + tally + if (outreach.size >= target) "\n" + s(R.string.work_card_hero_task_close) else "",
                "itermeum://work", outreach.size to target, open.first().id, heroTask = true,
            )
            else -> State(
                Step.TASKS, s(R.string.work_card_task_title, open.first().slot, tasks.size),
                open.first().title, "itermeum://work", openTaskId = open.first().id,
            )
        }
    }

    /** Re-reads the day and posts, updates or removes the card. Safe to call often. */
    suspend fun refresh() = mutex.withLock {
        val st = runCatching { state() }.getOrNull()
        if (st == null) {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            prefs.edit().remove(KEY_STEP).apply()
            return@withLock
        }
        val changed = prefs.getString(KEY_STEP, null) != st.step.name
        prefs.edit().putString(KEY_STEP, st.step.name).apply()
        channels.ensureCreated()
        val b = NotificationCompat.Builder(context, NotificationChannels.WORK_CARD)
            .setSmallIcon(R.drawable.ic_stat_itermeum)
            .setContentTitle(st.title)
            .setContentText(st.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(st.text))
            .setOngoing(true)
            .setOnlyAlertOnce(!changed)
            .setSilent(!changed)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open(st.deepLink))
        st.progress?.let { (done, target) -> b.setProgress(target.coerceAtLeast(1), done.coerceAtMost(target), false) }
        when (st.step) {
            Step.PLAN -> b.addAction(0, s(R.string.work_card_open_plan), open("itermeum://work"))
            Step.HEROES -> heroActions(b)
            // Android shows at most three buttons: the hero task closes from «Труд» (tap on the card).
            Step.TASKS -> if (st.heroTask) heroActions(b) else b.addAction(0, s(R.string.work_card_task_done), action(WorkCardReceiver.ACTION_TASK_DONE))
            Step.LAST_STONE -> b.addAction(0, s(R.string.work_card_open_stone), open("itermeum://laststone"))
        }
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, b.build())
        } catch (e: SecurityException) {
            // Notifications not granted: nothing to show.
        }
    }

    /** Block changes of today: start of work, the cutoff, 19:30 and one hour after work (removal). */
    fun scheduleTicks(workStart: LocalTime, cutoff: LocalTime, workEnd: LocalTime) {
        val date = clock.logicalToday()
        val now = clock.now()
        listOf(workStart, cutoff, LAST_STONE_AT, workEnd.plusHours(1)).forEachIndexed { i, t ->
            val at: LocalDateTime = date.atTime(t)
            val pi = alarms.broadcast(ExactAlarms.RC_WORK_CARD_BASE + i, Intent(context, WorkCardReceiver::class.java).setAction("${WorkCardReceiver.ACTION_TICK}.$i"))
            if (at.isAfter(now)) alarms.setExact(ZonedDateTime.of(at, clock.zone()).toInstant().toEpochMilli(), pi) else alarms.cancel(pi)
        }
    }

    private fun s(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun open(deepLink: String): PendingIntent = PendingIntent.getActivity(
        context, deepLink.hashCode(),
        Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).setClass(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Each tap is one hero attempt with its outcome (D-36). */
    private fun heroActions(b: NotificationCompat.Builder) {
        b.addAction(0, s(R.string.work_card_agreement), action(WorkCardReceiver.ACTION_AGREEMENT))
        b.addAction(0, s(R.string.work_card_callback), action(WorkCardReceiver.ACTION_CALLBACK))
        b.addAction(0, s(R.string.work_card_rejection), action(WorkCardReceiver.ACTION_REJECTION))
    }

    private fun action(action: String): PendingIntent = PendingIntent.getBroadcast(
        context, action.hashCode(), Intent(context, WorkCardReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val NOTIFICATION_ID = 3300
        val LAST_STONE_AT: LocalTime = LocalTime.of(19, 30)
        private const val KEY_STEP = "step"
    }
}
