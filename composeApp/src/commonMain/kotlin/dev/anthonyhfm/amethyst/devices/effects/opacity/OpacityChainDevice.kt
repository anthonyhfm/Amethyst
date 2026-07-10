package dev.anthonyhfm.amethyst.devices.effects.opacity

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.engine.heaven.isLit
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.LEDChainDevice
import dev.anthonyhfm.amethyst.ui.components.primitives.ChainDeviceShell
import dev.anthonyhfm.amethyst.ui.components.primitives.Dial
import dev.anthonyhfm.amethyst.ui.components.DialType
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt
import dev.anthonyhfm.amethyst.devices.ChainDeviceFactory

class OpacityChainDevice : LEDChainDevice<OpacityChainDeviceState>() {
    override val state = MutableStateFlow(OpacityChainDeviceState())
    override val helpRef = "Opacity"

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()
        val isSelected = selections.any { it.selectionUUID == this.selectionUUID }

        ChainDeviceShell(
            title = "Opacity",
            isSelected = isSelected,
            isDragging = isDragging.value,
            modifier = Modifier.width(100.dp),
            titleBarModifier = LocalTitleBarModifier.current
        ) {
            var beforeState = deviceState.copy()

            Dial(
                type = DialType.Continuous,
                title = "Opacity",
                text = "${(deviceState.opacity * 100).roundToInt()}%",
                value = deviceState.opacity,
                modifier = Modifier
                    .align(Alignment.Center),
                onStartValueChange = { beforeState = state.value.copy() },
                onValueChange = { v -> state.update { it.copy(opacity = v.coerceIn(0f, 1f)) } },
                onFinishValueChange = { pushStateChange(beforeState, state.value) },
                onResolveTextValue = {
                    it.removeSuffix("%").trim().toIntOrNull()?.coerceIn(0, 100)?.let { v ->
                        state.update { s -> s.copy(opacity = v / 100f) }
                    }
                },
            )
        }
    }

    override fun ledSignalEnter(n: List<Signal.LED>) {
        signalExit?.invoke(n.map { signal ->
            if (signal.color.isLit()) signal.copy(opacity = state.value.opacity)
            else signal  // off signal passes through unmodified (opacity=1f default) to clear its slot
        })
    }

    companion object : ChainDeviceFactory<OpacityChainDeviceState> {
        override val stateClass = OpacityChainDeviceState::class
        override val serializer = OpacityChainDeviceState.serializer()
        override fun create() = OpacityChainDevice()
    }
}

@Serializable
data class OpacityChainDeviceState(
    val opacity: Float = 1f,
) : DeviceState()
