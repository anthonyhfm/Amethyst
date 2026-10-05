package dev.anthonyhfm.amethyst.core.engine.audio.source

import dev.anthonyhfm.amethyst.core.util.AmethystProtoBuf
import dev.anthonyhfm.amethyst.timeline.data.AudioSource as ProjectAudioSource
import dev.anthonyhfm.amethyst.ui.components.computeWaveformEnvelope
import java.lang.ref.WeakReference
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalSerializationApi::class)
class FileBackedPcmTest {
    @AfterTest
    fun tearDown() {
        PreparedAudioSourceCache.configurePersistentRoot(root = null)
        PreparedAudioSourceCache.clear()
        ProjectPcmFiles.clear()
    }

    @Test
    fun mappedPcmPreservesEverySupportedSampleFormatAndBoundaries() {
        listOf(8, 16, 24, 32).forEach { depth ->
            val bytes = ByteArray(size = depth / 8 * 2 * 128) { index -> (index * 37).toByte() }
            val original = ByteArrayPcmAudioSource(
                id = "original",
                sampleRate = 44_100,
                channels = 2,
                bitDepth = depth,
                rawData = bytes,
            )
            val mapped = pcmSourceFromStorage(
                id = "mapped",
                sampleRate = 44_100,
                channels = 2,
                bitDepth = depth,
                storage = ProjectPcmFiles.store(bytes = bytes),
            )
            assertEquals(expected = 0, actual = mapped.residentPcmBytes)
            for (frame in -1L..128L) {
                repeat(2) { channel ->
                    assertEquals(
                        expected = original.sample(frameIndex = frame, channel = channel),
                        actual = mapped.sample(frameIndex = frame, channel = channel),
                    )
                }
            }
        }
    }

    @Test
    fun largePreparedPcmKeepsTheExistingSincAnd24BitOutput() {
        val bytes = ByteArray(size = 1_500_000) { index -> (index * 43).toByte() }
        val source = ByteArrayPcmAudioSource(
            id = "large",
            sampleRate = 8_000,
            channels = 1,
            bitDepth = 16,
            rawData = bytes,
        )
        val prepared = PreparedAudioSourceCache.getOrPrepare(source = source, outputRate = 16_000) as PcmAudioSource
        assertEquals(expected = 0, actual = prepared.residentPcmBytes)
        assertEquals(expected = 4_500_000, actual = prepared.pcmByteCount)
        val resampler = PolyphaseSincResampler(sourceRate = 8_000, outputRate = 16_000, channels = 1)
        val frame = FloatArray(size = 1)
        val positions = listOf(0L, 1L, 8191L, 8192L, prepared.frameCount - 1L) +
            List(1000) { index -> index * 1499L }
        positions.forEach { position ->
            resampler.reset(sourceFrame = position / 2.0)
            resampler.readFrame(source = source, destination = frame)
            val normalized = frame[0].coerceIn(-1f, 1f)
            val pcm = if (normalized <= -1f) -8_388_608 else (normalized * 8_388_607f).toInt()
            assertEquals(expected = pcm / 8388608f, actual = prepared.sample(frameIndex = position, channel = 0))
        }
    }

    @Test
    fun originalBytesLeaveTheHeapAndRemainReadableAfterProjectCleanup() {
        val (source, reference) = createLargeSource()
        repeat(30) {
            System.gc()
            if (reference.get() == null) {
                return@repeat
            }
            Thread.sleep(20)
        }
        assertNull(actual = reference.get())
        assertNull(actual = source.residentPcmData)
        assertSame(expected = source.pcmBytes, actual = source.copy(fileName = "renamed.wav").pcmBytes)
        ProjectPcmFiles.clear()
        val destination = java.nio.file.Files.createTempFile("amethyst-detached-pcm", ".pcm").toFile()
        try {
            source.writePcm(path = destination.absolutePath)
            assertContentEquals(expected = source.rawData, actual = destination.readBytes())
        } finally {
            destination.delete()
        }
        val restored = source.rawData
        assertEquals(expected = source.pcmByteCount, actual = restored.size)
        restored.indices.forEach { index -> assertEquals(expected = (index * 17).toByte(), actual = restored[index]) }
    }

    @Test
    fun savingMappedSourcesPreservesOriginalBytesFormatAndLegacySchema() {
        val (source, _) = createLargeSource()
        val encoded = AmethystProtoBuf.encodeToByteArray(value = source)
        val legacy = AmethystProtoBuf.decodeFromByteArray<LegacySource>(bytes = encoded)
        assertEquals(expected = 44_100, actual = legacy.sampleRate)
        assertEquals(expected = 24, actual = legacy.bitDepth)
        assertContentEquals(expected = source.rawData, actual = legacy.rawData)
        val oldEncoded = AmethystProtoBuf.encodeToByteArray(value = legacy)
        assertContentEquals(expected = oldEncoded, actual = encoded)
        val loaded = AmethystProtoBuf.decodeFromByteArray<ProjectAudioSource>(bytes = oldEncoded)
        assertContentEquals(expected = legacy.rawData, actual = loaded.rawData)
        assertNull(actual = loaded.residentPcmData)
    }

