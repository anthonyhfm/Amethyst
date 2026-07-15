package dev.anthonyhfm.amethyst.devices.effects.clear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.engine.echo.Echo
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.devices.ChainDeviceFactory
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.ui.components.primitives.ChainDeviceShell
import dev.anthonyhfm.amethyst.ui.components.primitives.Checkbox
import dev.anthonyhfm.amethyst.ui.components.primitives.Separator
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

class ClearChainDevice : GenericChainDevice<ClearChainDeviceState>() {
    override val state = MutableStateFlow(ClearChainDeviceState())
    override val helpRef = "Clear"

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()
        val isSelected = selections.any { it.selectionUUID == selectionUUID }

        ChainDeviceShell(
            title = "Clear",
            isSelected = isSelected,
            isDragging = isDragging.value,
            modifier = Modifier.width(140.dp),
            titleBarModifier = LocalTitleBarModifier.current
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(
                    vertical = 12.dp,
                    horizontal = 12.dp
                ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val entries = listOf(
                    "Lights" to deviceState.clearLights,
                    "Audio"  to deviceState.clearAudio,
                    "Multi"  to deviceState.clearMulti,
                )

                Column(
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    entries.forEachIndexed { index, (label, checked) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { value ->
                                    val before = state.value
                                    state.update {
                                        when (label) {
                                            "Lights" -> it.copy(clearLights = value)
                                            "Audio"  -> it.copy(clearAudio = value)
                                            else     -> it.copy(clearMulti = value)
                                        }
                                    }
                                    pushStateChange(before, state.value)
                                },
                                size = 18.dp,
                                iconSize = 14.dp,
                            )

                            Text(
                                text = label,
                                style = Theme[typography][small],
                                color = Theme[colors][foreground]
                            )
                        }
                    }
                }
            }
        }
    }

    override fun signalEnter(n: List<Signal>) {
        signalExit?.invoke(n)

        n.forEach { signal ->
            val released = when (signal) {
                is Signal.LED  -> signal.color == Color.Black
                is Signal.Midi -> signal.velocity == 0
                else           -> false
            }

            if (released) {
                Heaven.schedule(1.0) {
                    val s = state.value
                    if (s.clearLights) Heaven.clear()
                    if (s.clearAudio) Echo.stopAll()
                    if (s.clearMulti)  WorkspaceRepository.resetMulti()
                }
            }
        }
    }

    companion object : ChainDeviceFactory<ClearChainDeviceState> {
        override val stateClass = ClearChainDeviceState::class
        override val serializer = ClearChainDeviceState.serializer()
        override fun create() = ClearChainDevice()
    }
}

@Serializable
data class ClearChainDeviceState(
    val clearLights: Boolean = true,
    val clearAudio: Boolean = true,
    val clearMulti: Boolean = true,
) : DeviceState()
