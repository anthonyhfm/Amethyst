package dev.anthonyhfm.amethyst.ui.components.primitives

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.colors
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A [BringIntoViewSpec] that never scrolls in response to focus/bring-into-view requests.
 * Prevents unwanted scroll shifts when clicking interactive children inside a ScrollArea.
 */
private object NoBringIntoViewSpec : androidx.compose.foundation.gestures.BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

enum class ScrollBarOrientation {
    Vertical,
    Horizontal,
}

@Stable
class ScrollAreaState(initialValue: Int = 0) {
    var scrollValue by mutableIntStateOf(initialValue)
    var maxScrollValue by mutableIntStateOf(0)

    var scrollAccumulator: Float = 0f

    val scrollableState = ScrollableState { delta ->
        scrollAccumulator += delta
        val intDelta = scrollAccumulator.roundToInt()
        scrollAccumulator -= intDelta.toFloat()

        val newValue = (scrollValue + intDelta).coerceIn(0, maxScrollValue)
        val consumed = newValue - scrollValue
        scrollValue = newValue
        consumed.toFloat()
    }

    companion object {
        val Saver: Saver<ScrollAreaState, Int> = Saver(
            save = { it.scrollValue },
            restore = { ScrollAreaState(it) }
        )
    }
}

@Composable
fun rememberScrollAreaState(initialValue: Int = 0): ScrollAreaState {
    return rememberSaveable(saver = ScrollAreaState.Saver) {
        ScrollAreaState(initialValue)
    }
}

@Composable
fun ScrollArea(
    modifier: Modifier = Modifier,
    orientation: ScrollBarOrientation = ScrollBarOrientation.Vertical,
    state: ScrollAreaState = rememberScrollAreaState(),
    scrollBarThickness: Dp = 8.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val areaInteractionSource = remember { MutableInteractionSource() }
    val areaHovered by areaInteractionSource.collectIsHoveredAsState()

    var isScrolling by remember { mutableStateOf(false) }
    LaunchedEffect(state.scrollableState.isScrollInProgress) {
        if (state.scrollableState.isScrollInProgress) {
            isScrolling = true
        } else {
            delay(1200L)
            isScrolling = false
        }
    }

    // Clamp scroll position when content shrinks
    LaunchedEffect(state.maxScrollValue) {
        if (state.scrollValue > state.maxScrollValue) {
            state.scrollValue = state.maxScrollValue
        }
    }

    val visible = state.maxScrollValue > 0 && (areaHovered || isScrolling)
    val flingBehavior = ScrollableDefaults.flingBehavior()
    val scrollOrientation = when (orientation) {
        ScrollBarOrientation.Vertical -> Orientation.Vertical
        ScrollBarOrientation.Horizontal -> Orientation.Horizontal
    }
    // Match the direction convention of horizontalScroll() / verticalScroll()
    val reverseDirection = ScrollableDefaults.reverseDirection(
        layoutDirection = LocalLayoutDirection.current,
        orientation = scrollOrientation,
        reverseScrolling = false,
    )

    Box(modifier = modifier.hoverable(areaInteractionSource)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scrollable(
                    state = state.scrollableState,
                    orientation = scrollOrientation,
                    overscrollEffect = null,
                    reverseDirection = reverseDirection,
                    flingBehavior = flingBehavior,
                    bringIntoViewSpec = NoBringIntoViewSpec,
                )
                .layout { measurable, constraints ->
                    val childConstraints = when (orientation) {
                        ScrollBarOrientation.Vertical -> constraints.copy(maxHeight = Constraints.Infinity)
                        ScrollBarOrientation.Horizontal -> constraints.copy(maxWidth = Constraints.Infinity)
                    }
                    val placeable = measurable.measure(childConstraints)
                    val viewportSize = when (orientation) {
                        ScrollBarOrientation.Vertical -> constraints.maxHeight
                        ScrollBarOrientation.Horizontal -> constraints.maxWidth
                    }
                    val contentSize = when (orientation) {
                        ScrollBarOrientation.Vertical -> placeable.height
                        ScrollBarOrientation.Horizontal -> placeable.width
                    }
                    val newMax = (contentSize - viewportSize).coerceAtLeast(0)
                    if (state.maxScrollValue != newMax) state.maxScrollValue = newMax

                    layout(
                        width = when (orientation) {
                            ScrollBarOrientation.Horizontal -> viewportSize.coerceAtLeast(constraints.minWidth)
                            ScrollBarOrientation.Vertical -> placeable.width
                        },
                        height = when (orientation) {
                            ScrollBarOrientation.Vertical -> viewportSize.coerceAtLeast(constraints.minHeight)
                            ScrollBarOrientation.Horizontal -> placeable.height
                        }
                    ) {
                        val offset = state.scrollValue.coerceIn(0, newMax)
                        when (orientation) {
                            ScrollBarOrientation.Vertical -> placeable.place(0, -offset)
                            ScrollBarOrientation.Horizontal -> placeable.place(-offset, 0)
                        }
                    }
                },
            content = content,
        )

        val alignment = when (orientation) {
            ScrollBarOrientation.Vertical -> Alignment.CenterEnd
            ScrollBarOrientation.Horizontal -> Alignment.BottomCenter
        }

        ScrollBar(
            scrollValue = state.scrollValue,
            maxScrollValue = state.maxScrollValue,
            onScrollTo = { state.scrollValue = it.coerceIn(0, state.maxScrollValue) },
            orientation = orientation,
            visible = visible,
            thickness = scrollBarThickness,
            modifier = Modifier.align(alignment),
        )
    }
}

