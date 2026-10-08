package dev.anthonyhfm.amethyst.timeline.locators

import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoableAction
import dev.anthonyhfm.amethyst.timeline.TimelineRepository
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository

object TimelineLocatorRepository {
    val controller = TimelineLocatorController(
        onChange = { before, after ->
            UndoManager.addAction(
                action = UndoableAction.TimelineLocatorsChange(beforeLocators = before, afterLocators = after),
            )
        },
    )
    val locators = controller.locators

    fun load(locators: List<dev.anthonyhfm.amethyst.timeline.data.TimelineLocator>) {
        controller.load(locators = locators)
    }

    fun jump(id: String) {
        val locator = locators.value.firstOrNull { it.id == id } ?: return
        TimelineRepository.setPlayheadPosition(positionMs = locator.timeMs(bpm = WorkspaceRepository.bpm.value))
    }
}
