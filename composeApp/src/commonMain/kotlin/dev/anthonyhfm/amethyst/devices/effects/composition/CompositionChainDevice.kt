package dev.anthonyhfm.amethyst.devices.effects.composition

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Diamond
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.devices.ChainDeviceFactory
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.LEDChainDevice
import dev.anthonyhfm.amethyst.ui.components.primitives.Button
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonSize
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.ChainDeviceShell
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.primaryForeground
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable

class CompositionChainDevice : LEDChainDevice<CompositionChainDeviceState>() {
    override val state = MutableStateFlow(CompositionChainDeviceState())
    override val helpRef = "Composition"

    private val customMode = CompositionWorkspaceMode()

    override fun ledSignalEnter(n: List<Signal.LED>) {
        // Transparent pass-through of the signals
        signalExit?.invoke(n)
    }

    @Composable
    override fun Content() {
        val selections by SelectionManager.selections.collectAsState()

        ChainDeviceShell(
            title = "Composition",
            isSelected = selections.any { it.selectionUUID == this.selectionUUID },
            isDragging = isDragging.value,
            modifier = Modifier.width(120.dp),
            titleBarModifier = LocalTitleBarModifier.current,
        ) {
            Button(
                onClick = {
                    WorkspaceRepository.switchMode(mode = customMode)
                },
                variant = ButtonVariant.Default,
                size = ButtonSize.IconLarge,
            ) {
                Icon(
                    imageVector = Icons.TwoTone.Diamond,
                    contentDescription = "Open Composition Workspace",
                    modifier = Modifier.size(36.dp),
                    tint = Theme[colors][primaryForeground],
                )
            }
        }
    }

    companion object : ChainDeviceFactory<CompositionChainDeviceState> {
        override val stateClass = CompositionChainDeviceState::class
        override val serializer = CompositionChainDeviceState.serializer()
        override fun create() = CompositionChainDevice()
    }
}

@Serializable
class CompositionChainDeviceState : DeviceState()
