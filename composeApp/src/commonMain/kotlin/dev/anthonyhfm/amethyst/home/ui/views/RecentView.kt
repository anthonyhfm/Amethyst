package dev.anthonyhfm.amethyst.home.ui.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import dev.anthonyhfm.amethyst.core.data.settings.GlobalSettings
import dev.anthonyhfm.amethyst.home.ui.views.RecentViewContract.Event
import dev.anthonyhfm.amethyst.workspace.data.RecentWorkspace
import kotlinx.coroutines.flow.collect

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RecentView(
    navigator: NavHostController,
    onOpenWorkspace: () -> Unit = { }
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val viewModel = viewModel {
        RecentViewModel(
            navigator = navigator,
            snackbarHostState = snackbarHostState
        )
    }

    var recentProjects: List<RecentWorkspace> by remember { mutableStateOf(GlobalSettings.recentWorkspaces) }
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(recentProjects) {
        GlobalSettings.recentWorkspaces = recentProjects
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect {
            println("Effect: $it")
            when (it) {
                RecentViewContract.Effect.OpenWorkspace -> {
                    onOpenWorkspace()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Recent Projects")
                }
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState)
        },
        contentWindowInsets = WindowInsets.statusBars,
        floatingActionButton = {
            FloatingActionButtonMenu(
                expanded = showMenu,
                button = {
                    ToggleFloatingActionButton(
                        checked = showMenu,
                        onCheckedChange = { showMenu = !showMenu },
                    ) {
                        val imageVector by remember {
                            derivedStateOf {
                                if (checkedProgress > 0.5f) Icons.Filled.Close else Icons.Default.Add
                            }
                        }
                        Icon(
                            painter = rememberVectorPainter(imageVector),
                            contentDescription = null,
                            modifier = Modifier.animateIcon({ checkedProgress }),
                        )
                    }
                },
            ) {
                FloatingActionButtonMenuItem(
                    modifier = Modifier,
                    onClick = {
                        viewModel.onEvent(Event.OnClickOpenProject)

                        showMenu = false
                    },
                    icon = {
                        Icon(
                            Icons.Default.FileOpen,
                            contentDescription = "Open Project"
                        )
                    },
                    text = { Text("Open Project") },
                )

                FloatingActionButtonMenuItem(
                    modifier = Modifier,
                    onClick = {
                        viewModel.onEvent(Event.OnClickNewProject)

                        showMenu = false
                    },
                    icon = {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "New Project"
                        )
                    },
                    text = { Text("New Project") },
                )
            }
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .padding(it)
        ) {
            itemsIndexed(recentProjects) { index, it ->
                ListItem(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .height(64.dp)
                        .clickable {
                            viewModel.onEvent(Event.OpenProjectFromHistory(it))
                        },
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                    ),
                    headlineContent = {
                        Text(
                            text = it.title,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleMedium,
                            lineHeight = MaterialTheme.typography.titleMedium.fontSize
                        )
                    },
                    supportingContent = {
                        Text(
                            text = it.path,
                            maxLines = 1,
                            overflow = TextOverflow.StartEllipsis,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                        )
                    },
                    trailingContent = {
                        Row {
                            IconButton(
                                onClick = {
                                    viewModel.onEvent(Event.OnClickEditProject(it))
                                }
                            ) {
                                Icon(Icons.Default.Edit, null)
                            }

                            IconButton(
                                onClick = {
                                    GlobalSettings.recentWorkspaces = recentProjects
                                        .filter {
                                            it != recentProjects[index]
                                        }

                                    recentProjects = GlobalSettings.recentWorkspaces
                                }
                            ) {
                                Icon(Icons.Default.Delete, null)
                            }
                        }
                    }
                )
            }
        }
    }
}