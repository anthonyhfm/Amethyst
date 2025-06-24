package dev.anthonyhfm.amethyst.workspace.ui.components

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.novation
import amethyst.composeapp.generated.resources.piano
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadMk2
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadPro
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadProMk3
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadX
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportMidiFighter64
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportMystrix
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.collections.listOf
import kotlin.collections.mapOf
import kotlin.to

@Composable
fun InsertLaunchpadDialog(
    onEvent: (WorkspaceContract.Event) -> Unit
) {
    val virtualDevices: Map<LaunchpadTab, List<LaunchpadViewportElement>> by remember {
        mutableStateOf(
            mapOf(
                LaunchpadTab.Novation to listOf(
                    ViewportLaunchpadPro(interactive = false),
                    ViewportLaunchpadX(interactive = false),
                    ViewportLaunchpadProMk3(interactive = false),
                    ViewportLaunchpadMk2(interactive = false)
                ),
                LaunchpadTab.Other to listOf(
                    ViewportMystrix(interactive = false),
                    ViewportMidiFighter64(interactive = false)
                ),
            )
        )
    }

    var selectedTabIndex by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = {
            onEvent(WorkspaceContract.Event.DismissVirtualDevicePicker)
        },
        title = {
            Text("Add a virtual Launchpad device")
        },
        dismissButton = {
            Button(
                onClick = {
                    onEvent(WorkspaceContract.Event.DismissVirtualDevicePicker)
                }
            ) {
                Text("Cancel")
            }
        },
        confirmButton = { },
        text = {
            Column(
                modifier = Modifier
                    .width(400.dp)
                    .height(450.dp),
            ) {
                LaunchpadTabs(
                    selectedTabIndex = selectedTabIndex,
                    onTabSelected = {
                        selectedTabIndex = it
                    }
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(virtualDevices[LaunchpadTab.entries[selectedTabIndex]] ?: emptyList()) { device ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center,
                                propagateMinConstraints = true
                            ) {
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .clickable {
                                            onEvent(WorkspaceContract.Event.AddDeviceToViewport(device))
                                        }
                                ) {
                                    device.content()
                                }
                            }

                            Text(
                                text = device.name,
                                style = MaterialTheme.typography.labelLarge,
                                lineHeight = MaterialTheme.typography.labelLarge.fontSize,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                    .padding(vertical = 4.dp, horizontal = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    )
}

enum class LaunchpadTab(val displayName: String, val icon: DrawableResource) {
    Novation(
        displayName = "Novation",
        icon = Res.drawable.novation
    ),
    Other(
        displayName = "Other",
        icon = Res.drawable.piano
    )
}

@Composable
fun LaunchpadTabs(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
) {
    TabRow(
        selectedTabIndex = selectedTabIndex,
        containerColor = Color.Transparent,
    ) {
        LaunchpadTab.entries.forEachIndexed { index, tab ->
            LeadingIconTab(
                selected = selectedTabIndex == index,
                onClick = {
                    onTabSelected(index)
                },
                text = {
                    Text(tab.displayName)
                },
                icon = {
                    Icon(
                        painter = painterResource(tab.icon),
                        contentDescription = tab.displayName,
                        modifier = Modifier.size(24.dp)
                    )
                }
            )
        }
    }
}


