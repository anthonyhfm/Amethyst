package dev.anthonyhfm.amethyst.home.ui.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import dev.anthonyhfm.amethyst.core.util.Platform
import dev.anthonyhfm.amethyst.core.util.platform
import dev.anthonyhfm.amethyst.home.nav.HomeNavRoute
import kotlinx.coroutines.flow.SharedFlow

@OptIn(ExperimentalMaterial3Api::class)
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
                is ProjectCreationDialogContract.Effect.OpenWorkspace -> {
                    openWorkspace()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                contentPadding = if (platform is Platform.Desktop.MacOS) {
                    PaddingValues(top = 26.dp)
                } else {
                    PaddingValues(0.dp)
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            navigator.popBackStack()
                        }
                    ) {
                        Icon(Icons.Default.Close, null)
                    }
                },
                title = {
                    Text(if (projectPath != null) "Edit Project" else "Create a new Project")
                }
            )
        },
        floatingActionButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = {
                        navigator.popBackStack()
                    }
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        viewModel.onEvent(ProjectCreationDialogContract.Event.OnClickCreateProject)
                    }
                ) {
                    Text(if (projectPath != null) "Save Changes" else "Create Project")
                }
            }
        }
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp),

                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Project details")

                HorizontalDivider()

                OutlinedTextField(
                    value = state.name,
                    isError = !state.isNameValid,
                    onValueChange = {
                        viewModel.onEvent(ProjectCreationDialogContract.Event.OnChangeName(it))
                    },
                    singleLine = true,
                    label = { Text("Project name") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = state.author,
                    onValueChange = {
                        viewModel.onEvent(ProjectCreationDialogContract.Event.OnChangeAuthor(it))
                    },
                    singleLine = true,
                    label = { Text("Author") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier)
            }
        }
    }
}