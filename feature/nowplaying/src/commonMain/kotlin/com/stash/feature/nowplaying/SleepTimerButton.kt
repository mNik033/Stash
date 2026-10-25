package com.stash.feature.nowplaying

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.stash.core.media.SleepTimerController
import kotlinx.coroutines.delay

/** Space between the moon glyph and the caption under it. */
private val CaptionGap = 1.dp

/**
 * Milliseconds left on a running countdown [state] (null for any other state), kept current while
 * the screen is started. It wakes only when the readout changes ([msUntilSleepReadoutChanges]):
 * at each minute boundary, then each second in the final minute, or each second throughout with
 * [everySecond] (the sheet's clock). Nothing ticks in the background: the loop stops when the app
 * leaves the screen and catches up at once when it returns. Reads the wall clock, the one
 * [SleepTimerController.State.Countdown.endsAtMs] is set on (the controller re-reads the same clock
 * to fire, so the readout and the pause agree).
 */
@Composable
internal fun rememberSleepTimerRemainingMs(state: SleepTimerController.State, everySecond: Boolean = false): Long? {
    val countdown = state as? SleepTimerController.State.Countdown
    var nowMs by remember(countdown) { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(countdown, everySecond, lifecycle) {
        if (countdown == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                nowMs = System.currentTimeMillis()
                val wait = msUntilSleepReadoutChanges(countdown.endsAtMs - nowMs, everySecond) ?: break
                delay(wait)
            }
        }
    }
    return countdown?.let { (it.endsAtMs - nowMs).coerceAtLeast(0L) }
}

/**
 * The sleep timer's moon button with its live caption underneath while a timer is armed: "23m",
 * "1h 5m", "0:45" in the final minute, or "End" when it pauses at the end of the track. The
 * caption hangs below the glyph without moving it (the button keeps its size and place whether a
 * timer runs or not) and is hidden from TalkBack, which hears the button's own description instead:
 * "Sleep timer: 23 minutes left", "Sleep timer: pauses at the end of this track", or "Set sleep
 * timer".
 *
 * @param buttonSize The button's touch slot as laid out (26dp beside the title, 48dp in lyrics
 *   mode's header); [iconSize] the glyph inside it, centred, which the caption sits under.
 */
@Composable
internal fun SleepTimerButton(
    state: SleepTimerController.State,
    onClick: () -> Unit,
    activeTint: Color,
    idleTint: Color,
    buttonSize: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    val remaining = rememberSleepTimerRemainingMs(state)
    val caption = when (state) {
        is SleepTimerController.State.Countdown -> sleepTimerBadge(remaining ?: 0L)
        SleepTimerController.State.EndOfTrack -> "End"
        SleepTimerController.State.Off -> null
    }
    val description = when (state) {
        is SleepTimerController.State.Countdown -> "Sleep timer: ${sleepTimerSpoken(remaining ?: 0L)} left"
        SleepTimerController.State.EndOfTrack -> "Sleep timer: pauses at the end of this track"
        SleepTimerController.State.Off -> "Set sleep timer"
    }
    Layout(
        content = {
            IconButton(onClick = onClick, modifier = Modifier.size(buttonSize)) {
                Icon(
                    imageVector = Icons.Default.Bedtime,
                    contentDescription = description,
                    // Accented while a timer is armed, so the state shows at a glance.
                    tint = if (caption != null) activeTint else idleTint,
                    modifier = Modifier.size(iconSize),
                )
            }
            if (caption != null) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = activeTint,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val button = measurables[0].measure(constraints.copy(minWidth = 0, minHeight = 0))
        // Unconstrained: "1h 5m" is wider than a 26dp button and may hang past its sides.
        val label = measurables.getOrNull(1)?.measure(Constraints())
        val labelTop = ((buttonSize + iconSize) / 2 + CaptionGap).roundToPx()
        // The button's own size: the caption hangs below it rather than pushing it up.
        layout(button.width, button.height) {
            button.place(0, 0)
            label?.place((button.width - label.width) / 2, labelTop)
        }
    }
}
