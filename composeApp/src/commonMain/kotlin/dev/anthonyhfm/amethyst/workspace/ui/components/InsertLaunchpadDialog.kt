package dev.anthonyhfm.amethyst.workspace.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.rememberDialogState
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialog
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogCancel
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogDescription
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogFooter
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogHeader
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogTitle
import dev.anthonyhfm.amethyst.ui.components.primitives.Button
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonSize
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.DefaultShape
import dev.anthonyhfm.amethyst.ui.components.primitives.ScaleToFit
import dev.anthonyhfm.amethyst.ui.components.primitives.ScrollArea
import dev.anthonyhfm.amethyst.ui.components.primitives.ScrollBarOrientation
import dev.anthonyhfm.amethyst.ui.components.primitives.Tabs
import dev.anthonyhfm.amethyst.ui.components.primitives.TabsList
import dev.anthonyhfm.amethyst.ui.components.primitives.TabsTrigger
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadIdealised
import dev.anthonyhfm.amethyst.ui.theme.background
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.p
import dev.anthonyhfm.amethyst.ui.theme.primary
import dev.anthonyhfm.amethyst.ui.theme.typography
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadMk2
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadPro
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadProMk3
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadX
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportMidiFighter64
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportMystrix
import dev.anthonyhfm.amethyst.ui.theme.selectionBorder
import dev.anthonyhfm.amethyst.ui.theme.selectionSurface
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement

private data class VirtualLaunchpadOption(
    val category: LaunchpadCategory,
    val title: String,
    val device: LaunchpadViewportElement,
)

private enum class LaunchpadCategory(
    val displayName: String,
) {
    Novation("Novation Launchpads"),
    Other("Other devices"),
}

@Composable
fun InsertLaunchpadDialog(
    onEvent: (WorkspaceContract.Event) -> Unit,
) {
    val dialogState = rememberDialogState(initiallyVisible = true)
    val options = remember {
        listOf(
            VirtualLaunchpadOption(
                category = LaunchpadCategory.Novation,
                title = "Launchpad Pro",
                device = ViewportLaunchpadPro(interactive = false),
            ),
            VirtualLaunchpadOption(
                category = LaunchpadCategory.Novation,
                title = "Launchpad X",
                device = ViewportLaunchpadX(interactive = false),
            ),
            VirtualLaunchpadOption(
                category = LaunchpadCategory.Novation,
                title = "Launchpad Pro MK3",
                device = ViewportLaunchpadProMk3(interactive = false),
            ),
            VirtualLaunchpadOption(
                category = LaunchpadCategory.Novation,
                title = "Launchpad MK2",
                device = ViewportLaunchpadMk2(interactive = false),
            ),
            VirtualLaunchpadOption(
                category = LaunchpadCategory.Novation,
                title = "Idealised",
                device = ViewportLaunchpadIdealised(interactive = false),
            ),
            VirtualLaunchpadOption(
                category = LaunchpadCategory.Other,
                title = "Mystrix",
                device = ViewportMystrix(interactive = false),
            ),
            VirtualLaunchpadOption(
                category = LaunchpadCategory.Other,
                title = "Midi Fighter 64",
                device = ViewportMidiFighter64(interactive = false),
            ),
        )
    }

    var selectedCategory by remember { mutableStateOf(LaunchpadCategory.Novation) }
    var selectedOptionTitle by remember { mutableStateOf(options.first().title) }
    val filteredOptions = remember(options, selectedCategory) {
        options.filter { it.category == selectedCategory }
    }
    val selectedOption = remember(filteredOptions, selectedOptionTitle) {
        filteredOptions.firstOrNull { it.title == selectedOptionTitle } ?: filteredOptions.first()
    }

    AlertDialog(
        state = dialogState,
        modifier = Modifier
            .width(640.dp),
        onDismiss = {
            onEvent(WorkspaceContract.Event.DismissVirtualDevicePicker)
        },
    ) {
        AlertDialogHeader {
            AlertDialogTitle("Add Virtual Launchpad Device")
            AlertDialogDescription("Compare the layouts at a comfortable size, then add the controller that fits this workspace best.")
        }

        Column(
            modifier = Modifier
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Tabs(
                selectedTab = selectedCategory.name,
                tabs = LaunchpadCategory.entries.map { it.name },
                content = {
                    TabsList(
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        LaunchpadCategory.entries.forEach { category ->
                            TabsTrigger(
                                modifier = Modifier
                                    .weight(1f),
                                key = category.name,
                                selected = selectedCategory.name == category.name,
                                onSelected = { selectedCategory = category },
                            ) {
                                Text(
                                    text = category.displayName,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                )
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
            )

            ScrollArea(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(312.dp),
                orientation = ScrollBarOrientation.Horizontal,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    filteredOptions.forEach { option ->
                        VirtualLaunchpadTile(
                            option = option,
                            isSelected = selectedOptionTitle == option.title,
                            onClick = {
                                selectedOptionTitle = option.title
                            },
                        )
                    }
                }
            }
        }

        AlertDialogFooter(
            modifier = Modifier.fillMaxWidth(),
        ) {
            AlertDialogCancel(
                onClick = {
                    onEvent(WorkspaceContract.Event.DismissVirtualDevicePicker)
                },
            ) {
                Text("Cancel")
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = {
                    onEvent(WorkspaceContract.Event.AddDeviceToViewport(selectedOption.device))
                },
                size = ButtonSize.Small,
            ) {
                Text("Add to Workspace")
            }
        }
    }
}

@Composable
private fun VirtualLaunchpadTile(
    option: VirtualLaunchpadOption,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (isSelected) {
        Theme[colors][selectionBorder]
    } else {
        Theme[colors][border]
    }

    val cardBackground = if (isSelected) {
        Theme[colors][selectionSurface].copy(alpha = 0.15f)
    } else {
        Theme[colors][background]
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = modifier
                .height(250.dp)
                .border(2.dp, borderColor, DefaultShape)
                .background(cardBackground)
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center,
            ) {
                ScaleToFit {
                    option.device.Content()
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = option.title,
                style = Theme[typography][p],
                color = Theme[colors][foreground],
            )
        }
    }
}
