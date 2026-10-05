package dev.anthonyhfm.amethyst.workspace

import androidx.compose.runtime.snapshots.Snapshot

import dev.anthonyhfm.amethyst.conversion.unipad.data.KeyLED
import dev.anthonyhfm.amethyst.core.engine.audio.source.ByteArrayPcmAudioSource
import dev.anthonyhfm.amethyst.core.engine.audio.source.PreparedAudioSourceCache
import dev.anthonyhfm.amethyst.core.engine.audio.source.PcmAudioSource
import dev.anthonyhfm.amethyst.core.engine.audio.source.ProjectPcmFiles
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.timeline.data.AudioSource
import dev.anthonyhfm.amethyst.devices.effects.composition.CompositionChainDevice
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDevice
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDevice
import dev.anthonyhfm.amethyst.devices.effects.choke.ChokeChainDevice
import dev.anthonyhfm.amethyst.workspace.chain.ui.SignalIndicatorManager
import dev.anthonyhfm.amethyst.workspace.data.SavableWorkspaceData
import java.lang.ref.WeakReference
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class WorkspaceMemoryLifecycleTest {
    @AfterTest
    fun tearDown() {
        WorkspaceRepository.clean()
    }

    @Test
    fun closingProjectsReleasesNestedDevicesAfterSignalTraffic() = runBlocking {
        val references = buildList {
            repeat(4) {
                addAll(openProjectWithNestedDevices())
                WorkspaceRepository.clean()
            }
        }
        assertCollected(references = references)
    }

    @Test
    fun replacingProjectsReleasesOldDevicesAndUndoHistory() = runBlocking {
        val references = openProjectWithNestedDevices()
        WorkspaceRepository.loadWorkspace(workspaceData = SavableWorkspaceData(title = "Replacement"))
        assertCollected(references = references)
    }

    @Test
    fun closingProjectReleasesOriginalAndResampledAudio() = runBlocking {
        val references = openProjectWithPreparedAudio()
        WorkspaceRepository.clean()
        assertCollected(references = references)
    }

    @Test
    fun replacingProjectReleasesMappedAudioAndTemporaryFiles() = runBlocking {
        val references = openProjectWithMappedAudio()
        assertTrue(actual = ProjectPcmFiles.temporaryFileCount() >= 2)
        WorkspaceRepository.loadWorkspace(workspaceData = SavableWorkspaceData(title = "Replacement"))
        assertEquals(expected = 0, actual = ProjectPcmFiles.temporaryFileCount())
        assertCollected(references = references)
    }

    private fun openProjectWithMappedAudio(): List<WeakReference<Any>> {
        val source = AudioSource(
            id = "mapped-project",
            fileName = "long.wav",
            rawData = ByteArray(size = 4_194_304),
            sampleRate = 8_000,
            channels = 1,
            bitDepth = 16,
        )
        WorkspaceRepository.loadWorkspace(workspaceData = SavableWorkspaceData(audioSources = listOf(source)))
        val prepared = PreparedAudioSourceCache.getOrPrepare(
            source = source.pcmSource(),
            outputRate = 16_000,
            retainForProject = true,
        ) as PcmAudioSource
        return listOf(source.pcmBytes, prepared.pcmBytes).map { WeakReference<Any>(it) }
    }

    @Test
    fun conversionDoesNotLeaveBpmObserversRunning() = runBlocking {
        val bpmField = WorkspaceRepository.javaClass.getDeclaredField("_bpm").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val bpm = bpmField.get(WorkspaceRepository) as MutableStateFlow<Double>
        delay(timeMillis = 100)
        val previousSubscribers = bpm.subscriptionCount.value
        repeat(20) {
            KeyLED.convertToKeyframes(data = "on 1 1 a 5\ndelay 100".encodeToByteArray())
        }
        repeat(100) {
            if (bpm.subscriptionCount.value <= previousSubscribers) {
                return@runBlocking
            }
            delay(timeMillis = 20)
        }
        assertTrue(bpm.subscriptionCount.value <= previousSubscribers)
    }

    private fun openProjectWithNestedDevices(): List<WeakReference<Any>> {
        WorkspaceRepository.loadWorkspace(workspaceData = SavableWorkspaceData(title = "Memory regression"))
        val container = ChokeChainDevice()
        val nested = container.state.value.chain
        val devices = listOf(
            CompositionChainDevice(),
            CoordinateFilterChainDevice(),
            KeyframesChainDevice(),
        )
        devices.forEach { nested.add(device = it, fromUser = false) }
        WorkspaceRepository.lightsChain.add(device = container, fromUser = true)
        SignalIndicatorManager.events(chain = nested, slotIndex = 0)
        WorkspaceRepository.lightsChain.signalEnter(n = emptyList<Signal>())
        return (listOf(WorkspaceRepository.lightsChain, nested, container) + devices)
            .map { WeakReference<Any>(it) }
    }

    private fun openProjectWithPreparedAudio(): List<WeakReference<Any>> {
        val bytes = ByteArray(16_000)
        val source = AudioSource(
            id = "memory-audio",
            fileName = "tone.wav",
            rawData = bytes,
            sampleRate = 8_000,
            channels = 1,
            bitDepth = 16,
        )
        WorkspaceRepository.loadWorkspace(
            workspaceData = SavableWorkspaceData(audioSources = listOf(source)),
        )
        val prepared = PreparedAudioSourceCache.getOrPrepare(
            source = ByteArrayPcmAudioSource(
                id = source.id,
                sampleRate = source.sampleRate,
                channels = source.channels,
                bitDepth = source.bitDepth,
                rawData = bytes,
            ),
            outputRate = 16_000,
        )
        return listOf(WeakReference<Any>(bytes), WeakReference<Any>(prepared))
    }

    private suspend fun assertCollected(references: List<WeakReference<Any>>) {
        repeat(100) {
            Snapshot.sendApplyNotifications()
            System.gc()
            if (references.all { it.get() == null }) {
                return
            }
            delay(timeMillis = 20)
        }
        assertTrue(
            actual = references.all { it.get() == null },
            message = "Retained: ${references.mapNotNull { it.get()?.javaClass?.simpleName }}",
        )
    }
}
