package com.avangard.app.core.data.di

import com.avangard.app.core.data.RoomHabitRepository
import com.avangard.app.core.domain.repository.HabitRepository
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
    abstract fun bindHabitRepository(impl: RoomHabitRepository): HabitRepository
}
