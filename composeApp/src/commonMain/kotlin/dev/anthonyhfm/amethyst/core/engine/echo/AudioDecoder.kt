package dev.anthonyhfm.amethyst.core.engine.echo

import dev.anthonyhfm.amethyst.core.engine.elements.Signal

/**
 * Common interface for audio decoding across all platforms
 */
expect object AudioDecoder {

    /**
     * Decode audio file to PCM data
     * @param filePath Path to the audio file
     * @param sampleStart Optional start position in samples (for trimming)
     * @param sampleEnd Optional end position in samples (for trimming)
     * @return AudioSignal with raw PCM data or null if decoding failed
     */
    suspend fun decodeAudioFile(
        filePath: String,
        sampleStart: Long? = null,
        sampleEnd: Long? = null
    ): Signal.AudioSignal?

    /**
     * Decode audio data from byte array to PCM data
     * @param audioData Raw audio file data
     * @param fileName Original filename for format detection
     * @param sampleStart Optional start position in samples (for trimming)
     * @param sampleEnd Optional end position in samples (for trimming)
     * @return AudioSignal with raw PCM data or null if decoding failed
     */
    suspend fun decodeAudioData(
        audioData: ByteArray,
        fileName: String,
        sampleStart: Long? = null,
        sampleEnd: Long? = null
    ): Signal.AudioSignal?

    /**
     * Check if the given file format is supported
     * @param fileName File name with extension
     * @return true if format is supported
     */
    fun isFormatSupported(fileName: String): Boolean

    /**
     * Get supported audio formats
     * @return List of supported file extensions
     */
    fun getSupportedFormats(): List<String>
}
