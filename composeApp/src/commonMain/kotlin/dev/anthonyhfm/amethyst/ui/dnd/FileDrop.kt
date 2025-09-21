package dev.anthonyhfm.amethyst.ui.dnd

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import io.github.vinceglb.filekit.PlatformFile

@Composable
expect fun Modifier.fileDropTarget(
    onHover: (isHovering: Boolean, offset: Offset?, files: List<PlatformFile>) -> Unit,
    onDrop: (files: List<PlatformFile>) -> Unit
): Modifier