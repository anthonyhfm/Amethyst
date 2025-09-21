package dev.anthonyhfm.amethyst.ui.dnd

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import io.github.vinceglb.filekit.PlatformFile

@Composable
actual fun Modifier.fileDropTarget(
    onHover: (Boolean, Offset?, List<PlatformFile>) -> Unit,
    onDrop: (List<PlatformFile>) -> Unit,
): Modifier {
    println("File drop is not supported on iOS")
    return this
}