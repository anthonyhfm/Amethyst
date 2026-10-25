package dev.anthonyhfm.amethyst.workspace.ui

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.workspace_saving_compressing
import amethyst.composeapp.generated.resources.workspace_saving_finishing
import amethyst.composeapp.generated.resources.workspace_saving_preparing
import amethyst.composeapp.generated.resources.workspace_saving_title
import amethyst.composeapp.generated.resources.workspace_saving_writing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.components.primitives.Progress
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.workspace.utils.WorkspaceSaveHelper
import org.jetbrains.compose.resources.stringResource

@Composable
fun SavingProgressIndicator(
    progress: WorkspaceSaveHelper.SaveProgress,
    modifier: Modifier = Modifier,
) {
    val phase = when (progress.phase) {
        WorkspaceSaveHelper.SavePhase.Preparing -> stringResource(Res.string.workspace_saving_preparing)
        WorkspaceSaveHelper.SavePhase.Compressing -> stringResource(Res.string.workspace_saving_compressing)
        WorkspaceSaveHelper.SavePhase.Writing -> stringResource(Res.string.workspace_saving_writing)
        WorkspaceSaveHelper.SavePhase.Finishing -> stringResource(Res.string.workspace_saving_finishing)
    }

    Row(
        modifier = modifier
            .semantics {
                liveRegion = LiveRegionMode.Polite
                stateDescription = phase
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        Text(
            text = stringResource(Res.string.workspace_saving_title),
            color = Theme[colors][foreground],
        )
        Progress(
            value = progress.progress,
            modifier = Modifier
                .width(width = 72.dp),
        )
    }
}
