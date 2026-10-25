package com.itermeum.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.itermeum.app.sync.notifications.NotificationChannels
import com.itermeum.app.sync.scheduler.ReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class IterMeumApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var channels: NotificationChannels
    @Inject lateinit var reminderScheduler: ReminderScheduler

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        channels.ensureCreated()
        // Section 10: rescheduleAll() on every app start.
        applicationScope.launch { reminderScheduler.rescheduleAll() }
    }
}
