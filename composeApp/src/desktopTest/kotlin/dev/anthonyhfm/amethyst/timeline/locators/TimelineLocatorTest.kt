@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package dev.anthonyhfm.amethyst.timeline.locators

import dev.anthonyhfm.amethyst.core.util.AmethystProtoBuf
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.timeline.data.TimelineLocator
import dev.anthonyhfm.amethyst.workspace.data.SavableWorkspaceData
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.test.Test
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimelineLocatorTest {
    @BeforeTest
    @AfterTest
    fun clearLocatorState() {
        TimelineLocatorRepository.load(locators = emptyList())
        UndoManager.clear()
    }

    @Test
    fun sharedUndoHistoryRestoresLocatorEditsAndWorkspaceSaveIncludesThem() {
        val locator = assertNotNull(
            actual = TimelineLocatorRepository.controller.create(name = "Verse", timeMs = 2_000L, bpm = 120.0),
        )
        TimelineLocatorRepository.controller.edit(id = locator.id, name = "Chorus", beat = 16.0)
        UndoManager.undo()
        assertEquals(expected = locator, actual = TimelineLocatorRepository.locators.value.single())
        UndoManager.redo()
        assertEquals(expected = "Chorus", actual = TimelineLocatorRepository.locators.value.single().name)
        assertEquals(
            expected = TimelineLocatorRepository.locators.value,
            actual = WorkspaceRepository.saveWorkspace().timelineLocators,
        )
        UndoManager.undo()
        UndoManager.undo()
        assertTrue(actual = TimelineLocatorRepository.locators.value.isEmpty())
        UndoManager.redo()
        assertEquals(expected = locator, actual = TimelineLocatorRepository.locators.value.single())
    }

    @Test
    fun locatorsStayOnMusicalBeatsWhenTempoChanges() {
        val controller = TimelineLocatorController()
        val locator = assertNotNull(actual = controller.create(name = "Verse", timeMs = 4_000L, bpm = 120.0))

        assertEquals(expected = 8.0, actual = locator.beat)
        assertEquals(expected = 4_000L, actual = locator.timeMs(bpm = 120.0))
        assertEquals(expected = 8_000L, actual = locator.timeMs(bpm = 60.0))
        assertEquals(expected = 2_000L, actual = locator.timeMs(bpm = 240.0))
    }

    @Test
    fun createEditMoveDeleteCanRestoreSnapshotsWithoutRecordingAnotherChange() {
        val changes = mutableListOf<Pair<List<TimelineLocator>, List<TimelineLocator>>>()
        val controller = TimelineLocatorController(
            onChange = { before, after -> changes.add(element = before to after) },
        )
        val first = assertNotNull(actual = controller.create(name = "Intro", timeMs = 0L, bpm = 120.0))
        val second = assertNotNull(actual = controller.create(name = "Outro", timeMs = 8_000L, bpm = 120.0))
        assertNotEquals(illegal = first.id, actual = second.id)

        controller.edit(id = second.id, name = " Chorus ", beat = 8.0)
        controller.move(id = second.id, timeMs = 2_000L, bpm = 120.0)
        assertEquals(expected = "Chorus", actual = controller.locators.value.last().name)
        assertEquals(expected = 4.0, actual = controller.locators.value.last().beat)
        controller.delete(id = second.id)
        assertEquals(expected = listOf(first), actual = controller.locators.value)
        assertEquals(expected = 5, actual = changes.size)

        controller.load(locators = changes.last().first)
        assertEquals(expected = 2, actual = controller.locators.value.size)
        controller.load(locators = changes.last().second)
        assertEquals(expected = listOf(first), actual = controller.locators.value)
        assertEquals(expected = 5, actual = changes.size)
    }

    @Test
    fun emptyNamesInvalidPositionsAndNoOpChangesDoNotCreateHistory() {
        var changes = 0
        val controller = TimelineLocatorController(onChange = { _, _ -> changes++ })
        assertNull(actual = controller.create(name = " ", timeMs = 0L, bpm = 120.0))
        val locator = assertNotNull(actual = controller.create(name = "Intro", timeMs = -500L, bpm = Double.NaN))
        assertEquals(expected = 0.0, actual = locator.beat)

        controller.edit(id = locator.id, name = "", beat = 1.0)
        controller.edit(id = locator.id, name = "Changed", beat = Double.NaN)
        controller.edit(id = locator.id, name = "Changed", beat = -1.0)
        controller.edit(id = locator.id, name = locator.name, beat = locator.beat)
        controller.delete(id = "missing")
        assertEquals(expected = 1, actual = changes)
    }

    @Test
    fun workspaceRoundTripKeepsStableLocatorIdentityAndFractionalBeat() {
        val locator = TimelineLocator(id = "stable-locator", name = "Break", beat = 32.125)
        val workspace = SavableWorkspaceData(timelineLocators = listOf(locator))
        val bytes = AmethystProtoBuf.encodeToByteArray(serializer = SavableWorkspaceData.serializer(), value = workspace)
        val restored = AmethystProtoBuf.decodeFromByteArray(deserializer = SavableWorkspaceData.serializer(), bytes = bytes)

        assertEquals(expected = workspace.timelineLocators, actual = restored.timelineLocators)
        val controller = TimelineLocatorController()
        controller.load(locators = restored.timelineLocators)
        assertEquals(expected = locator, actual = controller.locators.value.single())
    }

    @Test
    fun workspaceWithoutLocatorFieldLoadsWithEmptyList() {
        val bytes = AmethystProtoBuf.encodeToByteArray(serializer = LegacyWorkspace.serializer(), value = LegacyWorkspace(title = "Old project"))
        val restored = AmethystProtoBuf.decodeFromByteArray(deserializer = SavableWorkspaceData.serializer(), bytes = bytes)

        assertEquals(expected = "Old project", actual = restored.title)
        assertTrue(actual = restored.timelineLocators.isEmpty())
    }

    @Serializable
    private data class LegacyWorkspace(
        @ProtoNumber(2)
        val title: String,
    )
}
