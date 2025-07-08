package dev.anthonyhfm.amethyst.core.audio

import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import kotlinx.serialization.Serializable

@Serializable
data class AudioClip(
    val name: String,
    val length: Long, // Length in milliseconds
    val data: ByteArray,
    val key: String,
)
