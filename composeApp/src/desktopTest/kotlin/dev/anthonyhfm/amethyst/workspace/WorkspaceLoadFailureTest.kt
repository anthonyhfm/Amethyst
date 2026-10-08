package dev.anthonyhfm.amethyst.workspace

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.devices.ChainDeviceFactory
import dev.anthonyhfm.amethyst.devices.DeviceRegistry
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.LEDChainDevice
import dev.anthonyhfm.amethyst.devices.TimelineDuration
import dev.anthonyhfm.amethyst.devices.TimelineDurationContext
import dev.anthonyhfm.amethyst.devices.effects.transmit.TransmitChainDevice
import dev.anthonyhfm.amethyst.devices.effects.transmit.TransmitChainDeviceState
import dev.anthonyhfm.amethyst.timeline.TimelineRepository
import dev.anthonyhfm.amethyst.workspace.audio.AudioLibraryRepository
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import dev.anthonyhfm.amethyst.workspace.data.SavableWorkspaceData
import dev.anthonyhfm.amethyst.workspace.utils.WorkspaceProjectOpenHelper
import dev.anthonyhfm.amethyst.workspace.utils.WorkspaceProjectOpenResult
import io.github.vinceglb.filekit.PlatformFile
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WorkspaceLoadFailureTest {
    @AfterTest
    fun tearDown() {
        WorkspaceRepository.clean()
    }

    @Test
    fun latePreparationFailureDisposesInstalledRuntimeAndAllowsRetry() {
        val failure = IllegalStateException("Late preparation failure")
        failPreparedLoad(failure = failure)

        WorkspaceRepository.loadWorkspace(workspaceData = SavableWorkspaceData(title = "Retry"))
        assertTrue(actual = WorkspaceRepository.isReady)
        assertEquals(expected = "Retry", actual = WorkspaceRepository.projectName.value)
    }

    @Test
    fun cancellationAfterInstallationDisposesTheRuntimeAndPropagates() {
        failPreparedLoad(failure = CancellationException("Import cancelled"))
    }

    @Test
    fun conversionFailurePreservesPreviouslyLoadedWorkspace() = runBlocking {
        WorkspaceRepository.loadWorkspace(workspaceData = SavableWorkspaceData(title = "Previous"))
        val previousLights = WorkspaceRepository.lightsChain
        val file = File.createTempFile("invalid-project-", ".ame")
        try {
            file.writeBytes(arrayOf<Byte>(1, 2, 3).toByteArray())
            val result = WorkspaceProjectOpenHelper.openProject(file = PlatformFile(file = file))

            assertTrue(actual = result is WorkspaceProjectOpenResult.Failure)
            assertTrue(actual = WorkspaceRepository.isReady)
            assertSame(expected = previousLights, actual = WorkspaceRepository.lightsChain)
            assertEquals(expected = "Previous", actual = WorkspaceRepository.projectName.value)
        } finally {
            file.delete()
        }
    }

    @Test
    fun failureBeforeChainPublicationDisposesStagedDevices() {
        val created = mutableListOf<FailingLifecycleDevice>()
        val factory = object : ChainDeviceFactory<FailingLifecycleState> {
            override val stateClass = FailingLifecycleState::class
            override val serializer = FailingLifecycleState.serializer()

            override fun create(): FailingLifecycleDevice {
                return FailingLifecycleDevice().also { created.add(element = it) }
            }
        }
        DeviceRegistry.register(factory = factory)

        kotlin.test.assertFailsWith<IllegalStateException> {
            WorkspaceRepository.loadWorkspace(
                workspaceData = SavableWorkspaceData(
                    lights = StateChain(
                        devices = listOf(
                            FailingLifecycleState(fail = false),
                            FailingLifecycleState(fail = true),
                        ),
                    ),
                ),
            )
        }

        assertEquals(expected = 2, actual = created.size)
        created.forEach { device ->
            assertNull(actual = device.parentChain)
            assertNull(actual = device.signalExit)
            assertTrue(actual = device.disposed)
        }
        assertFalse(actual = WorkspaceRepository.isReady)
        assertTrue(actual = WorkspaceRepository.lightsChain.devices.value.isEmpty())
    }

    private fun failPreparedLoad(failure: Throwable) {
        var receiver: TransmitChainDevice? = null
        try {
            WorkspaceRepository.loadWorkspace(
                workspaceData = SavableWorkspaceData(
                    title = "Partial project",
                    lights = StateChain(
                        devices = listOf(
                            TransmitChainDeviceState(mode = TransmitChainDeviceState.Mode.Receive),
                        ),
                    ),
                    launchpadDevices = listOf(SavableWorkspaceData.SavableViewportLaunchpad.LaunchpadPro(positionX = 0f, positionY = 0f)),
                ),
                onPrepared = {
                    assertFalse(actual = WorkspaceRepository.isReady)
                    receiver = WorkspaceRepository.lightsChain.devices.value.single() as TransmitChainDevice
                    throw failure
                },
            )
            kotlin.test.fail(message = "Load should fail")
        } catch (actual: Throwable) {
            assertSame(expected = failure, actual = actual)
        }

        assertFalse(actual = WorkspaceRepository.isReady)
        assertNull(actual = WorkspaceRepository.workspaceMeta)
        assertNull(actual = WorkspaceRepository.projectName.value)
        assertTrue(actual = WorkspaceRepository.lightsChain.devices.value.isEmpty())
        assertTrue(actual = WorkspaceRepository.samplingChain.devices.value.isEmpty())
        assertTrue(actual = ViewportRepository.devices.value.isEmpty())
        assertTrue(actual = TimelineRepository.tracks.value.isEmpty())
        assertTrue(actual = AudioLibraryRepository.sources.value.isEmpty())
        assertNull(actual = receiver?.parentChain)
        assertNull(actual = receiver?.signalExit)

        val leakedSignals = mutableListOf<Signal>()
        receiver?.signalExit = { signals -> leakedSignals.addAll(elements = signals) }
        val senderChain = Chain().apply {
            add(device = TransmitChainDevice(), fromUser = false)
        }
        senderChain.signalEnter(
            n = listOf(Signal.LED(origin = null, x = 1, y = 1, color = Color.White)),
        )
        assertTrue(actual = leakedSignals.isEmpty())
        senderChain.dispose()
    }
}

@Serializable
private data class FailingLifecycleState(val fail: Boolean = false) : DeviceState()

private class FailingLifecycleDevice : LEDChainDevice<FailingLifecycleState>() {
    override val state = MutableStateFlow(FailingLifecycleState())
    var disposed = false

    @Composable
    override fun Content() = Unit

    override fun ledSignalEnter(n: List<Signal.LED>) = Unit

    override fun timelineDuration(context: TimelineDurationContext): TimelineDuration = TimelineDuration.None

    override fun onAddedToChain() {
        if (state.value.fail) {
            throw IllegalStateException("Device attachment failed")
        }
    }

    override fun dispose() {
        disposed = true
        super.dispose()
    }
}
