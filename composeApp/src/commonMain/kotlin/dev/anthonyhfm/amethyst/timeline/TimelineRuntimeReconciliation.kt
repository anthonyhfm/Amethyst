package dev.anthonyhfm.amethyst.timeline

/**
 * Reconciles timeline snapshot values with stateful playback runtimes.
 *
 * Snapshot entries are intentionally immutable copies and therefore do not own transient
 * playback state (Echo source ids, active MIDI notes, and similar state). Matching entries
 * keep the previous runtime, while entries no longer represented by the timeline are stopped.
 */
internal fun <T> reconcileTimelineRuntimes(
    previous: Collection<T>,
    current: Collection<T>,
    matches: (previous: T, current: T) -> Boolean,
    keepPreviousRuntime: (current: T, previous: T) -> T,
    stopPreviousRuntime: (previous: T) -> Unit,
): Set<T> {
    val unmatchedCurrent = current.toMutableList()
    return previous.mapNotNullTo(linkedSetOf()) { previousEntry ->
        val currentIndex = unmatchedCurrent.indexOfFirst { currentEntry ->
            matches(previousEntry, currentEntry)
        }
        if (currentIndex < 0) {
            stopPreviousRuntime(previousEntry)
            null
        } else {
            keepPreviousRuntime(unmatchedCurrent.removeAt(currentIndex), previousEntry)
        }
    }
}
