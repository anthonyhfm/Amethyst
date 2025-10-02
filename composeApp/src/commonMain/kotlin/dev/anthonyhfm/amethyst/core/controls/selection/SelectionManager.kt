package dev.anthonyhfm.amethyst.core.controls.selection

import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Clock
import kotlin.time.ExperimentalTime


object SelectionManager {
    val selections: MutableStateFlow<List<Selectable>> = MutableStateFlow(emptyList())

    // Fire-and-forget request to trigger renaming on a specific list item.
    // Use parentUUID to avoid direct dependency on device classes.
    data class RenameTarget @OptIn(ExperimentalTime::class) constructor(
        val parentUUID: String,
        val groupIndex: Int,
        val token: Long = Clock.System.now().toEpochMilliseconds()
    )

    // Observers (e.g., Group/Multi group items) can listen to this to toggle rename mode.
    val renameRequest: MutableStateFlow<RenameTarget?> = MutableStateFlow(null)

    fun select(element: Selectable, single: Boolean = true) {
        if (single) {
            selections.value = emptyList()
        } else if (selections.value.find { it::class.simpleName == element::class.simpleName } == null) {
            selections.value = emptyList()
        }

        if (selections.value.find { it.selectionUUID == element.selectionUUID } == null) {
            selections.value += element
        }
    }

    fun clear() {
        selections.value = emptyList()
    }
}