package com.stash.feature.home

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.stash.feature.home.shelves.HomeShelvesViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AndroidHomeViewModel @Inject constructor(
    musicRepository: com.stash.core.data.repository.MusicRepository,
    playerRepository: com.stash.core.media.PlayerRepository,
    losslessPrefs: com.stash.data.download.lossless.LosslessSourcePreferences,
    settingsDeepLinkController: com.stash.core.data.navigation.SettingsDeepLinkController,
    libraryDeepLinkController: com.stash.core.data.navigation.LibraryDeepLinkController,
    tipJarRepository: com.stash.core.data.tipjar.TipJarRepository,
    recipeDao: com.stash.core.data.db.dao.StashMixRecipeDao,
    discoveryQueueDao: com.stash.core.data.db.dao.DiscoveryQueueDao,
    playlistDao: com.stash.core.data.db.dao.PlaylistDao,
    downloadNetworkPreference: com.stash.core.data.prefs.DownloadNetworkPreference,
    streamingPreference: com.stash.core.data.prefs.StreamingPreference,
    homeDiscoveryPreference: com.stash.core.data.prefs.HomeDiscoveryPreference,
    homeSectionsPreference: com.stash.core.data.prefs.HomeSectionsPreference,
    mixRotationPreference: com.stash.core.data.prefs.HomeMixRotationPreference,
    metadataBackfillState: com.stash.data.download.backfill.MetadataBackfillState,
    homeDiscoveryRepository: com.stash.core.data.discovery.HomeDiscoveryRepository,
    losslessSourceHealth: com.stash.core.media.streaming.LosslessSourceHealth,
    losslessAvailability: com.stash.data.download.lossless.LosslessAvailability,
    shownSongs: com.stash.core.data.home.HomeShownSongs,
    appPaths: com.stash.core.common.platform.AppPaths,
    mixWorkerScheduler: HomeMixWorkerScheduler,
) : HomeViewModel(
    musicRepository = musicRepository,
    playerRepository = playerRepository,
    losslessPrefs = losslessPrefs,
    settingsDeepLinkController = settingsDeepLinkController,
    libraryDeepLinkController = libraryDeepLinkController,
    tipJarRepository = tipJarRepository,
    recipeDao = recipeDao,
    discoveryQueueDao = discoveryQueueDao,
    playlistDao = playlistDao,
    downloadNetworkPreference = downloadNetworkPreference,
    streamingPreference = streamingPreference,
    homeDiscoveryPreference = homeDiscoveryPreference,
    homeSectionsPreference = homeSectionsPreference,
    mixRotationPreference = mixRotationPreference,
    metadataBackfillState = metadataBackfillState,
    homeDiscoveryRepository = homeDiscoveryRepository,
    losslessSourceHealth = losslessSourceHealth,
    losslessAvailability = losslessAvailability,
    shownSongs = shownSongs,
    appPaths = appPaths,
    mixWorkerScheduler = mixWorkerScheduler,
)

@HiltViewModel
class AndroidHomeShelvesViewModel @Inject constructor(
    newFromArtistsRepository: com.stash.core.data.home.NewFromArtistsRepository,
    becauseYouLikeRepository: com.stash.core.data.home.BecauseYouLikeRepository,
    shownSongs: com.stash.core.data.home.HomeShownSongs,
    playerRepository: com.stash.core.media.PlayerRepository,
    streamingPreference: com.stash.core.data.prefs.StreamingPreference,
    homeSectionsPreference: com.stash.core.data.prefs.HomeSectionsPreference,
    connectivity: com.stash.core.media.streaming.ConnectivityMonitor,
) : HomeShelvesViewModel(
    newFromArtistsRepository = newFromArtistsRepository,
    becauseYouLikeRepository = becauseYouLikeRepository,
    shownSongs = shownSongs,
    playerRepository = playerRepository,
    streamingPreference = streamingPreference,
    homeSectionsPreference = homeSectionsPreference,
    connectivity = connectivity,
)

@HiltViewModel
class AndroidPlaylistBrowseViewModel @Inject constructor(
    savedStateHandle: androidx.lifecycle.SavedStateHandle,
    repository: com.stash.core.data.discovery.HomeDiscoveryRepository,
) : PlaylistBrowseViewModel(
    savedStateHandle = savedStateHandle,
    repository = repository,
)

@Composable
actual fun homeViewModel(): HomeViewModel = hiltViewModel<AndroidHomeViewModel>()

@Composable
actual fun homeShelvesViewModel(): HomeShelvesViewModel = hiltViewModel<AndroidHomeShelvesViewModel>()

@Composable
actual fun playlistBrowseViewModel(genre: String): PlaylistBrowseViewModel = hiltViewModel<AndroidPlaylistBrowseViewModel>()

@Composable
actual fun rememberWordmarkPainter(): Painter? = painterResource(id = R.drawable.wordmark_stash)

@Composable
actual fun rememberVinylRecordPainter(): Painter? = painterResource(id = R.drawable.vinyl_record)

@Composable
actual fun rememberDiscordPainter(): Painter? = painterResource(id = com.stash.core.ui.R.drawable.ic_brand_discord)

@Composable
actual fun HandleUserMessages(viewModel: HomeViewModel) {
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.userMessages.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }
}

