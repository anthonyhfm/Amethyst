package dev.anthonyhfm.amethyst.workspace.ui

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.workspace_saving_compressing
import amethyst.composeapp.generated.resources.workspace_saving_description
import amethyst.composeapp.generated.resources.workspace_saving_finishing
import amethyst.composeapp.generated.resources.workspace_saving_preparing
import amethyst.composeapp.generated.resources.workspace_saving_title
import amethyst.composeapp.generated.resources.workspace_saving_writing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composeunstyled.rememberDialogState
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialog
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogDescription
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogHeader
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogTitle
import dev.anthonyhfm.amethyst.ui.components.primitives.Progress
import dev.anthonyhfm.amethyst.ui.components.primitives.TypographyMuted
import dev.anthonyhfm.amethyst.workspace.utils.WorkspaceSaveHelper
import org.jetbrains.compose.resources.stringResource

@Composable
fun SavingProgressDialog(progress: WorkspaceSaveHelper.SaveProgress) {
    val dialogState = rememberDialogState(initiallyVisible = true)
    val phase = when (progress.phase) {
        WorkspaceSaveHelper.SavePhase.Preparing -> stringResource(Res.string.workspace_saving_preparing)
        WorkspaceSaveHelper.SavePhase.Compressing -> stringResource(Res.string.workspace_saving_compressing)
        WorkspaceSaveHelper.SavePhase.Writing -> stringResource(Res.string.workspace_saving_writing)
        WorkspaceSaveHelper.SavePhase.Finishing -> stringResource(Res.string.workspace_saving_finishing)
    }

    AlertDialog(
        state = dialogState,
        modifier = Modifier.widthIn(min = 400.dp, max = 520.dp),
        onDismiss = {},
    ) {
        AlertDialogHeader {
            AlertDialogTitle(stringResource(Res.string.workspace_saving_title))
            AlertDialogDescription(stringResource(Res.string.workspace_saving_description))
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Progress(value = progress.progress)
            TypographyMuted("$phase · ${(progress.progress * 100).toInt()}%")
            TypographyMuted(progress.destination)
        }
    }
}
