package dev.anthonyhfm.amethyst.home.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.components.primitives.Button
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonSize
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.DefaultShape
import dev.anthonyhfm.amethyst.ui.components.primitives.DialogDescription
import dev.anthonyhfm.amethyst.ui.components.primitives.DialogFooter
import dev.anthonyhfm.amethyst.ui.components.primitives.DialogTitle
import dev.anthonyhfm.amethyst.ui.components.primitives.Field
import dev.anthonyhfm.amethyst.ui.components.primitives.FieldDescription
import dev.anthonyhfm.amethyst.ui.components.primitives.FieldError
import dev.anthonyhfm.amethyst.ui.components.primitives.FieldLabel
import dev.anthonyhfm.amethyst.ui.components.primitives.Input
import dev.anthonyhfm.amethyst.ui.components.primitives.Separator
import dev.anthonyhfm.amethyst.ui.theme.background
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.card
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground

@Composable
fun ProjectCreationDialog(
    navigator: NavHostController,
    openWorkspace: () -> Unit,
    projectPath: String? = null,
) {
    val viewModel = viewModel { ProjectCreationDialogViewModel(projectPath = projectPath) }
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.effect.collect {
            when (it) {
                ProjectCreationDialogContract.Effect.OpenWorkspace -> {
                    openWorkspace()
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp),
        ) {
            val stackedActions = maxWidth < 420.dp
            val isEditing = projectPath != null

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Theme[colors][border], DefaultShape)
                    .background(Theme[colors][card], DefaultShape)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                DialogHeaderRow(
                    isEditing = isEditing,
                    onClose = { navigator.popBackStack() },
                )

                Separator()

                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Field {
                        FieldLabel("Project name")
                        Input(
                            value = state.name,
                            onValueChange = {
                                viewModel.onEvent(ProjectCreationDialogContract.Event.OnChangeName(it))
                            },
                            placeholder = "My next performance",
                            modifier = Modifier.fillMaxWidth(),
                        )

                        if (!state.isNameValid) {
                            FieldError("Please enter a project name.")
                        } else {
                            FieldDescription("This is the title that will be shown in your workspace and recent projects.")
                        }
                    }

                    Field {
                        FieldLabel("Author")
                        Input(
                            value = state.author,
                            onValueChange = {
                                viewModel.onEvent(ProjectCreationDialogContract.Event.OnChangeAuthor(it))
                            },
                            placeholder = "creator",
                            modifier = Modifier.fillMaxWidth(),
                        )
                        FieldDescription("Saved as your default author. Leave it blank to fall back to “Unknown Author”.")
                    }
                }

                ProjectCreationActions(
                    stacked = stackedActions,
                    isEditing = isEditing,
                    canSubmit = state.isNameValid,
                    onCancel = { navigator.popBackStack() },
                    onSubmit = {
                        viewModel.onEvent(ProjectCreationDialogContract.Event.OnClickCreateProject)
                    },
                )
            }
        }
    }
}

@Composable
private fun DialogHeaderRow(
    isEditing: Boolean,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DialogTitle(if (isEditing) "Edit Project" else "Create a new Project")
            DialogDescription(
                if (isEditing) {
                    "Update the project details shown for this workspace."
                } else {
                    "Set up the basics for a fresh workspace before jumping into the editor."
                }
            )
        }

        Button(
            onClick = onClose,
            variant = ButtonVariant.Ghost,
            size = ButtonSize.Icon,
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = Theme[colors][foreground],
            )
        }
    }
}

@Composable
private fun ProjectCreationActions(
    stacked: Boolean,
    isEditing: Boolean,
    canSubmit: Boolean,
    onCancel: () -> Unit,
    onSubmit: () -> Unit,
) {
    val submitText = if (isEditing) "Save Changes" else "Create Project"

    if (stacked) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onSubmit,
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(submitText)
            }

            Button(
                onClick = onCancel,
                variant = ButtonVariant.Outline,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Cancel")
            }
        }
    } else {
        DialogFooter {
            Button(
                onClick = onCancel,
                variant = ButtonVariant.Outline,
            ) {
                Text("Cancel")
            }

            Button(
                onClick = onSubmit,
                enabled = canSubmit,
            ) {
                Text(submitText)
            }
        }
    }
}
