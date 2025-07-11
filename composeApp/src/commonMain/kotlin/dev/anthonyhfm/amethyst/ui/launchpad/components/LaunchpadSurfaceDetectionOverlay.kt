package dev.anthonyhfm.amethyst.ui.launchpad.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlin.math.floor

@Composable
fun LaunchpadSurfaceDetectionOverlay(
    layoutType: LaunchpadLayout,
    onPadPressed: (x: Int, y: Int) -> Unit,
    onPadReleased: (x: Int, y: Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var layoutSize by remember { mutableStateOf(IntSize.Zero) }
    var activePointers by remember { mutableStateOf(mapOf<PointerId, Pair<Int, Int>>()) }
    val workspaceMode by WorkspaceRepository.mode.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                layoutSize = coordinates.size
            }
            .then(
                if (workspaceMode is WorkspaceContract.WorkspaceMode.Layout) {
                    Modifier
                } else {
                    Modifier
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()

                                    when (event.type) {
                                        PointerEventType.Press -> {
                                            event.changes.forEach { change ->
                                                if (!change.isConsumed) {
                                                    val pad = calculatePadFromOffset(change.position, layoutSize, layoutType)
                                                    pad?.let { (x, y) ->
                                                        activePointers = activePointers + (change.id to Pair(x, y))
                                                        onPadPressed(x, y)
                                                        change.consume()
                                                    }
                                                }
                                            }
                                        }

                                        PointerEventType.Move -> {
                                            event.changes.forEach { change ->
                                                if (!change.isConsumed && activePointers.containsKey(change.id)) {
                                                    val newPad = calculatePadFromOffset(change.position, layoutSize, layoutType)
                                                    val currentPad = activePointers[change.id]

                                                    if (newPad != null && newPad != currentPad) {
                                                        currentPad?.let { (oldX, oldY) ->
                                                            onPadReleased(oldX, oldY)
                                                        }

                                                        val (newX, newY) = newPad
                                                        activePointers = activePointers + (change.id to Pair(newX, newY))
                                                        onPadPressed(newX, newY)
                                                        change.consume()
                                                    }
                                                }
                                            }
                                        }

                                        PointerEventType.Release -> {
                                            event.changes.forEach { change ->
                                                activePointers[change.id]?.let { (x, y) ->
                                                    onPadReleased(x, y)
                                                    activePointers = activePointers - change.id
                                                    change.consume()
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                }
            )
    ) {
        content()
    }
}

private fun calculatePadFromOffset(
    offset: Offset,
    layoutSize: IntSize,
    layoutType: LaunchpadLayout
): Pair<Int, Int>? {
    if (layoutSize.width == 0 || layoutSize.height == 0) return null

    val padWidth = layoutSize.width.toFloat() / layoutType.x
    val padHeight = layoutSize.height.toFloat() / layoutType.y

    val col = floor(offset.x / padWidth).toInt()
    val row = floor(offset.y / padHeight).toInt()

    val actualRow = (layoutType.y - 1) - row

    if (col in 0 until layoutType.x && actualRow in 0 until layoutType.y) {
        return Pair(
            col,
            actualRow
        )
    }

    return null
}
