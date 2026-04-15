package dev.anthonyhfm.amethyst.ui.components.primitives

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground

enum class ResizableOrientation {
    Horizontal,
    Vertical,
}

@Stable
class ResizablePanelGroupState internal constructor(
    initialSizes: List<Float>,
) {
    internal val sizes = mutableStateListOf<Float>().apply { addAll(initialSizes) }
    internal var availableSpacePx: Float = 0f

    internal fun resize(handleIndex: Int, deltaPx: Float) {
        if (availableSpacePx <= 0f) return
        val delta = deltaPx / availableSpacePx
        val left = handleIndex
        val right = handleIndex + 1
        if (left < 0 || right >= sizes.size) return

        val minWeight = 0.05f
        val sum = sizes[left] + sizes[right]
        val newLeft = (sizes[left] + delta).coerceIn(minWeight, sum - minWeight)
        sizes[left] = newLeft
        sizes[right] = sum - newLeft
    }
}

@Composable
fun rememberResizablePanelGroupState(
    vararg initialSizes: Float,
): ResizablePanelGroupState {
    return remember {
        val list = if (initialSizes.isEmpty()) {
            listOf(0.5f, 0.5f)
        } else {
            val total = initialSizes.sum()
            if (total > 0f) initialSizes.map { it / total } else initialSizes.toList()
        }
        ResizablePanelGroupState(list)
    }
}

// --- Internal types ---

private enum class ChildType { Panel, Handle }

private data class ChildData(val type: ChildType, val index: Int)

private class ChildDataModifier(private val data: ChildData) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?) = data
}

private fun Modifier.childData(type: ChildType, index: Int) =
    this.then(ChildDataModifier(ChildData(type, index)))

private class IndexAllocator {
    var panel = 0
    var handle = 0
    fun nextPanel() = panel++
    fun nextHandle() = handle++
    fun reset() {
        panel = 0
        handle = 0
    }
}

private val LocalGroupState = compositionLocalOf<ResizablePanelGroupState?> { null }
private val LocalOrientation = compositionLocalOf { ResizableOrientation.Horizontal }
private val LocalIndexAllocator = compositionLocalOf { IndexAllocator() }

private val HandleThickness = 4.dp

// --- Composables ---

