package dev.anthonyhfm.amethyst.devices.effects.coordinate_filter

import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.heaven.elements.RawUpdate
import dev.anthonyhfm.amethyst.core.heaven.elements.Signal
import dev.anthonyhfm.amethyst.devices.ChainDevice
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.ui.components.AmethystDevice
import dev.anthonyhfm.amethyst.workspace.WorkspaceController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

class CoordinateFilterChainDevice : ChainDevice<CoordinateFilterChainDeviceState>() {
    override val state = MutableStateFlow(CoordinateFilterChainDeviceState())

    private val customMode: CoordinateFilterWorkspaceMode = CoordinateFilterWorkspaceMode()

    init {
        customMode.modeWakeup = {
            refreshVirtualDevices()
        }

        customMode.onVirtualDevicePress = { x, y, offset ->
            onSetKeyFilter(x, y, offset)
        }
    }

    @Composable
    override fun Content() {
        val controller = koinInject<WorkspaceController>()

        AmethystDevice(
            title = "Coordinate Filter",
            modifier = Modifier
                .width(200.dp)
        ) {
            Button(
                onClick = {
                    controller.switchMode(
                        mode = customMode
                    )
                }
            ) {
                Text(
                    text = "Pick coordinates"
                )
            }
        }
    }

    fun refreshVirtualDevices() {
        // Update the virtual devices to show which coordinates are currently selected
        Heaven.devices.forEach { device ->
            // Clear the device's preview state
            device.previewState.clear()

            // Highlight the selected coordinates
            state.value.filters.forEach { (x, y) ->
                // Check if the coordinate is within the device's grid
                if (x >= device.position.value.x.toInt() && 
                    x < device.position.value.x.toInt() + 10 &&
                    y >= device.position.value.y.toInt() && 
                    y < device.position.value.y.toInt() + 10) {

                    // Calculate the local coordinates within the device
                    val localX = x - device.position.value.x.toInt()
                    val localY = 9 - (y - device.position.value.y.toInt())

                    // Highlight the button
                    device.previewState.sendToPreview(listOf(
                        RawUpdate(localX + localY * 10, Color.Green)
                    ))
                }
            }
        }
    }

    fun onSetKeyFilter(x: Int, y: Int, offset: Offset) {
        val globalX = offset.x.toInt() + x
        val globalY = offset.y.toInt() + (9 - y)

        val coordinatePair = Pair(globalX, globalY)

        val isAlreadyFiltered = state.value.filters.contains(coordinatePair)

        state.update { currentState ->
            if (isAlreadyFiltered) {
                currentState.copy(
                    filters = currentState.filters.filter { it != coordinatePair }
                )
            } else {
                currentState.copy(
                    filters = currentState.filters + coordinatePair
                )
            }
        }

        refreshVirtualDevices()
    }

    override fun midiEnter(n: List<Signal>) {
        // Only let signals with coordinates in the filters list pass through
        val filteredSignals = n.filter { signal ->
            state.value.filters.contains(Pair(signal.x, signal.y))
        }

        if (filteredSignals.isNotEmpty()) {
            midiExit?.invoke(filteredSignals)
        }
    }
}

@Serializable
data class CoordinateFilterChainDeviceState(
    val filters: List<Pair<Int, Int>> = emptyList()
) : DeviceState()
