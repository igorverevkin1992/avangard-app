package com.itermeum.app.sync.scheduler

import com.itermeum.app.core.common.Clock
import com.itermeum.app.core.content.ContentAssets
import com.itermeum.app.core.data.DayFlags
import com.itermeum.app.core.data.MorningRepository
import com.itermeum.app.core.data.PathRepository
import com.itermeum.app.core.data.StreakRepository
import com.itermeum.app.core.database.entity.StreakType
import com.itermeum.app.core.engine.StreakEngine
import android.content.Context
import com.itermeum.app.sync.notifications.NotificationBudget
import com.itermeum.app.sync.widget.DayWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Idempotent day close: evaluates every uncounted logical day before today
 * (at 03:00 and lazily on app open — section 6.1), leaves the freeze /
 * broken-streak banner for the Today screen, syncs travel and the coming
 * formation events.
 */
@Singleton
class DayCloseRunner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val streaks: StreakRepository,
    private val path: PathRepository,
    private val budget: NotificationBudget,
    private val assets: ContentAssets,
    private val flags: DayFlags,
    private val morning: MorningRepository,
    private val clock: Clock,
) {
    private val mutex = Mutex()

    suspend fun run() = mutex.withLock {
        val today = clock.logicalToday()
        path.syncAutoTravel()
        val upTo = lastClosableDay(today, clock.now(), flags.lastTimeZoneChange(), clock.nowEpochMillis(), morning.get(today)?.provenAt != null)
        val results = streaks.closeDaysUpTo(upTo)
        results.lastOrNull { it.outcomes[StreakType.FIDELITY].let { o -> o is StreakEngine.Outcome.Frozen || o is StreakEngine.Outcome.Broken } }
            ?.let { flags.markWhatBroke(it.date) }
        results.lastOrNull()?.outcomes?.get(StreakType.FIDELITY)?.let { outcome ->
            when (outcome) {
                is StreakEngine.Outcome.Frozen -> budget.setBanner(assets.notificationText("freeze_applied", 1))
                is StreakEngine.Outcome.Broken -> budget.setBanner(assets.notificationText("streak_broken", 1))
                else -> Unit
            }
        }
        budget.clearOld()
        // Formation events for the next 8 days keep the schedule's quiet windows and Mass reminders right.
        var d = today
        repeat(8) {
            if (d.dayOfWeek == java.time.DayOfWeek.SATURDAY) path.ensureCatechesisEvent(d)
            path.ensureMassEvent(d)
            d = d.plusDays(1)
        }
        DayWidget.refresh(context)
    }

    companion object {
        private val CLOSE_AT: LocalTime = LocalTime.of(4, 0)
        private val TZ_FALLBACK: LocalTime = LocalTime.of(12, 0)

        /**
         * Yesterday is closed from 04:00 (Examen «за вчера» runs until 03:59). After a
         * time-zone change in the last 24 h the new date only «starts» with the wake
         * proof or at 12:00 local (section 6.1), so a flight never closes a running day.
         */
        fun lastClosableDay(today: LocalDate, now: LocalDateTime, tzChangedAt: Long, nowEpoch: Long, wakeProvenToday: Boolean): LocalDate {
            if (now.isBefore(today.atTime(CLOSE_AT))) return today.minusDays(2)
            val recentTzChange = tzChangedAt > 0 && nowEpoch - tzChangedAt < 24 * 3_600_000L
            if (recentTzChange && !wakeProvenToday && now.isBefore(today.atTime(TZ_FALLBACK))) return today.minusDays(2)
            return today.minusDays(1)
        }
    }
}
