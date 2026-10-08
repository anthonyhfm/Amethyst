package dev.anthonyhfm.amethyst.devices.effects.coordinate_filter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composeunstyled.Icon
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
import dev.anthonyhfm.amethyst.workspace.ViewportRepository
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.containsLocalPad
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.resolveLaunchpadOrigin
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import dev.anthonyhfm.amethyst.devices.TimelineDuration
import dev.anthonyhfm.amethyst.devices.TimelineDurationContext
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

class CoordinateFilterChainDevice : GenericChainDevice<CoordinateFilterChainDeviceState>() {
    override fun dispose() {
        stateObserverScope.cancel()
        super.dispose()
    }

    override fun timelineDuration(context: TimelineDurationContext) =
        TimelineDuration.None
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
                .map { Triple(it.filters, it.padFilters, it.globalFilters) }
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
        val viewportDevices by ViewportRepository.devices.collectAsState()
        val original = viewportDevices.firstOrNull() ?: return
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

        fun buildPreviewUpdates(): List<RawLEDUpdate> =
            resolvedFilters(devices = listOf(original)).map { (x, y) ->
                val localX = x - original.position.value.x.toInt()
                val localY = y - original.position.value.y.toInt()
                RawLEDUpdate(
                    index = localX + original.layout.offsetX +
                        (original.layout.rows - 1 - localY + original.layout.offsetY) * 10,
                    color = Color.Green,
                )
            }

        newInstance.onCapturePad = { (down, localX, localY) ->
            if (down) {
                onSetKeyFilter(device = original, localX = localX, localY = localY)
            }
        }

        DisposableEffect(newInstance) {
            onDispose { newInstance.close() }
        }

        LaunchedEffect(newInstance, currentState.padFilters, currentState.filters, currentState.globalFilters) {
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
        dragRemoveMode = isFiltered(device = device, localX = localX, localY = localY)

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

    private fun isFiltered(device: LaunchpadViewportElement, localX: Int, localY: Int): Boolean {
        val snapshot = state.value
        if (snapshot.followSignalLaunchpad) {
            return Pair(first = localX, second = localY) in snapshot.filters
        }
        val global = localX + device.position.value.x.toInt() to localY + device.position.value.y.toInt()
        return snapshot.padFilters.contains(
            LaunchpadPadFilter(launchpadId = device.launchpadId, localX = localX, localY = localY),
        ) || global in snapshot.globalFilters || (snapshot.padFilters.isEmpty() && global in snapshot.filters)
    }

    private fun setFilterState(device: LaunchpadViewportElement, localX: Int, localY: Int, enabled: Boolean) {
        val padFilter = LaunchpadPadFilter(device.launchpadId, localX, localY)
        val isAlreadyFiltered = isFiltered(device = device, localX = localX, localY = localY)

        if (enabled == isAlreadyFiltered) return

        val stateBefore = state.value
        if (stateBefore.followSignalLaunchpad) {
            val local = Pair(first = localX, second = localY)
            state.update { currentState ->
                currentState.copy(
                    filters = if (enabled) {
                        currentState.filters + local
                    } else {
                        currentState.filters.filterNot { it == local }
                    },
                )
            }
            pushStateChange(before = stateBefore, after = state.value)
            return
        }

        state.update { currentState ->
            val global = localX + device.position.value.x.toInt() to localY + device.position.value.y.toInt()
            val activeGlobals = currentState.globalFilters +
                if (currentState.padFilters.isEmpty()) currentState.filters else emptyList()
            currentState.copy(
                filters = if (currentState.padFilters.isEmpty()) emptyList() else currentState.filters,
                globalFilters = activeGlobals.filterNot { it == global },
                padFilters = if (enabled) {
                    currentState.padFilters + padFilter
                } else {
                    currentState.padFilters.filterNot { it == padFilter }
                },
            )
        }

        pushStateChange(stateBefore, state.value)
    }

    internal fun resolvedFilters(devices: List<LaunchpadViewportElement> = Heaven.devices): Set<Pair<Int, Int>> {
        val snapshot = state.value
        if (snapshot.followSignalLaunchpad) {
            return devices.flatMap { device ->
                snapshot.filters.map { (x, y) ->
                    Pair(first = x + device.position.value.x.toInt(), second = y + device.position.value.y.toInt())
                }
            }.toSet()
        }
        return snapshot.globalFilters.toSet() +
            (if (snapshot.padFilters.isEmpty()) { snapshot.filters.toSet() } else { emptySet() }) +
            snapshot.padFilters.mapNotNull { filter ->
                val device = devices.firstOrNull { it.launchpadId == filter.launchpadId }
                    ?: devices.singleOrNull()
                    ?: return@mapNotNull null
                if (!device.containsLocalPad(x = filter.localX, y = filter.localY)) {
                    return@mapNotNull null
                }
                Pair(
                    first = filter.localX + device.position.value.x.toInt(),
                    second = filter.localY + device.position.value.y.toInt(),
                )
            }
    }

    fun refreshVirtualDevices() {
        val signals = resolvedFilters().map { (x, y) ->
            Signal.LED(origin = this, x = x, y = y, color = Color.Green, layer = 0)
        }
        Heaven.clear {
            if (WorkspaceRepository.mode.value === customMode) {
                Heaven.midiEnter(signals)
            }
        }
    }

    fun onSetKeyFilter(device: LaunchpadViewportElement, localX: Int, localY: Int) {
        val isAlreadyFiltered = isFiltered(device = device, localX = localX, localY = localY)
        setFilterState(device, localX, localY, enabled = !isAlreadyFiltered)
    }

    override fun signalEnter(n: List<Signal>) {
        val globalFilters = resolvedFilters()

        val filteredSignals = n.filter { signal ->
            val coordinate = when (signal) {
                is Signal.LED -> Pair(first = signal.x, second = signal.y)
                is Signal.Midi -> Pair(first = signal.x, second = signal.y)
                else -> return@filter false
            }
            if (state.value.followSignalLaunchpad) {
                val device = resolveLaunchpadOrigin(
                    origin = signal.origin,
                    x = coordinate.first,
                    y = coordinate.second,
                )
                val local = Pair(
                    first = coordinate.first - (device?.position?.value?.x?.toInt() ?: 0),
                    second = coordinate.second - (device?.position?.value?.y?.toInt() ?: 0),
                )
                local in state.value.filters
            } else {
                coordinate in globalFilters
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
    val padFilters: List<LaunchpadPadFilter> = emptyList(),
    val globalFilters: List<Pair<Int, Int>> = emptyList(),
    val followSignalLaunchpad: Boolean = false
) : DeviceState()
