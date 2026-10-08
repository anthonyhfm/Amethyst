package dev.anthonyhfm.amethyst.timeline.utils

import dev.anthonyhfm.amethyst.timeline.data.ChainEffectEntry
import dev.anthonyhfm.amethyst.timeline.data.TimelineEntry

enum class ChainEffectEditMode {
    MOVE,
    LEFT_EDGE,
    RIGHT_EDGE,
}

data class ChainEffectSpan(
    val startMs: Long,
    val durationMs: Long,
) {
    val endMs: Long get() = startMs + durationMs
}

internal fun resolveChainEffectSpan(
    entry: ChainEffectEntry,
    mode: ChainEffectEditMode,
    deltaMs: Long,
    blockers: List<TimelineEntry>,
    minDurationMs: Long,
    maxDurationMs: Long,
    snapTime: (Long) -> Long,
): ChainEffectSpan {
    val original = ChainEffectSpan(startMs = entry.startTimeMs, durationMs = entry.durationMs)
    val naturalLimit = maxDurationMs.coerceAtLeast(1L)
    val minimum = minOf(minDurationMs.coerceAtLeast(1L), entry.durationMs.coerceAtLeast(1L))
    return when (mode) {
        ChainEffectEditMode.MOVE -> {
            val start = snapTime((entry.startTimeMs + deltaMs).coerceAtLeast(0L))
            if (blockers.any { start >= it.startTimeMs && start < it.endTimeMs }) {
                original
            } else {
                val nextStart = blockers.filter { it.startTimeMs > start }.minOfOrNull { it.startTimeMs }
                ChainEffectSpan(
                    startMs = start,
                    durationMs = minOf(entry.durationMs, nextStart?.minus(start) ?: Long.MAX_VALUE).coerceAtLeast(1L),
                )
            }
        }
        ChainEffectEditMode.LEFT_EDGE -> {
            val previousEnd = blockers.filter { it.endTimeMs <= entry.startTimeMs }.maxOfOrNull { it.endTimeMs } ?: 0L
            val earliest = maxOf(previousEnd, entry.endTimeMs - naturalLimit, 0L)
            val latest = entry.endTimeMs - minimum
            if (earliest > latest) {
                original
            } else {
                val start = snapTime((entry.startTimeMs + deltaMs).coerceAtLeast(0L)).coerceIn(earliest, latest)
                ChainEffectSpan(startMs = start, durationMs = entry.endTimeMs - start)
            }
        }
        ChainEffectEditMode.RIGHT_EDGE -> {
            val nextStart = blockers.filter { it.startTimeMs >= entry.endTimeMs }.minOfOrNull { it.startTimeMs }
            val limit = minOf(naturalLimit, nextStart?.minus(entry.startTimeMs) ?: Long.MAX_VALUE).coerceAtLeast(1L)
            val end = snapTime((entry.endTimeMs + deltaMs).coerceAtLeast(entry.startTimeMs + 1L))
            ChainEffectSpan(
                startMs = entry.startTimeMs,
                durationMs = (end - entry.startTimeMs).coerceIn(minOf(minimum, limit), limit),
            )
        }
    }
}
