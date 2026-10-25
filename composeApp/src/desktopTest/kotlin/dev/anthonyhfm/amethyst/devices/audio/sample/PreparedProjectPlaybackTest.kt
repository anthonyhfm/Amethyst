package dev.anthonyhfm.amethyst.devices.audio.sample

import dev.anthonyhfm.amethyst.core.engine.audio.source.ProjectPcmFiles
import dev.anthonyhfm.amethyst.core.engine.audio.trigger.PadTriggerKey
import dev.anthonyhfm.amethyst.devices.AudioConfiguration
import dev.anthonyhfm.amethyst.devices.AudioProcessingBlock
import dev.anthonyhfm.amethyst.home.data.HomeRepository
import dev.anthonyhfm.amethyst.workspace.audio.AudioLibraryRepository
import io.github.vinceglb.filekit.PlatformFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.measureTime
import kotlinx.coroutines.runBlocking

class PreparedProjectPlaybackTest {
    @Test
    fun allProjectSourcesArePreparedBeforePlaybackAndReuseTheirPcm() = runBlocking {
        val fixture = System.getenv("AMETHYST_PROJECT_MEMORY_FIXTURE")
            ?.takeIf(String::isNotBlank) ?: return@runBlocking
        try {
            val workspace = HomeRepository.loadWorkspaceData(file = PlatformFile(fixture))
            AudioLibraryRepository.load(sources = workspace.audioSources)
            val configuration = AudioConfiguration(
                sampleRate = 48000,
                channels = 2,
                periodFrames = 128,
                maximumBlockFrames = 128,
            )
            val pools = workspace.audioSources.map { source ->
                val state = SampleChainDeviceState(
                    sourceId = source.id,
                    fileName = source.fileName,
                    sampleRate = source.sampleRate,
                    channels = source.channels,
                    bitDepth = source.bitDepth,
                    isLoaded = true,
                    volumeDb = -24f,
                )
                val prepared = checkNotNull(
                    value = SampleRenderSnapshot.prepareSource(state = state, outputSampleRate = 48000),
                )
                assertEquals(expected = 48000, actual = prepared.sampleRate)
                assertSame(
                    expected = prepared,
                    actual = SampleRenderSnapshot.prepareSource(state = state, outputSampleRate = 48000),
                )
                val snapshot = checkNotNull(SampleRenderSnapshot.from(state = state, source = prepared))
                SampleVoicePool(maximumVoices = 1).apply {
                    prepare(configuration = configuration)
                    apply(
                        command = SampleVoiceCommand.Start(
                            targetFrame = 0,
                            key = PadTriggerKey(originId = "test", x = 1, y = 1),
                            snapshot = snapshot,
                        ),
                    )
                }
            }
            val beforePlayback = ProjectPcmFiles.temporaryFileCount()
            val block = AudioProcessingBlock(samples = FloatArray(size = 256), channels = 2, maximumFrames = 128)
            block.configure(frameCount = 128, frameOffset = 0)
            fun render() {
                block.clear()
                pools.forEach { pool -> pool.render(block = block, destinationFrameOffset = 0, frameCount = 128) }
            }
            val firstBlock = measureTime { render() }
            repeat(times = 100) { render() }
            val durations = LongArray(size = 1000)
            repeat(times = durations.size) { index ->
                durations[index] = measureTime { render() }.inWholeNanoseconds
                assertTrue(actual = block.samples.all(Float::isFinite))
            }
            durations.sort()
            println(
                "PreparedPlaybackProfile voices=${pools.size} firstBlock=$firstBlock " +
                    "medianUs=${durations[500] / 1000} p99Us=${durations[990] / 1000} " +
                    "maxUs=${durations.last() / 1000} budgetUs=2666",
            )
            assertEquals(expected = beforePlayback, actual = ProjectPcmFiles.temporaryFileCount())
        } finally {
            AudioLibraryRepository.clear()
            ProjectPcmFiles.clear()
        }
    }
}
