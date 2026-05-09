package com.avangard.app

import android.app.Application
import com.avangard.app.sync.scheduler.ScheduleBootstrapper
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AvangardApplication : Application() {

    @Inject lateinit var scheduleBootstrapper: ScheduleBootstrapper

    override fun onCreate() {
        super.onCreate()
        scheduleBootstrapper.bootstrap()
    }
}
