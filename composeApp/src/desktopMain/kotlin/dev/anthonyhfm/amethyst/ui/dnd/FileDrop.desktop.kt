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
import io.github.vinceglb.filekit.PlatformFile
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.net.URI

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun Modifier.fileDropTarget(
    onHover: (isHovering: Boolean, offset: Offset?, files: List<PlatformFile>) -> Unit,
    onDrop: (files: List<PlatformFile>) -> Unit
): Modifier {
    var isDragOver by remember { mutableStateOf(false) }

    return this.dragAndDropTarget(
        shouldStartDragAndDrop = { event ->
            val transferable = event.awtTransferable
            transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor) ||
            transferable.isDataFlavorSupported(DataFlavor.stringFlavor)
        },
        target = object : DragAndDropTarget {
            override fun onStarted(event: DragAndDropEvent) {
                isDragOver = true
                onHover(true, null, emptyList())
            }

            override fun onEntered(event: DragAndDropEvent) {
                isDragOver = true
                onHover(true, null, emptyList())
            }

            override fun onMoved(event: DragAndDropEvent) {
                if (isDragOver) {
                    onHover(true, null, emptyList())
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

                onHover(false, null, getEventFiles(event))
                onDrop(getEventFiles(event))

                return false
            }
        }
    )
}

@OptIn(ExperimentalComposeUiApi::class)
private fun getEventFiles(event: DragAndDropEvent): List<PlatformFile> {
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
                // Try to parse as file paths or URIs
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
                                files.add(
                                    PlatformFile(file.path)
                                )
                            }
                        } catch (_: Exception) { }
                    }
                }
            }
        }
    } catch (_: Exception) {
        // Handle any transfer data errors gracefully
    }

    return files
}