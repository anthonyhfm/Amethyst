package dev.anthonyhfm.amethyst.devices.audio.sample

import dev.anthonyhfm.amethyst.core.engine.audio.source.PreparedAudioSourceCache
import dev.anthonyhfm.amethyst.core.engine.audio.source.ByteArrayPcmAudioSource
import dev.anthonyhfm.amethyst.devices.AudioConfiguration
import dev.anthonyhfm.amethyst.core.engine.echo.AudioPlaybackEngine
import dev.anthonyhfm.amethyst.core.engine.elements.AudioChain
import dev.anthonyhfm.amethyst.timeline.data.AudioSource
import dev.anthonyhfm.amethyst.workspace.audio.AudioLibraryRepository
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

class SamplePreparedAudioSharingTest {
    @AfterTest
    fun tearDown() {
        AudioLibraryRepository.clear()
    }

    @Test
    fun distantRegionsSharePreparedPcmAfterMoreThan64Sources() {
        val sources = List(65) { index -> source(id = "sample-$index") }
        AudioLibraryRepository.load(sources = sources)
        val prepared = sources.map { source ->
            requireNotNull(SampleRenderSnapshot.prepareSource(
                state = state(source = source),
                outputSampleRate = 16_000,
            ))
        }
        val laterRegion = state(source = sources.first()).copy(
            sourceStartFrame = 1,
            sourceEndFrameExclusive = 3,
        )
        val reused = SampleRenderSnapshot.prepareSource(
            state = laterRegion,
            outputSampleRate = 16_000,
        )

        assertSame(expected = prepared.first(), actual = reused)
    }

    @Test
    fun replacingLibraryDoesNotReusePreviousProjectsPreparedPcm() {
        val original = source(id = "reused-id")
        AudioLibraryRepository.load(sources = listOf(original))
        val previous = SampleRenderSnapshot.prepareSource(
            state = state(source = original),
            outputSampleRate = 16_000,
        )
        val replacement = original.copy(rawData = byteArrayOf(0, 64, 0, 32, 0, 0, 0, 16))
        AudioLibraryRepository.load(sources = listOf(replacement))
        val current = SampleRenderSnapshot.prepareSource(
            state = state(source = replacement),
            outputSampleRate = 16_000,
        )

        assertNotSame(illegal = previous, actual = current)
        PreparedAudioSourceCache.clear()
    }

    @Test
    fun releasingDeviceDropsPreparedPcmAndCanPrepareAgain() {
        val source = source(id = "release")
        AudioLibraryRepository.load(sources = listOf(source))
        val device = SampleChainDevice().apply { state.value = this@SamplePreparedAudioSharingTest.state(source = source) }
        val configuration = AudioConfiguration(
            sampleRate = 16_000,
            channels = 2,
            periodFrames = 16,
            maximumBlockFrames = 64,
        )
        try {
            device.prepareAudio(configuration = configuration)
            val prepared = requireNotNull(device.preparedPcmData())
            device.releaseAudio()
            assertNull(actual = device.preparedPcmData())
            device.prepareAudio(configuration = configuration)
            assertSame(expected = prepared, actual = device.preparedPcmData())
        } finally {
            device.releaseAudio()
            device.dispose()
        }
    }

    @Test
    fun transientCacheEvictionKeepsPromotedProjectSources() {
        val sources = List(66) { index -> source(id = "transient-$index") }
        AudioLibraryRepository.load(sources = sources)
        val first = prepareTransient(source = sources.first())
        val projectSource = requireNotNull(SampleRenderSnapshot.prepareSource(
            state = state(source = sources.first()),
            outputSampleRate = 16_000,
        ))
        assertSame(expected = first, actual = projectSource)
        val oldestTransient = prepareTransient(source = sources[1])
        sources.drop(2).forEach { prepareTransient(source = it) }
        assertSame(expected = projectSource, actual = prepareTransient(source = sources.first()))
        assertNotSame(illegal = oldestTransient, actual = prepareTransient(source = sources[1]))
    }

    @Test
    fun removingUnrelatedSourceKeepsRemainingPreparedPcmShared() {
        val sources = List(2) { index -> source(id = "removal-$index") }
        AudioLibraryRepository.load(sources = sources)
        val previous = SampleRenderSnapshot.prepareSource(
            state = state(source = sources.first()),
            outputSampleRate = 16_000,
        )
        AudioLibraryRepository.remove(sourceId = sources.last().id)
        val current = SampleRenderSnapshot.prepareSource(
            state = state(source = sources.first()),
            outputSampleRate = 16_000,
        )
        assertSame(expected = previous, actual = current)
    }

    @Test
    fun preparingLibraryForTimelineKeepsSourcesAcrossTransientEvictions() {
        val sources = List(65) { index -> source(id = "timeline-$index") }
        AudioLibraryRepository.load(sources = sources)
        val engine = AudioPlaybackEngine(chain = AudioChain())
        try {
            engine.prepare(configuration = AudioConfiguration(
                sampleRate = 16_000,
                channels = 2,
                periodFrames = 16,
                maximumBlockFrames = 64,
            ))
            engine.prepareSources(sources = sources.map { source ->
                ByteArrayPcmAudioSource(
                    id = source.id,
                    sampleRate = source.sampleRate,
                    channels = source.channels,
                    bitDepth = source.bitDepth,
                    rawData = source.rawData,
                )
            })
            val first = prepareTransient(source = sources.first())
            repeat(65) { index -> prepareTransient(source = source(id = "temporary-$index")) }
            assertSame(expected = first, actual = prepareTransient(source = sources.first()))
        } finally {
            engine.release()
        }
    }

    private fun prepareTransient(source: AudioSource) = PreparedAudioSourceCache.getOrPrepare(
        source = ByteArrayPcmAudioSource(
            id = source.id,
            sampleRate = source.sampleRate,
            channels = source.channels,
            bitDepth = source.bitDepth,
            rawData = source.rawData,
        ),
        outputRate = 16_000,
    )

    private fun source(id: String): AudioSource = AudioSource(
        id = id,
        fileName = "$id.wav",
        rawData = byteArrayOf(0, 0, 0, 16, 0, 32, 0, 0),
        sampleRate = 8_000,
        channels = 1,
        bitDepth = 16,
    )

    private fun state(source: AudioSource): SampleChainDeviceState = SampleChainDeviceState(
        fileName = source.fileName,
        sourceId = source.id,
        sampleRate = source.sampleRate,
        channels = source.channels,
        bitDepth = source.bitDepth,
        isLoaded = true,
    )
}
