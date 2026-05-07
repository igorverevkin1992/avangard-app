package com.avangard.app.core.data.di

import com.avangard.app.core.data.RoomReportRepository
import com.avangard.app.core.domain.repository.ReportRepository
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
}
