package dev.anthonyhfm.amethyst.home.ui.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import com.composables.icons.lucide.Ellipsis
import com.composables.icons.lucide.FolderOpen
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Trash2
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.composeunstyled.Text as UnstyledText
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.core.data.settings.GlobalSettings
import dev.anthonyhfm.amethyst.home.HomeCommandSurface
import dev.anthonyhfm.amethyst.home.ui.views.RecentViewContract.Event
import dev.anthonyhfm.amethyst.ui.components.primitives.Button
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonSize
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.Card
import dev.anthonyhfm.amethyst.ui.components.primitives.CardContent
import dev.anthonyhfm.amethyst.ui.components.primitives.CardDescription
import dev.anthonyhfm.amethyst.ui.components.primitives.CardHeader
import dev.anthonyhfm.amethyst.ui.components.primitives.CardTitle
import dev.anthonyhfm.amethyst.ui.components.primitives.DefaultShape
import dev.anthonyhfm.amethyst.ui.components.primitives.DropdownMenu
import dev.anthonyhfm.amethyst.ui.components.primitives.DropdownMenuContent
import dev.anthonyhfm.amethyst.ui.components.primitives.DropdownMenuItem
import dev.anthonyhfm.amethyst.ui.components.primitives.DropdownMenuTrigger
import dev.anthonyhfm.amethyst.ui.components.primitives.ScrollArea
import dev.anthonyhfm.amethyst.ui.components.primitives.TypographyH2
import dev.anthonyhfm.amethyst.ui.components.primitives.TypographyLead
import dev.anthonyhfm.amethyst.ui.components.primitives.TypographyMuted
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.card
import dev.anthonyhfm.amethyst.ui.theme.cardForeground
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.destructive
import dev.anthonyhfm.amethyst.ui.theme.destructiveForeground
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.p
import dev.anthonyhfm.amethyst.ui.theme.popoverForeground
import dev.anthonyhfm.amethyst.ui.theme.primaryForeground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import dev.anthonyhfm.amethyst.workspace.data.RecentWorkspace
import kotlinx.coroutines.flow.collect

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

    var recentProjects: List<RecentWorkspace> by remember { mutableStateOf(loadRecentProjects()) }

    val currentBackStackEntry by navigator.currentBackStackEntryFlow.collectAsState(initial = navigator.currentBackStackEntry)
    LaunchedEffect(currentBackStackEntry) {
        recentProjects = loadRecentProjects()
    }
    
    LaunchedEffect(Unit) {
        viewModel.effect.collect {
            when (it) {
                RecentViewContract.Effect.OpenWorkspace -> {
                    onOpenWorkspace()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        HomeCommandSurface.commands.collect { command ->
            when (command) {
                HomeCommandSurface.HomeCommand.NewProject ->
                    viewModel.onEvent(Event.OnClickNewProject)
                HomeCommandSurface.HomeCommand.OpenProject ->
                    viewModel.onEvent(Event.OnClickOpenProject)
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            RecentViewHeader(
                onOpenProject = { viewModel.onEvent(Event.OnClickOpenProject) },
                onCreateProject = { viewModel.onEvent(Event.OnClickNewProject) },
            )

            if (recentProjects.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    EmptyRecentProjectsCard()
                }
            } else {
                ScrollArea(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        recentProjects.forEachIndexed { index, project ->
                            RecentProjectCard(
                                project = project,
                                onOpen = {
                                    viewModel.onEvent(Event.OpenProjectFromHistory(project))
                                },
                                onEdit = {
                                    viewModel.onEvent(Event.OnClickEditProject(project))
                                },
                                onDelete = {
                                    GlobalSettings.recentWorkspaces = recentProjects
                                        .filterNot { it.path == project.path }
                                    recentProjects = loadRecentProjects()
                                },
                            )
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp),
        )
    }
}

@Composable
private fun RecentViewHeader(
    onOpenProject: () -> Unit,
    onCreateProject: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
    ) {
        val stackedActions = maxWidth < 720.dp

        if (stackedActions) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TypographyH2("Recent Projects")
                    TypographyLead("Open an existing workspace, continue where you left off, or start a new project.")
                }

                RecentActions(
                    stacked = true,
                    onOpenProject = onOpenProject,
                    onCreateProject = onCreateProject,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TypographyH2("Recent Projects")
                    TypographyLead("Open an existing workspace, continue where you left off, or start a new project.")
                }

                RecentActions(
                    stacked = false,
                    onOpenProject = onOpenProject,
                    onCreateProject = onCreateProject,
                )
            }
        }
    }
}

@Composable
private fun RecentActions(
    stacked: Boolean,
    onOpenProject: () -> Unit,
    onCreateProject: () -> Unit,
) {
    val openVariant = ButtonVariant.Outline
    val createVariant = ButtonVariant.Default

    if (stacked) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onOpenProject,
                modifier = Modifier.fillMaxWidth(),
                variant = openVariant,
            ) {
                Icon(
                    imageVector = Lucide.FolderOpen,
                    contentDescription = null,
                    tint = buttonContentColor(openVariant),
                )
                UnstyledText("Open Project")
            }

            Button(
                onClick = onCreateProject,
                modifier = Modifier.fillMaxWidth(),
                variant = createVariant,
            ) {
                Icon(
                    imageVector = Lucide.Plus,
                    contentDescription = null,
                    tint = buttonContentColor(createVariant),
                )
                UnstyledText("New Project")
            }
        }
    } else {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onOpenProject,
                variant = openVariant,
            ) {
                Icon(
                    imageVector = Lucide.FolderOpen,
                    contentDescription = null,
                    tint = buttonContentColor(openVariant),
                )
                UnstyledText("Open Project")
            }

            Button(
                onClick = onCreateProject,
                variant = createVariant,
            ) {
                Icon(
                    imageVector = Lucide.Plus,
                    contentDescription = null,
                    tint = buttonContentColor(createVariant),
                )
                UnstyledText("New Project")
            }
        }
    }
}

