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
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.effects.choke.ChokeChainDevice
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDevice
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement
import dev.anthonyhfm.amethyst.timeline.TimelineRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AutomappingState(
    val activeTarget: AutomappingTarget? = null,
    val isTriggerHeld: Boolean = false,
) {
    val isActive: Boolean
        get() = activeTarget != null
}

data class ResolvedAutomappingTarget(
    val domain: AutomappingChainDomain,
    val parentDevice: GroupChainDevice,
    val groupIndex: Int,
    val group: Group,
)

object AutomappingManager {
    private const val FEEDBACK_LAYER = 999
    private const val FEEDBACK_DURATION_MS = 180.0

    private val _state = MutableStateFlow(AutomappingState())
    val state: StateFlow<AutomappingState> = _state.asStateFlow()

    private var isTriggerKeyPressed: Boolean = false

    fun handleKeyEvent(event: KeyEvent) {
        when (event.type) {
            KeyEventType.KeyDown -> {
                if (event.key == Key.A) {
                    isTriggerKeyPressed = true
                }
            }

            KeyEventType.KeyUp -> {
                if (event.key == Key.A) {
                    isTriggerKeyPressed = false
                }
            }

            else -> Unit
        }

        refreshTriggerHeld()
    }

    fun toggleTarget(parentDevice: GroupChainDevice, groupId: String) {
        _state.update { currentState ->
            val isSameTarget = currentState.activeTarget?.parentDeviceSelectionUUID == parentDevice.selectionUUID &&
                currentState.activeTarget.groupId == groupId

            currentState.copy(
                activeTarget = if (isSameTarget) {
                    null
                } else {
                    AutomappingTarget(
                        parentDeviceSelectionUUID = parentDevice.selectionUUID,
                        groupId = groupId,
                    )
                }
            )
        }
    }

    fun isTarget(parentDeviceSelectionUUID: String, groupId: String): Boolean {
        val activeTarget = state.value.activeTarget ?: return false
        return activeTarget.parentDeviceSelectionUUID == parentDeviceSelectionUUID &&
            activeTarget.groupId == groupId
    }

    fun clearTarget() {
        _state.update { currentState ->
            if (currentState.activeTarget == null) currentState else currentState.copy(activeTarget = null)
        }
    }

    fun reset() {
        isTriggerKeyPressed = false
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
        groups: List<Group>,
    ) {
        val activeTarget = state.value.activeTarget ?: return
        if (activeTarget.parentDeviceSelectionUUID != parentDevice.selectionUUID) return
        if (groups.none { group -> group.id == activeTarget.groupId }) {
            clearTarget()
        }
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

    fun tryCommitPadMapping(
        device: LaunchpadViewportElement,
        globalX: Int,
        globalY: Int,
    ): Boolean {
        if (!state.value.isTriggerHeld) return false
        if (device.deviceConfig.launchpadDevice == null) return false

        val mode = WorkspaceRepository.mode.value
        val target = resolveActiveTarget() ?: return false

        // In Timeline mode use the target's own domain so the user can map from the timeline view.
        // In chain modes require that the current mode's domain matches the target's domain.
        val effectiveDomain: AutomappingChainDomain = when (mode) {
            is WorkspaceContract.WorkspaceMode.Timeline -> target.domain
            else -> {
                val modeDomain = resolveAutomappingChainDomain(mode) ?: return false
                if (modeDomain != target.domain) return false
                modeDomain
            }
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

        val multiGroup = AutomappingChainMutation.ensurePadContainer(
            targetGroup = target.group,
            launchpadId = device.launchpadId,
            localX = localX,
            localY = localY,
        )

        val appended = AutomappingChainMutation.appendClipToMultiGroup(
            multiGroup = multiGroup,
            clip = selectedClip,
        )

        if (!appended) return false

        flashPad(
            globalX = globalX,
            globalY = globalY,
            identifier = "${device.launchpadId}:$localX:$localY",
        )
        return true
    }

    private fun refreshTriggerHeld() {
        val isHeld = isTriggerKeyPressed &&
            ModifierKeysState.isAltPressed

        _state.update { currentState ->
            if (currentState.isTriggerHeld == isHeld) currentState else currentState.copy(isTriggerHeld = isHeld)
        }
    }

    private fun resolveTargetInChain(
        chain: Chain,
        domain: AutomappingChainDomain,
        target: AutomappingTarget,
    ): ResolvedAutomappingTarget? {
        chain.devices.value.forEach { device ->
            if (device.selectionUUID == target.parentDeviceSelectionUUID) {
                val groupDevice = device as? GroupChainDevice ?: return null
                val groupIndex = groupDevice.state.value.groups.indexOfFirst { group ->
                    group.id == target.groupId
                }

                if (groupIndex >= 0) {
                    return ResolvedAutomappingTarget(
                        domain = domain,
                        parentDevice = groupDevice,
                        groupIndex = groupIndex,
                        group = groupDevice.state.value.groups[groupIndex],
                    )
                }

                return null
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
