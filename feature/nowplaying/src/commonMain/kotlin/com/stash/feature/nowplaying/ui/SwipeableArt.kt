package com.stash.feature.nowplaying.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** How long the "−10 s" / "+10 s" indicator stays after the last double-tap. */
private const val SEEK_INDICATOR_HOLD_MS = 650L

/**
 * The Now Playing album art with its gestures: swipe sideways for the next / previous song, and
 * double-tap the left or right third to seek back / forward 10 s.
 *
 * The swipe is [rememberSwipeToSkip] (shared with the mini player): the art follows the finger,
 * skips on a third of its width or a flick through the same [onNext] / [onPrevious] the transport
 * buttons call, and springs back short of that or toward a side with nothing there ([canNext] /
 * [canPrevious]). Double-taps in quick succession add up ("+20 s"), each from the previous tap's
 * target ([nextSeekBurst]).
 *
 * The art has no single-tap action, so the double-tap detector delays nothing. Every gesture has a
 * TalkBack custom action on the art (Previous song, Next song, Back / Forward 10 seconds), merged
 * with the cover's "Album art" into one node. With the system's animations removed nothing slides
 * or fades: the skip simply happens, and the seek pill appears and goes without a fade.
 *
 * @param trackKey Identifies the playing song; a change ends the post-skip wait.
 * @param gesturesEnabled False for a Listen Together listener, who can neither skip nor seek the
 *   room's song: no gestures and no actions.
 * @param canSeek Whether double-tap seeking applies (a known duration).
 * @param positionMs The player's position, read only when a double-tap or an action lands.
 * @param modifier The cover's shared-element modifier (it morphs into lyrics mode's header cover).
 * @param art The cover itself, drawn with the [Modifier] it is handed (the swipe's offset).
 */
@Composable
internal fun SwipeableArt(
    trackKey: Any?,
    gesturesEnabled: Boolean,
    canNext: Boolean,
    canPrevious: Boolean,
    canSeek: Boolean,
    positionMs: () -> Long,
    durationMs: Long,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    art: @Composable (Modifier) -> Unit,
) {
    val reducedMotion = rememberNpReducedMotion()
    val swipe = rememberSwipeToSkip(
        trackKey = trackKey,
        enabled = gesturesEnabled,
        canNext = canNext,
        canPrevious = canPrevious,
        onNext = onNext,
        onPrevious = onPrevious,
    )

    // The double-tap handler outlives recompositions (pointerInput keeps its lambda), so what it
    // reads that can change goes through updated state.
    val currentCanSeek by rememberUpdatedState(canSeek)
    val currentPositionMs by rememberUpdatedState(positionMs)
    val currentDurationMs by rememberUpdatedState(durationMs)
    val currentOnSeekTo by rememberUpdatedState(onSeekTo)

    // The double-tap run on screen ("+20 s"); kept after it hides so the fade-out keeps its text.
    var burst by remember { mutableStateOf<SeekBurst?>(null) }
    var burstVisible by remember { mutableStateOf(false) }
    LaunchedEffect(burst) {
        if (burst != null) {
            delay(SEEK_INDICATOR_HOLD_MS)
            burstVisible = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(swipe.gesture)
            .then(
                if (!gesturesEnabled) {
                    Modifier
                } else {
                    Modifier
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = { offset ->
                                    val direction = doubleTapSeekDirection(offset.x, size.width.toFloat())
                                    if (currentCanSeek && direction != 0) {
                                        val next = nextSeekBurst(
                                            previous = burst,
                                            direction = direction,
                                            nowMs = SystemClock.uptimeMillis(),
                                            positionMs = currentPositionMs(),
                                            durationMs = currentDurationMs,
                                        )
                                        currentOnSeekTo(next.targetMs)
                                        burst = next
                                        burstVisible = true
                                    }
                                },
                            )
                        }
                },
            )
            // One node for TalkBack: the cover's "Album art" with every gesture as an action.
            .semantics(mergeDescendants = true) {
                if (gesturesEnabled) {
                    customActions = buildList {
                        if (canPrevious) add(CustomAccessibilityAction("Previous song") { onPrevious(); true })
                        if (canNext) add(CustomAccessibilityAction("Next song") { onNext(); true })
                        if (canSeek) {
                            add(
                                CustomAccessibilityAction("Back 10 seconds") {
                                    onSeekTo(seekStepMs(positionMs(), durationMs, -SEEK_STEP_MS)); true
                                },
                            )
                            add(
                                CustomAccessibilityAction("Forward 10 seconds") {
                                    onSeekTo(seekStepMs(positionMs(), durationMs, SEEK_STEP_MS)); true
                                },
                            )
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        art(swipe.content)

        val shown = burst
        if (shown != null) {
            AnimatedVisibility(
                visible = burstVisible,
                enter = if (reducedMotion) EnterTransition.None else fadeIn(tween(120)),
                exit = if (reducedMotion) ExitTransition.None else fadeOut(tween(220)),
                modifier = Modifier
                    .align(if (shown.direction < 0) Alignment.CenterStart else Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(1f / 3f)
                    // Visual feedback for a touch gesture; TalkBack's own seek actions say what they did.
                    .clearAndSetSemantics { },
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = seekBurstLabel(shown),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        // On the cover, not the ambient: a dark pill with white text reads on any art
                        // in either theme.
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}
