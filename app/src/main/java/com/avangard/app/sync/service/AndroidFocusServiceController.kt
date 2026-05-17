package com.avangard.app.sync.service

import android.content.Context
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
        FlashForegroundService.start(context)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class FocusServiceControllerModule {
    @Binds
    @Singleton
    abstract fun bind(impl: AndroidFocusServiceController): FocusServiceController
}
