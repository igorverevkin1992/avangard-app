package com.itermeum.app.sync.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.itermeum.app.core.data.MorningRepository
import com.itermeum.app.core.database.entity.MorningStage
import com.itermeum.app.sync.notifications.NotificationPresenter
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** MISSED at +60 min, the quarantine question at +35 min, and its Да/Нет answers. */
@AndroidEntryPoint
class MorningEventReceiver : BroadcastReceiver() {

    @Inject lateinit var morning: MorningRepository
    @Inject lateinit var presenter: NotificationPresenter

    override fun onReceive(context: Context, intent: Intent) {
        val date = intent.getStringExtra(EXTRA_DATE)?.let(LocalDate::parse) ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_MISSED -> {
                        val state = morning.get(date)
                        if (state != null && state.stage in setOf(MorningStage.RINGING, MorningStage.PROOF_PENDING, MorningStage.SCHEDULED)) {
                            morning.markMissed(date)
                            AlarmService.stop(context)
                        }
                    }
                    ACTION_QUARANTINE_CHECK -> {
                        val state = morning.get(date)
                        if (state != null && state.quarantineAnswer == null && state.stage == MorningStage.QUARANTINE) {
                            presenter.quarantineQuestion(date)
                        }
                    }
                    ACTION_QUARANTINE_YES -> { morning.answerQuarantine(date, true); presenter.cancel(NotificationPresenter.ID_QUARANTINE) }
                    ACTION_QUARANTINE_NO -> { morning.answerQuarantine(date, false); presenter.cancel(NotificationPresenter.ID_QUARANTINE) }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_MISSED = "com.itermeum.app.morning.MISSED"
        const val ACTION_QUARANTINE_CHECK = "com.itermeum.app.morning.QUARANTINE_CHECK"
        const val ACTION_QUARANTINE_YES = "com.itermeum.app.morning.QUARANTINE_YES"
        const val ACTION_QUARANTINE_NO = "com.itermeum.app.morning.QUARANTINE_NO"
        const val EXTRA_DATE = "date"
        fun intent(context: Context, action: String, date: LocalDate) =
            Intent(context, MorningEventReceiver::class.java).setAction(action).putExtra(EXTRA_DATE, date.toString())
    }
}
