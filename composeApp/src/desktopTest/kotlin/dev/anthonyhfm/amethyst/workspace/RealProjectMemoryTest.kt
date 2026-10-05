package dev.anthonyhfm.amethyst.workspace

import androidx.compose.runtime.snapshots.Snapshot
import dev.anthonyhfm.amethyst.core.engine.audio.source.PreparedAudioSourceCache
import dev.anthonyhfm.amethyst.core.engine.audio.source.ProjectPcmFiles
import dev.anthonyhfm.amethyst.core.engine.echo.Echo
import dev.anthonyhfm.amethyst.core.loading.residentMemoryBytes
import dev.anthonyhfm.amethyst.devices.audio.sample.SampleChainDevice
import dev.anthonyhfm.amethyst.devices.devicesDepthFirst
import dev.anthonyhfm.amethyst.home.data.HomeRepository
import dev.anthonyhfm.amethyst.workspace.audio.AudioLibraryRepository
import io.github.vinceglb.filekit.PlatformFile
import java.lang.ref.WeakReference
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RealProjectMemoryTest {
    private data class AudioProfile(
        val sourceCount: Int,
        val sourcePcmBytes: Long,
        val preparedPcmBytes: Long,
        val preparedCopies: Int,
        val mappedSourcePcmBytes: Long,
        val mappedPreparedPcmBytes: Long,
        val duplicateCopies: Int,
        val pcmReferences: List<WeakReference<Any>>,
    )

    @Test
    fun repeatedProjectImportsKeepPreparedPcmSharedAndReleaseOnClose() = runBlocking {
        val fixture = System.getenv("AMETHYST_PROJECT_MEMORY_FIXTURE")
            ?.takeIf(String::isNotBlank) ?: return@runBlocking
        try {
            Echo.initialize()
            println("MemoryProfile outputRate=${Echo.outputStatus.value.sampleRate}")
            val baseline = settledMemoryBytes()
            repeat(2) { iteration ->
                val profile = importAndProfile(file = PlatformFile(fixture))
                println(
                    "MemoryProfile iteration=${iteration + 1} sourceCount=${profile.sourceCount} " +
                        "sourcePcmBytes=${profile.sourcePcmBytes} preparedPcmBytes=${profile.preparedPcmBytes} " +
                        "preparedCopies=${profile.preparedCopies} duplicateCopies=${profile.duplicateCopies} " +
                        "mappedSourcePcmBytes=${profile.mappedSourcePcmBytes} mappedPreparedPcmBytes=${profile.mappedPreparedPcmBytes} " +
                        "heapAfterGc=${settledMemoryBytes()} residentBytes=${currentResidentBytes()} committedHeap=${Runtime.getRuntime().totalMemory()}",
                )
                assertEquals(expected = 0, actual = profile.duplicateCopies)
                WorkspaceRepository.clean()
                println("MemoryProfile iteration=${iteration + 1} closedHeapAfterGc=${settledMemoryBytes()} baseline=$baseline residentBytes=${currentResidentBytes()} committedHeap=${Runtime.getRuntime().totalMemory()}")
                assertTrue(actual = AudioLibraryRepository.sources.value.isEmpty())
                assertEquals(expected = 0L, actual = PreparedAudioSourceCache.retainedPcmBytes())
                assertEquals(expected = 0L, actual = PreparedAudioSourceCache.mappedPcmBytes())
                assertEquals(expected = 0, actual = ProjectPcmFiles.temporaryFileCount())
                assertTrue(actual = profile.pcmReferences.all { it.get() == null })
            }
        } finally {
            WorkspaceRepository.clean()
            Echo.shutdown()
        }
    }

    private suspend fun importAndProfile(file: PlatformFile): AudioProfile {
        val workspace = HomeRepository.loadWorkspaceData(file = file)
        HomeRepository.openWorkspace(workspace = workspace)
        val devices = WorkspaceRepository.samplingChain.devicesDepthFirst()
            .filterIsInstance<SampleChainDevice>()
        val copies = devices.filter { it.state.value.sourceId != null }
            .groupBy { it.state.value.sourceId }
            .mapValues { (_, samples) -> samples.mapNotNull(SampleChainDevice::preparedAudioSource).toSet().size }
        val sources = AudioLibraryRepository.sources.value.values
        val originalPcm = sources.mapNotNull { it.residentPcmData }.toSet()
        val preparedPcm = devices.mapNotNull(SampleChainDevice::preparedAudioSource).toSet()
        return AudioProfile(
            sourceCount = sources.size,
            sourcePcmBytes = originalPcm.sumOf { it.size.toLong() },
            preparedPcmBytes = preparedPcm.sumOf { it.residentPcmBytes.toLong() },
            mappedSourcePcmBytes = sources.filter { it.residentPcmData == null }.sumOf { it.pcmByteCount.toLong() },
            mappedPreparedPcmBytes = preparedPcm.filter { it.residentPcmBytes == 0 }.sumOf { it.pcmByteCount.toLong() },
            preparedCopies = preparedPcm.size,
            duplicateCopies = copies.values.sumOf { (it - 1).coerceAtLeast(0) },
            pcmReferences = (sources.map { it.pcmBytes } + preparedPcm.map { it.pcmBytes }).map { WeakReference<Any>(it) },
        )
    }

    private fun currentResidentBytes(): Long = ProcessBuilder(
        "ps", "-o", "rss=", "-p", ProcessHandle.current().pid().toString(),
    ).start().inputStream.bufferedReader().use { it.readText().trim().toLong() * 1024 }

    private suspend fun settledMemoryBytes(): Long? {
        Snapshot.sendApplyNotifications()
        System.gc()
        delay(timeMillis = 250)
        System.gc()
        return residentMemoryBytes()
    }
}
