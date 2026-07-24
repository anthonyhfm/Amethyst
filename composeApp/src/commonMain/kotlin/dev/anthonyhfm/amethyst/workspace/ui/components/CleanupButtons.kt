package dev.anthonyhfm.amethyst.workspace.ui.components

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.BrushCleaning
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCcw
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.ui.components.primitives.Separator
import dev.anthonyhfm.amethyst.ui.components.primitives.SeparatorOrientation
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository

@Composable
fun CleanupButtons() {
    WorkspaceToolbarSurface {
        WorkspaceToolbarIconButton(
            onClick = { WorkspaceRepository.resetMulti() },
            imageVector = Lucide.RefreshCcw,
            contentDescription = stringResource(Res.string.workspace_cleanup_reset_multi),
        )

        Separator(
            modifier = Modifier.height(20.dp),
            orientation = SeparatorOrientation.Vertical,
        )

        WorkspaceToolbarIconButton(
            onClick = { Heaven.clear() },
            imageVector = Lucide.BrushCleaning,
            contentDescription = stringResource(Res.string.workspace_cleanup_lights),
        )
    }
}
