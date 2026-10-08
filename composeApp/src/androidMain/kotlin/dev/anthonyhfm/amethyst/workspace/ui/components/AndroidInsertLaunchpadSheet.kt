package dev.anthonyhfm.amethyst.workspace.ui.components

import amethyst.composeapp.generated.resources.workspace_swap_launchpad_title
import amethyst.composeapp.generated.resources.workspace_swap_launchpad_description
import amethyst.composeapp.generated.resources.workspace_swap_launchpad_confirm
import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.workspace_insert_launchpad_add
import amethyst.composeapp.generated.resources.workspace_insert_launchpad_cancel
import amethyst.composeapp.generated.resources.workspace_insert_launchpad_category_novation
import amethyst.composeapp.generated.resources.workspace_insert_launchpad_category_other
import amethyst.composeapp.generated.resources.workspace_insert_launchpad_description
import amethyst.composeapp.generated.resources.workspace_insert_launchpad_idealised
import amethyst.composeapp.generated.resources.workspace_insert_launchpad_title
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.ui.components.primitives.ScaleToFit
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadIdealised
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadMk2
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadPro
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadProMk3
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadX
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportMidiFighter64
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportMystrix
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private enum class AndroidLaunchpadModel(
    val title: String,
    val otherDevice: Boolean = false,
) {
    Pro(title = "Launchpad Pro"),
    X(title = "Launchpad X"),
    ProMk3(title = "Launchpad Pro MK3"),
    Mk2(title = "Launchpad MK2"),
    Idealised(title = "Idealised"),
    Mystrix(title = "Mystrix", otherDevice = true),
    MidiFighter64(title = "Midi Fighter 64", otherDevice = true);

    fun createDevice(interactive: Boolean): LaunchpadViewportElement = when (this) {
        Pro -> ViewportLaunchpadPro(interactive = interactive)
        X -> ViewportLaunchpadX(interactive = interactive)
        ProMk3 -> ViewportLaunchpadProMk3(interactive = interactive)
        Mk2 -> ViewportLaunchpadMk2(interactive = interactive)
        Idealised -> ViewportLaunchpadIdealised(interactive = interactive)
        Mystrix -> ViewportMystrix(interactive = interactive)
        MidiFighter64 -> ViewportMidiFighter64(interactive = interactive)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AndroidInsertLaunchpadSheet() {
    val replacementId by WorkspaceRepository.devicePickerReplacementId.collectAsState()
    val isReplacing = replacementId != null
    val coroutineScope = rememberCoroutineScope()
    var selectedModel by rememberSaveable { mutableStateOf(AndroidLaunchpadModel.Pro) }
    var otherDevices by rememberSaveable { mutableStateOf(false) }
    var isAdding by remember { mutableStateOf(false) }
    var isDismissing by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { value -> !isAdding || value != SheetValue.Hidden },
    )
    val models = remember(otherDevices) {
        AndroidLaunchpadModel.entries.filter { it.otherDevice == otherDevices }
    }
    val dismiss = {
        isDismissing = true
        coroutineScope.launch {
            sheetState.hide()
            WorkspaceRepository.closeDevicePicker()
        }
        Unit
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (!isAdding) {
                WorkspaceRepository.closeDevicePicker()
            }
        },
        sheetState = sheetState,
        sheetMaxWidth = Dp.Unspecified,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(weight = 1f)
                    .selectableGroup(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
                verticalArrangement = Arrangement.spacedBy(space = 16.dp),
            ) {
                item(
                    span = { GridItemSpan(currentLineSpan = maxLineSpan) },
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(space = 16.dp),
                    ) {
                        Text(
                            text = stringResource(resource = if (isReplacing) Res.string.workspace_swap_launchpad_title else Res.string.workspace_insert_launchpad_title),
                            style = MaterialTheme.typography.headlineSmall,
                        )

                        Text(
                            text = stringResource(resource = if (isReplacing) Res.string.workspace_swap_launchpad_description else Res.string.workspace_insert_launchpad_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
                        ) {
                            FilterChip(
                                selected = !otherDevices,
                                onClick = {
                                    otherDevices = false
                                    if (selectedModel.otherDevice) {
                                        selectedModel = AndroidLaunchpadModel.Pro
                                    }
                                },
                                enabled = !isAdding && !isDismissing,
                                label = {
                                    Text(text = stringResource(resource = Res.string.workspace_insert_launchpad_category_novation))
                                },
                            )

                            FilterChip(
                                selected = otherDevices,
                                onClick = {
                                    otherDevices = true
                                    if (!selectedModel.otherDevice) {
                                        selectedModel = AndroidLaunchpadModel.Mystrix
                                    }
                                },
                                enabled = !isAdding && !isDismissing,
                                label = {
                                    Text(text = stringResource(resource = Res.string.workspace_insert_launchpad_category_other))
                                },
                            )
                        }
                    }
                }

                items(
                    items = models,
                    key = { it.name },
                ) { model ->
                    AndroidLaunchpadChoice(
                        model = model,
                        selected = selectedModel == model,
                        enabled = !isAdding && !isDismissing,
                        onSelect = { selectedModel = model },
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(space = 8.dp),
            ) {
                Button(
                    onClick = {
                        isAdding = true
                        coroutineScope.launch {
                            try {
                                val device = selectedModel.createDevice(interactive = true)
                                WorkspaceRepository.completeDevicePicker(element = device)
                            } finally {
                                isAdding = false
                            }

                            isDismissing = true
                            sheetState.hide()
                            WorkspaceRepository.closeDevicePicker()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth(),
                    enabled = !isAdding && !isDismissing,
                ) {
                    Text(text = stringResource(resource = if (isReplacing) Res.string.workspace_swap_launchpad_confirm else Res.string.workspace_insert_launchpad_add))
                }

                TextButton(
                    onClick = dismiss,
                    modifier = Modifier
                        .fillMaxWidth(),
                    enabled = !isAdding && !isDismissing,
                ) {
                    Text(text = stringResource(resource = Res.string.workspace_insert_launchpad_cancel))
                }
            }
        }
    }
}

@Composable
private fun AndroidLaunchpadChoice(
    model: AndroidLaunchpadModel,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    val device = remember(model) { model.createDevice(interactive = false) }

    DisposableEffect(device) {
        onDispose { device.close() }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onSelect,
            ),
        shape = MaterialTheme.shapes.large,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = BorderStroke(
            width = if (selected) {
                2.dp
            } else {
                1.dp
            },
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .padding(all = 12.dp),
            verticalArrangement = Arrangement.spacedBy(space = 8.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height = 152.dp)
                    .clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(size = 140.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ScaleToFit {
                        device.Content()
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
            ) {
                RadioButton(
                    selected = selected,
                    onClick = null,
                    enabled = enabled,
                )

                Text(
                    text = if (model == AndroidLaunchpadModel.Idealised) {
                        stringResource(resource = Res.string.workspace_insert_launchpad_idealised)
                    } else {
                        model.title
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}
