package dev.anthonyhfm.amethyst.core.controls.selection

import kotlinx.coroutines.flow.MutableStateFlow

object SelectionManager {
    val selections: MutableStateFlow<List<Selectable>> = MutableStateFlow(emptyList())

    fun select(element: Selectable, single: Boolean = true) {
        if (single) {
            selections.value = emptyList()
        } else if (selections.value.find { it::class.simpleName == element::class.simpleName } != null) {
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