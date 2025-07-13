package dev.anthonyhfm.amethyst.start.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.data.settings.GlobalSettings
import dev.anthonyhfm.amethyst.settings.SettingsDialog
import dev.anthonyhfm.amethyst.workspace.data.RecentWorkspace

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RowScope.ProjectsView(
    onClickCreateProject: () -> Unit,
    onClickOpenProject: () -> Unit,
    onRemoveProjectFromRecents: (RecentWorkspace) -> Unit,
    onOpenRecentWorkspace: (RecentWorkspace) -> Unit
) {
    var projects: List<RecentWorkspace> = GlobalSettings.recentWorkspaces
    var showSettings by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
        topBar = {
            TopAppBar(
                title = {
                    Text("Recent Projects")
                },
                actions = {
                    IconButton(
                        onClick = {
                            showSettings = true
                        }
                    ) {
                        Icon(Icons.Default.Settings, "Settings")

                        SettingsDialog(
                            visible = showSettings,
                            onDismiss = {
                                showSettings = false
                            },
                        )
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),

                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = {
                        onClickCreateProject()
                    },
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .weight(1f)
                ) {
                    Text(
                        text = "New Project",
                        style = MaterialTheme.typography.titleSmall,
                        lineHeight = MaterialTheme.typography.titleSmall.fontSize
                    )
                }

                Button(
                    onClick = {
                        onClickOpenProject()
                    },
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .weight(1f)
                ) {
                    Text(
                        text = "Open Project",
                        style = MaterialTheme.typography.titleSmall,
                        lineHeight = MaterialTheme.typography.titleSmall.fontSize
                    )
                }
            }
        }
    ) { paddingValues ->
        if (projects.isEmpty()) {
            EmptyProjects()
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(horizontal = 16.dp),

            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            projects.forEach {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .fillMaxWidth()
                        .height(46.dp)
                        .background(MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp))
                        .clickable {
                            onOpenRecentWorkspace(it)
                        }
                        .padding(start = 12.dp),

                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = it.title,
                            style = MaterialTheme.typography.labelLarge,
                            lineHeight = MaterialTheme.typography.labelLarge.fontSize
                        )

                        Text(
                            text = it.path,
                            style = MaterialTheme.typography.labelSmall,
                            lineHeight = MaterialTheme.typography.labelSmall.fontSize,
                            color = MaterialTheme.colorScheme.onBackground.copy(0.6f)
                        )
                    }

                    IconButton(
                        onClick = {
                            onRemoveProjectFromRecents(it)
                        }
                    ) {
                        Icon(Icons.Default.Remove, null)
                    }
                }
            }
        }
    }
}