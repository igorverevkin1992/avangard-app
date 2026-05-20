package com.avangard.app.core.data.di

import com.avangard.app.core.data.RoomBackupRepository
import com.avangard.app.core.data.RoomHabitRepository
import com.avangard.app.core.data.RoomSessionRepository
import com.avangard.app.core.domain.repository.BackupRepository
import com.avangard.app.core.domain.repository.HabitRepository
import com.avangard.app.core.domain.repository.SessionRepository
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

    @Binds
    @Singleton
    abstract fun bindSessionRepository(impl: RoomSessionRepository): SessionRepository

    @Binds
    @Singleton
    abstract fun bindBackupRepository(impl: RoomBackupRepository): BackupRepository
}
