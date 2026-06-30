package dev.anthonyhfm.amethyst.core.controls.automapping

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import dev.anthonyhfm.amethyst.core.controls.ModifierKeysState
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.effects.choke.ChokeChainDevice
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDevice
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.modes.defaults.TimelineWorkspaceMode
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement
import dev.anthonyhfm.amethyst.timeline.TimelineRepository
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDevice
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.LaunchpadPadFilter
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

object AutomappingManager {
    private const val SHOTGUN_LAYER = 998
    private const val FEEDBACK_LAYER = 999
    private const val FEEDBACK_DURATION_MS = 180.0

    private val _state = MutableStateFlow(AutomappingState())
    val state: StateFlow<AutomappingState> = _state.asStateFlow()

    private val _revision = MutableStateFlow(0)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        scope.launch {
            combine(
                _state,
                WorkspaceRepository.mode,
                SelectionManager.selections,
                TimelineRepository.tracks,
                _revision
            ) { state, mode, selections, tracks, _ ->
                val target = resolveActiveTarget() ?: return@combine clearShotgunLayer()
                
                val effectiveDomain: AutomappingChainDomain = when (mode) {
                    is TimelineWorkspaceMode -> target.domain

                    else -> return@combine clearShotgunLayer()
                }

                val selectedClip = resolveAutomappingSelectedClip(
                    domain = effectiveDomain,
                    tracks = tracks,
                    selections = selections,
                ) ?: return@combine clearShotgunLayer()

                val mappedPads = mutableMapOf<LaunchpadPadFilter, Boolean>()
                val targetDevice = target.parentDevice
                val groups = when (targetDevice) {
                    is GroupChainDevice -> targetDevice.state.value.groups
                    is MultiGroupChainDevice -> targetDevice.state.value.groups
                    else -> emptyList()
                }
                
                groups.forEach { group ->
                    val groupPads = gatherMappedPads(
                        chain = group.chain,
                        targetState = buildChainDeviceFromAutomappingClip(selectedClip)?.let { StateChain.packDevice(it) }
                    )
                    groupPads.forEach { (filter, hasClip) ->
                        mappedPads[filter] = mappedPads[filter] == true || hasClip
                    }
                }
                val signals = mutableListOf<Signal>()

                Heaven.devices.forEach { device ->
                    if (device.deviceConfig.launchpadDevice == null) return@forEach
                    val launchpadId = device.launchpadId
                    val mappedForDevice = mappedPads.filter { it.key.launchpadId == launchpadId }.map { it.key.localX to it.key.localY }.toSet()
                    val posX = device.position.value.x.toInt()
                    val posY = device.position.value.y.toInt()

                    for (x in 0 until device.layout.cols) {
                        for (y in 0 until device.layout.rows) {
                            val isMapped = mappedForDevice.contains(x to y)
                            val hasClip = isMapped && mappedPads.any { it.key.launchpadId == launchpadId && it.key.localX == x && it.key.localY == y && it.value }
                            signals.add(
                                Signal.LED(
                                    origin = AutomappingManager,
                                    x = posX + x,
                                    y = posY + y,
                                    color = if (hasClip) Color.Green else if (isMapped) Color.Red else Color.Gray,
                                    layer = SHOTGUN_LAYER
                                )
                            )
                        }
                    }
                }
                signals
            }.collect { signals ->
                if (signals.isNotEmpty()) {
                    @Suppress("UNCHECKED_CAST")
                    Heaven.midiEnter(signals as List<Signal.LED>)
                }
            }
        }
    }

    private fun clearShotgunLayer(): List<Signal> {
        val signals = mutableListOf<Signal>()
        Heaven.devices.forEach { device ->
            val posX = device.position.value.x.toInt()
            val posY = device.position.value.y.toInt()
            for (x in 0 until device.layout.cols) {
                for (y in 0 until device.layout.rows) {
                    signals.add(
                        Signal.LED(
                            origin = AutomappingManager,
                            x = posX + x,
                            y = posY + y,
                            color = Color.Black,
                            layer = SHOTGUN_LAYER
                        )
                    )
                }
            }
        }
        return signals
    }

    private fun gatherMappedPads(
        chain: Chain,
        targetState: DeviceState?
    ): Map<LaunchpadPadFilter, Boolean> {
        val result = mutableMapOf<LaunchpadPadFilter, Boolean>()
        val devices = chain.devices.value
        val coordFilter = devices.firstOrNull() as? CoordinateFilterChainDevice

        if (coordFilter != null) {
            val hasClip = targetState != null && hasMatchingDevice(devices.drop(1), targetState)
            coordFilter.state.value.padFilters.forEach { filter ->
                result[filter] = result[filter] == true || hasClip
            }
        } else {
            devices.forEach { device ->
                when (device) {
                    is GroupChainDevice -> {
                        device.state.value.groups.forEach { group ->
                            gatherMappedPads(group.chain, targetState).forEach { (filter, hasClip) ->
                                result[filter] = result[filter] == true || hasClip
                            }
                        }
                    }
                    is MultiGroupChainDevice -> {
                        device.state.value.groups.forEach { group ->
                            gatherMappedPads(group.chain, targetState).forEach { (filter, hasClip) ->
                                result[filter] = result[filter] == true || hasClip
                            }
                        }
                    }
                    is ChokeChainDevice -> {
                        gatherMappedPads(device.state.value.chain, targetState).forEach { (filter, hasClip) ->
                            result[filter] = result[filter] == true || hasClip
                        }
                    }
                }
            }
        }
        return result
    }

    private fun hasMatchingDevice(
        devices: List<GenericChainDevice<*>>,
        targetState: DeviceState
    ): Boolean {
        for (device in devices) {
            if (StateChain.packDevice(device) == targetState) return true
            if (device is MultiGroupChainDevice) {
                if (device.state.value.groups.any { group -> hasMatchingDevice(group.chain.devices.value, targetState) }) {
                    return true
                }
            }
        }
        return false
    }

    fun toggleTarget(parentDevice: GenericChainDevice<*>) {
        _state.update { currentState ->
            val isSameTarget = currentState.activeTarget?.parentDeviceSelectionUUID == parentDevice.selectionUUID

            currentState.copy(
                activeTarget = if (isSameTarget) {
                    null
                } else {
                    AutomappingTarget(
                        parentDeviceSelectionUUID = parentDevice.selectionUUID,
                    )
                }
            )
        }
    }

    fun clearTarget() {
        _state.update { currentState ->
            if (currentState.activeTarget == null) currentState else currentState.copy(activeTarget = null)
        }
    }

    fun reset() {
        _state.value = AutomappingState()
    }

    fun clearTargetForDevice(deviceSelectionUUID: String) {
        val activeTarget = state.value.activeTarget ?: return
        if (activeTarget.parentDeviceSelectionUUID == deviceSelectionUUID) {
            clearTarget()
        }
    }

    fun clearTargetIfMissing(
        parentDevice: GenericChainDevice<*>,
    ) {
        // Obsolete function, retained for API compatibility
    }

    fun resolveActiveTarget(): ResolvedAutomappingTarget? {
        val activeTarget = state.value.activeTarget ?: return null

        resolveTargetInChain(
            chain = WorkspaceRepository.lightsChain,
            domain = AutomappingChainDomain.Lights,
            target = activeTarget,
        )?.let { return it }

        resolveTargetInChain(
            chain = WorkspaceRepository.samplingChain,
            domain = AutomappingChainDomain.Sampling,
            target = activeTarget,
        )?.let { return it }

        clearTarget()
        return null
    }

    fun isMappingActive(): Boolean {
        val target = resolveActiveTarget() ?: return false
        val mode = WorkspaceRepository.mode.value
        val effectiveDomain = when (mode) {
            is TimelineWorkspaceMode -> target.domain
            else -> return false
        }
        val selectedClip = resolveAutomappingSelectedClip(
            domain = effectiveDomain,
            tracks = TimelineRepository.tracks.value,
            selections = SelectionManager.selections.value,
        )
        return selectedClip != null
    }

    fun tryCommitPadMapping(
        device: LaunchpadViewportElement,
        globalX: Int,
        globalY: Int,
    ): Boolean {
        if (device.deviceConfig.launchpadDevice == null) return false

        val mode = WorkspaceRepository.mode.value
        val target = resolveActiveTarget() ?: return false

        val effectiveDomain: AutomappingChainDomain = when (mode) {
            is TimelineWorkspaceMode -> target.domain
            else -> return false
        }

        val selectedClip = resolveAutomappingSelectedClip(
            domain = effectiveDomain,
            tracks = TimelineRepository.tracks.value,
            selections = SelectionManager.selections.value,
        ) ?: return false

        val localX = globalX - device.position.value.x.toInt()
        val localY = globalY - device.position.value.y.toInt()
        if (localX !in 0 until device.layout.cols || localY !in 0 until device.layout.rows) {
            return false
        }

        CoroutineScope(Dispatchers.Main).launch {
            val toggled = AutomappingChainMutation.toggleClipOnPad(
                parentDevice = target.parentDevice,
                launchpadId = device.launchpadId,
                localX = localX,
                localY = localY,
                clip = selectedClip,
            )

            if (toggled) {
                _revision.update { it + 1 }
                flashPad(
                    globalX = globalX,
                    globalY = globalY,
                    identifier = "${device.launchpadId}:$localX:$localY",
                )
            }
        }
        return true
    }

    private fun resolveTargetInChain(
        chain: Chain,
        domain: AutomappingChainDomain,
        target: AutomappingTarget,
    ): ResolvedAutomappingTarget? {
        chain.devices.value.forEach { device ->
            if (device.selectionUUID == target.parentDeviceSelectionUUID) {
                if (device is GroupChainDevice || device is MultiGroupChainDevice) {
                    return ResolvedAutomappingTarget(
                        domain = domain,
                        parentDevice = device,
                    )
                }
            }

            when (device) {
                is GroupChainDevice -> {
                    device.state.value.groups.forEach { group ->
                        resolveTargetInChain(group.chain, domain, target)?.let { return it }
                    }
                }

                is MultiGroupChainDevice -> {
                    device.state.value.groups.forEach { group ->
                        resolveTargetInChain(group.chain, domain, target)?.let { return it }
                    }
                }

                is ChokeChainDevice -> {
                    resolveTargetInChain(device.state.value.chain, domain, target)?.let { return it }
                }
            }
        }

        return null
    }

    private fun flashPad(
        globalX: Int,
        globalY: Int,
        identifier: String,
    ) {
        Heaven.cancelJobsForOwner(this, identifier)
        Heaven.midiEnter(
            listOf(
                Signal.LED(
                    origin = this,
                    x = globalX,
                    y = globalY,
                    color = Color.Green,
                    layer = FEEDBACK_LAYER,
                )
            )
        )
        Heaven.schedule(
            delayInMs = FEEDBACK_DURATION_MS,
            owner = this,
            identifier = identifier,
        ) {
            Heaven.midiEnter(
                listOf(
                    Signal.LED(
                        origin = this,
                        x = globalX,
                        y = globalY,
                        color = Color.Black,
                        layer = FEEDBACK_LAYER,
                    )
                )
            )
        }
    }
}

data class AutomappingState(
    val activeTarget: AutomappingTarget? = null,
) {
    val isActive: Boolean
        get() = activeTarget != null
}

data class ResolvedAutomappingTarget(
    val domain: AutomappingChainDomain,
    val parentDevice: GenericChainDevice<*>,
)