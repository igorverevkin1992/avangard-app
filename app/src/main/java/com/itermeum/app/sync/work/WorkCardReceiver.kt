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
                // A quick hero attempt: the name can be added later in «Обзвон».
                action == ACTION_HERO -> work.logOutreach(date, HERO_LABEL, null, null, OutreachKind.CALL, OutreachOutcome.REACHED, null)
                action == ACTION_AGREEMENT -> work.logOutreach(date, HERO_LABEL, null, null, OutreachKind.CALL, OutreachOutcome.AGREEMENT, null)
                action == ACTION_TASK_DONE -> work.tasksFor(date).filter { it.status == TaskStatus.OPEN }.minByOrNull { it.slot }
                    ?.let { work.closeTask(it, listOf(true, true, true, true)) }
                action.startsWith(ACTION_TICK) -> Unit
            }
            card.refresh()
            DayWidget.refresh(context)
        }
    }

    companion object {
        const val ACTION_HERO = "com.itermeum.app.work.HERO"
        const val ACTION_AGREEMENT = "com.itermeum.app.work.AGREEMENT"
        const val ACTION_TASK_DONE = "com.itermeum.app.work.TASK_DONE"
        const val ACTION_TICK = "com.itermeum.app.work.TICK"
        const val HERO_LABEL = "герой (из шторки)"
    }
}
