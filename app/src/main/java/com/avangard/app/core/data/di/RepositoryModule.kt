package com.avangard.app.core.data.di

import com.avangard.app.core.data.RoomHabitRepository
import com.avangard.app.core.data.RoomReportRepository
import com.avangard.app.core.domain.repository.HabitRepository
import com.avangard.app.core.domain.repository.ReportRepository
import com.avangard.app.core.domain.repository.WidgetRefresher
import com.avangard.app.widget.AvangardWidgetRefresher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindReportRepository(impl: RoomReportRepository): ReportRepository

    @Binds
    @Singleton
    abstract fun bindHabitRepository(impl: RoomHabitRepository): HabitRepository

    @Binds
    @Singleton
    abstract fun bindWidgetRefresher(impl: AvangardWidgetRefresher): WidgetRefresher
}
