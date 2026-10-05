package dev.anthonyhfm.amethyst.core.engine.audio.source

import kotlinx.atomicfu.atomic

/**
 * Small process-local cache of PCM sources prepared at the active hardware rate.
 *
 * Preparation is a control-thread operation. Returned sources are immutable and
 * can be read directly by the realtime renderer without sample-rate conversion.
 */
object PreparedAudioSourceCache {
    private data class Key(
        val id: String,
        val sourceRate: Int,
        val outputRate: Int,
        val channels: Int,
        val frameCount: Long,
    )

    private data class Entry(
        val sourceIdentity: Any?,
        val prepared: AudioSource,
        val retainForProject: Boolean,
    )

    private val entries = atomic<Map<Key, Entry>>(emptyMap())
    private val persistentRoot = atomic<String?>(null)

    /** A converted mobile project keeps prepared PCM for later offline opens. */
    fun configurePersistentRoot(root: String?) {
        if (persistentRoot.value != root) {
            entries.value = emptyMap()
            persistentRoot.value = root
        }
    }

    fun getOrPrepare(
        source: AudioSource,
        outputRate: Int,
        retainForProject: Boolean = false,
    ): AudioSource {
        require(outputRate > 0)
        if (source.sampleRate == outputRate) {
            return source
        }
        val key = Key(
            id = source.id,
            sourceRate = source.sampleRate,
            outputRate = outputRate,
            channels = source.channels,
            frameCount = source.frameCount,
        )
        val sourceIdentity = (source as? PcmAudioSource)?.pcmCacheIdentity ?: source
        cachedSource(key = key, sourceIdentity = sourceIdentity, retainForProject = retainForProject)?.let {
            return it
        }

        val root = persistentRoot.value
        val diskKey = if (root != null && source is PcmAudioSource) {
            "${source.id.hashCode().toUInt().toString(16)}-${source.pcmBytes.contentHash().toUInt().toString(16)}-" +
                "${source.sampleRate}-$outputRate-${source.channels}-${source.frameCount}"
        } else {
            null
        }
        val outputFrames = (source.frameCount.toDouble() * outputRate / source.sampleRate)
            .toLong().coerceAtLeast(1L)
        val expectedBytes = outputFrames * source.channels * BYTES_PER_PCM24_SAMPLE
        val cachedStorage = if (root != null && diskKey != null && expectedBytes <= Int.MAX_VALUE) {
            runCatching {
                if (expectedBytes >= MAPPED_PCM_THRESHOLD_BYTES) {
                    mapPcmFile(path = "$root/$diskKey.pcm", expectedBytes = expectedBytes.toInt())
                } else {
                    PreparedAudioDiskCache.read(root = root, key = diskKey, expectedBytes = expectedBytes.toInt())
                        ?.let { InMemoryPcmBytes(bytes = it) }
                }
            }.getOrNull()
        } else {
            null
        }
        val prepared = cachedStorage?.let {
            pcmSourceFromStorage(id = source.id, sampleRate = outputRate, channels = source.channels, bitDepth = 24, storage = it)
        } ?: (if (source is PcmAudioSource) {
            platformResampleToPcm24(source = source, outputRate = outputRate)
        } else {
            null
        } ?: resampleToPcm24(source = source, outputRate = outputRate)).also { result ->
            if (root != null && diskKey != null && result is PcmAudioSource) {
                runCatching {
                    val path = result.pcmBytes.filePath
                    if (path != null) {
                        PreparedAudioDiskCache.copyFile(root = root, key = diskKey, sourcePath = path)
                    } else {
                        PreparedAudioDiskCache.write(root = root, key = diskKey, bytes = result.pcmBytes.readAll())
                    }
                }
            }
        }
        (source as? PcmAudioSource)?.pcmBytes?.releaseCachedPages()
        (prepared as? PcmAudioSource)?.pcmBytes?.releaseCachedPages()
        while (true) {
            val current = entries.value
            val existing = current[key]?.takeIf { it.sourceIdentity === sourceIdentity }
            if (existing != null) {
                if (!retainForProject || existing.retainForProject) {
                    return existing.prepared
                }
                if (
                    entries.compareAndSet(
                        expect = current,
                        update = current + (key to existing.copy(retainForProject = true)),
                    )
                ) {
                    return existing.prepared
                }
                continue
            }
            val updated = current + (key to Entry(
                sourceIdentity = sourceIdentity,
                prepared = prepared,
                retainForProject = retainForProject,
            ))
            val transientEntries = updated.entries.filter { !it.value.retainForProject }
            val evictedKeys = transientEntries
                .take((transientEntries.size - MAXIMUM_ENTRIES).coerceAtLeast(0))
                .map { it.key }
            if (entries.compareAndSet(expect = current, update = updated - evictedKeys.toSet())) {
                return prepared
            }
        }
    }

