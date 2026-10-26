package com.stash.desktop.ui.home

import com.stash.core.data.discovery.GenreCatalog
import com.stash.core.data.prefs.StreamingPreference
import com.stash.core.data.repository.MusicRepository
import com.stash.core.media.PlayerRepository
import com.stash.core.model.Playlist
import com.stash.core.model.PlaylistType
import com.stash.core.model.Track
import com.stash.data.ytmusic.YTMusicApiClient
import com.stash.data.ytmusic.model.AlbumSummary
import com.stash.data.ytmusic.model.SearchResultSection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class DesktopHomeUiState(
    val isLoading: Boolean = true,
    val streamingEnabled: Boolean = true,
    val dailyDiscover: Playlist? = null,
    val quickAccessPlaylists: List<Playlist> = emptyList(),
    val madeForYouMixes: List<Playlist> = emptyList(),
    val radioMixes: List<Playlist> = emptyList(),
    val moodAndDecadeMixes: List<Playlist> = emptyList(),
    val stashMixes: List<Playlist> = emptyList(),
    val recentlyAdded: List<Track> = emptyList(),
    val mostPlayed: List<Track> = emptyList(),
    val selectedGenre: String = GenreCatalog.GENRES.first().label,
    val newReleases: List<AlbumSummary> = emptyList(),
    val topAlbums: List<AlbumSummary> = emptyList(),
    val isDiscoveryLoading: Boolean = true,
)