    @Test
    fun projectBundleReadsAndWritesMappedOriginalsWithoutHeapCopies() {
        val (source, _) = createLargeSource()
        val path = java.nio.file.Files.createTempFile("amethyst-bundle-pcm", ".pcm").toFile()
        try {
            source.writePcm(path = path.absolutePath)
            val loaded = source.copy(rawData = ByteArray(size = 0)).copyFromPcmFile(path = path.absolutePath)!!
            assertNull(actual = loaded.residentPcmData)
            assertEquals(expected = source.pcmByteCount, actual = loaded.pcmByteCount)
            assertNull(actual = mapPcmFile(path = path.absolutePath, expectedBytes = source.pcmByteCount - 1))
            assertContentEquals(expected = source.rawData, actual = loaded.rawData)
            assertContentEquals(
                expected = source.rawData.copyOfRange(fromIndex = 24, toIndex = 96),
                actual = loaded.pcmRegionBytes(fromIndex = 24, toIndex = 96),
            )
            loaded.pcmSource().pcmBytes.releaseCachedPages()
            assertEquals(
                expected = source.pcmSource().sample(frameIndex = 8192L, channel = 1),
                actual = loaded.pcmSource().sample(frameIndex = 8192L, channel = 1),
            )
        } finally {
            path.delete()
        }
    }

    @Test
    fun replacingASavedBundleDoesNotChangeAnExistingMappedSource() {
        val (source, _) = createLargeSource()
        val directory = java.nio.file.Files.createTempDirectory("amethyst-bundle-replacement").toFile()
        try {
            val path = java.io.File(directory, "original.pcm")
            source.writePcm(path = path.absolutePath)
            val loaded = source.copyFromPcmFile(path = path.absolutePath)!!
            val replacement = java.io.File(directory, "replacement.pcm")
            replacement.writeBytes(ByteArray(size = source.pcmByteCount))
            java.nio.file.Files.move(
                replacement.toPath(),
                path.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            )
            loaded.pcmSource().pcmBytes.releaseCachedPages()
            assertContentEquals(expected = source.rawData, actual = loaded.rawData)
            val snapshot = java.io.File(directory, "snapshot.pcm")
            loaded.writePcm(path = snapshot.absolutePath)
            assertContentEquals(expected = source.rawData, actual = snapshot.readBytes())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun waveformPeaksMatchThePreviousDecodedFloatEnvelope() {
        val bytes = byteArrayOf(0, 0, 0, 64, 0, -128, 0, -64)
        val source = pcmSourceFromStorage(
            id = "waveform",
            sampleRate = 8_000,
            channels = 1,
            bitDepth = 16,
            storage = ProjectPcmFiles.store(bytes = bytes),
        )
        val expected = computeWaveformEnvelope(
            samples = floatArrayOf(0f, 0.5f, -1f, -0.5f),
            startSample = 1L,
            endSample = 4L,
            zoomLevel = 1f,
            sampleRate = 8_000,
            widthPx = 20,
        )
        val actual = computeWaveformEnvelope(
            source = source,
            startSample = 1L,
            endSample = 4L,
            zoomLevel = 1f,
            sampleRate = 8_000,
            widthPx = 20,
        )
        assertContentEquals(expected = expected, actual = actual)
        assertTrue(actual = actual.isNotEmpty())
    }

    private fun createLargeSource(): Pair<ProjectAudioSource, WeakReference<ByteArray>> {
        val bytes = ByteArray(size = 4_194_306) { index -> (index * 17).toByte() }
        val source = ProjectAudioSource(
            id = "original",
            fileName = "original.wav",
            rawData = bytes,
            sampleRate = 44_100,
            channels = 2,
            bitDepth = 24,
        )
        return source to WeakReference(bytes)
    }

    @Serializable
    private data class LegacySource(
        @ProtoNumber(1) val id: String,
        @ProtoNumber(2) val fileName: String,
        @ProtoNumber(3) val rawData: ByteArray,
        @ProtoNumber(4) val sampleRate: Int,
        @ProtoNumber(5) val channels: Int,
        @ProtoNumber(6) val bitDepth: Int,
        @ProtoNumber(7) val stemMetadata: dev.anthonyhfm.amethyst.timeline.data.StemMetadata? = null,
        @ProtoNumber(8) val isLibraryAsset: Boolean = true,
    )
}
