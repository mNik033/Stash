package com.stash.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputEventHandler
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import com.stash.core.ui.theme.StashTheme
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Shared snapshot written by the draw layer, read by the drag-handle layer. */
private class ScrollbarTrackInfo {
    var progress by mutableStateOf(0f)
    var thumbFraction by mutableStateOf(1f)
}

/** Fader-cap palette (and the A–Z bubble's), resolved from the active theme's own chrome tokens. */
private class FaderColors(
    val body: Color,
    val border: Color,
    val groove: Color,
    val bubble: Color,
    val onBubble: Color,
)

/**
 * The cap dresses like the rest of the app's chrome — chip surface body,
 * glass hairline edge, one plum groove — rather than importing its own
 * palette. surfaceVariant/primary/glassBorderBright all resolve per theme,
 * so light, dark, and AMOLED each get their native rendering for free.
 */
@Composable
private fun faderColors(): FaderColors = FaderColors(
    body = MaterialTheme.colorScheme.surfaceVariant,
    border = StashTheme.extendedColors.glassBorderBright,
    groove = MaterialTheme.colorScheme.primary,
    bubble = MaterialTheme.colorScheme.primary,
    onBubble = MaterialTheme.colorScheme.onPrimary,
)

/** The thumb never shrinks below this — a scrubbable cap, not a sliver. */
private val MIN_THUMB_HEIGHT = 48.dp

/** Extra vertical slack around the cap that still counts as grabbing it. */
private val GRAB_TOLERANCE = 32.dp

/** The A–Z bubble: big enough to read past the thumb that's dragging the cap. */
private val BUBBLE_SIZE = 64.dp

/** Gap between the bubble's tip and the fader cap. */
private val BUBBLE_GAP = 12.dp

/** The bubble's fade in and out: quicker than the cap's, it follows the finger. */
private const val BUBBLE_FADE_MS = 150

/** Clamp the raw viewport fraction so the drawn cap keeps its minimum size. */
private fun DrawScope.clampedThumbFraction(rawFraction: Float): Float =
    maxOf(rawFraction, MIN_THUMB_HEIGHT.toPx() / size.height.coerceAtLeast(1f))
        .coerceAtMost(1f)

/**
 * Average row height excluding item 0 — every wired list/grid leads with one
 * oversized full-width header item, and averaging it in makes the position
 * estimate lurch whenever the header scrolls in or out of the viewport.
 */
private fun avgSizeSkippingHeader(items: List<Pair<Int, Int>>): Float {
    val body = items.filter { it.first != 0 }.ifEmpty { items }
    return body.sumOf { it.second }.toFloat() / body.size
}

/**
 * Draws the fader cap: a near-square chip-surface block with a glass
 * hairline edge and a single plum groove across the middle — the classic
 * mixer fader cap, in the app's own chrome.
 */
private fun DrawScope.drawFaderCap(
    info: ScrollbarTrackInfo,
    colors: FaderColors,
    alpha: Float,
    capWidthPx: Float,
    thumbHeightOffset: Float,
) {
    if (alpha <= 0.001f) return
    val trackHeight = size.height
    val thumbHeight = (trackHeight * info.thumbFraction) + thumbHeightOffset
    val thumbOffsetY = info.progress * (trackHeight - thumbHeight)
    val left = size.width - capWidthPx - 3.dp.toPx()
    val corner = CornerRadius(3.dp.toPx(), 3.dp.toPx())

    // Cap body — chip surface, faintly translucent so it sits IN the page.
    drawRoundRect(
        color = colors.body.copy(alpha = 0.95f * alpha),
        topLeft = Offset(left, thumbOffsetY),
        size = Size(capWidthPx, thumbHeight),
        cornerRadius = corner,
    )
    // Glass hairline edge (token carries its own low alpha).
    drawRoundRect(
        color = colors.border.copy(alpha = colors.border.alpha * alpha),
        topLeft = Offset(left, thumbOffsetY),
        size = Size(capWidthPx, thumbHeight),
        cornerRadius = corner,
        style = Stroke(width = 1.dp.toPx()),
    )
    // Single center groove — the fader-cap signature.
    val grooveWidth = capWidthPx * 0.6f
    val gx = left + (capWidthPx - grooveWidth) / 2f
    val cy = thumbOffsetY + thumbHeight / 2f
    drawLine(
        color = colors.groove.copy(alpha = 0.9f * alpha),
        start = Offset(gx, cy),
        end = Offset(gx + grooveWidth, cy),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round,
    )
}

