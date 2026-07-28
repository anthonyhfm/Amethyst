package dev.anthonyhfm.amethyst.devices.effects.coordinate_filter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.core.engine.heaven.RawLEDUpdate
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.devices.ChainDeviceFactory
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.ui.components.primitives.Button
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonSize
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.ChainDeviceShell
import dev.anthonyhfm.amethyst.ui.components.primitives.ScaleToFit
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadIdealised
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadMk2
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadPro
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadProMk3
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadX
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportMidiFighter64
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportMystrix
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.primaryForeground
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

class CoordinateFilterChainDevice : GenericChainDevice<CoordinateFilterChainDeviceState>() {
    override val state = MutableStateFlow(CoordinateFilterChainDeviceState())
    override val helpRef = "CoordinateFilter"

    private val customMode: CoordinateFilterWorkspaceMode = CoordinateFilterWorkspaceMode()
    private val dragVisitedPads: MutableSet<LaunchpadPadFilter> = mutableSetOf()
    private var dragRemoveMode: Boolean = false
    private val stateObserverScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        customMode.modeClose = {
            Heaven.clear()
        }

        customMode.onVirtualDeviceDragStart = { device, localX, localY ->
            beginVirtualDrag(device, localX, localY)
        }

        customMode.onVirtualDeviceDrag = { device, localX, localY ->
            continueVirtualDrag(device, localX, localY)
        }

        customMode.onVirtualDeviceDragEnd = {
            endVirtualDrag()
        }

        customMode.modeWakeup = {
            refreshVirtualDevices()
        }

