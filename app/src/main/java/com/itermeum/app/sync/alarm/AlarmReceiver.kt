package com.itermeum.app.sync.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDate

/** Fires at wake time: hands over to the foreground [AlarmService] immediately. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val date = intent.getStringExtra(EXTRA_DATE) ?: return
        val test = intent.getBooleanExtra(EXTRA_TEST, false)
        AlarmService.start(context, LocalDate.parse(date), test)
    }

    companion object {
        const val ACTION = "com.itermeum.app.alarm.WAKE"
        const val EXTRA_DATE = "date"
        const val EXTRA_TEST = "test"
        fun intent(context: Context, date: LocalDate, test: Boolean) =
            Intent(context, AlarmReceiver::class.java).setAction(if (test) "$ACTION.TEST" else ACTION)
                .putExtra(EXTRA_DATE, date.toString()).putExtra(EXTRA_TEST, test)
    }
}
