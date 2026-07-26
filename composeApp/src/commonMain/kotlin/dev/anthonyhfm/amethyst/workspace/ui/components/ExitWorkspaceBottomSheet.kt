package dev.anthonyhfm.amethyst.workspace.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composeunstyled.DialogState
import com.composeunstyled.Text
import com.composeunstyled.rememberDialogState
import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.workspace_exit_sheet_cancel
import amethyst.composeapp.generated.resources.workspace_exit_sheet_description
import amethyst.composeapp.generated.resources.workspace_exit_sheet_dont_save
import amethyst.composeapp.generated.resources.workspace_exit_sheet_save
import amethyst.composeapp.generated.resources.workspace_exit_sheet_title
import dev.anthonyhfm.amethyst.ui.components.primitives.Button
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonSize
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.Drawer
import dev.anthonyhfm.amethyst.ui.components.primitives.DrawerContent
import dev.anthonyhfm.amethyst.ui.components.primitives.DrawerDescription
import dev.anthonyhfm.amethyst.ui.components.primitives.DrawerHeader
import dev.anthonyhfm.amethyst.ui.components.primitives.DrawerTitle
import org.jetbrains.compose.resources.stringResource

@Composable
fun ExitWorkspaceBottomSheet(
    onSaveAndExit: () -> Unit,
    onDiscardAndExit: () -> Unit,
    onCancel: () -> Unit,
    state: DialogState = rememberDialogState(initiallyVisible = true),
) {
    Drawer(
        state = state,
        onDismiss = onCancel,
    ) {
        DrawerHeader(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        ) {
            DrawerTitle(
                text = stringResource(Res.string.workspace_exit_sheet_title),
                modifier = Modifier.fillMaxWidth(),
            )
            DrawerDescription(
                text = stringResource(Res.string.workspace_exit_sheet_description),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        DrawerContent(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Button(
                    onClick = onSaveAndExit,
                    variant = ButtonVariant.Default,
                    size = ButtonSize.Mobile,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(Res.string.workspace_exit_sheet_save),
                        textAlign = TextAlign.Center,
                    )
                }

                Button(
                    onClick = onDiscardAndExit,
                    variant = ButtonVariant.Destructive,
                    size = ButtonSize.Mobile,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(Res.string.workspace_exit_sheet_dont_save),
                        textAlign = TextAlign.Center,
                    )
                }

                Button(
                    onClick = onCancel,
                    variant = ButtonVariant.Ghost,
                    size = ButtonSize.Mobile,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(Res.string.workspace_exit_sheet_cancel),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
