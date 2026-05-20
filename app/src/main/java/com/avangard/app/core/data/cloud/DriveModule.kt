package com.avangard.app.core.data.cloud

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object DriveModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        // Conservative timeouts — the backup is <1 MB, but mobile networks
        // wobble. Multipart upload includes a body stream so keep write
        // higher than read.
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Provides
    @Singleton
    @Named(DriveBackupClient.DRIVE_BASE_URL_QUALIFIER)
    fun provideDriveBaseUrl(): String = DriveBackupClient.DEFAULT_BASE_URL
}
