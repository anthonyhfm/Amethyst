package dev.anthonyhfm.amethyst.workspace.chain.ui

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import com.composeunstyled.Icon
import com.composeunstyled.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.components.primitives.Button
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonSize
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.ScrollArea
import dev.anthonyhfm.amethyst.ui.components.primitives.ScrollBarOrientation
import dev.anthonyhfm.amethyst.ui.components.primitives.Dial
import dev.anthonyhfm.amethyst.ui.components.DialType
import dev.anthonyhfm.amethyst.ui.theme.cardForeground
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.primary
import dev.anthonyhfm.amethyst.ui.theme.secondary
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import dev.anthonyhfm.amethyst.ui.theme.destructive
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.data.Macro
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Pencil
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenu
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenuItem
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenuItemVariant
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.card
import dev.anthonyhfm.amethyst.ui.theme.selectionForeground
import dev.anthonyhfm.amethyst.devices.audio.automation.AutomationChainDevice
import dev.anthonyhfm.amethyst.core.controls.automation.LiveAutomationTarget
import kotlinx.coroutines.delay
import com.composeunstyled.rememberDialogState
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialog
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogAction
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogCancel
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogDescription
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogFooter
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogHeader
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogTitle
import dev.anthonyhfm.amethyst.ui.components.primitives.Input
import dev.anthonyhfm.amethyst.devices.devicesDepthFirst
import dev.anthonyhfm.amethyst.ui.theme.foreground

private val MacroControlsButtonWidth = 136.dp
private val MacroControlsButtonHeight = 32.dp
private val MacroControlsListHeight = 132.dp
private val MacroControlsAddButtonWidth = 56.dp
private val MacroControlsHorizontalPadding = 24.dp
private val MacroControlsItemWidth = 56.dp
private val MacroControlsDividerWidth = 1.dp
private val MacroControlsItemGap = 16.dp
private val MacroControlsShape = RoundedCornerShape(4.dp)

@Composable
fun BoxScope.MacroControls(
    macrosVisible: Boolean,
    onMacrosVisibleChange: (Boolean) -> Unit,
) {
    val macros by WorkspaceRepository.macros.collectAsState()
    val numDividers = (macros.size - 1).coerceAtLeast(0)
    val numItems = macros.size
    val numAddButton = 1
    val totalElements = numItems + numDividers + numAddButton
    val totalGaps = (totalElements - 1).coerceAtLeast(0)

    val expandedWidth = maxOf(
        MacroControlsButtonWidth,
        MacroControlsHorizontalPadding +
            (MacroControlsItemWidth * numItems) +
            (MacroControlsDividerWidth * numDividers) +
            MacroControlsAddButtonWidth +
            (MacroControlsItemGap * totalGaps)
    )
    val chevronRotation by animateFloatAsState(
        targetValue = if (macrosVisible) 90f else 0f,
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
    )

    BoxWithConstraints(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .align(Alignment.BottomStart),
    ) {
        val availableWidth = maxWidth
        val targetWidth = if (macrosVisible) {
            minOf(expandedWidth, availableWidth)
        } else {
            MacroControlsButtonWidth
        }
        val animatedWidth by animateDpAsState(
            targetValue = targetWidth,
            animationSpec = spring(
                dampingRatio = 0.86f,
                stiffness = Spring.StiffnessMediumLow,
            ),
        )
        val animatedHeight by animateDpAsState(
            targetValue = if (macrosVisible) {
                MacroControlsButtonHeight + MacroControlsListHeight
            } else {
                MacroControlsButtonHeight
            },
            animationSpec = spring(
                dampingRatio = 0.86f,
                stiffness = Spring.StiffnessMediumLow,
            ),
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .width(animatedWidth)
                .height(animatedHeight)
                .clip(MacroControlsShape)
                .background(Theme[colors][selectionForeground])
                .border(1.dp, Theme[colors][border], MacroControlsShape)
                .clipToBounds(),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .width(animatedWidth)
                    .height(MacroControlsListHeight)
                    .clipToBounds(),
            ) {
                if (macrosVisible || animatedHeight > MacroControlsButtonHeight) {
                    ScrollArea(
                        modifier = Modifier
                            .width(animatedWidth)
                            .height(MacroControlsListHeight),
                        orientation = ScrollBarOrientation.Horizontal,
                    ) {
                        MacroList(macros = macros)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .width(MacroControlsButtonWidth)
                    .height(MacroControlsButtonHeight)
                    .clip(MacroControlsShape)
                    .background(Theme[colors][selectionForeground])
                    .clickable {
                        onMacrosVisibleChange(!macrosVisible)
                    }
                    .padding(4.dp),

                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = stringResource(Res.string.workspace_chain_macrocontrols_toggle_macros),
                    modifier = Modifier.rotate(chevronRotation),
                    tint = Theme[colors][foreground]
                )

                Text(
                    text = stringResource(Res.string.workspace_chain_macrocontrols_global_macros),
                    style = Theme[typography][small],
                    color = Theme[colors][foreground],
                    modifier = Modifier
                        .padding(end = 6.dp)
                )
            }
        }
    }
}

