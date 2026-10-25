package com.stash.feature.search

import com.google.common.truth.Truth.assertThat
import com.stash.feature.search.RecentSearch.Type
import org.junit.Test

/**
 * The recents identity rules ([RecentSearch.sameEntryAs], [RecentSearch.supersedes]),
 * on plain JVM. [RecentSearchesStoreTest] covers them through the DataStore.
 */
class RecentSearchDedupeTest {

    private fun query(text: String) = RecentSearch(Type.QUERY, text)
    private fun track(title: String, artist: String) = RecentSearch(Type.TRACK, title, subtitle = artist)
    private fun artist(name: String) = RecentSearch(Type.ARTIST, name, artistId = "UC-$name")

    @Test fun `the same query in another case is the same entry`() {
        assertThat(query("BEATLES").supersedes(query("beatles"))).isTrue()
    }

    @Test fun `a song does not evict a broader query that shares its title`() {
        assertThat(track("Yellow", "Coldplay").supersedes(query("yellow"))).isFalse()
    }

    @Test fun `a song absorbs the query it re-runs`() {
        assertThat(track("Yellow", "Coldplay").supersedes(query("coldplay yellow"))).isTrue()
    }

    @Test fun `an artist absorbs the plain query of its name`() {
        // The designed merge: "lil wayne" then the artist → one row, the artist.
        assertThat(artist("Lil Wayne").supersedes(query("lil wayne"))).isTrue()
    }

    @Test fun `a query does not evict a richer entry`() {
        assertThat(query("lil wayne").supersedes(artist("Lil Wayne"))).isFalse()
    }

    @Test fun `an artist and a track with the same name are different entries`() {
        assertThat(artist("Intro").supersedes(track("Intro", "The xx"))).isFalse()
        assertThat(track("Intro", "The xx").supersedes(artist("Intro"))).isFalse()
    }

    @Test fun `the same title by two artists is two entries`() {
        assertThat(track("Intro", "M83").sameEntryAs(track("Intro", "The xx"))).isFalse()
    }

    @Test fun `the same track again is the same entry, whatever its art`() {
        val old = track("Intro", "The xx").copy(thumbnailUrl = "https://img/old.jpg")
        val new = track("intro", "THE XX").copy(thumbnailUrl = "https://img/new.jpg")
        assertThat(new.supersedes(old)).isTrue()
    }

    @Test fun `a missing subtitle matches a legacy empty one`() {
        // Legacy rows decode with a null subtitle; new queries have none either.
        assertThat(query("beatles").sameEntryAs(RecentSearch(Type.QUERY, "beatles", subtitle = ""))).isTrue()
    }
}
