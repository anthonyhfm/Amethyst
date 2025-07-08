package dev.anthonyhfm.amethyst.core.audio

/**
 * # AudioPlayer
 *
 * The AudioPlayer interface provides methods to load and play audio files.
 * This implementation is expected to be platform-specific, allowing for
 * different audio handling capabilities across platforms.
 */
expect object AudioPlayer {
    fun loadAudio(data: ByteArray, uuid: String? = null): String
    fun playAudio(audioKey: String)
    fun preloadFromAudioClip(audioClip: AudioClip)
}