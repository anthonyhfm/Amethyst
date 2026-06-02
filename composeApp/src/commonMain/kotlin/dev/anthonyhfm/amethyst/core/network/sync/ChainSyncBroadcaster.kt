package dev.anthonyhfm.amethyst.core.network.sync

import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ConnectEvent
import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectProvider
import dev.anthonyhfm.amethyst.core.network.connect.pathOf
import dev.anthonyhfm.amethyst.core.network.connect.toChainAddress
import dev.anthonyhfm.amethyst.devices.DeviceRegistry
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.effects.choke.ChokeChainDevice
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDeviceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class ChainSyncBroadcaster(
    private val provider: AmethystConnectProvider,
    private val scope: CoroutineScope
) {
    private data class PendingStateChange(
        val chainPath: dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ChainPath,
        val path: dev.anthonyhfm.amethyst.core.network.connect.DevicePath,
        val state: DeviceState
    )

    private data class ObservedDevice(
        val device: GenericChainDevice<*>,
        val job: Job
    )

    private val pendingStateChanges = mutableMapOf<String, PendingStateChange>()
    private val pendingStateJobs = mutableMapOf<String, Job>()
    private val observedDevices = mutableMapOf<String, ObservedDevice>()
    private var scanJob: Job? = null

    fun start() {
        if (scanJob != null) return
        refreshDeviceStateObservers()
        scanJob = scope.launch {
            while (true) {
                delay(500)
                refreshDeviceStateObservers()
            }
        }
    }

    fun stop() {
        scanJob?.cancel()
        scanJob = null
        pendingStateJobs.values.forEach { it.cancel() }
        pendingStateJobs.clear()
        pendingStateChanges.clear()
        observedDevices.values.forEach { it.job.cancel() }
        observedDevices.clear()
    }

    fun refreshDeviceStateObservers() {
        val devices = collectCurrentDevices()
        val currentIds = devices.map { it.selectionUUID }.toSet()

        observedDevices
            .filter { (deviceId, observed) -> deviceId !in currentIds || devices.firstOrNull { it.selectionUUID == deviceId } !== observed.device }
            .keys
            .toList()
            .forEach { deviceId ->
                observedDevices.remove(deviceId)?.job?.cancel()
            }

        devices.forEach { device ->
            if (observedDevices[device.selectionUUID]?.device === device) return@forEach
            observedDevices[device.selectionUUID]?.job?.cancel()
            observedDevices[device.selectionUUID] = ObservedDevice(
                device = device,
                job = scope.launch {
                    device.state
                        .drop(1)
                        .collect { state ->
                            if (ChainSyncCoordinator.shouldSuppressDeviceStateBroadcast(device.selectionUUID)) {
                                return@collect
                            }
                            onDeviceStateChanged(device, state)
                        }
                }
            )
        }
    }

    fun onDevicePlaced(chain: Chain, device: GenericChainDevice<*>, atIndex: Int) {
        val address = chain.toChainAddress() ?: return
        scope.launch {
            provider.send(
                ConnectEvent.ChainDevicePlaced(
                    chainPath = address.chainPath,
                    parentPath = address.parentPath,
                    deviceId = device.selectionUUID,
                    atIndex = atIndex,
                    initialState = DeviceRegistry.pack(device)
                )
            )
            WorkspaceSyncCoordinator.triggerVerification()
            refreshDeviceStateObservers()
        }
    }

    fun onDeviceRemoved(chain: Chain, deviceId: String) {
        val address = chain.toChainAddress() ?: return
        scope.launch {
            provider.send(
                ConnectEvent.ChainDeviceRemoved(
                    chainPath = address.chainPath,
                    parentPath = address.parentPath,
                    deviceId = deviceId
                )
            )
            WorkspaceSyncCoordinator.triggerVerification()
            refreshDeviceStateObservers()
        }
    }

    fun onDeviceMoved(
        chainBefore: Chain,
        chainAfter: Chain,
        device: GenericChainDevice<*>,
        fromIndex: Int,
        toIndex: Int
    ) {
        val fromAddress = chainBefore.toChainAddress() ?: return
        val toAddress = chainAfter.toChainAddress() ?: return

        scope.launch {
            if (fromAddress.chainPath == toAddress.chainPath) {
                provider.send(
                    ConnectEvent.ChainDeviceMoved(
                        chainPath = fromAddress.chainPath,
                        fromParentPath = fromAddress.parentPath,
                        toParentPath = toAddress.parentPath,
                        deviceId = device.selectionUUID,
                        fromIndex = fromIndex,
                        toIndex = toIndex
                    )
                )
            } else {
                provider.send(
                    ConnectEvent.ChainDeviceRemoved(
                        chainPath = fromAddress.chainPath,
                        parentPath = fromAddress.parentPath,
                        deviceId = device.selectionUUID
                    )
                )
                provider.send(
                    ConnectEvent.ChainDevicePlaced(
                        chainPath = toAddress.chainPath,
                        parentPath = toAddress.parentPath,
                        deviceId = device.selectionUUID,
                        atIndex = toIndex,
                        initialState = DeviceRegistry.pack(device)
                    )
                )
            }
            WorkspaceSyncCoordinator.triggerVerification()
            refreshDeviceStateObservers()
        }
    }

    fun onDeviceStateChanged(device: GenericChainDevice<*>, state: DeviceState) {
        val lightsPath = dev.anthonyhfm.amethyst.workspace.WorkspaceRepository.lightsChain.pathOf(device)
        val samplingPath = dev.anthonyhfm.amethyst.workspace.WorkspaceRepository.samplingChain.pathOf(device)
        val chainPath = when {
            lightsPath != null -> dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ChainPath.LIGHTS
            samplingPath != null -> dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ChainPath.SAMPLING
            else -> return
        }
        val path = lightsPath ?: samplingPath ?: return

        enqueueStateChange(
            deviceId = device.selectionUUID,
            pending = PendingStateChange(chainPath, path, DeviceRegistry.pack(device))
        )
    }

    fun onGroupStateChanged(device: GenericChainDevice<*>, before: DeviceState, after: DeviceState) {
        val lightsPath = dev.anthonyhfm.amethyst.workspace.WorkspaceRepository.lightsChain.pathOf(device)
        val samplingPath = dev.anthonyhfm.amethyst.workspace.WorkspaceRepository.samplingChain.pathOf(device)
        val chainPath = when {
            lightsPath != null -> dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ChainPath.LIGHTS
            samplingPath != null -> dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ChainPath.SAMPLING
            else -> return
        }
        val path = lightsPath ?: samplingPath ?: return

        scope.launch {
            groupEvents(chainPath, path, before, after).forEach { provider.send(it) }
            WorkspaceSyncCoordinator.triggerVerification()
            refreshDeviceStateObservers()
        }
    }

    private fun enqueueStateChange(deviceId: String, pending: PendingStateChange) {
        pendingStateChanges[deviceId] = pending
        pendingStateJobs[deviceId]?.cancel()
        pendingStateJobs[deviceId] = scope.launch {
            delay(50)
            val latest = pendingStateChanges.remove(deviceId) ?: return@launch
            pendingStateJobs.remove(deviceId)
            provider.send(
                ConnectEvent.DeviceStateChanged(
                    chainPath = latest.chainPath,
                    path = latest.path,
                    state = latest.state
                )
            )
            WorkspaceSyncCoordinator.triggerVerification()
        }
    }

    private fun collectCurrentDevices(): List<GenericChainDevice<*>> {
        val devices = mutableListOf<GenericChainDevice<*>>()
        collectDevices(dev.anthonyhfm.amethyst.workspace.WorkspaceRepository.lightsChain, devices)
        collectDevices(dev.anthonyhfm.amethyst.workspace.WorkspaceRepository.samplingChain, devices)
        return devices.distinctBy { it.selectionUUID }
    }

    private fun collectDevices(chain: Chain, devices: MutableList<GenericChainDevice<*>>) {
        chain.devices.value.forEach { device ->
            devices += device
            when (device) {
                is GroupChainDevice -> {
                    device.state.value.groups.forEach { group ->
                        collectDevices(group.chain, devices)
                    }
                }

                is MultiGroupChainDevice -> {
                    device.state.value.groups.forEach { group ->
                        collectDevices(group.chain, devices)
                    }
                    collectDevices(device.preprocessChain, devices)
                }

                is ChokeChainDevice -> collectDevices(device.state.value.chain, devices)
            }
        }
    }

    private fun groupEvents(
        chainPath: dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ChainPath,
        devicePath: dev.anthonyhfm.amethyst.core.network.connect.DevicePath,
        before: DeviceState,
        after: DeviceState
    ): List<ConnectEvent> {
        return when {
            before is GroupChainDeviceState && after is GroupChainDeviceState ->
                diffGroups(chainPath, devicePath, before.groups, after.groups)
            before is MultiGroupChainDeviceState && after is MultiGroupChainDeviceState ->
                diffGroups(chainPath, devicePath, before.groups, after.groups)
            else -> emptyList()
        }
    }

    private fun diffGroups(
        chainPath: dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ChainPath,
        devicePath: dev.anthonyhfm.amethyst.core.network.connect.DevicePath,
        before: List<dev.anthonyhfm.amethyst.devices.effects.group.data.Group>,
        after: List<dev.anthonyhfm.amethyst.devices.effects.group.data.Group>
    ): List<ConnectEvent> {
        val beforeIds = before.map { it.id }
        val afterIds = after.map { it.id }
        val events = mutableListOf<ConnectEvent>()

        after.forEachIndexed { index, group ->
            if (group.id !in beforeIds) {
                events += ConnectEvent.GroupCreated(chainPath, devicePath, index, group.name)
            }
        }

        before.forEachIndexed { index, group ->
            if (group.id !in afterIds) {
                events += ConnectEvent.GroupRemoved(chainPath, devicePath, index)
            }
        }

        after.forEachIndexed { index, group ->
            val beforeIndex = beforeIds.indexOf(group.id)
            if (beforeIndex != -1 && beforeIndex != index) {
                events += ConnectEvent.GroupReordered(chainPath, devicePath, beforeIndex, index)
            }
            val beforeGroup = before.getOrNull(beforeIndex)
            if (beforeGroup != null && beforeGroup.name != group.name) {
                events += ConnectEvent.GroupRenamed(chainPath, devicePath, index, group.name)
            }
        }

        return events
    }
}
