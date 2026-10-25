package com.itermeum.app.core.crypto

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CryptoModule {
    @Binds
    @Singleton
    abstract fun bindFieldCipher(impl: KeystoreFieldCipher): FieldCipher
}
