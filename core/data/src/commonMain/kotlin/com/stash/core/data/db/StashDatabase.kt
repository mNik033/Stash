package com.stash.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.stash.core.data.db.converter.Converters
import com.stash.core.data.db.dao.ArtistGenreDao
import com.stash.core.data.db.dao.ArtistImageDao
import com.stash.core.data.db.dao.ArtistProfileCacheDao
import com.stash.core.data.db.dao.DiscoveryQueueDao
import com.stash.core.data.db.dao.DownloadQueueDao
import com.stash.core.data.db.dao.FlacUpgradeQueueDao
import com.stash.core.data.db.dao.LastFmCacheDao
import com.stash.core.data.db.dao.ListenSubmissionDao
import com.stash.core.data.db.dao.SyncUndoDao
import com.stash.core.data.db.dao.ListeningEventDao
import com.stash.core.data.db.dao.LyricsDao
import com.stash.core.data.db.dao.PlaylistDao
import com.stash.core.data.db.dao.RemoteSnapshotDao
import com.stash.core.data.db.dao.ScannedSongDao
import com.stash.core.data.db.dao.SharedMixDao
import com.stash.core.data.db.dao.SourceAccountDao
import com.stash.core.data.db.dao.SpotifyResolutionDao
import com.stash.core.data.db.dao.StashMixRecipeDao
import com.stash.core.data.db.dao.SyncHistoryDao
import com.stash.core.data.db.dao.TrackBlocklistDao
import com.stash.core.data.db.dao.TrackDao
import com.stash.core.data.db.dao.TrackSkipEventDao
import com.stash.core.data.db.dao.TrackTagDao
import com.stash.core.data.db.dao.WebLibraryExportDao
import com.stash.core.data.db.entity.ArtistGenreEntity
import com.stash.core.data.db.entity.ArtistImageEntity
import com.stash.core.data.db.entity.ArtistProfileCacheEntity
import com.stash.core.data.db.entity.DiscoveryQueueEntity
import com.stash.core.data.db.entity.DownloadQueueEntity
import com.stash.core.data.db.entity.FlacUpgradeQueueEntity
import com.stash.core.data.db.entity.LastFmCacheEntity
import com.stash.core.data.db.entity.ListenSubmissionEntity
import com.stash.core.data.db.entity.SyncUndoMembershipEntity
import com.stash.core.data.db.entity.SyncUndoPlaylistEntity
import com.stash.core.data.db.entity.SyncUndoPointEntity
import com.stash.core.data.db.entity.ListeningEventEntity
import com.stash.core.data.db.entity.LyricsEntity
import com.stash.core.data.db.entity.PlaylistEntity
import com.stash.core.data.db.entity.PlaylistTrackCrossRef
import com.stash.core.data.db.entity.RemotePlaylistSnapshotEntity
import com.stash.core.data.db.entity.RemoteTrackSnapshotEntity
import com.stash.core.data.db.entity.ScanFolderEntity
import com.stash.core.data.db.entity.ScannedSongEntity
import com.stash.core.data.db.entity.SharedMixEntity
import com.stash.core.data.db.entity.SourceAccountEntity
import com.stash.core.data.db.entity.SpotifyResolutionEntity
import com.stash.core.data.db.entity.StashMixRecipeEntity
import com.stash.core.data.db.entity.SyncHistoryEntity
import com.stash.core.data.db.entity.TrackBlocklistEntity
import com.stash.core.data.db.entity.TrackEntity
import com.stash.core.data.db.entity.TrackFts
import com.stash.core.data.db.entity.TrackSkipEventEntity
import com.stash.core.data.db.entity.TrackTagEntity

