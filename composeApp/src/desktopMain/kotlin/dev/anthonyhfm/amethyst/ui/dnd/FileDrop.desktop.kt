package dev.anthonyhfm.amethyst.ui.dnd

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import dev.nucleusframework.window.tao.TaoDragAndDropPayload
import io.github.vinceglb.filekit.PlatformFile
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.net.URI

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun Modifier.fileDropTarget(
    onHover: (isHovering: Boolean, offset: Offset?, files: List<PlatformFile>) -> Unit,
    onDrop: (offset: Offset?, files: List<PlatformFile>) -> Unit
): Modifier {
    var isDragOver by remember { mutableStateOf(false) }
    var targetBoundsInRoot by remember { mutableStateOf(Rect.Zero) }
    val density = LocalDensity.current.density

    fun toLocalOffset(event: DragAndDropEvent): Offset? {
        val point = when (val ne = event.nativeEvent) {
            is java.awt.dnd.DropTargetDragEvent -> ne.location
            is java.awt.dnd.DropTargetDropEvent -> ne.location
            else -> null
        } ?: return null

        // In Tao on macOS (convertPointToBacking), native drag coordinates are in physical pixels.
        // Compose layout boundsInRoot are in density-independent pixels (DP).
        // Convert to DP before subtracting target bounds:
        val logicalX = if (density > 0f) point.x.toFloat() / density else point.x.toFloat()
        val logicalY = if (density > 0f) point.y.toFloat() / density else point.y.toFloat()

        return Offset(
            x = logicalX - targetBoundsInRoot.left,
            y = logicalY - targetBoundsInRoot.top
        )
    }

    return this
        .onGloballyPositioned { coordinates ->
            targetBoundsInRoot = coordinates.boundsInRoot()
        }
        .dragAndDropTarget(
            shouldStartDragAndDrop = { event ->
                val taoPayload = event.nativeEvent as? TaoDragAndDropPayload
                if (taoPayload != null) return@dragAndDropTarget true

                try {
                    val transferable = event.awtTransferable
                    transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor) ||
                    transferable.isDataFlavorSupported(DataFlavor.stringFlavor)
                } catch (_: Exception) {
                    true
                }
            },
            target = remember(density) {
                object : DragAndDropTarget {
                    override fun onStarted(event: DragAndDropEvent) {
                        isDragOver = false
                    }

                    override fun onEntered(event: DragAndDropEvent) {
                        val local = toLocalOffset(event)
                        val isInside = local != null &&
                            local.x >= 0f && local.x <= targetBoundsInRoot.width &&
                            local.y >= 0f && local.y <= targetBoundsInRoot.height

                        if (isInside) {
                            isDragOver = true
                            val files = getEventFiles(event)
                            onHover(true, local, files)
                        }
                    }

                    override fun onMoved(event: DragAndDropEvent) {
                        val local = toLocalOffset(event)
                        val isInside = local != null &&
                            local.x >= 0f && local.x <= targetBoundsInRoot.width &&
                            local.y >= 0f && local.y <= targetBoundsInRoot.height

                        if (isInside) {
                            isDragOver = true
                            val files = getEventFiles(event)
                            onHover(true, local, files)
                        } else if (isDragOver) {
                            isDragOver = false
                            onHover(false, null, emptyList())
                        }
                    }

                    override fun onExited(event: DragAndDropEvent) {
                        isDragOver = false
                        onHover(false, null, emptyList())
                    }

                    override fun onEnded(event: DragAndDropEvent) {
                        isDragOver = false
                        onHover(false, null, emptyList())
                    }

                    override fun onDrop(event: DragAndDropEvent): Boolean {
                        isDragOver = false
                        val local = toLocalOffset(event)
                        val files = getEventFiles(event)
                        onHover(false, null, emptyList())
                        onDrop(local, files)
                        return files.isNotEmpty()
                    }
                }
            }
        )
}

@OptIn(ExperimentalComposeUiApi::class)
private fun getEventFiles(event: DragAndDropEvent): List<PlatformFile> {
    // 1. Tao backend payload
    try {
        val payload = when (val ne = event.nativeEvent) {
            is TaoDragAndDropPayload -> ne
            else -> {
                val method = ne?.javaClass?.methods?.firstOrNull { it.name == "getPayload" }
                method?.invoke(ne) as? TaoDragAndDropPayload
            }
        }
        if (payload != null && payload.files.isNotEmpty()) {
            return payload.files.map { PlatformFile(it) }
        }
    } catch (_: Throwable) { }

    // 2. AWT transferable fallback
    val files = mutableListOf<PlatformFile>()
    try {
        val transferable = event.awtTransferable

        when {
            transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor) -> {
                @Suppress("UNCHECKED_CAST")
                val fileList = transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
                files.addAll(fileList.map { file ->
                    PlatformFile(file.path)
                })
            }
            transferable.isDataFlavorSupported(DataFlavor.stringFlavor) -> {
                val stringData = transferable.getTransferData(DataFlavor.stringFlavor) as String
                stringData.lines().forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) {
                        try {
                            val file = if (trimmed.startsWith("file://")) {
                                File(URI(trimmed))
                            } else {
                                File(trimmed)
                            }
                            if (file.exists()) {
                                files.add(PlatformFile(file.path))
                            }
                        } catch (_: Exception) { }
                    }
                }
            }
        }
    } catch (_: Exception) { }

    return files
}
