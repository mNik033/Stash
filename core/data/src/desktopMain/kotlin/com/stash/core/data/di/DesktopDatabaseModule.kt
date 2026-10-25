package com.stash.core.data.di

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.stash.core.data.db.StashDatabase
import com.stash.core.data.db.dao.ArtistGenreDao
import com.stash.core.data.db.dao.ArtistImageDao
import com.stash.core.data.db.dao.ArtistProfileCacheDao
import com.stash.core.data.db.dao.CloudDao
import com.stash.core.data.db.dao.DiscoveryQueueDao
import com.stash.core.data.db.dao.DownloadQueueDao
import com.stash.core.data.db.dao.FlacUpgradeQueueDao
import com.stash.core.data.db.dao.LastFmCacheDao
import com.stash.core.data.db.dao.ListenSubmissionDao
import com.stash.core.data.db.dao.ListeningEventDao
import com.stash.core.data.db.dao.ListeningHistoryDao
import com.stash.core.data.db.dao.LyricsDao
import com.stash.core.data.db.dao.MirrorDao
import com.stash.core.data.db.dao.PlaylistDao
import com.stash.core.data.db.dao.RemoteSnapshotDao
import com.stash.core.data.db.dao.ScannedSongDao
import com.stash.core.data.db.dao.SharedMixDao
import com.stash.core.data.db.dao.SourceAccountDao
import com.stash.core.data.db.dao.SpotifyResolutionDao
import com.stash.core.data.db.dao.StashMixRecipeDao
import com.stash.core.data.db.dao.SyncHistoryDao
import com.stash.core.data.db.dao.SyncUndoDao
import com.stash.core.data.db.dao.TidalMatchDao
import com.stash.core.data.db.dao.TrackBlocklistDao
import com.stash.core.data.db.dao.TrackDao
import com.stash.core.data.db.dao.TrackSkipEventDao
import com.stash.core.data.db.dao.TrackTagDao
import com.stash.core.data.db.dao.WebLibraryExportDao
import com.stash.core.common.platform.AppPaths
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.Dispatchers
import java.io.File
import javax.inject.Singleton

@Module
object DesktopDatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(appPaths: AppPaths): StashDatabase {
        val dbFile = appPaths.getDatabasePath(StashDatabase.DATABASE_NAME)
        dbFile.parentFile?.mkdirs()
        return Room.databaseBuilder<StashDatabase>(name = dbFile.absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }

    @Provides
    fun provideTrackDao(db: StashDatabase): TrackDao = db.trackDao()

    @Provides
    fun provideCloudDao(db: StashDatabase): CloudDao = db.cloudDao()

    @Provides
    fun providePlaylistDao(db: StashDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun provideSyncHistoryDao(db: StashDatabase): SyncHistoryDao = db.syncHistoryDao()

    @Provides
    fun provideDownloadQueueDao(db: StashDatabase): DownloadQueueDao = db.downloadQueueDao()

    @Provides
    fun provideSourceAccountDao(db: StashDatabase): SourceAccountDao = db.sourceAccountDao()

    @Provides
    fun provideRemoteSnapshotDao(db: StashDatabase): RemoteSnapshotDao = db.remoteSnapshotDao()

    @Provides
    fun provideArtistProfileCacheDao(db: StashDatabase): ArtistProfileCacheDao =
        db.artistProfileCacheDao()

    @Provides
    fun provideArtistImageDao(db: StashDatabase): ArtistImageDao = db.artistImageDao()

    @Provides
    fun provideArtistGenreDao(db: StashDatabase): ArtistGenreDao = db.artistGenreDao()

    @Provides
    fun provideListeningEventDao(db: StashDatabase): ListeningEventDao = db.listeningEventDao()

    @Provides
    fun provideTrackTagDao(db: StashDatabase): TrackTagDao = db.trackTagDao()

    @Provides
    fun provideStashMixRecipeDao(db: StashDatabase): StashMixRecipeDao = db.stashMixRecipeDao()

    @Provides
    fun provideDiscoveryQueueDao(db: StashDatabase): DiscoveryQueueDao = db.discoveryQueueDao()

    @Provides
    fun provideTrackBlocklistDao(db: StashDatabase): TrackBlocklistDao = db.trackBlocklistDao()

    @Provides
    fun provideTrackSkipEventDao(db: StashDatabase): TrackSkipEventDao = db.trackSkipEventDao()

    @Provides
    fun provideLyricsDao(db: StashDatabase): LyricsDao = db.lyricsDao()

    @Provides
    fun provideLastFmCacheDao(db: StashDatabase): LastFmCacheDao = db.lastFmCacheDao()

    @Provides
    fun provideSpotifyResolutionDao(db: StashDatabase): SpotifyResolutionDao =
        db.spotifyResolutionDao()

    @Provides
    fun provideTidalMatchDao(db: StashDatabase): TidalMatchDao = db.tidalMatchDao()

    @Provides
    fun provideFlacUpgradeQueueDao(db: StashDatabase): FlacUpgradeQueueDao =
        db.flacUpgradeQueueDao()

    @Provides
    fun provideListenSubmissionDao(db: StashDatabase): ListenSubmissionDao =
        db.listenSubmissionDao()

    @Provides
    fun provideSyncUndoDao(db: StashDatabase): SyncUndoDao = db.syncUndoDao()

    @Provides
    fun provideSharedMixDao(db: StashDatabase): SharedMixDao = db.sharedMixDao()

    @Provides
    fun provideScannedSongDao(db: StashDatabase): ScannedSongDao = db.scannedSongDao()

    @Provides
    fun provideListeningHistoryDao(db: StashDatabase): ListeningHistoryDao =
        db.listeningHistoryDao()
}
