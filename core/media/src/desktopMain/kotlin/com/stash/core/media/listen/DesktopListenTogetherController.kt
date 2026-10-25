package com.stash.core.media.listen

import com.stash.core.model.listen.ServerMessage
import com.stash.core.model.share.SharedTrack
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class DesktopListenTogetherController @Inject constructor() : ListenTogetherController {
    private val _active = MutableStateFlow(false)
    override val active: StateFlow<Boolean> = _active.asStateFlow()

    private val _sessionEnds = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val sessionEnds: SharedFlow<Unit> = _sessionEnds.asSharedFlow()

    private val _state = MutableStateFlow<ListenTogetherState>(ListenTogetherState.Idle)
    override val state: StateFlow<ListenTogetherState> = _state.asStateFlow()

    private val _reactions = MutableSharedFlow<ServerMessage.Reaction>(extraBufferCapacity = 16)
    override val reactions: SharedFlow<ServerMessage.Reaction> = _reactions.asSharedFlow()

    private val _events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 8)
    override val events: SharedFlow<SessionEvent> = _events.asSharedFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    override val messages: SharedFlow<String> = _messages.asSharedFlow()

    override fun send(command: ListenTogetherController.Command) {}
    override fun rejoin() {}
}