        stateObserverScope.launch {
            state
                .map { it.filters to it.padFilters }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    if (WorkspaceRepository.mode.value === customMode) {
                        refreshVirtualDevices()
                    }
                }
        }
    }

    @Composable
    override fun Content() {
        val selections by SelectionManager.selections.collectAsState()

        ChainDeviceShell(
            title = "Coordinate Filter",
            isSelected = selections.any { it.selectionUUID == this.selectionUUID },
            isDragging = isDragging.value,
            modifier = if (Heaven.devices.size == 1) {
                Modifier
                    .width(280.dp - 58.dp)
            } else {
                Modifier
                    .width(140.dp)
            },
            titleBarModifier = LocalTitleBarModifier.current,
        ) {
            if (Heaven.devices.size == 1) {
                VirtualDeviceContainer()
            } else {
                Button(
                    onClick = {
                        WorkspaceRepository.switchMode(mode = customMode)
                    },
                    variant = ButtonVariant.Default,
                    size = ButtonSize.IconLarge,
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Pick",
                        modifier = Modifier
                            .size(36.dp),
                        tint = Theme[colors][primaryForeground],
                    )
                }
            }
        }
    }

    @Composable
    private fun VirtualDeviceContainer() {
        val original = Heaven.devices.firstOrNull() ?: return
        val currentState by state.collectAsState()

        val newInstance = remember(original) {
            when (original) {
                is ViewportLaunchpadPro -> ViewportLaunchpadPro()
                is ViewportLaunchpadIdealised -> ViewportLaunchpadIdealised()
                is ViewportLaunchpadMk2 -> ViewportLaunchpadMk2()
                is ViewportLaunchpadX -> ViewportLaunchpadX()
                is ViewportLaunchpadProMk3 -> ViewportLaunchpadProMk3()
                is ViewportMystrix -> ViewportMystrix()
                is ViewportMidiFighter64 -> ViewportMidiFighter64()

                else -> error("Not supported viewport device type for CoordinateFilter preview")
            }
        }

        fun buildPreviewUpdates(): List<RawLEDUpdate> {
            return if (currentState.padFilters.isNotEmpty()) {
                currentState.padFilters
                    .filter { it.launchpadId == original.launchpadId }
                    .map {
                        val posX = it.localX + original.layout.offsetX
                        val posY = (original.layout.rows - 1 - it.localY) + original.layout.offsetY
                        RawLEDUpdate(
                            index = posX + posY * 10,
                            color = Color.Green
                        )
                    }
            } else {
                // Legacy fallback: global coordinate pairs (from old saves / Ableton imports).
                currentState.filters.map { (gx, gy) ->
                    RawLEDUpdate(
                        index = gx + (9 - gy) * 10,
                        color = Color.Green
                    )
                }
            }
        }

        newInstance.onCapturePad = { (down, localX, localY) ->
            if (down) {
                onSetKeyFilter(device = original, localX = localX, localY = localY)
            }
        }

        LaunchedEffect(currentState.padFilters, currentState.filters) {
            newInstance.previewState.clear()
            newInstance.previewState.sendToPreview(buildPreviewUpdates())
        }

        Box(
            modifier = Modifier
                .padding(8.dp)
                .shadow(elevation = 4.dp, shape = newInstance.shape)
                .aspectRatio(1f)
                .clip(newInstance.shape)
        ) {
            ScaleToFit {
                newInstance.Content()
            }
        }
    }

    private fun beginVirtualDrag(device: LaunchpadViewportElement, localX: Int, localY: Int) {
        isDragging.value = true
        dragVisitedPads.clear()
        dragRemoveMode = state.value.padFilters.contains(LaunchpadPadFilter(device.launchpadId, localX, localY))

        applyDragAt(device, localX, localY)
    }

    private fun continueVirtualDrag(device: LaunchpadViewportElement, localX: Int, localY: Int) {
        if (!isDragging.value) {
            beginVirtualDrag(device, localX, localY)
            return
        }

        applyDragAt(device, localX, localY)
    }

    private fun endVirtualDrag() {
        isDragging.value = false
        dragVisitedPads.clear()
    }

    private fun applyDragAt(device: LaunchpadViewportElement, localX: Int, localY: Int) {
        val pad = LaunchpadPadFilter(device.launchpadId, localX, localY)
        if (!dragVisitedPads.add(pad)) return

        setFilterState(device, localX, localY, enabled = !dragRemoveMode)
    }

    private fun setFilterState(device: LaunchpadViewportElement, localX: Int, localY: Int, enabled: Boolean) {
        val padFilter = LaunchpadPadFilter(device.launchpadId, localX, localY)
        val isAlreadyFiltered = state.value.padFilters.contains(padFilter)

        if (enabled == isAlreadyFiltered) return

        val stateBefore = state.value

        state.update { currentState ->
            if (enabled) {
                currentState.copy(padFilters = currentState.padFilters + padFilter)
            } else {
                currentState.copy(padFilters = currentState.padFilters.filter { it != padFilter })
            }
        }

        pushStateChange(stateBefore, state.value)
    }

    fun refreshVirtualDevices() {
        val signals = if (state.value.padFilters.isNotEmpty()) {
            state.value.padFilters.mapNotNull { filter ->
                val device = Heaven.devices.firstOrNull { it.launchpadId == filter.launchpadId }
                    ?: return@mapNotNull null
                Signal.LED(
                    origin = this,
                    x = filter.localX + device.position.value.x.toInt(),
                    y = filter.localY + device.position.value.y.toInt(),
                    color = Color.Green,
                    layer = 0
                )
            }
        } else {
            state.value.filters.map {
                Signal.LED(origin = this, x = it.first, y = it.second, color = Color.Green, layer = 0)
            }
        }

        Heaven.clear {
            if (WorkspaceRepository.mode.value === customMode) {
                Heaven.midiEnter(signals)
            }
        }
    }

    fun onSetKeyFilter(device: LaunchpadViewportElement, localX: Int, localY: Int) {
        val padFilter = LaunchpadPadFilter(device.launchpadId, localX, localY)
        val isAlreadyFiltered = state.value.padFilters.contains(padFilter)
        setFilterState(device, localX, localY, enabled = !isAlreadyFiltered)
    }

    override fun signalEnter(n: List<Signal>) {
        val globalFilters: Set<Pair<Int, Int>> = if (state.value.padFilters.isNotEmpty()) {
            state.value.padFilters.mapNotNull { filter ->
                val device = Heaven.devices.firstOrNull { it.launchpadId == filter.launchpadId }
                    ?: return@mapNotNull null
                Pair(
                    filter.localX + device.position.value.x.toInt(),
                    filter.localY + device.position.value.y.toInt()
                )
            }.toSet()
        } else {
            // Legacy fallback: global coordinate pairs (from old saves / Ableton imports)
            state.value.filters.toSet()
        }

        val filteredSignals = n.filter { signal ->
            when (signal) {
                is Signal.LED  -> globalFilters.contains(Pair(signal.x, signal.y))
                is Signal.Midi -> globalFilters.contains(Pair(signal.x, signal.y))
                else -> false
            }
        }

        if (filteredSignals.isNotEmpty()) {
            signalExit?.invoke(filteredSignals)
        }
    }

    companion object : ChainDeviceFactory<CoordinateFilterChainDeviceState> {
        override val stateClass = CoordinateFilterChainDeviceState::class
        override val serializer = CoordinateFilterChainDeviceState.serializer()
        override fun create() = CoordinateFilterChainDevice()
    }
}


@Serializable
data class LaunchpadPadFilter(
    val launchpadId: String,
    val localX: Int,
    val localY: Int
)

@Serializable
data class CoordinateFilterChainDeviceState(
    /** Legacy field – kept for backward-compatible deserialization only. Not written on save. */
    val filters: List<Pair<Int, Int>> = emptyList(),
    val padFilters: List<LaunchpadPadFilter> = emptyList()
) : DeviceState()