    private fun cachedSource(
        key: Key,
        sourceIdentity: Any?,
        retainForProject: Boolean,
    ): AudioSource? {
        while (true) {
            val current = entries.value
            val existing = current[key]?.takeIf { it.sourceIdentity === sourceIdentity } ?: return null
            if (!retainForProject || existing.retainForProject) {
                return existing.prepared
            }
            if (
                entries.compareAndSet(
                    expect = current,
                    update = current + (key to existing.copy(retainForProject = true)),
                )
            ) {
                return existing.prepared
            }
        }
    }

    internal fun retainedPcmBytes(): Long = entries.value.values
        .mapNotNull { it.prepared as? PcmAudioSource }
        .distinctBy { it.pcmBytes }
        .sumOf { it.residentPcmBytes.toLong() }

    internal fun mappedPcmBytes(): Long = entries.value.values
        .mapNotNull { it.prepared as? PcmAudioSource }
        .filter { it.residentPcmBytes == 0 }
        .distinctBy { it.pcmBytes }
        .sumOf { it.pcmByteCount.toLong() }

    internal fun removeSources(sourceIds: Set<String>) {
        while (true) {
            val current = entries.value
            val updated = current.filterKeys { it.id !in sourceIds }
            if (entries.compareAndSet(expect = current, update = updated)) {
                return
            }
        }
    }

    fun clear() {
        entries.value = emptyMap()
    }

    private fun resampleToPcm24(source: AudioSource, outputRate: Int): AudioSource {
        val outputFrames = (
            source.frameCount.toDouble() * outputRate / source.sampleRate
            ).toLong().coerceAtLeast(1L)
        require(outputFrames <= Int.MAX_VALUE / (source.channels * BYTES_PER_PCM24_SAMPLE)) {
            "Prepared audio source is too large"
        }
        val storage = ProjectPcmFiles.prepare(
            expectedBytes = outputFrames.toInt() * source.channels * BYTES_PER_PCM24_SAMPLE,
        ) { writer ->
            val blockFrames = 8192
            val output = ByteArray(size = blockFrames * source.channels * BYTES_PER_PCM24_SAMPLE)
            val frame = FloatArray(size = source.channels)
            val resampler = PolyphaseSincResampler(
                sourceRate = source.sampleRate,
                outputRate = outputRate,
                channels = source.channels,
            )
            var outputFrame = 0
            while (outputFrame < outputFrames.toInt()) {
                val count = minOf(blockFrames, outputFrames.toInt() - outputFrame)
                var localFrame = 0
                while (localFrame < count) {
                    resampler.readFrame(source = source, destination = frame)
                    var channel = 0
                    while (channel < source.channels) {
                        writePcm24(
                            destination = output,
                            sampleIndex = localFrame * source.channels + channel,
                            sample = frame[channel],
                        )
                        channel++
                    }
                    resampler.advance()
                    localFrame++
                }
                writer.write(bytes = output, offset = 0, length = count * source.channels * BYTES_PER_PCM24_SAMPLE)
                outputFrame += count
            }
        }
        return pcmSourceFromStorage(
            id = source.id,
            sampleRate = outputRate,
            channels = source.channels,
            bitDepth = 24,
            storage = storage,
        )
    }

    private fun writePcm24(
        destination: ByteArray,
        sampleIndex: Int,
        sample: Float,
    ) {
        val normalized = sample.takeIf(Float::isFinite)?.coerceIn(-1f, 1f) ?: 0f
        val value = if (normalized <= -1f) {
            -8_388_608
        } else {
            (normalized * 8_388_607f).toInt()
        }
        val offset = sampleIndex * BYTES_PER_PCM24_SAMPLE
        destination[offset] = (value and 0xff).toByte()
        destination[offset + 1] = ((value ushr 8) and 0xff).toByte()
        destination[offset + 2] = ((value ushr 16) and 0xff).toByte()
    }

    private const val MAXIMUM_ENTRIES = 64
    private const val BYTES_PER_PCM24_SAMPLE = 3
}
