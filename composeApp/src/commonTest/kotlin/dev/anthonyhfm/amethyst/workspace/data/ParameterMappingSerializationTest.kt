package dev.anthonyhfm.amethyst.workspace.data

import dev.anthonyhfm.amethyst.core.parameter.ParameterAddress
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.core.util.AmethystProtoBuf
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalSerializationApi::class)
class ParameterMappingSerializationTest {
    @Test
    fun workspaceRoundTripPreservesMappingAddressesAndIdentity() {
        val mapping = ParameterMapping(
            id = "mapping-a",
            macroId = "macro-a",
            target = ParameterAddress("device-a", "gain"),
            minimum = 0.2f,
            maximum = 0.8f,
            inverted = true,
            mode = ParameterMappingMode.Additive,
        )
        val workspace = SavableWorkspaceData(parameterMappings = listOf(mapping))

        val bytes = AmethystProtoBuf.encodeToByteArray(SavableWorkspaceData.serializer(), workspace)
        val restored = AmethystProtoBuf.decodeFromByteArray(SavableWorkspaceData.serializer(), bytes)

        assertEquals(listOf(mapping), restored.parameterMappings)
    }

    @Test
    fun localMappingChangesUndoAndRedoWhileRemoteSyncDoesNotCreateHistory() {
        val mapping = ParameterMapping(
            id = "mapping-undo",
            macroId = "macro-a",
            target = ParameterAddress("device-a", "gain"),
        )
        UndoManager.clear()
        WorkspaceRepository.setParameterMappings(emptyList(), fromRemote = true, undoable = false)
        try {
            WorkspaceRepository.setParameterMappings(listOf(mapping))
            assertEquals(listOf(mapping), WorkspaceRepository.parameterMappings.value)
            UndoManager.undo()
            assertEquals(emptyList(), WorkspaceRepository.parameterMappings.value)
            UndoManager.redo()
            assertEquals(listOf(mapping), WorkspaceRepository.parameterMappings.value)

            UndoManager.clear()
            WorkspaceRepository.setParameterMappings(emptyList(), fromRemote = true)
            assertEquals(false, UndoManager.canUndo())
        } finally {
            WorkspaceRepository.setParameterMappings(emptyList(), fromRemote = true, undoable = false)
            UndoManager.clear()
        }
    }
}
