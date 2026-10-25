package com.itermeum.app.sync.scheduler

import com.itermeum.app.core.common.Clock
import com.itermeum.app.core.content.ContentAssets
import com.itermeum.app.core.data.PathRepository
import com.itermeum.app.core.data.StreakRepository
import com.itermeum.app.core.database.entity.StreakType
import com.itermeum.app.core.engine.StreakEngine
import com.itermeum.app.sync.notifications.NotificationBudget
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
    private val streaks: StreakRepository,
    private val path: PathRepository,
    private val budget: NotificationBudget,
    private val assets: ContentAssets,
    private val clock: Clock,
) {
    private val mutex = Mutex()

    suspend fun run() = mutex.withLock {
        val today = clock.logicalToday()
        path.syncAutoTravel()
        val results = streaks.closeDaysUpTo(today.minusDays(1))
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
    }
}