@Composable
fun ResizablePanelGroup(
    state: ResizablePanelGroupState,
    modifier: Modifier = Modifier,
    orientation: ResizableOrientation = ResizableOrientation.Horizontal,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val handleThicknessPx = with(density) { HandleThickness.roundToPx() }
    val allocator = remember { IndexAllocator() }
    allocator.reset()

    CompositionLocalProvider(
        LocalGroupState provides state,
        LocalOrientation provides orientation,
        LocalIndexAllocator provides allocator,
    ) {
        Layout(
            content = content,
            modifier = modifier,
        ) { measurables, constraints ->
            val isHorizontal = orientation == ResizableOrientation.Horizontal
            val totalSize = if (isHorizontal) constraints.maxWidth else constraints.maxHeight
            val crossSize = if (isHorizontal) constraints.maxHeight else constraints.maxWidth

            val identified = measurables.mapNotNull { m ->
                val data = m.parentData as? ChildData ?: return@mapNotNull null
                Triple(data.type, data.index, m)
            }
            val panelItems = identified
                .filter { it.first == ChildType.Panel }
                .sortedBy { it.second }
            val handleItems = identified
                .filter { it.first == ChildType.Handle }
                .sortedBy { it.second }

            val totalHandleSpace = handleItems.size * handleThicknessPx
            val available = (totalSize - totalHandleSpace).coerceAtLeast(0)
            state.availableSpacePx = available.toFloat()

            // Calculate panel pixel sizes with rounding correction on last panel
            val rawSizes = panelItems.map { (_, index, _) ->
                val fraction = state.sizes.getOrElse(index) {
                    1f / state.sizes.size.coerceAtLeast(1)
                }
                (available * fraction).toInt()
            }
            val adjustedSizes = if (rawSizes.isNotEmpty()) {
                val remainder = available - rawSizes.sum()
                rawSizes.toMutableList().apply { this[lastIndex] += remainder }
            } else {
                rawSizes
            }

            val panelPlaceables = panelItems.mapIndexed { i, (_, index, measurable) ->
                val size = adjustedSizes.getOrElse(i) { 0 }.coerceAtLeast(0)
                val c = if (isHorizontal) {
                    Constraints(
                        minWidth = size, maxWidth = size,
                        minHeight = 0, maxHeight = crossSize,
                    )
                } else {
                    Constraints(
                        minWidth = 0, maxWidth = crossSize,
                        minHeight = size, maxHeight = size,
                    )
                }
                index to measurable.measure(c)
            }

            val handlePlaceables = handleItems.map { (_, index, measurable) ->
                val c = if (isHorizontal) {
                    Constraints(
                        minWidth = handleThicknessPx, maxWidth = handleThicknessPx,
                        minHeight = 0, maxHeight = crossSize,
                    )
                } else {
                    Constraints(
                        minWidth = 0, maxWidth = crossSize,
                        minHeight = handleThicknessPx, maxHeight = handleThicknessPx,
                    )
                }
                index to measurable.measure(c)
            }

            layout(constraints.maxWidth, constraints.maxHeight) {
                var offset = 0
                val panelMap = panelPlaceables.toMap()
                val handleMap = handlePlaceables.toMap()

                for (i in state.sizes.indices) {
                    panelMap[i]?.let { p ->
                        if (isHorizontal) p.place(offset, 0) else p.place(0, offset)
                        offset += if (isHorizontal) p.width else p.height
                    }
                    if (i < state.sizes.size - 1) {
                        handleMap[i]?.let { h ->
                            if (isHorizontal) h.place(offset, 0) else h.place(0, offset)
                            offset += if (isHorizontal) h.width else h.height
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ResizablePanel(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val allocator = LocalIndexAllocator.current
    val index = remember { allocator.nextPanel() }

    Box(
        modifier = Modifier
            .childData(ChildType.Panel, index)
            .fillMaxSize()
            .then(modifier),
        content = content,
    )
}

@Composable
fun ResizableHandle(
    modifier: Modifier = Modifier,
    withHandle: Boolean = false,
) {
    val state = LocalGroupState.current ?: return
    val orientation = LocalOrientation.current
    val allocator = LocalIndexAllocator.current
    val index = remember { allocator.nextHandle() }

    val borderColor = Theme[colors][border]
    val gripColor = Theme[colors][mutedForeground]

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier = Modifier
            .childData(ChildType.Handle, index)
            .hoverable(interactionSource)
            .pointerInput(orientation) {
                detectDragGestures { _, dragAmount ->
                    val delta = when (orientation) {
                        ResizableOrientation.Horizontal -> dragAmount.x
                        ResizableOrientation.Vertical -> dragAmount.y
                    }
                    state.resize(index, delta)
                }
            }
            .then(modifier),
        contentAlignment = Alignment.Center,
    ) {
        // Visible divider line
        Box(
            modifier = when (orientation) {
                ResizableOrientation.Horizontal ->
                    Modifier.width(1.dp).fillMaxHeight()

                ResizableOrientation.Vertical ->
                    Modifier.height(1.dp).fillMaxWidth()
            }.background(if (isHovered) gripColor.copy(alpha = 0.4f) else borderColor)
        )

        if (withHandle) {
            Box(
                modifier = when (orientation) {
                    ResizableOrientation.Horizontal ->
                        Modifier.width(6.dp).height(24.dp)

                    ResizableOrientation.Vertical ->
                        Modifier.height(6.dp).width(24.dp)
                }
                    .clip(RoundedCornerShape(50))
                    .background(borderColor),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = when (orientation) {
                        ResizableOrientation.Horizontal ->
                            Modifier.width(2.dp).height(16.dp)

                        ResizableOrientation.Vertical ->
                            Modifier.height(2.dp).width(16.dp)
                    }
                        .clip(RoundedCornerShape(50))
                        .background(gripColor)
                )
            }
        }
    }
}
