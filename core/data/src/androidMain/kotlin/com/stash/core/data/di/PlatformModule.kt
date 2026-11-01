package com.stash.core.data.di

import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import com.stash.core.auth.crypto.EncryptionManager
import com.stash.core.common.platform.AppPaths
import com.stash.core.common.platform.BackgroundScheduler
import com.stash.core.common.platform.Connectivity
import com.stash.core.common.platform.PlatformInfo
import com.stash.core.data.audio.AndroidAudioDurationExtractor
import com.stash.core.data.audio.AndroidChargingMonitor
import com.stash.core.data.audio.AudioDurationExtractor
import com.stash.core.data.audio.ChargingMonitor
import com.stash.core.data.files.AndroidLocalFileOps
import com.stash.core.data.files.LocalFileOps
import com.stash.core.data.platform.AndroidAppPaths
import com.stash.core.data.platform.AndroidBackgroundScheduler
import com.stash.core.data.platform.AndroidConnectivity
import com.stash.core.data.platform.AndroidPlatformInfo
import com.stash.core.data.supporter.SupporterRepository
import com.stash.core.data.tipjar.SupportersJsonUrl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlatformModule {

    @Binds
    @Singleton
    abstract fun bindAppPaths(impl: AndroidAppPaths): AppPaths

    @Binds
    @Singleton
    abstract fun bindConnectivity(impl: AndroidConnectivity): Connectivity

    @Binds
    @Singleton
    abstract fun bindPlatformInfo(impl: AndroidPlatformInfo): PlatformInfo

    @Binds
    @Singleton
    abstract fun bindBackgroundScheduler(impl: AndroidBackgroundScheduler): BackgroundScheduler

    @Binds
    @Singleton
    abstract fun bindAudioDurationExtractor(impl: AndroidAudioDurationExtractor): AudioDurationExtractor

    @Binds
    @Singleton
    abstract fun bindChargingMonitor(impl: AndroidChargingMonitor): ChargingMonitor

    @Binds
    @Singleton
    abstract fun bindLocalFileOps(impl: AndroidLocalFileOps): LocalFileOps

    companion object {
        @Provides
        @Singleton
        fun provideSupporterRepository(
            paths: AppPaths,
            sharedClient: OkHttpClient,
            @SupportersJsonUrl tipJarUrl: String,
            encryption: EncryptionManager,
        ): SupporterRepository = SupporterRepository(
            store = PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                produceFile = { java.io.File(paths.filesDir, "datastore/" + SupporterRepository.DATASTORE_FILE) }
            ),
            sharedClient = sharedClient,
            tipJarUrl = tipJarUrl,
            encryption = encryption,
        )

        @Provides
        @Singleton
        fun provideDownloadQueueTrigger(
            singleTrackDownloadEnqueuer: com.stash.core.data.sync.SingleTrackDownloadEnqueuer,
        ): com.stash.core.data.sync.DownloadQueueTrigger = object : com.stash.core.data.sync.DownloadQueueTrigger {
            override fun triggerDrain() {}
            override suspend fun enqueue(queueId: Long) {
                singleTrackDownloadEnqueuer.enqueue(queueId)
            }
        }
    }
}