/**
 * Draws the A–Z bubble beside the cap while it's dragged: the Contacts-style teardrop, three
 * round corners and one sharp one whose tip sits on the cap's centre line, pointing at it, with
 * the section [letter] centred inside. Pinned inside the track at either end.
 *
 * Drawn in the same pass as the cap so the two can never be a frame apart. It carries no
 * semantics on purpose: it only echoes, for the eye, the first letter of the rows the drag
 * is passing, and TalkBack reads those rows themselves.
 */
private fun DrawScope.drawSectionBubble(
    info: ScrollbarTrackInfo,
    colors: FaderColors,
    alpha: Float,
    capWidthPx: Float,
    thumbHeightOffset: Float,
    letter: TextLayoutResult,
) {
    if (alpha <= 0.001f) return
    val trackHeight = size.height
    val thumbHeight = (trackHeight * info.thumbFraction) + thumbHeightOffset
    val thumbCenterY = info.progress * (trackHeight - thumbHeight) + thumbHeight / 2f
    val bubble = BUBBLE_SIZE.toPx()
    val right = size.width - capWidthPx - 3.dp.toPx() - BUBBLE_GAP.toPx()
    val left = right - bubble
    val top = (thumbCenterY - bubble).coerceIn(0f, (trackHeight - bubble).coerceAtLeast(0f))
    val round = CornerRadius(bubble / 2f, bubble / 2f)
    val tip = CornerRadius(bubble / 10f, bubble / 10f)
    val teardrop = Path().apply {
        addRoundRect(
            RoundRect(
                left = left,
                top = top,
                right = right,
                bottom = top + bubble,
                topLeftCornerRadius = round,
                topRightCornerRadius = round,
                bottomRightCornerRadius = tip,
                bottomLeftCornerRadius = round,
            ),
        )
    }
    drawPath(teardrop, color = colors.bubble.copy(alpha = colors.bubble.alpha * alpha))
    drawText(
        letter,
        color = colors.onBubble,
        topLeft = Offset(
            x = left + (bubble - letter.size.width) / 2f,
            y = top + (bubble - letter.size.height) / 2f,
        ),
        alpha = alpha,
    )
}

/**
 * `Modifier.pointerInput` that SHARES hit-testing with occluded siblings.
 *
 * A plain pointerInput node is an opaque touch wall for its whole bounds:
 * Compose dispatches a pointer only to the topmost hit path, so "leaving the
 * event unconsumed" does NOT let it reach a sibling drawn underneath — the
 * sibling never receives it at all. The full-height grab strip was therefore
 * swallowing every tap on the right edge of every scrollbar-equipped list
 * (dead ⋮ track menus, #288 regression). Overriding
 * [PointerInputModifierNode.sharePointerInputWithSiblings] restores the
 * contract the scrubber always assumed: off-thumb touches fall through to
 * the row below; on-thumb touches are consumed, which the row's clickable
 * correctly ignores.
 *
 * Like `pointerInput(key)`, the handler is reset only when [key] changes —
 * lambda identity is deliberately ignored so recomposition doesn't cancel
 * an in-flight scrub.
 */
private class SharedPointerInputElement(
    private val key: Any?,
    private val block: PointerInputEventHandler,
) : ModifierNodeElement<SharedPointerInputNode>() {
    override fun create() = SharedPointerInputNode(key, block)
    override fun update(node: SharedPointerInputNode) = node.update(key, block)
    override fun equals(other: Any?) =
        other is SharedPointerInputElement && other.key == key
    override fun hashCode() = key?.hashCode() ?: 0
}

private class SharedPointerInputNode(
    private var key: Any?,
    block: PointerInputEventHandler,
) : DelegatingNode(), PointerInputModifierNode {
    private val inner = delegate(SuspendingPointerInputModifierNode(block))

    fun update(key: Any?, block: PointerInputEventHandler) {
        if (this.key != key) {
            this.key = key
            inner.pointerInputEventHandler = block
        }
    }

    override fun sharePointerInputWithSiblings() = true

    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) =
        inner.onPointerEvent(pointerEvent, pass, bounds)

    override fun onCancelPointerInput() = inner.onCancelPointerInput()
}

/**
 * Shared scrub gesture: claims (and scrubs) ONLY when the touch lands on the
 * VISIBLE cap. Off-thumb or faded-out touches are left unconsumed and —
 * because the strip shares pointer input with siblings (see
 * [SharedPointerInputElement]) — genuinely fall through to the list rows and
 * their ⋮ buttons underneath.
 */
