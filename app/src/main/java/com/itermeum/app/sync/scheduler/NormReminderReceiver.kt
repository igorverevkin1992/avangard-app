package com.itermeum.app.sync.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.itermeum.app.core.common.Clock
import com.itermeum.app.core.content.ContentAssets
import com.itermeum.app.core.content.NormCatalog
import com.itermeum.app.core.data.NormRepository
import com.itermeum.app.core.data.ProfileRepository
import com.itermeum.app.core.data.StreakRepository
import com.itermeum.app.core.data.WorkRepository
import com.itermeum.app.core.database.dao.OutreachDao
import com.itermeum.app.core.engine.DayScheduleEngine
import com.itermeum.app.core.engine.EscalationEngine
import com.itermeum.app.core.engine.StreakEngine
import com.itermeum.app.core.liturgy.LiturgicalCalendar
import com.itermeum.app.sync.notifications.NotificationBudget
import com.itermeum.app.sync.notifications.NotificationChannels
import com.itermeum.app.sync.notifications.NotificationPresenter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.itermeum.app.sync.runAsync
import kotlinx.coroutines.flow.first

/**
 * Fires at a planned reminder time. Everything is re-checked at fire time:
 * the norm may be done, the moment may be inside a Mass/catechesis quiet
 * window or quiet hours, and the daily budget may be spent (section 6.5).
 */
@AndroidEntryPoint
class NormReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var norms: NormRepository
    @Inject lateinit var profiles: ProfileRepository
    @Inject lateinit var streaks: StreakRepository
    @Inject lateinit var outreachDao: OutreachDao
    @Inject lateinit var work: WorkRepository
    @Inject lateinit var assets: ContentAssets
    @Inject lateinit var budget: NotificationBudget
    @Inject lateinit var presenter: NotificationPresenter
    @Inject lateinit var calendar: LiturgicalCalendar
    @Inject lateinit var clock: Clock

    override fun onReceive(context: Context, intent: Intent) {
        val normId = intent.getStringExtra(EXTRA_NORM) ?: return
        val level = intent.getIntExtra(EXTRA_LEVEL, 1)
        runAsync("NormReminder") { fire(normId, level) }
    }

    private suspend fun fire(normId: String, level: Int) {
        val date = clock.logicalToday()
        if (budget.alreadySent(normId, level)) return
        val input = norms.inputFor(date)
        val schedule = DayScheduleEngine.schedule(input)
        val item = schedule.firstOrNull { it.normId == normId }
        val doneIds = norms.observeDoneIds(date).first()
        if (normId != THREAT && (item == null || normId in doneIds)) return
        val quiet = DayScheduleEngine.quietWindows(input.events)
        val now = clock.now()
        val muted = DayScheduleEngine.isQuiet(now, quiet)
        val priority = when {
            normId == THREAT -> EscalationEngine.Priority.CORE
            item?.isCore == true -> EscalationEngine.Priority.CORE
            else -> EscalationEngine.Priority.OTHER
        }
        val candidate = EscalationEngine.Candidate(normId, level, now, priority, muted)
        if (!EscalationEngine.allow(candidate, budget.current())) return

        val profile = profiles.get()
        val text = when (normId) {
            THREAT -> {
                val facts = streaks.facts(date)
                if (StreakEngine.fidelityMet(facts)) return
                val threshold = if (facts.travel) StreakEngine.THRESHOLD_TRAVEL else StreakEngine.THRESHOLD_HOME
                val needed = kotlin.math.ceil(facts.activeNorms * threshold).toInt()
                assets.notificationText("streak_threat", 3)?.format(needed - facts.completedNorms, facts.activeNorms) ?: return
            }
            NormCatalog.FROG -> {
                val attempts = outreachDao.countBeforeCutoff(date)
                val target = work.targetFor(date)
                val lvl = effectiveLevel(level, profile.notificationTone)
                assets.notificationText("frog", lvl)?.let { if (lvl == 3) it.format(attempts, target) else it } ?: return
            }
            NormCatalog.ANGELUS -> assets.notificationText(if (calendar.day(date).reginaCaeli) "regina_caeli" else "angelus", 1) ?: return
            else -> assets.notificationText(normId, effectiveLevel(level, profile.notificationTone)) ?: assets.notificationText(normId, 1) ?: return
        }
        val channel = if (level >= 2) NotificationChannels.NORM_ESCALATION else NotificationChannels.NORM_REMINDERS
        val deepLink = DEEP_LINKS[normId] ?: "itermeum://today"
        presenter.post(
            notificationId(normId), channel,
            if (normId == THREAT) "Iter Meum" else (norms.norm(normId)?.title ?: "Iter Meum"), text, deepLink,
            if (level >= 2) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT,
        )
        budget.record(level)
        budget.markSent(normId, level)
    }

    /** Tone setting: CALM never escalates wording beyond level 1; STERN uses level-2 wording from the first reminder of core norms. */
    private fun effectiveLevel(level: Int, tone: String): Int = when (tone) {
        "CALM" -> minOf(level, if (level == 3) 3 else 1)
        "STERN" -> maxOf(level, 2).coerceAtMost(3)
        else -> level
    }

    companion object {
        const val ACTION = "com.itermeum.app.reminder.NORM"
        const val EXTRA_NORM = "norm"
        const val EXTRA_LEVEL = "level"
        const val THREAT = "streak_threat"
        val DEEP_LINKS = mapOf(
            NormCatalog.MENTAL_PRAYER to "itermeum://mental", NormCatalog.GOSPEL to "itermeum://gospel", NormCatalog.READING to "itermeum://gospel",
            NormCatalog.SRS to "itermeum://srs", NormCatalog.ANGELUS to "itermeum://angelus", NormCatalog.DAY_PLAN to "itermeum://work",
            NormCatalog.FROG to "itermeum://outreach", NormCatalog.LAST_STONE to "itermeum://laststone", NormCatalog.ROSARY to "itermeum://rosary",
            NormCatalog.EXAMEN to "itermeum://examen", NormCatalog.CATECHESIS to "itermeum://catechesis", NormCatalog.WEEKLY_REVIEW to "itermeum://path",
            NormCatalog.MORTIFICATION to "itermeum://today", NormCatalog.MASS to "itermeum://prayer", THREAT to "itermeum://today",
        )
        /** The action carries (norm, slot) so the PendingIntent identity is stable across reschedules. */
        fun intent(context: Context, normId: String, level: Int, slot: Int = 0) =
            Intent(context, NormReminderReceiver::class.java).setAction("$ACTION.$normId.$slot").putExtra(EXTRA_NORM, normId).putExtra(EXTRA_LEVEL, level)

        /** One notification per norm, collision-free (index in the catalog). */
        fun notificationId(normId: String): Int {
            val index = NormCatalog.all.indexOfFirst { it.id == normId }.let { if (it < 0) NormCatalog.all.size else it }
            return NotificationPresenter.ID_NORM_BASE + index
        }
    }
}
