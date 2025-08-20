package dev.anthonyhfm.amethyst.core.audio

actual object AudioPlayer {
    actual fun loadAudio(data: ByteArray, uuid: String?): String {
        TODO("Not yet implemented")
    }

    actual fun playAudio(audioKey: String) {
    }

    actual fun preloadFromAudioClip(audioClip: AudioClip) {
    }

    actual fun stopAudio(audioKey: String) {
        TODO("Not yet implemented")
    }

    actual fun getAudioClip(data: ByteArray): AudioClip? {
        TODO("Not yet implemented")
    }

    actual fun getAudioClip(data: ByteArray, sampleStart: Long, sampleEnd: Long): AudioClip? {
        TODO("Not yet implemented")
    }
}