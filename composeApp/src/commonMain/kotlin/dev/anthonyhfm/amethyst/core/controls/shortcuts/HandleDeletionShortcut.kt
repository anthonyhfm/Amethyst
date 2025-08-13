package dev.anthonyhfm.amethyst.core.controls.shortcuts

import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import kotlinx.coroutines.flow.update

fun handleDeletionShortcut(): Boolean {
    when {
        SelectionManager.selections.value.any { it is Selectable.GradientStep } -> {
            SelectionManager.selections.value.filterIsInstance<Selectable.GradientStep>().forEach { step ->
                if (step.parent.state.value.gradientData.size - 1 < 1) return@forEach

                step.parent.state.update {
                    it.copy(
                        gradientData = it.gradientData.toMutableList().apply {
                            removeAll { it.selectionUUID == step.selectionUUID }
                        }
                    )
                }
            }

            return true
        }
    }

    return false
}