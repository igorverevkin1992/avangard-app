package com.avangard.app

import android.app.Application
import com.avangard.app.sync.notifications.SimpleNotificationPresenter
import com.avangard.app.sync.scheduler.EveningCloseScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AvangardApplication : Application() {

    @Inject lateinit var scheduler: EveningCloseScheduler
    @Inject lateinit var presenter: SimpleNotificationPresenter

    override fun onCreate() {
        super.onCreate()
        presenter.ensureChannel()
        scheduler.ensureScheduled()
    }
}
