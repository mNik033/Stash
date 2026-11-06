package com.stash.feature.settings

/** Where a search result lives; the hub opens that page. */
enum class SettingsPage { PLAYBACK, AUDIO, LOSSLESS_SOURCES, EQUALIZER, ACCOUNTS, LIBRARY, YOUR_STASH, APPEARANCE, HOME_LAYOUT, ABOUT }

/**
 * One searchable setting: its name as the screen shows it, the page it is on,
 * and the other words people use for it ("eq", "dark mode", "dac").
 */
data class SettingsSearchEntry(
    val title: String,
    val page: SettingsPage,
    val keywords: List<String> = emptyList(),
)

/**
 * Settings search. Every query word must match the start of a word in the
 * setting's name, its synonyms, or its page's name, so "dark theme", "bit
 * perfect" and "eq" all land. Settings whose name matches come first, then
 * the rest in index order (which follows the screens).
 */
fun searchSettings(
    query: String,
    index: List<SettingsSearchEntry>,
    pageTitle: (SettingsPage) -> String,
): List<SettingsSearchEntry> {
    val tokens = normalize(query).split(' ').filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return emptyList()
    return index
        .mapIndexedNotNull { i, entry ->
            val titleWords = words(entry.title)
            val all = titleWords + entry.keywords.flatMap(::words) + words(pageTitle(entry.page))
            if (!tokens.all { t -> all.any { it.startsWith(t) } }) return@mapIndexedNotNull null
            val rank = when {
                normalize(entry.title).startsWith(normalize(query)) -> 0
                tokens.all { t -> titleWords.any { it.startsWith(t) } } -> 1
                else -> 2
            }
            Triple(rank, i, entry)
        }
        .sortedWith(compareBy({ it.first }, { it.second }))
        .map { it.third }
}

/**
 * The index the hub searches: Your Stash's entries only when the app can open it ([yourStash]: the link is switched on in
 * this build), so a result never leads nowhere.
 */
fun searchableIndex(index: List<SettingsSearchEntry>, yourStash: Boolean): List<SettingsSearchEntry> =
    if (yourStash) index else index.filter { it.page != SettingsPage.YOUR_STASH }

private fun normalize(s: String): String =
    s.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

/**
 * "Wi-Fi" → [wi, fi, wifi]: each pair of neighbouring words joined too, so
 * "wifi" finds "Wi-Fi" and "lastfm" finds "Last.fm".
 */
private fun words(s: String): List<String> {
    val parts = normalize(s).split(' ').filter { it.isNotEmpty() }
    return parts + parts.zipWithNext { a, b -> a + b }
}