@Composable
private fun EmptyRecentProjectsCard(
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp),
    ) {
        CardHeader {
            CardTitle("No recent projects yet")
            CardDescription("Open an existing `.ame` workspace or create a new project to start building your next performance.")
        }

        CardContent {
            TypographyMuted("Use the actions in the header above to open an existing workspace or create your first project.")
        }
    }
}

@Composable
private fun RecentProjectCard(
    project: RecentWorkspace,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var actionsExpanded by remember(project.path) { mutableStateOf(false) }
    val folderPathLabel = remember(project.path) { displayFolderPath(project.path) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, DefaultShape)
            .clip(DefaultShape)
            .border(1.dp, Theme[colors][border], DefaultShape)
            .background(Theme[colors][card])
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = project.title,
                style = Theme[typography][p],
                fontWeight = FontWeight.SemiBold,
                color = Theme[colors][cardForeground],
            )
            Text(
                text = folderPathLabel,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                style = Theme[typography][small],
                color = Theme[colors][mutedForeground],
            )
        }

        DropdownMenu(
            expanded = actionsExpanded,
            onExpandRequest = { actionsExpanded = true },
            onDismissRequest = { actionsExpanded = false },
        ) {
            DropdownMenuTrigger(
                onClick = { actionsExpanded = true },
            ) {
                Button(
                    onClick = { actionsExpanded = true },
                    variant = ButtonVariant.Ghost,
                    size = ButtonSize.Icon,
                ) {
                    Icon(
                        imageVector = Lucide.Ellipsis,
                        contentDescription = "Project actions",
                        tint = buttonContentColor(ButtonVariant.Ghost),
                    )
                }
            }

            DropdownMenuContent(
                expanded = actionsExpanded,
                onDismissRequest = { actionsExpanded = false },
            ) {
                DropdownMenuItem(
                    onClick = {
                        actionsExpanded = false
                        onOpen()
                    },
                ) {
                    Icon(
                        imageVector = Lucide.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Theme[colors][popoverForeground],
                    )
                    UnstyledText("Open")
                }
                DropdownMenuItem(
                    onClick = {
                        actionsExpanded = false
                        onEdit()
                    },
                ) {
                    Icon(
                        imageVector = Lucide.Pencil,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Theme[colors][popoverForeground],
                    )
                    UnstyledText("Edit details")
                }
                DropdownMenuItem(
                    onClick = {
                        actionsExpanded = false
                        onDelete()
                    },
                    destructive = true,
                ) {
                    Icon(
                        imageVector = Lucide.Trash2,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Theme[colors][destructive],
                    )
                    UnstyledText("Remove from recent")
                }
            }
        }
    }
}

@Composable
private fun buttonContentColor(variant: ButtonVariant): Color {
    return when (variant) {
        ButtonVariant.Default -> Theme[colors][primaryForeground]
        ButtonVariant.Secondary -> Theme[colors][foreground]
        ButtonVariant.Destructive -> Theme[colors][destructiveForeground]
        ButtonVariant.Outline -> Theme[colors][foreground]
        ButtonVariant.Ghost -> Theme[colors][foreground]
        ButtonVariant.Link -> Theme[colors][foreground]
    }
}

private fun loadRecentProjects(): List<RecentWorkspace> {
    return GlobalSettings.recentWorkspaces.sortedByDescending { it.lastOpened }
}

private fun displayFolderPath(path: String): String {
    val normalizedPath = path.replace('\\', '/')
    val separatorIndex = normalizedPath.lastIndexOf('/')
    if (separatorIndex <= 0) return normalizedPath

    val parentPath = normalizedPath.substring(0, separatorIndex).trimEnd('/')
    return abbreviateHomePrefix(parentPath)
}

private fun abbreviateHomePrefix(path: String): String {
    val unixHomePrefixes = listOf("/Users/", "/home/")

    unixHomePrefixes.forEach { prefix ->
        if (path.startsWith(prefix)) {
            val userSeparatorIndex = path.indexOf('/', startIndex = prefix.length)
            return if (userSeparatorIndex == -1) "~" else "~${path.substring(userSeparatorIndex)}"
        }
    }

    val windowsUsersMarker = "/Users/"
    if (path.length > 2 && path[1] == ':' && path.contains(windowsUsersMarker)) {
        val markerIndex = path.indexOf(windowsUsersMarker)
        val userSeparatorIndex = path.indexOf('/', startIndex = markerIndex + windowsUsersMarker.length)
        return if (userSeparatorIndex == -1) "~" else "~${path.substring(userSeparatorIndex)}"
    }

    return path
}
