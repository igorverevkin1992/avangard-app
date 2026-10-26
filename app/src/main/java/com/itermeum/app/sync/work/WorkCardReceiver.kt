package com.itermeum.app.sync.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.itermeum.app.core.common.Clock
import com.itermeum.app.core.data.WorkRepository
import com.itermeum.app.core.database.entity.OutreachKind
import com.itermeum.app.core.database.entity.OutreachOutcome
import com.itermeum.app.core.database.entity.TaskStatus
import com.itermeum.app.sync.runAsync
import com.itermeum.app.sync.widget.DayWidget
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** One-tap actions from the work card, and its scheduled block changes (D-34). */
@AndroidEntryPoint
class WorkCardReceiver : BroadcastReceiver() {

    @Inject lateinit var work: WorkRepository
    @Inject lateinit var card: WorkCard
    @Inject lateinit var clock: Clock

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        runAsync("WorkCard") {
            val date = clock.logicalToday()
            when {
                // A quick hero attempt with its outcome: the name can be added later in «Обзвон».
                action == ACTION_AGREEMENT -> hero(date, OutreachOutcome.AGREEMENT)
                action == ACTION_CALLBACK -> hero(date, OutreachOutcome.CALLBACK)
                action == ACTION_REJECTION -> hero(date, OutreachOutcome.REJECTION)
                action == ACTION_TASK_DONE -> work.tasksFor(date).filter { it.status == TaskStatus.OPEN }.minByOrNull { it.slot }
                    ?.let { work.closeTask(it, listOf(true, true, true, true)) }
                action.startsWith(ACTION_TICK) -> Unit
            }
            card.refresh()
            DayWidget.refresh(context)
        }
    }

    private suspend fun hero(date: java.time.LocalDate, outcome: OutreachOutcome) =
        work.logOutreach(date, HERO_LABEL, null, null, OutreachKind.CALL, outcome, null)

    companion object {
        const val ACTION_AGREEMENT = "com.itermeum.app.work.AGREEMENT"
        const val ACTION_CALLBACK = "com.itermeum.app.work.CALLBACK"
        const val ACTION_REJECTION = "com.itermeum.app.work.REJECTION"
        const val ACTION_TASK_DONE = "com.itermeum.app.work.TASK_DONE"
        const val ACTION_TICK = "com.itermeum.app.work.TICK"
        const val HERO_LABEL = "герой (из шторки)"
    }
}
