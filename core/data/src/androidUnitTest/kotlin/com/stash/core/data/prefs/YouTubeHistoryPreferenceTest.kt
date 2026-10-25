package com.stash.core.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The opt-in instant is the YouTube history scrobbler's cutoff: only plays from
 * it onward are sent, so turning the feature on never sends the back catalogue.
 */
@RunWith(RobolectricTestRunner::class)
class YouTubeHistoryPreferenceTest {
    private lateinit var context: Context

    // The DataStore instance outlives a test, so deleting its file doesn't reset it: each test starts from an empty store.
    @Before fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        context.youtubeHistoryDataStore.edit { it.clear() }
    }

    @Test fun `no cutoff while off, so nothing is ever sent`() = runTest {
        val pref = YouTubeHistoryPreference(context)
        assertNull(pref.sendPlaysSinceMs(nowMs = 500))
        assertNull(pref.sendPlaysSince.first())
    }

    @Test fun `turning on stamps the instant, and turning on again does not move it`() = runTest {
        val pref = YouTubeHistoryPreference(context)
        pref.setEnabled(true, nowMs = 100)
        pref.setEnabled(true, nowMs = 200)

        assertEquals(100L, pref.sendPlaysSinceMs(nowMs = 300))
        assertEquals(100L, pref.sendPlaysSince.first())
    }

    @Test fun `turning off and on again starts from the new instant`() = runTest {
        val pref = YouTubeHistoryPreference(context)
        pref.setEnabled(true, nowMs = 100)
        pref.setEnabled(false, nowMs = 200)
        assertNull(pref.sendPlaysSinceMs(nowMs = 250))

        pref.setEnabled(true, nowMs = 300)
        assertEquals(300L, pref.sendPlaysSinceMs(nowMs = 400))
    }

    @Test fun `an install that turned it on before the cutoff existed gets one, once, not a backfill`() = runTest {
        // What an earlier version left behind: the bool, no instant.
        context.youtubeHistoryDataStore.edit { it[booleanPreferencesKey("enabled")] = true }
        val pref = YouTubeHistoryPreference(context)
        assertNull(pref.sendPlaysSince.first())

        assertEquals(1_000L, pref.sendPlaysSinceMs(nowMs = 1_000))
        assertEquals(1_000L, pref.sendPlaysSinceMs(nowMs = 2_000))
        assertEquals(1_000L, pref.sendPlaysSince.first())
    }
}
