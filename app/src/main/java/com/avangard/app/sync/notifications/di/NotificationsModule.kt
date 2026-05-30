package com.avangard.app.sync.notifications.di

import com.avangard.app.sync.notifications.StatusNotifier
import com.avangard.app.sync.notifications.StatusNotifierImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationsModule {

    @Binds
    @Singleton
    abstract fun bindStatusNotifier(impl: StatusNotifierImpl): StatusNotifier
}
