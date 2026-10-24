package com.stash.core.auth.di

import com.stash.core.auth.TokenManager
import com.stash.core.auth.TokenManagerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that wires up authentication dependencies.
 *
 * Binds the [TokenManagerImpl] concrete implementation to the [TokenManager]
 * interface so that consumers depend only on the abstraction.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    @Singleton
    abstract fun bindTokenManager(impl: TokenManagerImpl): TokenManager

    @Binds
    @Singleton
    abstract fun bindTokenStore(impl: com.stash.core.auth.store.EncryptedTokenStore): com.stash.core.auth.store.TokenStore

    @Binds
    @Singleton
    abstract fun bindEncryptionManager(impl: com.stash.core.auth.crypto.TinkEncryptionManager): com.stash.core.auth.crypto.EncryptionManager
}
