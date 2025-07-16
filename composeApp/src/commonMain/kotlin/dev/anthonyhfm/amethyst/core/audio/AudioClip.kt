package dev.anthonyhfm.amethyst.core.audio

import kotlinx.serialization.Serializable

/**
 * Represents an audio clip with metadata and raw audio data
 */
@Serializable
data class AudioClip(
    val name: String,
    val length: Long, // Duration in milliseconds
    val data: ByteArray,
    val key: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as AudioClip

        if (name != other.name) return false
        if (length != other.length) return false
        if (!data.contentEquals(other.data)) return false
        if (key != other.key) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + length.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + key.hashCode()
        return result
    }
}
