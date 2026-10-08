@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package dev.anthonyhfm.amethyst.timeline.data

import dev.anthonyhfm.amethyst.timeline.utils.GridUtils
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.math.roundToLong

@Serializable
data class TimelineLocator(
    @ProtoNumber(1)
    val id: String,
    @ProtoNumber(2)
    val name: String,
    @ProtoNumber(3)
    val beat: Double,
) {
    fun timeMs(bpm: Double): Long = (beat * GridUtils.beatDurationMs(bpm = bpm)).roundToLong().coerceAtLeast(minimumValue = 0L)
}