@Composable
fun MacroList(
    macros: List<Macro>,
) {
    val mappings by WorkspaceRepository.parameterMappings.collectAsState()
    val automationDevices by WorkspaceRepository.samplingChain.devices
    val automatedMacroValues by produceState(emptyMap<String, Float>(), automationDevices) {
        while (true) {
            value = WorkspaceRepository.samplingChain.devicesDepthFirst()
                .filterIsInstance<AutomationChainDevice>()
                .mapNotNull { device ->
                    val macroId = (device.target as? LiveAutomationTarget.Macro)?.macroId
                    val current = device.currentNormalizedValue
                    if (macroId != null && current != null) macroId to current else null
                }.toMap()
            delay(50)
        }
    }
    var pendingDeletion by remember { mutableStateOf<Pair<Int, Macro>?>(null) }
    var pendingRename by remember { mutableStateOf<Pair<Int, Macro>?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    val deleteDialog = rememberDialogState(initiallyVisible = false)
    Row(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .padding(top = 12.dp, bottom = 4.dp),

        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        macros.forEachIndexed { index, macro ->
            ContextMenu(
                trigger = {
                    Dial(
                        title = macro.name.ifBlank { "Macro ${index + 1}" },
                        text = buildString {
                            automatedMacroValues[macro.id]?.let { automated ->
                                append("AUTO ${((automated * 127f).toInt()).coerceIn(0, 127)} · base ")
                            }
                            append("${macro.value} · ${mappings.count { it.macroId == macro.id }} map")
                        },
                        type = DialType.Steps(IntArray(128) { it }.toList()),
                        value = macro.value,
                        containerColor = Theme[colors][secondary],
                        dialColor = Theme[colors][primary],
                        onResolveTextValue = {
                            val valueText = it.trim().toIntOrNull()

                            valueText?.let { value ->
                                WorkspaceRepository.setMacroValue(
                                    index = index,
                                    macro = macro.copy(
                                        value = value.coerceIn(0, 127)
                                    )
                                )
                            }
                        },
                        onValueChange = {
                            WorkspaceRepository.setMacroValue(
                                index = index,
                                macro = macro.copy(
                                    value = it
                                )
                            )
                        }
                    )
                }
            ) {
                ContextMenuItem(
                    onClick = {
                        pendingRename = index to macro
                        renameDraft = macro.name.ifBlank { "Macro ${index + 1}" }
                    }
                ) {
                    Icon(
                        imageVector = Lucide.Pencil,
                        contentDescription = null,
                        tint = Theme[colors][foreground],
                        modifier = Modifier.size(16.dp),
                    )
                    Text("Rename macro", modifier = Modifier.weight(1f))
                }
                ContextMenuItem(
                    variant = ContextMenuItemVariant.Destructive,
                    onClick = {
                        pendingDeletion = index to macro
                        deleteDialog.visible = true
                    }
                ) {
                    Icon(
                        imageVector = Lucide.Trash2,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Theme[colors][destructive],
                    )
                    Text(
                        text = stringResource(Res.string.workspace_chain_macrocontrols_delete_macro),
                        modifier = Modifier.weight(1f),
                        color = Theme[colors][destructive],
                    )
                }
            }

            if (index < macros.lastIndex) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(64.dp)
                        .background(Theme[colors][cardForeground].copy(0.1f)),
                )
            }
        }

        Box(
            modifier = Modifier
                .width(56.dp)
                .height(116.dp),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = { WorkspaceRepository.addMacro(Macro(value = 0)) },
                variant = ButtonVariant.Ghost,
                size = ButtonSize.Icon,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(Res.string.workspace_chain_macrocontrols_add_macro),
                    tint = Theme[colors][foreground]
                )
            }
        }
    }

    pendingDeletion?.let { (index, macro) ->
        val mappingCount = mappings.count { it.macroId == macro.id }
        AlertDialog(
            state = deleteDialog,
            onDismiss = {
                deleteDialog.visible = false
                pendingDeletion = null
            },
        ) {
            AlertDialogHeader {
                AlertDialogTitle("Delete ${macro.name.ifBlank { "Macro ${index + 1}" }}?")
                AlertDialogDescription("This also removes $mappingCount parameter mappings. The action can be undone.")
            }
            AlertDialogFooter {
                AlertDialogCancel(onClick = {
                    deleteDialog.visible = false
                    pendingDeletion = null
                }) { Text("Cancel") }
                AlertDialogAction(
                    onClick = {
                        WorkspaceRepository.removeMacro(index)
                        deleteDialog.visible = false
                        pendingDeletion = null
                    },
                    variant = ButtonVariant.Destructive,
                ) { Text("Delete") }
            }
        }
    }

    pendingRename?.let { (index, macro) ->
        val renameDialog = rememberDialogState(initiallyVisible = true)
        AlertDialog(
            state = renameDialog,
            onDismiss = { pendingRename = null },
        ) {
            AlertDialogHeader {
                AlertDialogTitle("Rename macro")
                AlertDialogDescription("Choose a short name that identifies this performance control.")
            }
            Input(
                value = renameDraft,
                onValueChange = { renameDraft = it.take(40) },
                placeholder = "Macro ${index + 1}",
            )
            AlertDialogFooter {
                AlertDialogCancel(onClick = { pendingRename = null }) { Text("Cancel") }
                AlertDialogAction(onClick = {
                    WorkspaceRepository.setMacroValue(index, macro.copy(name = renameDraft.trim()))
                    pendingRename = null
                }) { Text("Save") }
            }
        }
    }
}
