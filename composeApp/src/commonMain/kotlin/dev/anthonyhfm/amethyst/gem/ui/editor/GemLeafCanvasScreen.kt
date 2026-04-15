package dev.anthonyhfm.amethyst.gem.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.anthonyhfm.amethyst.gem.GemNodeRegistry

@Composable
internal fun GemLeafCanvasScreen(
    session: GemEditorSession,
    modifier: Modifier = Modifier,
    registry: GemNodeRegistry = GemNodeRegistry.builtIns
) {
    var nodePaletteVisible by remember { mutableStateOf(false) }

    GemCanvasEditor(
        session = session,
        registry = registry,
        nodePaletteVisible = nodePaletteVisible,
        onNodePaletteVisibilityChange = { nodePaletteVisible = it },
        modifier = modifier
    )
}
