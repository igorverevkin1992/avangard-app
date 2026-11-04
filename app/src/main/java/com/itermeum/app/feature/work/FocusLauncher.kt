package com.itermeum.app.feature.work

import android.content.Context
import com.itermeum.app.core.data.TimerController
import com.itermeum.app.core.data.TimerFinisher
import com.itermeum.app.core.data.WorkItemRepository
import com.itermeum.app.sync.service.SessionTimerService
import com.itermeum.app.sync.widget.WidgetUpdater
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

/**
 * D-64: focus on one work item — the shared timer card in the shade shows the
 * whole time spent on it, with «Пауза» and «Готово». One timer at a time: a
 * running prayer or reading session is not interrupted.
 */
@Singleton
class FocusLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val timers: TimerController,
    private val finisher: TimerFinisher,
    private val items: WorkItemRepository,
    private val widget: WidgetUpdater,
) {
    val session: StateFlow<TimerController.Session?> = timers.session

    /** False when a prayer or reading timer is running. Focus on another item ends that one first. */
    suspend fun start(itemId: Long): Boolean {
        val cur = timers.current()
        if (cur != null && cur.kind != TimerController.Kind.FOCUS) return false
        if (cur != null) {
            if (cur.refId == itemId) return true
            finisher.finish()
        }
        val item = items.get(itemId) ?: return false
        timers.start(TimerController.Kind.FOCUS, FOCUS_MS, priorMs = item.spentMs, refId = itemId, label = item.title)
        SessionTimerService.start(context)
        return true
    }

    suspend fun pause() { if (timers.current()?.kind == TimerController.Kind.FOCUS) finisher.finish(); widget.requestUpdate() }

    suspend fun done() { if (timers.current()?.kind == TimerController.Kind.FOCUS) finisher.finish(complete = true); widget.requestUpdate() }

    companion object {
        const val FOCUS_MS = 25 * 60_000L
    }
}