@Composable
fun ScrollBar(
    scrollValue: Int,
    maxScrollValue: Int,
    onScrollTo: (Int) -> Unit,
    modifier: Modifier = Modifier,
    orientation: ScrollBarOrientation = ScrollBarOrientation.Vertical,
    visible: Boolean = true,
    thickness: Dp = 8.dp,
) {
    val density = LocalDensity.current
    var trackSizePx by remember { mutableStateOf(0) }

    val animatedAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
    )

    val minThumbSizePx = with(density) { 20.dp.toPx() }

    val totalContentSize = trackSizePx.toFloat() + maxScrollValue.toFloat()
    val thumbFraction = if (trackSizePx > 0 && totalContentSize > 0f)
        trackSizePx.toFloat() / totalContentSize
    else 1f
    val thumbSizePx = max(trackSizePx * thumbFraction, minThumbSizePx)
        .roundToInt()
        .coerceIn(0, trackSizePx.coerceAtLeast(0))
    val scrollRange = (trackSizePx - thumbSizePx).coerceAtLeast(0)

    val thumbOffsetPx = if (maxScrollValue > 0 && scrollRange > 0) {
        (scrollRange.toFloat() * (scrollValue.toFloat() / maxScrollValue.toFloat())).roundToInt()
    } else 0

    val layoutDirection = LocalLayoutDirection.current
    val reverseThumbDrag = orientation == ScrollBarOrientation.Horizontal &&
        layoutDirection == LayoutDirection.Rtl
    val scrollValueState by rememberUpdatedState(scrollValue)
    val scrollRangeState by rememberUpdatedState(scrollRange)
    val maxScrollValueState by rememberUpdatedState(maxScrollValue)
    val onScrollToState by rememberUpdatedState(onScrollTo)
    var dragStartScroll by remember { mutableFloatStateOf(0f) }
    var dragAccumulatedPx by remember { mutableFloatStateOf(0f) }

    if (animatedAlpha == 0f && maxScrollValue <= 0) return

    // Track
    Box(
        modifier = modifier
            .alpha(animatedAlpha)
            .then(
                when (orientation) {
                    ScrollBarOrientation.Vertical -> Modifier
                        .fillMaxHeight()
                        .width(thickness)
                        .padding(vertical = 12.dp)

                    ScrollBarOrientation.Horizontal -> Modifier
                        .fillMaxWidth()
                        .height(thickness)
                        .padding(horizontal = 12.dp)
                }
            )
            .clip(FullShape)
            .background(Theme[colors][border].copy(alpha = 0.2f))
            .padding(1.dp)
            .onSizeChanged { size ->
                trackSizePx = when (orientation) {
                    ScrollBarOrientation.Vertical -> size.height
                    ScrollBarOrientation.Horizontal -> size.width
                }
            }
    ) {
        if (trackSizePx > 0) {
            // Thumb
            Box(
                modifier = Modifier
                    .then(
                        when (orientation) {
                            ScrollBarOrientation.Vertical -> Modifier
                                .fillMaxWidth()
                                .height(with(density) { thumbSizePx.toDp() })
                                .offset { IntOffset(0, thumbOffsetPx) }

                            ScrollBarOrientation.Horizontal -> Modifier
                                .fillMaxHeight()
                                .width(with(density) { thumbSizePx.toDp() })
                                .offset { IntOffset(thumbOffsetPx, 0) }
                        }
                    )
                    .clip(FullShape)
                    .background(Theme[colors][border])
                    .pointerInput(orientation, reverseThumbDrag, scrollRange, maxScrollValue) {
                        detectDragGestures(
                            onDragStart = {
                                dragStartScroll = scrollValueState.toFloat()
                                dragAccumulatedPx = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val range = scrollRangeState
                                val maxScroll = maxScrollValueState
                                if (range <= 0 || maxScroll <= 0) return@detectDragGestures
                                val delta = when (orientation) {
                                    ScrollBarOrientation.Vertical -> dragAmount.y
                                    ScrollBarOrientation.Horizontal -> {
                                        if (reverseThumbDrag) -dragAmount.x else dragAmount.x
                                    }
                                }
                                dragAccumulatedPx += delta
                                val newScroll = dragStartScroll +
                                    (dragAccumulatedPx / range.toFloat()) * maxScroll.toFloat()
                                onScrollToState(newScroll.roundToInt().coerceIn(0, maxScroll))
                            },
                        )
                    }
            )
        }
    }
}
