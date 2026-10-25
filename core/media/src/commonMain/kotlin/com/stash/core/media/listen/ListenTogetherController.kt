package com.stash.core.media.listen

import com.stash.core.model.listen.RoomMember
import com.stash.core.model.listen.RoomSuggestion
import com.stash.core.model.listen.ServerMessage
import com.stash.core.model.share.SharedTrack
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

sealed interface ListenTogetherState {
    data object Idle : ListenTogetherState
    data class Connecting(val hosting: Boolean) : ListenTogetherState
    data class InRoom(
        val code: String,
        /** The invite link, `https://…/l/{code}`. */
        val url: String,
        val myId: String,
        val isHost: Boolean,
        val hostId: String?,
        val members: List<RoomMember>,
        val suggestions: List<RoomSuggestion>,
        /** "Reconnecting…": playback carries on from the last timeline meanwhile (spec §6). */
        val reconnecting: Boolean = false,
        /** "This song isn't available to you". */
        val unavailable: Boolean = false,
        /** "Your version may be a few seconds off": duration differs from the host's by over 2 s (spec §4). */
        val versionMismatch: Boolean = false,
        /** Paused on this phone by an unplug or a call while the room plays on: "Paused — tap to rejoin". */
        val pausedLocally: Boolean = false,
        /** The room's current song; [SharedTrack.addedBy] says whose pick it is. */
        val track: SharedTrack? = null,
        /** What plays next in the room, each song with who added it. */
        val queue: List<SharedTrack> = emptyList(),
        /** Everyone seen this session, id → name, kept after they leave so "Maya's pick" still says Maya. */
        val names: Map<String, String?> = emptyMap(),
    ) : ListenTogetherState
}

/** Something a person did in the room, for a short named notice: "Maya joined", "Rawn skipped". */
data class SessionEvent(val kind: Kind, val memberId: String, val name: String?) {
    /** HOSTING: [memberId] is the new host (maybe this phone: "You're hosting now"). */
    enum class Kind { JOINED, LEFT, SKIPPED, WENT_BACK, PAUSED, RESUMED, HOSTING }
}

interface ListenTogetherController {
    sealed interface Command {
        data object Host : Command
        data class Join(val code: String) : Command
        /** A listener leaves; a host hands the room to the longest-joined listener, then leaves. */
        data object Leave : Command
        /** Host only: ends the room for everyone. */
        data object End : Command
        data class React(val emoji: String) : Command
        data class Suggestion(val id: String, val add: Boolean) : Command
        data class MakeHost(val memberId: String) : Command
        /** Host only: Up next after the host removed or moved songs. Replaces the room's queue. */
        data class SetQueue(val queue: List<SharedTrack>) : Command
        /** Catch up with the room after a local pause (see [ListenTogetherState.InRoom.pausedLocally]). */
        data object Rejoin : Command
    }

    val active: StateFlow<Boolean>
    val sessionEnds: SharedFlow<Unit>
    val state: StateFlow<ListenTogetherState>
    val reactions: SharedFlow<ServerMessage.Reaction>
    val events: SharedFlow<SessionEvent>
    val messages: SharedFlow<String>

    var restorePending: Boolean
        get() = false
        set(value) {}

    fun send(command: Command)
    fun rejoin()
}