private fun Modifier.faderScrubber(
    stateKey: Any,
    info: ScrollbarTrackInfo,
    animatedAlpha: () -> Float,
    thumbHeightOffset: Float,
    onWake: () -> Unit,
    onScrub: (Float) -> Unit,
    onRelease: () -> Unit,
): Modifier = this.then(SharedPointerInputElement(stateKey) {
    val grabTolerance = GRAB_TOLERANCE.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val trackHeight = size.height.toFloat()
        val thumbHeight = (trackHeight * info.thumbFraction) + thumbHeightOffset
        val thumbOffsetY = info.progress * (trackHeight - thumbHeight)
        val onThumb = animatedAlpha() > 0.01f && down.position.y in
            (thumbOffsetY - grabTolerance)..(thumbOffsetY + thumbHeight + grabTolerance)
        if (!onThumb) return@awaitEachGesture
        down.consume()
        onWake()
        var dragProgress = info.progress
        val maxOffset = trackHeight - thumbHeight
        // finally: a lifted finger, a cancelled gesture (another node took the pointer) and
        // a reset handler all end the scrub, so the A–Z bubble never sticks on screen.
        try {
            verticalDrag(down.id) { change ->
                // Read the delta BEFORE consuming — a consumed change reports a
                // zero positionChange, which would freeze the scrub in place.
                val deltaY = change.positionChange().y
                change.consume()
                if (maxOffset > 0f) {
                    dragProgress = (dragProgress + deltaY / maxOffset).coerceIn(0f, 1f)
                    onScrub(dragProgress)
                }
            }
        } finally {
            onRelease()
        }
    }
})

/**
 * Excludes ONLY the thumb's current rect (plus grab slack) from the system
 * back-gesture band. Android silently caps exclusion at 200dp per edge, so
 * excluding the whole strip is quietly ignored and the system keeps stealing
 * edge touches — a thumb-sized rect always fits the budget and follows the
 * cap wherever it sits.
 */
internal expect fun Modifier.thumbGestureExclusion(
    thumbFraction: Float,
    progress: Float,
    thumbHeightOffset: Float,
    grabTolerancePx: Float,
): Modifier

/**
 * Where the A–Z bubble's drag is: whether the cap is held, and the lazy item the scrub has
 * reached. Remembered once per scrollbar, because the gesture handler that writes it is
 * created once (see [SharedPointerInputElement]) and must keep writing the same object.
 */
private class SectionBubbleDrag {
    var dragging by mutableStateOf(false)
        private set

    /** Lazy item index at the top of the scrub target: where the drag has taken the list. */
    var scrubIndex by mutableIntStateOf(0)

    /** The cap was grabbed with [firstVisibleIndex] at the top: show its letter until it moves. */
    fun grab(firstVisibleIndex: Int) {
        scrubIndex = firstVisibleIndex
        dragging = true
    }

    fun release() {
        dragging = false
    }
}

/**
 * What the draw phase needs to paint the bubble. Rebuilt every composition (it only holds
 * references), so a new [label] (the list changed) reaches the next frame with no extra state.
 */
private class SectionBubble(
    private val drag: SectionBubbleDrag,
    private val label: ((index: Int) -> String?)?,
    private val alpha: State<Float>,
    private val measurer: TextMeasurer,
    private val style: TextStyle,
) {
    /** Paints the bubble when there's one to show. Reads the fade and the scrub index here, in draw. */
    fun draw(scope: DrawScope, info: ScrollbarTrackInfo, colors: FaderColors, capWidthPx: Float, thumbHeightOffset: Float) {
        val a = alpha.value
        if (a <= 0.001f) return
        val letter = label?.invoke(drag.scrubIndex) ?: return
        val layout = measurer.measure(text = letter, style = style, maxLines = 1)
        scope.drawSectionBubble(info, colors, a, capWidthPx, thumbHeightOffset, layout)
    }
}

