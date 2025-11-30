package dev.anthonyhfm.amethyst.devices.effects.coordinate_filter

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.ui.components.AmethystDevice
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

class CoordinateFilterChainDevice : GenericChainDevice<CoordinateFilterChainDeviceState>() {
    override val state = MutableStateFlow(CoordinateFilterChainDeviceState())

    private val customMode: CoordinateFilterWorkspaceMode = CoordinateFilterWorkspaceMode()
    private val dragVisitedPads: MutableSet<Pair<Int, Int>> = mutableSetOf()
    private var dragRemoveMode: Boolean = false

    init {
        customMode.modeClose = {
            Heaven.clear()
        }

        customMode.onVirtualDeviceDragStart = { x, y ->
            beginVirtualDrag(x, y)
        }

        customMode.onVirtualDeviceDrag = { x, y ->
            continueVirtualDrag(x, y)
        }

        customMode.onVirtualDeviceDragEnd = {
            endVirtualDrag()
        }

        customMode.modeWakeup = {
            refreshVirtualDevices()
        }
    }

    @Composable
    override fun Content() {
        val selections by SelectionManager.selections.collectAsState()

        AmethystDevice(
            title = "Coordinate Filter",
            isSelected = selections.any { it.selectionUUID == this.selectionUUID },
            isDragging = isDragging.value,
            modifier = Modifier
                .width(140.dp)
        ) {
            FilledIconButton(
                onClick = {
                    WorkspaceRepository.switchMode(mode = customMode)
                },
                modifier = Modifier
                    .size(72.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Pick",
                    modifier = Modifier
                        .size(36.dp)
                )
            }
        }
    }

    private fun beginVirtualDrag(x: Int, y: Int) {
        isDragging.value = true
        dragVisitedPads.clear()
        dragRemoveMode = state.value.filters.contains(Pair(x, y))

        applyDragAt(x, y)
    }

    private fun continueVirtualDrag(x: Int, y: Int) {
        if (!isDragging.value) {
            beginVirtualDrag(x, y)
            return
        }

        applyDragAt(x, y)
    }

    private fun endVirtualDrag() {
        isDragging.value = false
        dragVisitedPads.clear()
    }

    private fun applyDragAt(x: Int, y: Int) {
        if (!dragVisitedPads.add(Pair(x, y))) return

        setFilterState(x, y, enabled = !dragRemoveMode)
    }

    private fun setFilterState(x: Int, y: Int, enabled: Boolean) {
        val coordinatePair = Pair(x, y)
        val isAlreadyFiltered = state.value.filters.contains(coordinatePair)

        if (enabled == isAlreadyFiltered) return

        val stateBefore = state.value

        state.update { currentState ->
            if (enabled) {
                currentState.copy(
                    filters = currentState.filters + coordinatePair
                )
            } else {
                currentState.copy(
                    filters = currentState.filters.filter { it != coordinatePair }
                )
            }
        }

        pushStateChange(stateBefore, state.value)

        Heaven.midiEnter(
            listOf(
                Signal.LED(
                    origin = this,
                    x = x,
                    y = y,
                    color = if (enabled) Color.Green else Color.Black,
                    layer = 0
                )
            )
        )
    }

    fun refreshVirtualDevices() {
        Heaven.clear()

        Heaven.midiEnter(
            state.value.filters.map {
                Signal.LED(
                    origin = this,
                    x = it.first,
                    y = it.second,
                    color = Color.Green,
                    layer = 0
                )
            }
        )
    }

    fun onSetKeyFilter(x: Int, y: Int) {
        val isAlreadyFiltered = state.value.filters.contains(Pair(x, y))
        setFilterState(x, y, enabled = !isAlreadyFiltered)
    }

    override fun signalEnter(n: List<Signal>) {
        val filteredSignals = n.filter { signal ->
            when (signal) {
                is Signal.LED -> {
                    state.value.filters.contains(Pair(signal.x, signal.y))
                }

                is Signal.Midi -> {
                    state.value.filters.contains(Pair(signal.x, signal.y))
                }

                else -> false
            }
        }

        if (filteredSignals.isNotEmpty()) {
            signalExit?.invoke(filteredSignals)
        }
    }
}

@Serializable
data class CoordinateFilterChainDeviceState(
    val filters: List<Pair<Int, Int>> = emptyList()
) : DeviceState()
