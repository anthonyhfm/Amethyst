package dev.anthonyhfm.amethyst.editor.ui.projectsettings.launchpadconfig

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.anthonyhfm.amethyst.core.data.ProjectRepository
import dev.anthonyhfm.amethyst.editor.ui.projectsettings.launchpadconfig.ui.LaunchpadConfigItem
import org.koin.compose.koinInject

@Composable
fun LaunchpadConfigSettings() {
    val projectRepository = koinInject<ProjectRepository>()

    val viewModel: LaunchpadConfigViewModel = viewModel {
        LaunchpadConfigViewModel(projectRepository)
    }

    val state by projectRepository.launchpadConfigs.collectAsState()

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier,

            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Launchpad Settings"
            )

            Spacer(Modifier.weight(1f))

            IconButton(
                onClick = {
                    viewModel.createDevice()
                }
            ) {
                Icon(Icons.Rounded.Add, null)
            }
        }

        state.forEach {
            LaunchpadConfigItem(
                item = it,
                onChangeItemProperties = { changedItem ->
                    viewModel.changeDeviceProperties(
                        before = it,
                        after = changedItem
                    )
                }
            )
        }
    }
}