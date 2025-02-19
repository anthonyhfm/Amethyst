package dev.anthonyhfm.amethyst.workspace

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.ui.launchpad.components.GenericLaunchpadButton
import dev.anthonyhfm.amethyst.ui.launchpad.previews.LaunchpadPro
import dev.anthonyhfm.amethyst.workspace.ui.viewport.ViewportElement
import dev.anthonyhfm.amethyst.workspace.ui.viewport.WorkspaceViewport

@Composable
fun Workspace() {
    Surface(
        modifier = Modifier
            .fillMaxSize()
    ) {
        WorkspaceViewport(
            elements = listOf(
                ViewportElement.ViewportLaunchpad()
            )
        )
    }
}