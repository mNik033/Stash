package com.stash.core.media.cast

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** A linked browser as "Play on" lists it (Remote v2, spec 2026-11-03 §3). */
data class BrowserOutput(
    val device: String,
    /** "Chrome on Windows". */
    val name: String,
    val kind: Kind,
    /** What it plays, for "Playing Pink + White". */
    val title: String?,
) {
    enum class Kind {
        /** Its tab is open and it can play this phone's music. */
        READY,

        /** It plays its own music now ([title]); picking it plays this phone's instead (owner's decision D5). */
        PLAYING,

        /** Its tab was never clicked: it will hold the song until it is ("Click the Stash tab once first"). */
        NEEDS_CLICK,

        /** An older tab without Remote v2 ("Reload the Stash tab to play there"): it can't be picked. */
        RELOAD,

        /** Linked, but no tab is open ("Open play.stashfm.app to play there"): it can't be picked. */
        NOT_OPEN,
    }

    val canPick: Boolean get() = kind != Kind.RELOAD && kind != Kind.NOT_OPEN
}

/** The browser this phone's queue plays on, and how that stands. */
data class BrowserSession(val device: String, val name: String, val link: Link) {
    /** How the session stands, for "Play on", Now Playing and the mini player. */
    enum class Link {
        /** The browser plays (or holds) this phone's songs. */
        READY,

        /** The first song went out and the browser hasn't said it has it yet: "Starting on Chrome on Windows…". */
        STARTING,

        /** The browser waits for a click in its tab before it can start the music. */
        NEEDS_CLICK,

        /** The browser, or this phone's connection, is gone for now: "Reconnecting to Chrome…". */
        RECONNECTING,
    }

    /** "Chrome on Windows" → "Chrome", for "Reconnecting to Chrome…" and "Click the Stash tab once in Chrome". */
    val app: String get() = name.substringBefore(" on ").trim().ifEmpty { name }
}

/** One line for the user when a browser session ends by itself, or skips songs (a snackbar). */
sealed interface BrowserNotice {
    /** The browser closed: the music is back on this phone, paused. Offers Play. */
    data class Closed(val name: String) : BrowserNotice

    /** This phone's connection stayed down: the music is back on this phone, paused. Offers Play. */
    data class Lost(val name: String) : BrowserNotice

    /** Someone at the browser picked their own music. */
    data class OwnMusic(val name: String) : BrowserNotice

    /** The browser never said it had the song: the music stayed on this phone. */
    data class NoAnswer(val name: String) : BrowserNotice

    /** The browser was unlinked (from this phone or from it): the music is back on this phone, paused. Offers Play. */
    data class Unlinked(val name: String) : BrowserNotice

    /** Songs only this phone has were stepped over (once a session). */
    data class Skipped(val count: Int) : BrowserNotice
}

/**
 * The linked browsers this phone can play its queue on (Remote v2, spec 2026-11-03 §2.2): the "Play on" sheet lists [browsers],
 * [connect] picks one, and the playback service plays through [remote] while it is set, exactly as through a Chromecast's
 * ([CastDevices.remote]).
 */
interface BrowserOutputs {
    var sourceLabel: () -> String?
    val remote: StateFlow<CastRemote?>
    val session: StateFlow<BrowserSession?>
    val notices: SharedFlow<BrowserNotice>
    val served: StateFlow<Boolean>
    val hasBrowser: StateFlow<Boolean>
    val browsers: StateFlow<List<BrowserOutput>>
    val phoneOnly: StateFlow<Set<Long>>

    fun connect(device: String): Boolean
    fun disconnect()

    object None : BrowserOutputs {
        override var sourceLabel: () -> String? = { null }
        override val remote: StateFlow<CastRemote?> = MutableStateFlow<CastRemote?>(null).asStateFlow()
        override val session: StateFlow<BrowserSession?> = MutableStateFlow<BrowserSession?>(null).asStateFlow()
        override val notices: SharedFlow<BrowserNotice> = MutableSharedFlow<BrowserNotice>().asSharedFlow()
        override val served: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
        override val hasBrowser: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
        override val browsers: StateFlow<List<BrowserOutput>> = MutableStateFlow(emptyList<BrowserOutput>()).asStateFlow()
        override val phoneOnly: StateFlow<Set<Long>> = MutableStateFlow(emptySet<Long>()).asStateFlow()
        override fun connect(device: String): Boolean = false
        override fun disconnect() {}
    }
}

