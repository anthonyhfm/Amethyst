package dev.anthonyhfm.amethyst.workspace

import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ConnectEvent
import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ConnectSession
import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ConnectUser
import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectProvider
import dev.anthonyhfm.amethyst.core.network.sync.ChainSyncBroadcaster
import dev.anthonyhfm.amethyst.core.network.sync.ChainSyncCoordinator
import dev.anthonyhfm.amethyst.core.network.sync.DeviceSyncBroadcaster
import dev.anthonyhfm.amethyst.core.network.sync.DeviceSyncCoordinator
import dev.anthonyhfm.amethyst.core.network.sync.ViewportDeviceModelChange
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDevice
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.LaunchpadPadFilter
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadPro
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportMidiFighter64
import dev.anthonyhfm.amethyst.workspace.data.SavableWorkspaceData.SavableViewportLaunchpad.MidiFighter64.MidiFighter64Style
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class LaunchpadModelSwapSyncTest {
    private class RecordingProvider : AmethystConnectProvider() {
        val sent = mutableListOf<ConnectEvent>()

        override suspend fun host(sessionName: String, localUser: ConnectUser): Result<ConnectSession> =
            Result.failure(exception = UnsupportedOperationException())

        override suspend fun join(address: String, localUser: ConnectUser): Result<ConnectSession> =
            Result.failure(exception = UnsupportedOperationException())

        override suspend fun leave() = Unit

        override suspend fun send(event: ConnectEvent) {
            sent.add(element = event)
        }
    }

    @Test
    fun pendingUserEditsPrecedeAtomicModelAndStyleChangeWithoutEchoingRemappedStates() = runTest {
        WorkspaceRepository.clean()
        val provider = RecordingProvider()
        val chains = ChainSyncBroadcaster(provider = provider, scope = this)
        val devices = DeviceSyncBroadcaster(provider = provider, scope = this)
        try {
            val original = ViewportMidiFighter64(initialStyle = MidiFighter64Style.White).apply {
                launchpadId = "target"
            }
            ViewportRepository.setDevices(newDevices = listOf(original))
            val filter = CoordinateFilterChainDevice()
            WorkspaceRepository.samplingChain.add(device = filter, fromUser = false)
            ChainSyncCoordinator.attach(broadcaster = chains)
            DeviceSyncCoordinator.attach(broadcaster = devices)
            chains.start()
            runCurrent()
            filter.state.value = CoordinateFilterChainDeviceState(padFilters = listOf(
                LaunchpadPadFilter(launchpadId = "target", localX = 0, localY = 7),
            ))
            runCurrent()
            WorkspaceRepository.replaceVirtualDevice(deviceId = "target", replacement = ViewportLaunchpadPro())
            runCurrent()
            advanceTimeBy(delayTimeMillis = 100)
            runCurrent()

            assertEquals(expected = 2, actual = provider.sent.size)
            val pending = assertIs<ConnectEvent.DeviceStateChanged>(value = provider.sent[0])
            val beforeState = pending.state as CoordinateFilterChainDeviceState
            assertEquals(expected = 0 to 7, actual = beforeState.padFilters.single().let { it.localX to it.localY })
            val model = assertIs<ConnectEvent.DevicePropertyChanged>(value = provider.sent[1])
            assertEquals(expected = ConnectEvent.DeviceProperty.MODEL, actual = model.property)
            assertEquals(expected = "target", actual = model.deviceId)

            UndoManager.undo()
            runCurrent()
            advanceTimeBy(delayTimeMillis = 100)
            runCurrent()
            assertEquals(expected = 3, actual = provider.sent.size)
            val undo = provider.sent.last() as ConnectEvent.DevicePropertyChanged
            val restoredModel = Json.decodeFromString<ViewportDeviceModelChange>(string = undo.value)
            assertEquals(expected = MidiFighter64Style.White.name, actual = restoredModel.style)
            val remote = dev.anthonyhfm.amethyst.core.network.sync.ViewportDeviceFactory.create(
                type = restoredModel.type,
                id = "target",
                position = androidx.compose.ui.geometry.Offset.Zero,
            )
            try {
                remote.applyNetworkStyle(key = restoredModel.style!!)
                assertEquals(expected = MidiFighter64Style.White, actual = (remote as ViewportMidiFighter64).style)
            } finally {
                remote.close()
            }
        } finally {
            chains.stop()
            ChainSyncCoordinator.detach(broadcaster = chains)
            DeviceSyncCoordinator.detach(broadcaster = devices)
            WorkspaceRepository.clean()
        }
    }
}