@Singleton
class DesktopHomeViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val playerRepository: PlayerRepository,
    private val streamingPreference: StreamingPreference,
    private val ytMusicApiClient: YTMusicApiClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val selectedGenreFlow = MutableStateFlow(GenreCatalog.GENRES.first().label)
    private val newReleasesFlow = MutableStateFlow<List<AlbumSummary>>(emptyList())
    private val topAlbumsFlow = MutableStateFlow<List<AlbumSummary>>(emptyList())
    private val discoveryLoadingFlow = MutableStateFlow(true)

    private val genreCache = mutableMapOf<String, Pair<List<AlbumSummary>, List<AlbumSummary>>>()
    private var discoveryJob: Job? = null

    private val madeForYouNames = setOf(
        "discover weekly", "release radar", "on repeat", "repeat rewind", "daylist", "time capsule",
        "my supermix", "discover mix", "replay mix", "archive mix", "new release mix",
    )
    private val dailyOrMyMix = Regex("""^(daily mix|my mix)\s*\d+$""", RegexOption.IGNORE_CASE)

    val uiState: StateFlow<DesktopHomeUiState> = combine(
        musicRepository.getAllPlaylists(),
        musicRepository.getRecentlyAdded(20),
        musicRepository.getMostPlayed(20),
        streamingPreference.enabled,
    ) { playlists, recentlyAdded, mostPlayed, streamingEnabled ->
        val dailyDiscover = playlists.firstOrNull {
            it.type == PlaylistType.STASH_MIX &&
                it.name.contains("Daily Discover", ignoreCase = true) &&
                it.trackCount > 0
        }
        val stashMixes = playlists.filter {
            it.type == PlaylistType.STASH_MIX &&
                !it.name.contains("Daily Discover", ignoreCase = true) &&
                it.trackCount > 0
        }

        val dailyMixes = playlists.filter { it.type == PlaylistType.DAILY_MIX && it.trackCount > 0 }
        val madeForYou = dailyMixes.filter {
            val n = it.name.trim().lowercase()
            dailyOrMyMix.matches(n) || n in madeForYouNames
        }
        val radios = dailyMixes.filter { it.name.trim().endsWith("Radio", ignoreCase = true) }
        val moodAndDecades = dailyMixes.filter {
            val n = it.name.trim().lowercase()
            !it.name.trim().endsWith("Radio", ignoreCase = true) && !dailyOrMyMix.matches(n) && n !in madeForYouNames
        }

        val quickAccess = playlists
            .filter {
                it.type != PlaylistType.STASH_MIX &&
                    it.trackCount > 0
            }
            .sortedWith(
                compareByDescending<Playlist> { it.pinned || it.pinnedToHomeAt != null }
                    .thenByDescending { it.type == PlaylistType.LIKED_SONGS || it.type == PlaylistType.STASH_LIKED }
                    .thenByDescending { it.lastSynced ?: 0L },
            )
            .take(8)

        DesktopHomeUiState(
            isLoading = false,
            streamingEnabled = streamingEnabled,
            dailyDiscover = dailyDiscover,
            quickAccessPlaylists = quickAccess,
            madeForYouMixes = madeForYou,
            radioMixes = radios,
            moodAndDecadeMixes = moodAndDecades,
            stashMixes = stashMixes,
            recentlyAdded = recentlyAdded,
            mostPlayed = mostPlayed,
        )
    }.combine(
        combine(
            selectedGenreFlow,
            newReleasesFlow,
            topAlbumsFlow,
            discoveryLoadingFlow,
        ) { genre, newRel, topAlb, discLoading ->
            DiscoverySlice(genre, newRel, topAlb, discLoading)
        }
    ) { base, disc ->
        base.copy(
            selectedGenre = disc.genre,
            newReleases = disc.newReleases,
            topAlbums = disc.topAlbums,
            isDiscoveryLoading = disc.isLoading,
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DesktopHomeUiState(),
    )

    private data class DiscoverySlice(
        val genre: String,
        val newReleases: List<AlbumSummary>,
        val topAlbums: List<AlbumSummary>,
        val isLoading: Boolean,
    )

    init {
        loadGenreDiscovery(selectedGenreFlow.value)
    }

    fun selectGenre(label: String) {
        if (label == selectedGenreFlow.value && newReleasesFlow.value.isNotEmpty()) return
        selectedGenreFlow.value = label
        loadGenreDiscovery(label)
    }

    fun refreshDiscovery() {
        genreCache.remove(selectedGenreFlow.value)
        loadGenreDiscovery(selectedGenreFlow.value)
    }

    private fun loadGenreDiscovery(genreLabel: String) {
        genreCache[genreLabel]?.let { (cachedNew, cachedTop) ->
            newReleasesFlow.value = cachedNew
            topAlbumsFlow.value = cachedTop
            discoveryLoadingFlow.value = false
            return
        }

        discoveryJob?.cancel()
        discoveryJob = scope.launch(Dispatchers.IO) {
            discoveryLoadingFlow.value = true
            val year = LocalDate.now().year
            val prefix = if (genreLabel.equals("All", ignoreCase = true)) "" else "$genreLabel "

            val newReleasesDeferred = async {
                fetchAlbumsForQuery("${prefix}new album releases $year")
            }
            val topAlbumsDeferred = async {
                fetchAlbumsForQuery("${prefix}top albums")
            }

            val newRel = newReleasesDeferred.await()
            val topAlb = topAlbumsDeferred.await()

            if (newRel.isNotEmpty() || topAlb.isNotEmpty()) {
                genreCache[genreLabel] = newRel to topAlb
            }
            newReleasesFlow.value = newRel
            topAlbumsFlow.value = topAlb
            discoveryLoadingFlow.value = false
        }
    }

    private suspend fun fetchAlbumsForQuery(query: String): List<AlbumSummary> {
        return runCatching {
            val results = ytMusicApiClient.searchAll(query)
            val shelfAlbums = results.sections
                .filterIsInstance<SearchResultSection.Albums>()
                .flatMap { it.albums }
            val combined = buildList {
                results.topAlbum?.let { add(it) }
                addAll(shelfAlbums)
            }
            combined.distinctBy { it.id }
        }.getOrDefault(emptyList())
    }

    fun toggleStreamingMode() {
        scope.launch {
            val current = streamingPreference.current()
            streamingPreference.setEnabled(!current)
        }
    }

    fun playPlaylist(playlistId: Long) {
        scope.launch(Dispatchers.IO) {
            val tracks = musicRepository.getTracksByPlaylist(playlistId).first()
            if (tracks.isNotEmpty()) {
                playerRepository.setQueue(tracks, startIndex = 0)
            }
        }
    }

    fun playTrackList(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        scope.launch {
            playerRepository.setQueue(tracks, startIndex = startIndex.coerceIn(0, tracks.lastIndex))
        }
    }

    fun playQuickMix() {
        val state = uiState.value
        val pool = (state.mostPlayed + state.recentlyAdded).distinctBy { it.id }.shuffled()
        if (pool.isNotEmpty()) {
            playTrackList(pool, startIndex = 0)
        }
    }
}

