package dev.anthonyhfm.amethyst.devices.effects.composition

data class CompositionClipContext(
    val clipId: String,
    val startTimeMs: Long,
    val durationMs: Long,
) {
    val endTimeMs: Long get() = startTimeMs + durationMs

    fun localTimeMs(progress: Float): Long {
        return (durationMs.toDouble() * progress.coerceIn(minimumValue = 0f, maximumValue = 1f)).toLong()
    }

    fun progressAt(positionMs: Long): Float {
        return ((positionMs - startTimeMs).toDouble() / durationMs.coerceAtLeast(minimumValue = 1L))
            .toFloat()
            .coerceIn(minimumValue = 0f, maximumValue = 1f)
    }
}