/**
 * Central Room database for the Stash application.
 *
 * Version 2 — adds remote playlist/track snapshot entities and
 * a snapshot_id column on the playlists table.
 *
 * **Security note:** This database stores track metadata, playlists, sync
 * diagnostics, and download queue state. It does NOT store authentication
 * credentials (OAuth tokens, sp_dc cookies, etc.) — those live in
 * [com.stash.core.auth.EncryptedTokenStore] backed by EncryptedSharedPreferences.
 * The app manifest disables backups (`allowBackup=false`, `fullBackupContent=false`)
 * to prevent database extraction via `adb backup`. On rooted devices the
 * unencrypted SQLite file is still readable; adding SQLCipher encryption is a
 * future enhancement tracked separately.
 */
@Database(
    entities = [
        TrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class,
        SyncHistoryEntity::class,
        DownloadQueueEntity::class,
        SourceAccountEntity::class,
        TrackFts::class,
        RemotePlaylistSnapshotEntity::class,
        RemoteTrackSnapshotEntity::class,
        ArtistProfileCacheEntity::class,
        ListeningEventEntity::class,
        TrackTagEntity::class,
        StashMixRecipeEntity::class,
        DiscoveryQueueEntity::class,
        TrackBlocklistEntity::class,
        TrackSkipEventEntity::class,
        LyricsEntity::class,
        LastFmCacheEntity::class,
        SpotifyResolutionEntity::class,
        FlacUpgradeQueueEntity::class,
        ListenSubmissionEntity::class,
        SyncUndoPointEntity::class,
        SyncUndoPlaylistEntity::class,
        SyncUndoMembershipEntity::class,
        ArtistImageEntity::class,
        SharedMixEntity::class,
        com.stash.core.data.db.entity.TidalMatchEntity::class,
        ScanFolderEntity::class,
        ScannedSongEntity::class,
        ArtistGenreEntity::class,
        com.stash.core.data.db.entity.CloudAccountEntity::class,
        com.stash.core.data.db.entity.CloudFolderEntity::class,
        com.stash.core.data.db.entity.CloudItemEntity::class,
        com.stash.core.data.db.entity.CloudUploadEntity::class,
    ],
    version = 62,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class StashDatabase : RoomDatabase() {

    abstract fun trackDao(): TrackDao

    abstract fun cloudDao(): com.stash.core.data.db.dao.CloudDao

    abstract fun playlistDao(): PlaylistDao

    abstract fun syncHistoryDao(): SyncHistoryDao

    abstract fun downloadQueueDao(): DownloadQueueDao

    abstract fun sourceAccountDao(): SourceAccountDao

    abstract fun remoteSnapshotDao(): RemoteSnapshotDao

    abstract fun artistProfileCacheDao(): ArtistProfileCacheDao

    abstract fun artistImageDao(): ArtistImageDao

    abstract fun artistGenreDao(): ArtistGenreDao

    abstract fun listeningEventDao(): ListeningEventDao

    abstract fun trackTagDao(): TrackTagDao

    abstract fun stashMixRecipeDao(): StashMixRecipeDao

    abstract fun discoveryQueueDao(): DiscoveryQueueDao

    abstract fun trackBlocklistDao(): TrackBlocklistDao

    abstract fun trackSkipEventDao(): TrackSkipEventDao

    abstract fun lyricsDao(): LyricsDao

    abstract fun lastFmCacheDao(): LastFmCacheDao

    abstract fun spotifyResolutionDao(): SpotifyResolutionDao

    abstract fun tidalMatchDao(): com.stash.core.data.db.dao.TidalMatchDao

    abstract fun flacUpgradeQueueDao(): FlacUpgradeQueueDao

    abstract fun listenSubmissionDao(): ListenSubmissionDao

    abstract fun syncUndoDao(): SyncUndoDao

    abstract fun sharedMixDao(): SharedMixDao

    abstract fun webLibraryExportDao(): WebLibraryExportDao

    abstract fun mirrorDao(): com.stash.core.data.db.dao.MirrorDao

    abstract fun scannedSongDao(): ScannedSongDao

    abstract fun listeningHistoryDao(): com.stash.core.data.db.dao.ListeningHistoryDao

    companion object {
        const val DATABASE_NAME = "stash.db"
    }
}
