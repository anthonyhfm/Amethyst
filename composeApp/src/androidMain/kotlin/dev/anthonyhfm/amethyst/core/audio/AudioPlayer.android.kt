package dev.anthonyhfm.amethyst.core.audio

import io.github.vinceglb.filekit.core.PlatformInputStream

actual object AudioPlayer {
    actual fun loadAudio(data: ByteArray, uuid: String?): String {
        TODO()
    }

    actual fun playAudio(audioKey: String) {
    }

    actual fun preloadFromAudioClip(audioClip: AudioClip) {
    }
}