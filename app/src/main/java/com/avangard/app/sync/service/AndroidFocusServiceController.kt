package com.avangard.app.sync.service

import android.content.Context
import android.util.Log
import com.avangard.app.core.domain.usecase.FocusServiceController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidFocusServiceController @Inject constructor(
    @ApplicationContext private val context: Context,
) : FocusServiceController {
    override fun start() {
        // The FlashForegroundService manifest entry is temporarily disabled
        // (see AndroidManifest.xml) while a fresh-install startup crash is
        // isolated. Without the entry, startForegroundService throws
        // SecurityException at runtime. Guard with try/catch and log so
        // the use-case stays happy; the focus row is the source of truth
        // for the pulpit timer regardless.
        try {
            FlashForegroundService.start(context)
        } catch (e: Throwable) {
            Log.w("FocusService", "FlashForegroundService unavailable", e)
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class FocusServiceControllerModule {
    @Binds
    @Singleton
    abstract fun bind(impl: AndroidFocusServiceController): FocusServiceController
}
