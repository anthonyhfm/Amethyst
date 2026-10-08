package dev.anthonyhfm.amethyst.timeline.locators

import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.timeline.data.TimelineLocator
import dev.anthonyhfm.amethyst.timeline.utils.GridUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class TimelineLocatorController(
    private val onChange: (List<TimelineLocator>, List<TimelineLocator>) -> Unit = { _, _ -> },
) {
    private val mutableLocators = MutableStateFlow<List<TimelineLocator>>(value = emptyList())
    val locators = mutableLocators.asStateFlow()

    fun load(locators: List<TimelineLocator>) {
        mutableLocators.value = locators
            .filter { it.id.isNotBlank() && it.name.isNotBlank() && it.beat.isFinite() && it.beat >= 0.0 }
            .distinctBy { it.id }
            .sortedBy { it.beat }
    }

    fun create(name: String, timeMs: Long, bpm: Double): TimelineLocator? {
        val normalizedName = name.trim()
        if (normalizedName.isEmpty()) {
            return null
        }
        val locator = TimelineLocator(
            id = UUID.randomUUID(),
            name = normalizedName,
            beat = timeMs.coerceAtLeast(minimumValue = 0L) / GridUtils.beatDurationMs(bpm = bpm),
        )
        update(after = locators.value + locator)
        return locator
    }

    fun edit(id: String, name: String, beat: Double) {
        val normalizedName = name.trim()
        if (normalizedName.isEmpty() || !beat.isFinite() || beat < 0.0) {
            return
        }
        update(
            after = locators.value.map { locator ->
                if (locator.id == id) {
                    locator.copy(name = normalizedName, beat = beat)
                } else {
                    locator
                }
            },
        )
    }

    fun move(id: String, timeMs: Long, bpm: Double) {
        val locator = locators.value.firstOrNull { it.id == id } ?: return
        edit(
            id = id,
            name = locator.name,
            beat = timeMs.coerceAtLeast(minimumValue = 0L) / GridUtils.beatDurationMs(bpm = bpm),
        )
    }

    fun delete(id: String) {
        update(after = locators.value.filterNot { it.id == id })
    }

    private fun update(after: List<TimelineLocator>) {
        val before = locators.value
        val sorted = after.sortedBy { it.beat }
        if (before == sorted) {
            return
        }
        mutableLocators.value = sorted
        onChange(before, sorted)
    }
}