/** The bubble for this composition: fades in while [drag] holds the cap on a list with a [sectionLabel]. */
@Composable
private fun sectionBubble(drag: SectionBubbleDrag, sectionLabel: ((index: Int) -> String?)?): SectionBubble {
    val alpha = animateFloatAsState(
        targetValue = if (drag.dragging && sectionLabel != null) 1f else 0f,
        animationSpec = tween(BUBBLE_FADE_MS),
        label = "scrollbarBubbleAlpha",
    )
    return SectionBubble(
        drag = drag,
        label = sectionLabel,
        alpha = alpha,
        measurer = rememberTextMeasurer(),
        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

/**
 * The fader scrollbar for a lazy list (see the grid overload for a lazy grid).
 *
 * @param sectionLabel For a list sorted A–Z: the section letter of lazy item `index`
 *   (usually [fastScrollSectionLetter] of the item's sort key), or null for an item with none
 *   (a footer). While the cap is dragged, a big bubble beside it shows the letter of the row
 *   the drag has reached, so "jump to the Ms" is one drag rather than a guess. Pass null (the
 *   default) for any other order: a "D" bubble over a most-played list would mean nothing.
 */
@Composable
fun BoxScope.VerticalScrollbar(
    state: LazyListState,
    width: Dp = 12.dp,
    idleDelayMs: Long = 1500L,
    thumbHeightOffset: Float = 0f,
    hitZoneWidth: Dp = 24.dp,
    sectionLabel: ((index: Int) -> String?)? = null,
) {
    val info = remember { ScrollbarTrackInfo() }
    val colors = faderColors()
    val grabTolerancePx = with(LocalDensity.current) { GRAB_TOLERANCE.toPx() }
    var alpha by remember { mutableStateOf(0f) }
    val animatedAlpha by animateFloatAsState(alpha, tween(200), label = "scrollbarAlpha")
    val scope = rememberCoroutineScope()
    val bubbleDrag = remember { SectionBubbleDrag() }
    val bubble = sectionBubble(bubbleDrag, sectionLabel)

    // A cap held still under the finger (dragging, but no scroll this instant) stays lit:
    // it used to fade out mid-scrub after idleDelayMs, leaving the bubble pointing at nothing.
    LaunchedEffect(state.isScrollInProgress, bubbleDrag.dragging) {
        if (state.isScrollInProgress || bubbleDrag.dragging) alpha = 1f else { delay(idleDelayMs); alpha = 0f }
    }

    // Draw-only layer — fillMaxSize but NO pointerInput, so it never blocks touches.
    Box(
        modifier = Modifier.fillMaxSize().drawWithContent {
            drawContent()
            val layout = state.layoutInfo
            val totalItems = layout.totalItemsCount
            if (totalItems == 0 || layout.visibleItemsInfo.isEmpty()) return@drawWithContent
            val firstVisible = layout.visibleItemsInfo.first()
            val avgItemSize = avgSizeSkippingHeader(layout.visibleItemsInfo.map { it.index to it.size })
            val estimatedTotalHeight = avgItemSize * totalItems
            if (estimatedTotalHeight <= layout.viewportEndOffset) return@drawWithContent

            val scrolledPx = firstVisible.index * avgItemSize - firstVisible.offset
            val maxScrollPx = estimatedTotalHeight - layout.viewportEndOffset
            info.thumbFraction = clampedThumbFraction(layout.viewportEndOffset / estimatedTotalHeight)
            info.progress = (scrolledPx / maxScrollPx).coerceIn(0f, 1f)
            drawFaderCap(info, colors, animatedAlpha, width.toPx(), thumbHeightOffset)
            bubble.draw(this, info, colors, width.toPx(), thumbHeightOffset)
        },
    )

    // Narrow grab strip — ONLY this slice is touchable, everything left of it
    // passes straight through to the list underneath untouched.
    // systemGestureExclusion: the strip lives on the screen's right edge,
    // which Android's back-gesture nav owns — without the exclusion the
    // system intercepts edge drags and the cap is randomly ungrabbable.
    Box(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(hitZoneWidth)
            .thumbGestureExclusion(info.thumbFraction, info.progress, thumbHeightOffset, grabTolerancePx)
            .faderScrubber(
                stateKey = state,
                info = info,
                animatedAlpha = { animatedAlpha },
                thumbHeightOffset = thumbHeightOffset,
                onWake = {
                    alpha = 1f
                    bubbleDrag.grab(state.firstVisibleItemIndex)
                },
                onRelease = bubbleDrag::release,
                onScrub = { progress ->
                    // Pixel-granular target: index + intra-item offset. Snapping
                    // to whole items reads as comically jumpy on tall rows.
                    val layout = state.layoutInfo
                    val total = layout.totalItemsCount
                    if (total > 0 && layout.visibleItemsInfo.isNotEmpty()) {
                        val avgItemSize =
                            avgSizeSkippingHeader(layout.visibleItemsInfo.map { it.index to it.size })
                        val maxScrollPx =
                            (avgItemSize * total - layout.viewportEndOffset).coerceAtLeast(0f)
                        val targetPx = progress * maxScrollPx
                        val index = (targetPx / avgItemSize).toInt().coerceIn(0, total - 1)
                        val offsetPx = (targetPx - index * avgItemSize).roundToInt()
                        bubbleDrag.scrubIndex = index
                        scope.launch { state.scrollToItem(index, offsetPx) }
                    }
                },
            ),
    )
}

/**
 * The fader scrollbar for a lazy grid. [sectionLabel] is as for the list overload; the index
 * it gets is the first item of the row the drag has reached.
 */
@Composable
fun BoxScope.VerticalScrollbar(
    state: LazyGridState,
    width: Dp = 12.dp,
    idleDelayMs: Long = 1500L,
    thumbHeightOffset: Float = 0f,
    hitZoneWidth: Dp = 24.dp,
    sectionLabel: ((index: Int) -> String?)? = null,
) {
    val info = remember { ScrollbarTrackInfo() }
    val colors = faderColors()
    val grabTolerancePx = with(LocalDensity.current) { GRAB_TOLERANCE.toPx() }
    var alpha by remember { mutableStateOf(0f) }
    val animatedAlpha by animateFloatAsState(alpha, tween(200), label = "scrollbarAlpha")
    val scope = rememberCoroutineScope()
    val bubbleDrag = remember { SectionBubbleDrag() }
    val bubble = sectionBubble(bubbleDrag, sectionLabel)

    // A cap held still under the finger (dragging, but no scroll this instant) stays lit:
    // it used to fade out mid-scrub after idleDelayMs, leaving the bubble pointing at nothing.
    LaunchedEffect(state.isScrollInProgress, bubbleDrag.dragging) {
        if (state.isScrollInProgress || bubbleDrag.dragging) alpha = 1f else { delay(idleDelayMs); alpha = 0f }
    }

    Box(
        modifier = Modifier.fillMaxSize().drawWithContent {
            drawContent()
            val layout = state.layoutInfo
            val totalItems = layout.totalItemsCount
            if (totalItems == 0 || layout.visibleItemsInfo.isEmpty()) return@drawWithContent
            val firstVisible = layout.visibleItemsInfo.first()
            val avgItemHeight =
                avgSizeSkippingHeader(layout.visibleItemsInfo.map { it.index to it.size.height })
            val columns = layout.visibleItemsInfo.map { it.column }.distinct().size.coerceAtLeast(1)
            val totalRows = (totalItems + columns - 1) / columns
            val estimatedTotalHeight = avgItemHeight * totalRows
            if (estimatedTotalHeight <= layout.viewportEndOffset) return@drawWithContent

            val scrolledPx = firstVisible.row * avgItemHeight - firstVisible.offset.y
            val maxScrollPx = estimatedTotalHeight - layout.viewportEndOffset
            info.thumbFraction = clampedThumbFraction(layout.viewportEndOffset / estimatedTotalHeight)
            info.progress = (scrolledPx / maxScrollPx).coerceIn(0f, 1f)
            drawFaderCap(info, colors, animatedAlpha, width.toPx(), thumbHeightOffset)
            bubble.draw(this, info, colors, width.toPx(), thumbHeightOffset)
        },
    )

    Box(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(hitZoneWidth)
            .thumbGestureExclusion(info.thumbFraction, info.progress, thumbHeightOffset, grabTolerancePx)
            .faderScrubber(
                stateKey = state,
                info = info,
                animatedAlpha = { animatedAlpha },
                thumbHeightOffset = thumbHeightOffset,
                onWake = {
                    alpha = 1f
                    bubbleDrag.grab(state.firstVisibleItemIndex)
                },
                onRelease = bubbleDrag::release,
                onScrub = { progress ->
                    // Pixel-granular target: row + intra-row offset (see list overload).
                    val layout = state.layoutInfo
                    val totalItems = layout.totalItemsCount
                    if (totalItems > 0 && layout.visibleItemsInfo.isNotEmpty()) {
                        val columns = layout.visibleItemsInfo
                            .map { it.column }.distinct().size.coerceAtLeast(1)
                        val totalRows = (totalItems + columns - 1) / columns
                        val avgRowHeight = avgSizeSkippingHeader(
                            layout.visibleItemsInfo.map { it.index to it.size.height },
                        )
                        val maxScrollPx =
                            (avgRowHeight * totalRows - layout.viewportEndOffset).coerceAtLeast(0f)
                        val targetPx = progress * maxScrollPx
                        val targetRow = (targetPx / avgRowHeight).toInt().coerceAtLeast(0)
                        val index = (targetRow * columns).coerceIn(0, totalItems - 1)
                        val offsetPx = (targetPx - targetRow * avgRowHeight).roundToInt()
                        bubbleDrag.scrubIndex = index
                        scope.launch { state.scrollToItem(index, offsetPx) }
                    }
                },
            ),
    )
}
