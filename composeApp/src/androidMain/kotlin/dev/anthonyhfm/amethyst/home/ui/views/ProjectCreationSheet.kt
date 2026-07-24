package dev.anthonyhfm.amethyst.home.ui.views

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.anthonyhfm.amethyst.home.ui.views.ProjectCreationSheetContract.Effect

@Composable
fun ProjectCreationSheet(
    onDismiss: () -> Unit,
    openWorkspace: () -> Unit,
    projectPath: String? = null,
) {
    val viewModel = viewModel(key = projectPath) { ProjectCreationSheetViewModel(projectPath = projectPath) }
    val state by viewModel.state.collectAsState()

    val isEditing = projectPath != null

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                Effect.OpenWorkspace -> openWorkspace()
            }
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        cursorColor = MaterialTheme.colorScheme.primary,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = if (isEditing) stringResource(Res.string.home_project_creation_sheet_edit_title) else stringResource(Res.string.home_project_creation_sheet_new_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Text(
            text = if (isEditing)
                stringResource(Res.string.home_project_creation_sheet_edit_desc)
            else
                stringResource(Res.string.home_project_creation_sheet_new_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(4.dp))

        OutlinedTextField(
            value = state.name,
            onValueChange = {
                viewModel.onEvent(ProjectCreationSheetContract.Event.OnChangeName(it))
            },
            label = { Text(stringResource(Res.string.home_project_creation_sheet_name_label)) },
            placeholder = { Text(stringResource(Res.string.home_project_creation_sheet_name_placeholder)) },
            isError = state.name.isNotEmpty() && !state.isNameValid,
            supportingText = if (state.name.isNotEmpty() && !state.isNameValid) {
                { Text(stringResource(Res.string.home_project_creation_sheet_name_error)) }
            } else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.author,
            onValueChange = {
                viewModel.onEvent(ProjectCreationSheetContract.Event.OnChangeAuthor(it))
            },
            label = { Text(stringResource(Res.string.home_project_creation_sheet_author_label)) },
            placeholder = { Text(stringResource(Res.string.home_project_creation_sheet_author_placeholder)) },
            supportingText = { Text(stringResource(Res.string.home_project_creation_sheet_author_desc)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (state.isNameValid) {
                        viewModel.onEvent(ProjectCreationSheetContract.Event.OnClickSubmit)
                    }
                }
            ),
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.home_project_creation_sheet_cancel))
            }
            Button(
                onClick = { viewModel.onEvent(ProjectCreationSheetContract.Event.OnClickSubmit) },
                enabled = state.isNameValid,
            ) {
                Text(if (isEditing) stringResource(Res.string.home_project_creation_sheet_save) else stringResource(Res.string.home_project_creation_sheet_create))
            }
        }
    }
}
