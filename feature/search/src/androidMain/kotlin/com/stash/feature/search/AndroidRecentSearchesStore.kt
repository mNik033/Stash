package com.stash.feature.search

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal val Context.recentSearchesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "recent_searches",
)

@Singleton
class AndroidRecentSearchesStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : RecentSearchesStore {
    private val key = stringPreferencesKey("queries")

    override val recent: Flow<List<RecentSearch>> = context.recentSearchesDataStore.data.map { prefs ->
        prefs[key].toEntries()
    }

    override suspend fun record(entry: RecentSearch) {
        val text = entry.text.clean()
        if (text.isEmpty()) return
        val sanitized = entry.copy(
            text = text,
            subtitle = entry.subtitle?.clean()?.takeIf { it.isNotEmpty() },
            thumbnailUrl = entry.thumbnailUrl?.clean()?.takeIf { it.isNotEmpty() },
            artistId = entry.artistId?.clean()?.takeIf { it.isNotEmpty() },
            videoId = entry.videoId?.clean()?.takeIf { it.isNotEmpty() },
        )
        context.recentSearchesDataStore.edit { prefs ->
            val deduped = prefs[key].toEntries()
                .filterNot { sanitized.supersedes(it) }
            prefs[key] = (listOf(sanitized) + deduped).take(RecentSearchesStore.MAX)
                .joinToString("\n") { it.encode() }
        }
    }

    override suspend fun remove(entry: RecentSearch) {
        context.recentSearchesDataStore.edit { prefs ->
            prefs[key] = prefs[key].toEntries()
                .filterNot { it.sameEntryAs(entry) }
                .joinToString("\n") { it.encode() }
        }
    }

    override suspend fun clear() {
        context.recentSearchesDataStore.edit { it.remove(key) }
    }

    private fun String.clean(): String = replace("\n", " ").replace(SEP, ' ').trim()

    private fun RecentSearch.encode(): String = listOf(
        type.name, text, subtitle.orEmpty(), thumbnailUrl.orEmpty(), artistId.orEmpty(),
        videoId.orEmpty(), durationSeconds?.toString().orEmpty(),
    ).joinToString(SEP.toString())

    private fun String?.toEntries(): List<RecentSearch> =
        this?.split("\n")?.filter { it.isNotBlank() }?.map { line ->
            val f = line.split(SEP)
            if (f.size < 2) {
                RecentSearch(RecentSearch.Type.QUERY, line.trim())
            } else {
                RecentSearch(
                    type = runCatching { RecentSearch.Type.valueOf(f[0]) }
                        .getOrDefault(RecentSearch.Type.QUERY),
                    text = f[1],
                    subtitle = f.getOrNull(2)?.takeIf { it.isNotEmpty() },
                    thumbnailUrl = f.getOrNull(3)?.takeIf { it.isNotEmpty() },
                    artistId = f.getOrNull(4)?.takeIf { it.isNotEmpty() },
                    videoId = f.getOrNull(5)?.takeIf { it.isNotEmpty() },
                    durationSeconds = f.getOrNull(6)?.toDoubleOrNull(),
                )
            }
        } ?: emptyList()

    companion object {
        private const val SEP = '\u001F'
    }
}
